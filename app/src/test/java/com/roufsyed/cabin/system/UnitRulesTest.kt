package com.roufsyed.cabin.system

import com.roufsyed.cabin.apps.SplitWay
import com.roufsyed.cabin.model.Samples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitRulesTest {

    private val old = Samples.unit.copy(
        release = "10", sdk = 29, patch = "2020-09-05", build = "QP1A.190711.020", maker = "Maker", model = "Unit 7",
        board = "board7", hardware = "soc7", memoryBytes = 3_900_000_000L, split = SplitWay.Toggle,
    )

    private fun line(facts: UnitFacts, label: String) = UnitRules.report(facts).single { it.label == label }.value

    @Test
    fun `the page has a row for each thing, and fits`() {
        val report = UnitRules.report(old)
        assertEquals(listOf("Android", "Build", "Device", "Screen", "Memory", "Split screen", "Accessibility"), report.map { it.label })
        assertTrue(report.sumOf { it.lines } <= UnitRules.PAGE_LINES)
    }

    @Test
    fun `Android is given as it really is, with the level apps go by`() {
        assertEquals("10 (API 29), security patch 2020-09-05", line(old, "Android"))
        assertEquals("13 (API 33), no security patch level given", line(Samples.unit.copy(patch = ""), "Android"))
        assertEquals("QP1A.190711.020", line(old, "Build"))
        assertEquals("Maker Unit 7, board board7, hardware soc7", line(old, "Device"))
        assertEquals("Design Unit", line(Samples.unit, "Device"))
        assertEquals("1280 x 800 at 160 dpi", line(old, "Screen"))
    }

    @Test
    fun `memory is in gigabytes, and says when Android counts the unit as short of it`() {
        assertEquals("3.6 GB", line(old, "Memory"))
        assertEquals("4.0 GB", line(Samples.unit, "Memory"))
        assertEquals("0.9 GB, which Android counts as a low-memory device", line(old.copy(memoryBytes = 1_000_000_000L, lowMemory = true), "Memory"))
    }

    @Test
    fun `split screen says which way Cabin goes about it and what the firmware's switches read`() {
        assertEquals(
            "Android's own: Cabin opens one app beside another (multi-window on, split screen on)",
            line(Samples.unit, "Split screen"),
        )
        assertEquals(
            "Android's older kind: Cabin asks for it through Accessibility (multi-window on, split screen on)",
            line(old, "Split screen"),
        )
        assertEquals(
            "None that Cabin can use (multi-window on, split screen off)",
            line(old.copy(split = SplitWay.None, splitScreen = false), "Split screen"),
        )
        assertEquals(
            "Android's own: Cabin opens one app beside another (multi-window not said, split screen not said)",
            line(Samples.unit.copy(multiWindow = null, splitScreen = null), "Split screen"),
        )
    }

    @Test
    fun `accessibility says whether Cabin is allowed and what Android then offers it`() {
        assertEquals("Off: Cabin is not allowed under Accessibility just now", line(old, "Accessibility"))
        assertEquals("On. This Android does not say what it offers", line(old.copy(serviceOn = true), "Accessibility"))
        assertEquals(
            "On. Android offers back, recent apps, split screen, screenshot, power menu",
            line(old.copy(serviceOn = true, offered = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9)), "Accessibility"),
        )
        assertEquals(
            "On. Android offers back, recent apps, power menu; not split screen, screenshot",
            line(old.copy(serviceOn = true, offered = listOf(1, 2, 3, 6)), "Accessibility"),
        )
    }

    private val online = old.copy(wifiAddress = "192.168.43.57")

    @Test
    fun `the network row is only there while the unit is on Wi-Fi, and the page still fits with it`() {
        assertEquals(null, UnitRules.network(old))
        assertTrue(UnitRules.report(old).none { it.label == "Network" })

        val report = UnitRules.report(online.copy(debugging = true))
        assertEquals("Network", report.last().label)
        assertEquals(8, report.size)
        assertTrue(report.sumOf { it.lines } <= UnitRules.PAGE_LINES)
    }

    @Test
    fun `it gives the address, how USB debugging stands, and what to type on a computer`() {
        assertEquals(
            "192.168.43.57 on Wi-Fi. USB debugging is on: try adb connect 192.168.43.57:5555",
            line(online.copy(debugging = true), "Network"),
        )
        assertEquals(
            "192.168.43.57 on Wi-Fi. USB debugging is off; once on, try adb connect 192.168.43.57:5555",
            line(online.copy(debugging = false), "Network"),
        )
        assertEquals("192.168.43.57 on Wi-Fi. Try adb connect 192.168.43.57:5555", line(online, "Network"))
    }

    @Test
    fun `an address as Android's Wi-Fi gives it is written lowest byte first`() {
        assertEquals("192.168.43.57", UnitRules.dotted(0x392BA8C0))
        assertEquals("10.0.2.16", UnitRules.dotted(0x1002000A))
    }

    private val all = CabinFacts(
        home = true, notifications = true, brightness = true, granted = Grant.entries.toSet(),
        speech = true, positioned = true, bluetoothOn = true,
    )
    private val nothing = CabinFacts()

    private fun ready(facts: UnitFacts, cabin: CabinFacts, label: String) =
        UnitRules.ready(facts, cabin).single { it.label == label }.value

    private fun has(facts: UnitFacts, label: String) = UnitRules.hardware(facts).single { it.label == label }.value

    @Test
    fun `the second and third pages have a row for each thing, each on one line, and fit`() {
        val ready = UnitRules.ready(Samples.unit, all)
        assertEquals(
            listOf(
                "Home button", "Notifications", "Brightness", "Side bar", "Split screen", "Voice", "Position",
                "Bluetooth", "Volume", "Headlights", "Keys and knob",
            ),
            ready.map { it.label },
        )
        val hardware = UnitRules.hardware(Samples.unit)
        assertEquals(
            listOf(
                "Wi-Fi", "GPS", "Touch", "Knob", "Cameras", "USB host", "Phone modem", "Light sensor",
                "Picture in picture", "Free windows",
            ),
            hardware.map { it.label },
        )
        for (page in listOf(ready, hardware)) {
            assertTrue(page.all { it.lines == 1 })
            assertTrue(page.size <= UnitRules.PAGE_ROWS)
        }
    }

    @Test
    fun `with everything allowed on a unit that has it all, what Cabin can use is ready`() {
        val on = Samples.unit.copy(serviceOn = true)
        assertEquals("Ready: Cabin is the home app", ready(on, all, "Home button"))
        assertEquals("Ready: now playing, navigation and the tray can be read", ready(on, all, "Notifications"))
        assertEquals("Ready: Cabin sets the unit's brightness", ready(on, all, "Brightness"))
        assertEquals("Ready: Cabin is allowed under Accessibility", ready(on, all, "Side bar"))
        assertEquals("Ready: Android's own", ready(on, all, "Split screen"))
        assertEquals("Ready: \"Where to?\" listens", ready(on, all, "Voice"))
        assertEquals("Ready: a position is known, for sunrise and sunset", ready(on, all, "Position"))
        assertEquals("On, and Cabin can show a connected phone", ready(on, all, "Bluetooth"))
        assertEquals("Ready: Cabin can change it", ready(on, all, "Volume"))
        assertEquals("Ready: the unit reports them", ready(on, all.copy(headlights = false), "Headlights"))
        assertEquals("No signal known for this unit yet", ready(on, all, "Headlights"))
        assertEquals("None seen yet: press a wheel key or turn the knob", ready(on, all, "Keys and knob"))
    }

    @Test
    fun `what is not ready says what is missing, and what the unit lacks is said to be none`() {
        val unit = Samples.unit
        assertEquals("Not yet: another app is the home app", ready(unit, nothing, "Home button"))
        assertEquals("Not yet: allow notification access in Setup", ready(unit, nothing, "Notifications"))
        assertEquals("Not yet: allow brightness control in Setup", ready(unit, nothing, "Brightness"))
        assertEquals("Not yet: allow Cabin under Accessibility in Setup", ready(unit, nothing, "Side bar"))
        assertEquals(
            "Not yet: the unit switched it off. Allow it again in Setup",
            ready(unit, nothing.copy(barLost = true), "Side bar"),
        )
        assertEquals("None: this unit has no speech service", ready(unit, nothing, "Voice"))
        assertEquals("Not yet: allow the microphone in Setup", ready(unit, nothing.copy(speech = true), "Voice"))
        assertEquals("None: this unit has no microphone", ready(unit.copy(microphone = false), all, "Voice"))
        assertEquals("Not yet: allow location in Setup", ready(unit, nothing, "Position"))
        assertEquals("Allowed, but Android has no position yet", ready(unit, all.copy(positioned = false), "Position"))
        assertEquals("Off", ready(unit, nothing, "Bluetooth"))
        assertEquals("None on this unit", ready(unit.copy(bluetooth = false), all, "Bluetooth"))
        assertEquals(
            "On. Allow nearby devices in Setup to show the phone",
            ready(unit, all.copy(granted = emptySet()), "Bluetooth"),
        )
        assertEquals("Fixed by the unit: Cabin cannot change it", ready(unit.copy(volumeFixed = true), all, "Volume"))
        assertEquals("Not said", ready(unit.copy(volumeFixed = null), all, "Volume"))
    }

    @Test
    fun `split screen is ready, to be tried, waiting on Accessibility or not there, by the unit's Android`() {
        assertEquals("None: this unit's Android has no split screen", ready(old.copy(split = SplitWay.None), all, "Split screen"))
        assertEquals("Not yet: allow Cabin under Accessibility in Setup", ready(old, all, "Split screen"))
        val allowed = old.copy(serviceOn = true)
        assertEquals("Can be asked for. Try it: some units do not finish it", ready(allowed, all, "Split screen"))
        assertEquals(
            "Can be asked for. Try it: some units do not finish it",
            ready(allowed.copy(offered = listOf(1, 7)), all, "Split screen"),
        )
        assertEquals(
            "None: this Android does not offer it to Cabin",
            ready(allowed.copy(offered = listOf(1, 3)), all, "Split screen"),
        )
    }

    @Test
    fun `the hardware list says what the unit has, what it lacks, and what Android does not say`() {
        val unit = Samples.unit
        assertEquals("On", has(unit, "Wi-Fi"))
        assertEquals("Off", has(unit.copy(wifiOn = false), "Wi-Fi"))
        assertEquals("None", has(unit.copy(wifi = false, wifiOn = false), "Wi-Fi"))
        assertEquals("Yes", has(unit.copy(gpsOn = null), "GPS"))
        assertEquals("5 or more fingers at once", has(unit, "Touch"))
        assertEquals("2 or more fingers at once", has(unit.copy(touchPoints = 2), "Touch"))
        assertEquals("One finger at a time: two-finger drags will not work", has(unit.copy(touchPoints = 1), "Touch"))
        assertEquals("None declared", has(unit.copy(touchPoints = 0), "Touch"))
        assertEquals("1", has(unit, "Cameras"))
        assertEquals("None", has(unit.copy(cameras = 0), "Cameras"))
        assertEquals("Yes", has(unit, "USB host"))
        assertEquals("None", has(unit, "Phone modem"))
        assertEquals("None", has(unit, "Light sensor"))
        assertEquals("Yes", has(unit, "Picture in picture"))
        assertEquals("No", has(unit, "Free windows"))

        val silent = unit.copy(
            wifi = null, wifiOn = null, gps = null, gpsOn = null, touchPoints = null, cameras = null, usbHost = null,
            telephony = null, lightSensor = null, pictureInPicture = null, freeform = null, rotary = null,
        )
        assertEquals(List(10) { "Not said" }, UnitRules.hardware(silent).map { it.value })
    }

    @Test
    fun `keys that reach Cabin are named as they come, each once, and one it has no name for by its number`() {
        assertEquals("None seen yet: press a wheel key or turn the knob", UnitRules.keys(emptyList()))
        assertEquals("Seen: volume up, volume down", UnitRules.keys(listOf(24, 25)))
        assertEquals("Seen: play, next, previous, mute", UnitRules.keys(listOf(85, 126, 127, 79, 87, 88, 164)))
        assertEquals("Seen: voice, mode, back, key 131", UnitRules.keys(listOf(231, 84, 219, 110, 4, 131)))
        assertEquals("Seen: volume up", ready(Samples.unit, all.copy(keys = listOf(24)), "Keys and knob"))
    }

    @Test
    fun `the knob is as Android lists it, which may be not at all`() {
        assertEquals("Not listed by Android. Its turns may still arrive as keys", has(Samples.unit, "Knob"))
        assertEquals("Android lists a rotary control: rotary-encoder", has(Samples.unit.copy(rotary = listOf("rotary-encoder")), "Knob"))
    }
}
