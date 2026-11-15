package com.roufsyed.cabin

import android.view.KeyEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roufsyed.cabin.apps.FixedApps
import com.roufsyed.cabin.data.CabinSettings
import com.roufsyed.cabin.data.InMemorySettings
import com.roufsyed.cabin.data.SideSwipe
import com.roufsyed.cabin.data.TwoFingerDrag
import com.roufsyed.cabin.media.FixedMedia
import com.roufsyed.cabin.model.BarSide
import com.roufsyed.cabin.model.HomePanel
import com.roufsyed.cabin.model.LayoutRules
import com.roufsyed.cabin.model.Slot
import com.roufsyed.cabin.model.SourceId
import com.roufsyed.cabin.model.TuckAfter
import com.roufsyed.cabin.system.FixedHeadlights
import com.roufsyed.cabin.system.FixedPermissions
import com.roufsyed.cabin.system.Grant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Settings, the Steering wheel keys screen and the keys themselves, with the design's values. */
@RunWith(AndroidJUnit4::class)
class SettingsAndKeysTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues()

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private val media get() = values.container.media as FixedMedia
    private val settings get() = values.container.settings.settings.value

    private fun tap(description: String) = cabin.onNodeWithContentDescription(description).performClick()
    private fun shows(description: String) = cabin.onNodeWithContentDescription(description).assertIsDisplayed()
    private fun gone(description: String) = cabin.onNodeWithContentDescription(description).assertDoesNotExist()
    private fun openSettings() {
        tap("All apps")
        tap("Settings")
    }

    /**
     * Waits for [text] to be on screen. Settings are saved off the main thread, so what a tap changes
     * reaches the screen a moment after the tap returns.
     */
    private fun awaitText(text: String, count: Int = 1) {
        cabin.waitUntil(2_000) { cabin.onAllNodesWithText(text).fetchSemanticsNodes().size == count }
    }

    private fun awaitNoText(text: String) = awaitText(text, count = 0)

    /** Presses a key the way the unit delivers a steering wheel button to the app on screen. */
    private fun press(keyCode: Int) = cabin.runOnUiThread {
        cabin.activity.onKeyDown(keyCode, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        cabin.activity.onKeyUp(keyCode, KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    @Test
    fun settingsHasTheDesignsSixTilesThenThemeAndGesturesAndSetup() {
        openSettings()
        for (tile in listOf(
            "Arrange apps", "Arrange sources", "Steering wheel keys", "Screen off options",
            "Bluetooth settings", "Wi-Fi settings", "Theme and gestures", "Open setup",
        )) shows(tile)
        // The design's two "Prototype only" tiles are not part of the app.
        cabin.onNodeWithText("Incoming call").assertDoesNotExist()
        cabin.onNodeWithText("Headlights").assertDoesNotExist()
    }

    @Test
    fun screenOffTileOpensThePageThatTurnsTheScreenOff() {
        openSettings()
        tap("Screen off options")
        cabin.onNodeWithText("Choose what the dark screen shows").assertIsDisplayed()
        // The tile itself no longer darkens the screen: that is one tap further in.
        gone("Wake screen")
        tap("Turn the screen off")
        shows("Wake screen")
        tap("Wake screen")
        gone("Wake screen")
        tap("Back to settings")
        shows("Arrange apps")
    }

    @Test
    fun theSixKeyTilesDoWhatTheKeysDo() {
        openSettings()
        tap("Steering wheel keys")
        cabin.onNodeWithText("Tap a key here to try what it does").assertIsDisplayed()

        tap("Steering key: volume up")
        tap("Steering key: volume up")
        tap("Steering key: volume down")
        assertEquals(13, values.container.volume.volume.value.level)

        tap("Steering key: next")
        tap("Steering key: previous")
        cabin.waitForIdle()
        assertEquals(listOf("next", "queue:2", "previous", "queue:1"), media.calls)

        // Mode steps to the source after the active one: Bluetooth is active, USB follows it.
        tap("Steering key: mode")
        cabin.waitUntil(2_000) { settings.activeSource == SourceId.Usb }

        tap("Steering key: voice")
        cabin.onNodeWithText("Listening…").assertIsDisplayed()
        tap("Cancel")

        tap("Back to settings")
        shows("Arrange apps")
    }

    @Test
    fun realKeysStepVolumeControlThePlayerAndSwitchSource() {
        press(KeyEvent.KEYCODE_VOLUME_UP)
        press(KeyEvent.KEYCODE_VOLUME_UP)
        cabin.onNodeWithText("14").assertIsDisplayed()
        press(KeyEvent.KEYCODE_VOLUME_MUTE)
        assertTrue(values.container.volume.volume.value.muted)

        press(KeyEvent.KEYCODE_MEDIA_NEXT)
        press(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        press(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        cabin.waitForIdle()
        assertEquals(listOf("next", "queue:2", "previous", "queue:1", "playPause"), media.calls)

        press(KeyEvent.KEYCODE_BUTTON_MODE)
        cabin.waitUntil(2_000) { settings.activeSource == SourceId.Usb }
    }

    @Test
    fun theVoiceKeyOpensWhereTo() {
        // Let the first frame settle, as it has long before anyone can reach a wheel button.
        cabin.waitForIdle()
        press(KeyEvent.KEYCODE_VOICE_ASSIST)
        cabin.onNodeWithText("Listening…").assertIsDisplayed()
    }

    @Test
    fun setupListsWhatCabinAsksForOnItsFirstPageAndTheAppsItOpensOnItsSecond() {
        openSettings()
        tap("Open setup")
        cabin.onNodeWithText("Setup").assertIsDisplayed()
        for (tile in listOf(
            "Make Cabin the home app", "Allow notification access", "Allow changing system settings",
            "Dim with the headlights", "Allow location", "Allow microphone", "Allow nearby devices",
            "Keep the side bar over other apps",
        )) shows(tile)
        gone("Choose the phone app")

        tap("Next setup page")
        for (tile in listOf(
            "Choose the phone app", "Choose the phone link app", "Choose the maps app", "Choose the music app",
        )) shows(tile)
        gone("Allow location")
        cabin.onNodeWithText("The apps Cabin opens. Tap one to change it.").assertIsDisplayed()

        tap("Choose the phone app")
        cabin.onNodeWithText("Phone opens").assertIsDisplayed()
        tap("Maps")
        // Back on the same page of Setup, where the tile now names the chosen app.
        shows("Choose the phone app")
        cabin.waitUntil(2_000) { settings.bindings[Slot.Phone] == FixedApps.id("Maps") }

        tap("Previous setup page")
        shows("Allow location")
        tap("Finish setup")
        shows("Arrange apps")
    }

    @Test
    fun theSideBarTileSaysWhatItNeedsAndThenSwitchesTheBar() {
        openSettings()
        tap("Open setup")
        // Until Cabin is switched on under Accessibility the bar cannot be drawn, and the tile says so.
        cabin.onNodeWithText("Side bar in apps").assertIsDisplayed()
        cabin.onNodeWithText("Allow Cabin under Accessibility").assertIsDisplayed()
        assertTrue(settings.sideBar)

        // Once it is, the tile is the bar's own switch.
        values.container.sideBar.allowed.value = true
        cabin.waitForIdle()
        cabin.onNodeWithText("The rail stays beside every app").assertIsDisplayed()
        tap("Keep the side bar over other apps")
        cabin.waitUntil(2_000) { !settings.sideBar }
        tap("Keep the side bar over other apps")
        cabin.waitUntil(2_000) { settings.sideBar }
    }

    /** Settings > Theme and gestures > its second page. */
    private fun openLayout() {
        openSettings()
        cabin.onNodeWithText("Colours, swipes and layout").assertIsDisplayed()
        tap("Theme and gestures")
        tap("Layout page")
        cabin.onNodeWithText("Layout").assertIsDisplayed()
    }

    @Test
    fun theSideBarsSwitchIsAlsoOnTheLayoutPage() {
        openLayout()
        cabin.onNodeWithText("Side bar in apps").assertIsDisplayed()
        cabin.onNodeWithText("Allow Cabin under Accessibility").assertIsDisplayed()

        values.container.sideBar.allowed.value = true
        cabin.waitForIdle()
        cabin.onNodeWithText("The rail stays beside every app").assertIsDisplayed()
        tap("Side bar over other apps: on or off")
        cabin.waitUntil(2_000) { !settings.sideBar }
        // It is the one setting, so Setup's tile shows the same.
        tap("Back to settings")
        tap("Open setup")
        cabin.onNodeWithContentDescription("Keep the side bar over other apps").assertTextContains("Off")
        tap("Keep the side bar over other apps")
        cabin.waitUntil(2_000) { settings.sideBar }
        cabin.onNodeWithContentDescription("Keep the side bar over other apps").assertTextContains("On")
    }

    @Test
    fun theSideBarsHandleIsOnUntilSwitchedOffOnTheLayoutPage() {
        assertTrue(settings.barHandle)
        openLayout()
        cabin.onNodeWithText("Side bar handle").assertIsDisplayed()
        cabin.onNodeWithContentDescription("Side bar handle: on or off").assertTextContains("On")
        tap("Side bar handle: on or off")
        cabin.waitUntil(2_000) { !settings.barHandle }
        cabin.onNodeWithContentDescription("Side bar handle: on or off").assertTextContains("Off")
        tap("Side bar handle: on or off")
        cabin.waitUntil(2_000) { settings.barHandle }
    }

    private val tuckTile = "Side bar tucks away: immediate, 5 sec, 10 sec or never"

    @Test
    fun theSideBarTucksAwayAfterFiveSecondsUntilAnotherWaitIsPickedFromTheList() {
        assertEquals(TuckAfter.FiveSeconds, settings.barTuck)
        openLayout()
        cabin.onNodeWithText("Tuck away").assertIsDisplayed()
        cabin.onNodeWithContentDescription(tuckTile).assertTextContains("5 sec")
        cabin.onNodeWithContentDescription(tuckTile).assertTextContains("After 5 seconds untouched")
        // Nothing is listed until the tile is tapped.
        gone("Tuck away: Immediate")

        tap(tuckTile)
        for (option in listOf("Immediate", "5 sec", "10 sec", "Never")) shows("Tuck away: $option")
        // The one in use is marked.
        cabin.onNodeWithContentDescription("Tuck away: 5 sec").assertIsSelected()
        for (option in listOf("Immediate", "10 sec", "Never")) {
            cabin.onNodeWithContentDescription("Tuck away: $option").assertIsNotSelected()
        }

        // A tap on one puts it in use and closes the list.
        tap("Tuck away: Immediate")
        cabin.waitUntil(2_000) { settings.barTuck == TuckAfter.Immediate }
        gone("Tuck away: Immediate")
        cabin.onNodeWithContentDescription(tuckTile).assertTextContains("Immediate")
        cabin.onNodeWithContentDescription(tuckTile).assertTextContains("As soon as an app opens")

        tap(tuckTile)
        cabin.onNodeWithContentDescription("Tuck away: Immediate").assertIsSelected()
        tap("Tuck away: 10 sec")
        cabin.waitUntil(2_000) { settings.barTuck == TuckAfter.TenSeconds }
        cabin.onNodeWithContentDescription(tuckTile).assertTextContains("10 sec")

        tap(tuckTile)
        tap("Tuck away: Never")
        cabin.waitUntil(2_000) { settings.barTuck == TuckAfter.Never }
        cabin.onNodeWithContentDescription(tuckTile).assertTextContains("Never")
        cabin.onNodeWithContentDescription(tuckTile).assertTextContains("Only by a tap on its arrow")
    }

    @Test
    fun theListOfWaitsClosesWithNothingChangedOnATapBesideItAndOnBack() {
        openLayout()
        tap(tuckTile)
        shows("Tuck away: 10 sec")
        // The list lies over the left-hand column; this is a tap in the right-hand one.
        cabin.onNodeWithContentDescription("Close the list").performTouchInput { click(percentOffset(0.75f, 0.5f)) }
        gone("Tuck away: 10 sec")
        assertEquals(TuckAfter.FiveSeconds, settings.barTuck)
        // The tile that was under the tap was not pressed.
        assertEquals(BarSide.Right, settings.barSide)

        tap(tuckTile)
        shows("Tuck away: 10 sec")
        cabin.runOnUiThread { cabin.activity.onBackPressedDispatcher.onBackPressed() }
        gone("Tuck away: 10 sec")
        // Back closed the list and went no further.
        cabin.onNodeWithText("Layout").assertIsDisplayed()
        shows(tuckTile)
        assertEquals(TuckAfter.FiveSeconds, settings.barTuck)
    }

    @Test
    fun withTheHandleSwitchedOffTheBarCannotTuckAwayAndTheTileSaysSo() {
        openLayout()
        tap("Side bar handle: on or off")
        cabin.waitUntil(2_000) { !settings.barHandle }
        cabin.onNodeWithContentDescription(tuckTile).assertTextContains("Off")
        cabin.onNodeWithContentDescription(tuckTile).assertTextContains("Needs the side bar handle")
        tap(tuckTile)
        gone("Tuck away: 5 sec")
        // What was chosen is kept for when the handle is switched back on.
        assertEquals(TuckAfter.FiveSeconds, settings.barTuck)
        tap("Side bar handle: on or off")
        cabin.waitUntil(2_000) { settings.barHandle }
        cabin.onNodeWithContentDescription(tuckTile).assertTextContains("5 sec")
    }

    @Test
    fun theLayoutPageMovesTheSideBarAndHomesPanelsAndResetPutsThemBack() {
        openLayout()
        // As the design has them: the bar on the right, then clock, navigation, now playing.
        cabin.onNodeWithContentDescription("Side bar position: right or left").assertTextContains("Right")
        cabin.onNodeWithContentDescription("Clock and date on Home: left, middle or right").assertTextContains("Left")
        cabin.onNodeWithContentDescription("Navigation on Home: left, middle or right").assertTextContains("Middle")
        cabin.onNodeWithContentDescription("Now playing on Home: left, middle or right").assertTextContains("Right")

        tap("Side bar position: right or left")
        cabin.waitUntil(2_000) { settings.barSide == BarSide.Left }
        cabin.onNodeWithContentDescription("Side bar position: right or left").assertTextContains("Left")

        // A tap moves a panel one place to the right; the one that was there takes its place.
        tap("Clock and date on Home: left, middle or right")
        cabin.waitUntil(2_000) { settings.homeOrder == listOf(HomePanel.Navigation, HomePanel.Clock, HomePanel.NowPlaying) }
        cabin.onNodeWithContentDescription("Clock and date on Home: left, middle or right").assertTextContains("Middle")
        cabin.onNodeWithContentDescription("Navigation on Home: left, middle or right").assertTextContains("Left")
        // From the right a panel goes round to the left.
        tap("Now playing on Home: left, middle or right")
        cabin.waitUntil(2_000) { settings.homeOrder == listOf(HomePanel.NowPlaying, HomePanel.Navigation, HomePanel.Clock) }

        tap("Reset positions")
        cabin.waitUntil(2_000) { settings.barSide == BarSide.Right && settings.homeOrder == LayoutRules.DESIGN_ORDER }
        cabin.onNodeWithContentDescription("Side bar position: right or left").assertTextContains("Right")
        cabin.onNodeWithContentDescription("Clock and date on Home: left, middle or right").assertTextContains("Left")
        // Reset is about places only: the bar's own switches stay as they were.
        assertTrue(settings.sideBar)
        assertTrue(settings.barHandle)
        assertEquals(TuckAfter.FiveSeconds, settings.barTuck)
    }

    @Test
    fun aSwipeTurnsBetweenThemeAndGesturesAndLayout() {
        openSettings()
        tap("Theme and gestures")
        cabin.onRoot().performTouchInput { swipeLeft() }
        cabin.onNodeWithText("Layout").assertIsDisplayed()
        shows("Reset positions")
        cabin.onRoot().performTouchInput { swipeRight() }
        cabin.onNodeWithText("Theme and gestures").assertIsDisplayed()
        shows("Choose a theme")
    }

    @Test
    fun cabinTellsTheSideBarWhenItsOwnScreenIsShowing() {
        // The activity is up, with the whole rail: the bar for other apps steps aside.
        assertTrue(values.container.sideBar.cabinShowing.value)
    }

    /** A point given in design pixels, as a position on this screen. */
    private fun at(x: Float, y: Float): Offset {
        val root = cabin.onRoot().fetchSemanticsNode().boundsInRoot
        val scale = minOf(root.width / 1280f, root.height / 800f)
        return Offset((root.width - 1280f * scale) / 2f + x * scale, (root.height - 800f * scale) / 2f + y * scale)
    }

    @Test
    fun setupPagesTurnWithASwipeAndASwipeNeverLeavesSetup() {
        openSettings()
        tap("Open setup")
        cabin.onRoot().performTouchInput { swipe(at(900f, 400f), at(400f, 400f), durationMillis = 200) }
        shows("Choose the maps app")
        cabin.onRoot().performTouchInput { swipe(at(400f, 400f), at(900f, 400f), durationMillis = 200) }
        shows("Allow location")
        cabin.onRoot().performTouchInput { swipe(at(400f, 400f), at(900f, 400f), durationMillis = 200) }
        // Still on Setup: only Done leaves it.
        shows("Allow location")
        cabin.onNodeWithText("Setup").assertIsDisplayed()
    }

    @Test
    fun theMapsAndMusicAppsAreChosenInSetup() {
        openSettings()
        tap("Open setup")
        tap("Next setup page")
        // Maps is found without being chosen; music is whichever app played last until one is.
        cabin.onNodeWithText("Last one that played").assertIsDisplayed()
        assertEquals(null, settings.bindings[Slot.Maps])

        tap("Choose the music app")
        cabin.onNodeWithText("Music opens").assertIsDisplayed()
        tap("Radio")
        shows("Choose the music app")
        cabin.waitUntil(2_000) { settings.bindings[Slot.Music] == FixedApps.id("Radio") }
        awaitNoText("Last one that played")
        cabin.waitUntil(2_000) { media.musicApp == "design.radio" }

        tap("Choose the maps app")
        cabin.onNodeWithText("Maps opens").assertIsDisplayed()
        tap("Browser")
        shows("Choose the maps app")
        cabin.waitUntil(2_000) { settings.bindings[Slot.Maps] == FixedApps.id("Browser") }

        // "Auto" in the chooser goes back to no particular music app.
        tap("Choose the music app")
        tap("Choose automatically")
        awaitText("Last one that played")
        cabin.waitUntil(2_000) { settings.bindings[Slot.Music] == null && media.musicApp == null }
    }

    @Test
    fun autoBrightnessFollowsTheHeadlightsUnlessToldNotTo() {
        val headlights = values.container.headlights as FixedHeadlights
        // The design is drawn with the headlights off: Auto shows the day level.
        cabin.onNodeWithText("8").assertIsDisplayed()

        cabin.runOnUiThread { headlights.set(true) }
        awaitText("3")
        cabin.onNodeWithText("Auto").assertIsDisplayed()

        openSettings()
        tap("Open setup")
        cabin.onNodeWithText("Auto dims with them").assertIsDisplayed()
        tap("Dim with the headlights")
        cabin.waitUntil(2_000) { !settings.followHeadlights }
        awaitText("Auto goes by the sun")
        // Back to the sun, which is up in the design's values.
        awaitText("8")

        tap("Dim with the headlights")
        cabin.waitUntil(2_000) { settings.followHeadlights }
        awaitText("Auto dims with them")
        awaitText("3")

        // A unit that gives no signal is said to give none, and Auto goes by the sun.
        cabin.runOnUiThread { headlights.set(null) }
        awaitText("No signal from the unit yet")
        awaitText("8")
    }

    @Test
    fun themeAndGesturesListsTheThemeAndTheFourGesturesAndATapChangesEach() {
        openSettings()
        tap("Theme and gestures")
        cabin.onNodeWithText("Theme and gestures").assertIsDisplayed()
        // The theme in use is named on its tile.
        cabin.onNodeWithText("Blue").assertIsDisplayed()
        cabin.onNodeWithText("Tray").assertIsDisplayed()
        cabin.onNodeWithText("Apps").assertIsDisplayed()
        cabin.onNodeWithText("Volume").assertIsDisplayed()
        cabin.onNodeWithText("Split").assertIsDisplayed()

        tap("Swipe down: tray or off")
        cabin.waitUntil(2_000) { !settings.swipeDownTray }
        tap("Swipe sideways: apps, tracks or off")
        cabin.waitUntil(2_000) { settings.sideSwipe == SideSwipe.Tracks }
        awaitText("Tracks")
        tap("Swipe sideways: apps, tracks or off")
        cabin.waitUntil(2_000) { settings.sideSwipe == SideSwipe.Off }
        tap("Two-finger drag: volume, brightness or off")
        cabin.waitUntil(2_000) { settings.twoFingerDrag == TwoFingerDrag.Brightness }
        awaitText("Brightness")
        // Swipe down and swipe sideways are both off now.
        awaitText("Off", count = 2)
        tap("Long press on an app: split screen or off")
        cabin.waitUntil(2_000) { !settings.longPressSplit }
        awaitText("Off", count = 3)
        tap("Long press on an app: split screen or off")
        cabin.waitUntil(2_000) { settings.longPressSplit }
        awaitText("Split")

        tap("Back to settings")
        shows("Arrange apps")
    }
}

/** The very first start, before Setup has been finished. */
@RunWith(AndroidJUnit4::class)
class FirstRunTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues {
        it.copy(
            settings = InMemorySettings(CabinSettings(loaded = true, setupDone = false)),
            permissions = FixedPermissions(setOf(Grant.Location)),
        )
    }

    @get:Rule(order = 1)
    val cabin = cabinRule()

    @Test
    fun cabinOpensOnSetupOnceAndThenOnHome() {
        cabin.onNodeWithText("Welcome to Cabin").assertIsDisplayed()
        // Location is granted; microphone and nearby devices are not, and nor yet is the side bar.
        cabin.onAllNodesWithText("Off").assertCountEquals(3)

        cabin.onNodeWithContentDescription("Finish setup").performClick()
        cabin.onNodeWithContentDescription("Open Maps").assertIsDisplayed()
        cabin.waitUntil(2_000) { values.container.settings.settings.value.setupDone }
    }
}
