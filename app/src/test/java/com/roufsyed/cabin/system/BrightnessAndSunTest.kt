package com.roufsyed.cabin.system

import com.roufsyed.cabin.data.CabinSettings
import com.roufsyed.cabin.data.InMemorySettings
import com.roufsyed.cabin.model.BrightnessMode
import com.roufsyed.cabin.model.BrightnessUi
import com.roufsyed.cabin.model.VolumeUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class BrightnessAndSunTest {

    // ---- Sunrise and sunset

    private fun assertNear(expected: LocalTime, actual: ZonedDateTime, what: String) {
        val minutes = Duration.between(expected, actual.toLocalTime()).abs().toMinutes()
        assertTrue("$what expected about $expected but was ${actual.toLocalTime()}", minutes <= 8)
    }

    private fun check(place: String, latitude: Double, longitude: Double, date: LocalDate, zone: String, rise: String, set: String) {
        val day = SunTimes.of(date, latitude, longitude, ZoneId.of(zone))!!
        assertNear(LocalTime.parse(rise), day.sunrise, "$place sunrise")
        assertNear(LocalTime.parse(set), day.sunset, "$place sunset")
        assertEquals(date, day.sunrise.toLocalDate())
        assertEquals(date, day.sunset.toLocalDate())
    }

    @Test
    fun `sunrise and sunset match published times within a few minutes`() {
        check("Bengaluru", 12.9716, 77.5946, LocalDate.of(2026, 11, 12), "Asia/Kolkata", "06:15", "17:51")
        check("New Delhi", 28.6139, 77.2090, LocalDate.of(2026, 1, 1), "Asia/Kolkata", "07:14", "17:36")
        check("London", 51.5074, -0.1278, LocalDate.of(2026, 6, 21), "Europe/London", "04:43", "21:21")
        check("Sydney", -33.8688, 151.2093, LocalDate.of(2026, 12, 21), "Australia/Sydney", "05:41", "20:05")
    }

    @Test
    fun `where the sun does not rise or set there is no answer`() {
        assertNull(SunTimes.of(LocalDate.of(2026, 6, 21), 69.65, 18.96, ZoneId.of("Europe/Oslo")))
        assertNull(SunTimes.of(LocalDate.of(2026, 12, 21), 69.65, 18.96, ZoneId.of("Europe/Oslo")))
    }

    private fun at(time: String) = ZonedDateTime.of(LocalDate.of(2026, 11, 12), LocalTime.parse(time), ZoneId.of("Asia/Kolkata"))

    @Test
    fun `night is before sunrise and after sunset at the known position`() {
        assertTrue(SunTimes.isNight(at("05:00"), 12.97, 77.59))
        assertFalse(SunTimes.isNight(at("07:00"), 12.97, 77.59))
        assertFalse(SunTimes.isNight(at("12:00"), 12.97, 77.59))
        assertFalse(SunTimes.isNight(at("17:30"), 12.97, 77.59))
        assertTrue(SunTimes.isNight(at("18:15"), 12.97, 77.59))
        assertTrue(SunTimes.isNight(at("23:59"), 12.97, 77.59))
    }

    @Test
    fun `with no position a plain 6_30 to 18_30 day is used`() {
        assertTrue(SunTimes.isNight(at("06:29"), null, null))
        assertFalse(SunTimes.isNight(at("06:30"), null, null))
        assertFalse(SunTimes.isNight(at("18:29"), null, null))
        assertTrue(SunTimes.isNight(at("18:30"), null, null))
    }

    // ---- Brightness rules

    private val settings = CabinSettings(dayLevel = 8, nightLevel = 3)

    @Test
    fun `the mode button cycles Auto, Day, Night`() {
        assertEquals(BrightnessMode.Day, BrightnessRules.nextMode(BrightnessMode.Auto))
        assertEquals(BrightnessMode.Night, BrightnessRules.nextMode(BrightnessMode.Day))
        assertEquals(BrightnessMode.Auto, BrightnessRules.nextMode(BrightnessMode.Night))
    }

    @Test
    fun `the tray's night switch changes the look every time, and hands back to Auto where Auto agrees`() {
        fun mode(from: BrightnessMode, dark: Boolean) =
            BrightnessRules.toggled(settings.copy(brightnessMode = from), dark).brightnessMode

        // By day: Auto shows day, so on means forcing night, and off again is plain Auto.
        assertEquals(BrightnessMode.Night, mode(BrightnessMode.Auto, dark = false))
        assertEquals(BrightnessMode.Auto, mode(BrightnessMode.Night, dark = false))
        assertEquals(BrightnessMode.Night, mode(BrightnessMode.Day, dark = false))
        // By night: Auto shows night, so off means forcing day, and on again is plain Auto.
        assertEquals(BrightnessMode.Day, mode(BrightnessMode.Auto, dark = true))
        assertEquals(BrightnessMode.Auto, mode(BrightnessMode.Day, dark = true))
        assertEquals(BrightnessMode.Day, mode(BrightnessMode.Night, dark = true))

        // Whatever it starts from, the look that results is the other one, and the levels are untouched.
        for (from in BrightnessMode.entries) {
            for (dark in listOf(false, true)) {
                val before = settings.copy(brightnessMode = from)
                val after = BrightnessRules.toggled(before, dark)
                assertEquals(
                    "from $from, dark $dark",
                    !BrightnessRules.night(from, dark), BrightnessRules.night(after.brightnessMode, dark),
                )
                assertEquals(before.copy(brightnessMode = after.brightnessMode), after)
            }
        }
    }

    @Test
    fun `night forced on in the afternoon and switched off the next morning leaves Auto in charge`() {
        val headlights = FixedHeadlights(false)
        val store = InMemorySettings(settings.copy(loaded = true))
        val brightness = FixedBrightness(store, headlights = headlights)

        brightness.toggleNight()
        assertEquals(BrightnessUi(3, night = true, mode = BrightnessMode.Night), brightness.state.value)
        // Dusk comes and goes; the forced mode stands, as it does from the bottom row.
        headlights.set(true)
        headlights.set(false)
        assertEquals(BrightnessMode.Night, brightness.state.value.mode)

        brightness.toggleNight()
        assertEquals(BrightnessUi(8, night = false, mode = BrightnessMode.Auto), brightness.state.value)
    }

    @Test
    fun `Auto follows whether it is dark, Day and Night ignore it`() {
        assertEquals(BrightnessUi(8, night = false, mode = BrightnessMode.Auto), BrightnessRules.ui(settings, dark = false))
        assertEquals(BrightnessUi(3, night = true, mode = BrightnessMode.Auto), BrightnessRules.ui(settings, dark = true))
        val day = settings.copy(brightnessMode = BrightnessMode.Day)
        assertEquals(BrightnessUi(8, night = false, mode = BrightnessMode.Day), BrightnessRules.ui(day, dark = true))
        val night = settings.copy(brightnessMode = BrightnessMode.Night)
        assertEquals(BrightnessUi(3, night = true, mode = BrightnessMode.Night), BrightnessRules.ui(night, dark = false))
    }

    @Test
    fun `the headlights decide what is dark while the unit reports them`() {
        // As in the design: lights on is night, lights off is day, whatever the hour.
        assertTrue(BrightnessRules.dark(settings, headlights = true, sunIsDown = false))
        assertFalse(BrightnessRules.dark(settings, headlights = false, sunIsDown = true))
    }

    @Test
    fun `with no headlight signal, or with it switched off, the sun decides`() {
        assertTrue(BrightnessRules.dark(settings, headlights = null, sunIsDown = true))
        assertFalse(BrightnessRules.dark(settings, headlights = null, sunIsDown = false))
        val bySun = settings.copy(followHeadlights = false)
        assertFalse(BrightnessRules.dark(bySun, headlights = true, sunIsDown = false))
        assertTrue(BrightnessRules.dark(bySun, headlights = false, sunIsDown = true))
    }

    @Test
    fun `a step changes the level in use and leaves the other alone`() {
        assertEquals(settings.copy(dayLevel = 9), BrightnessRules.step(settings, dark = false, delta = 1))
        assertEquals(settings.copy(nightLevel = 2), BrightnessRules.step(settings, dark = true, delta = -1))
        // Several steps at once, as a two-finger drag makes them.
        assertEquals(settings.copy(dayLevel = 5), BrightnessRules.step(settings, dark = false, delta = -3))
    }

    @Test
    fun `levels stop at 1 and 10`() {
        assertEquals(10, BrightnessRules.step(settings.copy(dayLevel = 10), false, 1).dayLevel)
        assertEquals(1, BrightnessRules.step(settings.copy(nightLevel = 1), true, -1).nightLevel)
    }

    @Test
    fun `each level is brighter than the last, from just above off to full`() {
        val values = (1..10).map(BrightnessRules::systemValue)
        assertEquals(8, values.first())
        assertEquals(255, values.last())
        assertEquals(values.sorted().distinct(), values)
    }

    @Test
    fun `switching the headlights on moves Auto to the night level at once`() {
        val headlights = FixedHeadlights(false)
        val store = InMemorySettings(settings.copy(loaded = true))
        val brightness = FixedBrightness(store, headlights = headlights)
        assertEquals(BrightnessUi(8, night = false, mode = BrightnessMode.Auto), brightness.state.value)

        headlights.set(true)
        assertEquals(BrightnessUi(3, night = true, mode = BrightnessMode.Auto), brightness.state.value)
        // A step now changes the night level only.
        brightness.up()
        assertEquals(4, brightness.state.value.level)
        assertEquals(8, store.settings.value.dayLevel)

        // Told not to follow them, Auto goes back to the sun, which is up here.
        kotlinx.coroutines.runBlocking { store.update { it.copy(followHeadlights = false) } }
        assertEquals(BrightnessUi(8, night = false, mode = BrightnessMode.Auto), brightness.state.value)

        // A unit that stops reporting them is treated the same way.
        kotlinx.coroutines.runBlocking { store.update { it.copy(followHeadlights = true) } }
        assertTrue(brightness.state.value.night)
        headlights.set(null)
        assertFalse(brightness.state.value.night)
    }

    @Test
    fun `the in-memory brightness behaves like the real one`() {
        val brightness = FixedBrightness(InMemorySettings(settings.copy(loaded = true)))
        brightness.up()
        assertEquals(9, brightness.state.value.level)
        brightness.cycleMode()
        brightness.cycleMode()
        assertEquals(BrightnessUi(3, night = true, mode = BrightnessMode.Night), brightness.state.value)
        brightness.down()
        assertEquals(2, brightness.state.value.level)
        brightness.cycleMode()
        // Back on Auto in daylight: the day level, which the night steps did not touch.
        assertEquals(BrightnessUi(9, night = false, mode = BrightnessMode.Auto), brightness.state.value)
    }

    // ---- Volume

    @Test
    fun `volume steps one at a time and stays within the unit's range`() {
        val volume = FixedVolume(level = 29, max = 30)
        volume.up()
        volume.up()
        assertEquals(VolumeUi(30, muted = false), volume.volume.value)
        volume.set(-5)
        assertEquals(0, volume.volume.value.level)
        volume.down()
        assertEquals(0, volume.volume.value.level)
    }

    @Test
    fun `muting keeps the level and any change unmutes`() {
        val volume = FixedVolume(level = 12)
        volume.toggleMute()
        assertEquals(VolumeUi(12, muted = true), volume.volume.value)
        volume.up()
        assertEquals(VolumeUi(13, muted = false), volume.volume.value)
    }
}
