package com.roufsyed.cabin

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.roufsyed.cabin.data.CabinSettings
import com.roufsyed.cabin.data.InMemorySettings
import com.roufsyed.cabin.model.BrightnessMode
import com.roufsyed.cabin.model.Samples
import com.roufsyed.cabin.system.AndroidBrightness
import com.roufsyed.cabin.system.BrightnessRules
import com.roufsyed.cabin.system.FixedBrightness
import com.roufsyed.cabin.system.FixedClock
import com.roufsyed.cabin.system.FixedHeadlights
import com.roufsyed.cabin.system.NoHeadlightSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZonedDateTime

private val HOURS = listOf("Night starts: earlier", "Night starts: later", "Day starts: earlier", "Day starts: later")

@RunWith(AndroidJUnit4::class)
class NightModeTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues {
        it.copy(
            headlights = NoHeadlightSignal,
            brightness = FixedBrightness(it.settings, headlights = NoHeadlightSignal, minuteOfDay = 12 * 60),
        )
    }

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private val settings get() = values.container.settings.settings.value
    private val night get() = values.container.brightness.state.value.night
    private val is24Hour get() = DateFormat.is24HourFormat(InstrumentationRegistry.getInstrumentation().targetContext)

    private fun hour(hours: Int, minutes: Int = 0) = BrightnessRules.hour(hours * 60 + minutes, is24Hour)
    private fun node(description: String) = cabin.onNodeWithContentDescription(description)
    private fun tap(description: String) = node(description).performClick()
    private fun shows(description: String) = node(description).assertIsDisplayed()
    private fun text(text: String) = cabin.onNodeWithText(text).assertIsDisplayed()
    private fun change(transform: (CabinSettings) -> CabinSettings) = runBlocking { values.container.settings.update(transform) }

    private fun openThemeAndGestures() {
        for (step in listOf("All apps", "Settings", "Theme and gestures")) tap(step)
    }

    @Test
    fun autoIsChosenAtFirstAndGoesByTheSunWithNoHoursOnShow() {
        openThemeAndGestures()
        text("By sunrise and sunset")
        tap("Night mode: auto or manual")
        text("Night mode")
        text("Day now")
        node("Auto night mode").assertIsSelected()
        node("Manual night mode").assertIsNotSelected()
        text("By sunrise and sunset")
        text("At the hours you set")
        for (hidden in HOURS) node(hidden).assertDoesNotExist()
        cabin.onNodeWithText("Starts at").assertDoesNotExist()
        assertFalse(settings.nightHours)

        text(
            "No position is known yet, so night runs from ${hour(18, 30)} to ${hour(6, 30)}. " +
                "With Location allowed in Setup it follows sunrise and sunset where you are.",
        )
        change { it.copy(latitude = 12.97, longitude = 77.59) }
        val worked = BrightnessRules.autoDetail(settings, null, ZonedDateTime.now(), is24Hour)
        assertTrue(worked, worked.startsWith("Worked out on this unit from the date and its last known position. Today: sunrise "))
        text(worked)
    }

    @Test
    fun choosingManualBringsItsHoursInAndTheyMoveHalfAnHourAtATime() {
        openThemeAndGestures()
        tap("Night mode: auto or manual")
        tap("Manual night mode")
        cabin.waitUntil(2_000) { settings.nightHours }
        node("Manual night mode").assertIsSelected()
        node("Auto night mode").assertIsNotSelected()
        for (button in HOURS) shows(button)
        shows("Night starts: ${hour(19)}")
        shows("Day starts: ${hour(6)}")
        cabin.onNodeWithText("No position is known yet", substring = true).assertIsDisplayed()

        tap("Night starts: later")
        cabin.waitUntil(2_000) { settings.nightStart == 19 * 60 + 30 }
        shows("Night starts: ${hour(19, 30)}")
        tap("Day starts: earlier")
        tap("Day starts: earlier")
        cabin.waitUntil(2_000) { settings.dayStart == 5 * 60 }
        shows("Day starts: ${hour(5)}")
        tap("Day starts: later")
        cabin.waitUntil(2_000) { settings.dayStart == 5 * 60 + 30 }

        tap("Back to theme and gestures")
        text("From ${hour(19, 30)} to ${hour(5, 30)}")
        assertEquals(BrightnessMode.Auto, settings.brightnessMode)

        tap("Night mode: auto or manual")
        shows("Night starts: ${hour(19, 30)}")
        tap("Auto night mode")
        cabin.waitUntil(2_000) { !settings.nightHours }
        node("Auto night mode").assertIsSelected()
        for (hidden in HOURS) node(hidden).assertDoesNotExist()
        assertEquals(19 * 60 + 30, settings.nightStart)
        assertEquals(5 * 60 + 30, settings.dayStart)

        tap("Manual night mode")
        cabin.waitUntil(2_000) { settings.nightHours }
        shows("Night starts: ${hour(19, 30)}")
        shows("Day starts: ${hour(5, 30)}")
    }

    @Test
    fun onManualTheHoursDecideAndTheBrightnessButtonSaysManual() {
        openThemeAndGestures()
        tap("Night mode: auto or manual")
        text("Day now")
        assertFalse(night)

        tap("Manual night mode")
        cabin.waitUntil(2_000) { settings.nightHours }
        node("Night mode: auto, day or night").assertIsDisplayed()
        assertEquals("Manual", values.container.brightness.state.value.label)
        text("Day now")

        change { it.copy(nightStart = 11 * 60, dayStart = 13 * 60) }
        cabin.waitUntil(2_000) { night }
        text("Night now")

        tap("Home")
        text("Manual")
        tap("Night mode: auto, day or night")
        text("Day")
        tap("Night mode: auto, day or night")
        text("Night")
        tap("Night mode: auto, day or night")
        text("Manual")
        assertEquals(BrightnessMode.Auto, settings.brightnessMode)
    }

    @Test
    fun aLookHeldByHandIsSaidSoAndChoosingAgainLetsItGo() {
        openThemeAndGestures()
        tap("Night mode: auto or manual")
        change { it.copy(nightHours = true, nightStart = 11 * 60, dayStart = 13 * 60) }
        text("Night now")

        tap("Night mode: auto, day or night")
        cabin.waitUntil(2_000) { settings.brightnessMode == BrightnessMode.Day }
        text("Held on Day. Tap Auto or Manual to let it switch again.")
        assertFalse(night)
        node("Manual night mode").assertIsSelected()

        tap("Manual night mode")
        cabin.waitUntil(2_000) { settings.brightnessMode == BrightnessMode.Auto }
        text("Night now")
        assertTrue(night)
        assertTrue(settings.nightHours)

        change { it.copy(brightnessMode = BrightnessMode.Night) }
        text("Held on Night. Tap Auto or Manual to let it switch again.")
        tap("Auto night mode")
        cabin.waitUntil(2_000) { settings.brightnessMode == BrightnessMode.Auto && !settings.nightHours }
        text("Day now")
        assertFalse(night)
    }

    @Test
    fun backLeadsToThemeAndGestures() {
        openThemeAndGestures()
        tap("Night mode: auto or manual")
        cabin.runOnUiThread { cabin.activity.onBackPressedDispatcher.onBackPressed() }
        text("Theme and gestures")
        shows("Night mode: auto or manual")
    }
}

