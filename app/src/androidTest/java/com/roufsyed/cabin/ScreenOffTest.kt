package com.roufsyed.cabin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roufsyed.cabin.media.FixedMedia
import com.roufsyed.cabin.model.BrightnessMode
import com.roufsyed.cabin.model.NavUi
import com.roufsyed.cabin.model.Samples
import com.roufsyed.cabin.model.ScreenOffShows
import com.roufsyed.cabin.model.StatusUi
import com.roufsyed.cabin.nav.FixedNavigation
import com.roufsyed.cabin.system.FixedHeadlights
import com.roufsyed.cabin.system.FixedStatus
import com.roufsyed.cabin.ui.theme.Slate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Settings > Screen off, and the dark screen it sets up, with the design's values. */
@RunWith(AndroidJUnit4::class)
class ScreenOffTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues()

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private val chosen get() = values.container.settings.settings.value.screenOff
    private val media get() = values.container.media as FixedMedia
    private val navigation get() = values.container.navigation as FixedNavigation
    private val headlights get() = values.container.headlights as FixedHeadlights

    private val everything = ScreenOffShows(clock = true, date = true, playing = true, turn = true, status = true)
    private val track = "Open Road · Aster Lane"
    private val turn = "350 m · Ring Road"
    private val marks = "Phone connected, Wi-Fi on, GPS ready"

    private fun tap(description: String) = cabin.onNodeWithContentDescription(description).performClick()
    private fun shows(description: String) = cabin.onNodeWithContentDescription(description).assertIsDisplayed()
    private fun gone(description: String) = cabin.onNodeWithContentDescription(description).assertDoesNotExist()
    private fun text(text: String) = cabin.onNodeWithText(text).assertIsDisplayed()
    private fun noText(text: String) = cabin.onNodeWithText(text).assertDoesNotExist()

    /** Waits for [text] to be on screen [count] times: what a tap or the unit changes arrives a moment later. */
    private fun awaitText(text: String, count: Int = 1) {
        cabin.waitUntil(2_000) { cabin.onAllNodesWithText(text).fetchSemanticsNodes().size == count }
    }

    private fun choose(shows: ScreenOffShows) = runBlocking {
        values.container.settings.update { it.copy(screenOff = shows) }
    }

    private fun openPage() {
        tap("All apps")
        tap("Settings")
        tap("Screen off options")
    }

    private fun turnOff() {
        tap("Turn the screen off")
        shows("Wake screen")
    }

    private fun wake() {
        tap("Wake screen")
        gone("Wake screen")
    }

    @Test
    fun thePageTurnsTheScreenOffAndATapWakesItBackOntoThePage() {
        openPage()
        text("Screen off")
        text("Tap the screen to wake it")
        turnOff()
        wake()
        // Still on the page, so another choice can be tried straight away.
        shows("Show the clock: on or off")
        tap("Back to settings")
        shows("Arrange apps")
    }

    @Test
    fun leftAloneTheDarkScreenIsTheDesignsClockDateAndHint() {
        assertEquals(ScreenOffShows(), chosen)
        openPage()
        // Clock and Date are on, the other three off.
        awaitText("On", count = 2)
        awaitText("Off", count = 3)

        turnOff()
        text("10:10")
        text("Thursday 12 November")
        text("Tap to wake")
        // A track is playing and a route is being followed, but neither was asked for.
        noText(track)
        noText(turn)
        gone(marks)
    }

    @Test
    fun eachSwitchPutsItsOwnLineOnTheDarkScreenAndTakesItOffAgain() {
        openPage()
        tap("Show what is playing: on or off")
        tap("Show the next turn: on or off")
        tap("Show the status marks: on or off")
        cabin.waitUntil(2_000) { chosen == everything }
        awaitText("On", count = 5)

        turnOff()
        text("10:10")
        text("Thursday 12 November")
        text(track)
        text(turn)
        shows(marks)
        text("Tap to wake")
        wake()

        tap("Show the clock: on or off")
        tap("Show the date: on or off")
        tap("Show the next turn: on or off")
        cabin.waitUntil(2_000) { chosen == ScreenOffShows(clock = false, date = false, playing = true, status = true) }
        awaitText("Off", count = 3)

        turnOff()
        noText("10:10")
        noText("Thursday 12 November")
        noText(turn)
        text(track)
        shows(marks)
        text("Tap to wake")
    }

    @Test
    fun theTrackShowsOnlyWhileItPlaysAndTheTurnOnlyWhileARouteIsFollowed() {
        choose(everything)
        openPage()
        turnOff()
        text(track)
        text(turn)

        cabin.runOnUiThread { media.playPause() }
        awaitText(track, count = 0)
        cabin.runOnUiThread { navigation.set(NavUi.Idle) }
        awaitText(turn, count = 0)
        // The rest stays as it was.
        text("10:10")
        shows(marks)

        cabin.runOnUiThread { media.playPause() }
        awaitText(track)
        cabin.runOnUiThread { navigation.set(Samples.nav.copy(distance = "120 m", road = "Lake View Road")) }
        awaitText("120 m · Lake View Road")
    }

    @Test
    fun theStatusMarksOnTheDarkScreenFollowTheUnit() {
        choose(ScreenOffShows(status = true))
        openPage()
        turnOff()
        shows(marks)
        cabin.runOnUiThread {
            (values.container.status as FixedStatus).set(StatusUi(phone = false, wifi = true, gps = false))
        }
        shows("Phone not connected, Wi-Fi on, GPS off")
        gone(marks)
    }

    @Test
    fun withEverySwitchOffTheScreenIsPlainBlackAndATapStillWakesIt() {
        choose(ScreenOffShows(clock = false, date = false))
        openPage()
        text("Plain black, tap to wake")
        awaitText("Off", count = 5)

        turnOff()
        noText("Tap to wake")
        noText("10:10")
        val black = Color.Black.toArgb()
        assertEquals(0, cabin.pixels().count { it != black })
        wake()
        shows("Show the clock: on or off")
    }

    @Test
    fun theTextIsLighterByDayAndTheDesignsDimGreyWithTheHeadlightsOn() {
        // Both greys are the same in every theme; a line of text has thousands of pixels of exactly its colour.
        val greys = listOf(Slate.Muted, Slate.Disabled)
        fun awaitGrey(lighter: Boolean) = cabin.waitUntil(3_000) {
            val (light, dim) = cabin.pixelsOf(greys)
            if (lighter) light > 1_000 && dim == 0 else dim > 1_000 && light == 0
        }
        choose(everything)
        openPage()
        turnOff()

        // The design's values have the headlights off: day, so the lighter grey, on every line.
        awaitGrey(lighter = true)
        cabin.runOnUiThread { headlights.set(true) }
        awaitGrey(lighter = false)
        cabin.runOnUiThread { headlights.set(false) }
        awaitGrey(lighter = true)

        // It is the brightness control's night that counts: forced to Night it is dim with the lights off...
        val brightness = values.container.brightness
        cabin.runOnUiThread { repeat(2) { brightness.cycleMode() } }
        cabin.waitUntil(2_000) { brightness.state.value.mode == BrightnessMode.Night }
        awaitGrey(lighter = false)
        // ...and forced to Day it is light with them on.
        cabin.runOnUiThread {
            repeat(2) { brightness.cycleMode() }
            headlights.set(true)
        }
        cabin.waitUntil(2_000) { brightness.state.value.mode == BrightnessMode.Day }
        awaitGrey(lighter = true)
        assertFalse(brightness.state.value.night)
    }
}