@RunWith(AndroidJUnit4::class)
class NightModeWithHeadlightsTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues()

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private val settings get() = values.container.settings.settings.value
    private val night get() = values.container.brightness.state.value.night
    private fun node(description: String) = cabin.onNodeWithContentDescription(description)

    @Test
    fun autoGoesByTheHeadlightsWhileTheUnitReportsThemAndManualDoesNot() {
        val headlights = values.container.headlights as FixedHeadlights
        for (step in listOf("All apps", "Settings", "Theme and gestures")) node(step).performClick()
        cabin.onNodeWithText("By the headlights").assertIsDisplayed()
        node("Night mode: auto or manual").performClick()
        cabin.onNodeWithText("By the headlights").assertIsDisplayed()
        cabin.onNodeWithText("Night while the car's headlights are on", substring = true).assertIsDisplayed()

        cabin.runOnUiThread { headlights.set(true) }
        cabin.waitUntil(2_000) { night }
        cabin.onNodeWithText("Night now").assertIsDisplayed()

        node("Manual night mode").performClick()
        cabin.waitUntil(2_000) { settings.nightHours }
        cabin.waitUntil(2_000) { !night }
        cabin.onNodeWithText("Day now").assertIsDisplayed()
        cabin.onNodeWithText("By the headlights").assertIsDisplayed()

        node("Auto night mode").performClick()
        cabin.waitUntil(2_000) { night }
        runBlocking { values.container.settings.update { it.copy(followHeadlights = false) } }
        cabin.onNodeWithText("By sunrise and sunset").assertIsDisplayed()
        cabin.onNodeWithText("No position is known yet", substring = true).assertIsDisplayed()
        cabin.waitUntil(2_000) { !night }
    }
}

@RunWith(AndroidJUnit4::class)
class NightModeOnTheUnitsClockTest {

    @Test
    fun theRealBrightnessFollowsHoursSetAroundNowAsSoonAsTheyAreSet() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val store = InMemorySettings(CabinSettings(loaded = false))
            val brightness = AndroidBrightness(
                ApplicationProvider.getApplicationContext<Context>(), store, scope, FixedClock(Samples.clock), NoHeadlightSignal,
            )
            val now = ZonedDateTime.now().let { it.hour * 60 + it.minute }
            fun around(minutes: Int) = Math.floorMod(now + minutes, BrightnessRules.MINUTES_IN_DAY)

            runBlocking { store.update { it.copy(nightHours = true, nightStart = around(-60), dayStart = around(60)) } }
            assertTrue(brightness.state.value.night)
            assertEquals("Manual", brightness.state.value.label)
            runBlocking { store.update { it.copy(nightStart = around(60), dayStart = around(120)) } }
            assertFalse(brightness.state.value.night)
            runBlocking { store.update { it.copy(nightStart = around(-120), dayStart = around(-60)) } }
            assertFalse(brightness.state.value.night)
        } finally {
            scope.cancel()
        }
    }
}
