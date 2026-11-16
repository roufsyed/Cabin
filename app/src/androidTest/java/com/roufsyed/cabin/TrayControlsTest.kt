package com.roufsyed.cabin

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roufsyed.cabin.apps.FixedApps
import com.roufsyed.cabin.model.BrightnessMode
import com.roufsyed.cabin.model.Samples
import com.roufsyed.cabin.notify.FixedTray
import com.roufsyed.cabin.notify.TrayItem
import com.roufsyed.cabin.notify.TrayKind
import com.roufsyed.cabin.system.FixedMemory
import com.roufsyed.cabin.system.FixedStatus
import com.roufsyed.cabin.system.FixedSwitches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** What every class here does to the tray: opens it with a swipe, taps by description, and looks. */
private class Tray(private val cabin: CabinRule) {
    /** A tap made a moment after the swipe before it: Cabin ignores a tap within 0.4 s of a swipe. */
    fun tap(description: String) = cabin.onNodeWithContentDescription(description).performTouchInput {
        advanceEventTime(1_000)
        click()
    }

    fun shows(description: String) = cabin.onNodeWithContentDescription(description).assertIsDisplayed()
    fun gone(description: String) = cabin.onNodeWithContentDescription(description).assertDoesNotExist()

    private fun at(x: Float, y: Float): Offset {
        val root = cabin.onRoot().fetchSemanticsNode().boundsInRoot
        val scale = minOf(root.width / 1280f, root.height / 800f)
        return Offset((root.width - 1280f * scale) / 2f + x * scale, (root.height - 800f * scale) / 2f + y * scale)
    }

    fun open() = cabin.onRoot().performTouchInput {
        swipe(at(520f, 250f), at(520f, 460f), durationMillis = 200)
        advanceEventTime(600)
    }
}

/** The controls at the top of the tray, with the design's values and switches Android lets Cabin throw. */
@RunWith(AndroidJUnit4::class)
class TrayControlsTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues()

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private val tray by lazy { Tray(cabin) }
    private val switches get() = values.container.switches as FixedSwitches
    private val memory get() = values.container.memory as FixedMemory
    private val apps get() = values.container.apps as FixedApps
    private val bar get() = values.container.sideBar

    @Test
    fun theTrayHasItsTenControlsEachSayingHowItStands() {
        tray.open()
        // The four switches, as the unit has them.
        tray.shows("Bluetooth: on")
        tray.shows("Wi-Fi: on")
        tray.shows("Hotspot: off")
        tray.shows("Night mode: off")
        // What is done in one tap. Three of them wait for Cabin to be allowed under Accessibility.
        tray.shows("Recent apps: allow Cabin under Accessibility first")
        tray.shows("Split screen: choose two apps")
        tray.shows("Screenshot: allow Cabin under Accessibility first")
        tray.shows("Unit settings")
        tray.shows("Clear memory")
        tray.shows("Restart: allow Cabin under Accessibility first")
        // The notifications are still under them, and the buttons under those.
        tray.shows("Missed call. Meera · tap to call back")
        tray.shows("Close notification tray")
    }

    @Test
    fun bluetoothAndWifiAreSwitchedInPlaceAndTheTrayStaysOpen() {
        tray.open()
        tray.tap("Bluetooth: on")
        tray.shows("Bluetooth: off")
        tray.tap("Wi-Fi: on")
        tray.shows("Wi-Fi: off")
        // The mark beside the clock is the same Wi-Fi.
        tray.shows("Phone connected, Wi-Fi off, GPS ready")
        tray.tap("Bluetooth: off")
        tray.shows("Bluetooth: on")
        assertEquals(listOf("bluetooth off", "wifi off", "bluetooth on"), switches.asked)
        tray.shows("Close notification tray")
    }

    @Test
    fun nightModeChangesTheLookAndGivesItBackToAuto() {
        fun mode() = values.container.settings.settings.value.brightnessMode
        tray.open()
        tray.tap("Night mode: off")
        tray.shows("Night mode: on")
        assertEquals(BrightnessMode.Night, mode())
        // By day, off again is plain Auto, not a forced Day.
        tray.tap("Night mode: on")
        tray.shows("Night mode: off")
        assertEquals(BrightnessMode.Auto, mode())
    }

    @Test
    fun recentAppsScreenshotAndRestartAreAskedOfTheSideBarsServiceAndCloseTheTray() {
        val asked = mutableListOf<String>()
        cabin.runOnUiThread {
            bar.allowed.value = true
            bar.showRecents = { asked += "recents"; true }
            bar.takeScreenshot = { asked += "screenshot"; true }
            bar.showPowerMenu = { asked += "power"; true }
        }
        try {
            tray.open()
            tray.tap("Recent apps")
            tray.gone("Close notification tray")
            tray.open()
            tray.tap("Take a screenshot")
            tray.gone("Close notification tray")
            tray.open()
            tray.tap("Restart: opens the power menu")
            tray.gone("Close notification tray")
            assertEquals(listOf("recents", "screenshot", "power"), asked)
        } finally {
            cabin.runOnUiThread {
                bar.allowed.value = false
                bar.showRecents = null
                bar.takeScreenshot = null
                bar.showPowerMenu = null
            }
        }
    }

    @Test
    fun splitScreenChoosesTheLeftAppThenTheRight() {
        tray.open()
        tray.tap("Split screen: choose two apps")
        tray.gone("Close notification tray")
        cabin.onNodeWithText("Split screen").assertIsDisplayed()
        cabin.onNodeWithText("Tap the app for the left.").assertIsDisplayed()
        // Cabin's own tile cannot be one of the two.
        tray.gone("Settings")

        tray.tap("Maps")
        cabin.onNodeWithText("Maps on the left. Tap the app for the right.").assertIsDisplayed()
        tray.gone("Maps")
        assertEquals(emptyList<Pair<String, String>>(), apps.pairs)

        tray.tap("Music")
        cabin.waitForIdle()
        assertEquals(listOf(FixedApps.id("Maps") to FixedApps.id("Music")), apps.pairs)
        assertEquals(emptyList<String>(), apps.launched)
        // Cabin is back where the tray was opened, for when it is next shown.
        tray.shows("Open Maps")
        tray.gone("Cancel split screen")
    }

    @Test
    fun leavingThatChooserLeadsBackToWhereTheTrayWasOpened() {
        tray.tap("All apps")
        tray.open()
        tray.tap("Split screen: choose two apps")
        tray.tap("Cancel split screen")
        tray.shows("Browser")
        tray.gone("Cancel split screen")

        // Android's Back does the same, after the left app has been chosen too.
        tray.open()
        tray.tap("Split screen: choose two apps")
        tray.tap("Browser")
        cabin.runOnUiThread { cabin.activity.onBackPressedDispatcher.onBackPressed() }
        tray.shows("Browser")
        tray.gone("Cancel split screen")
        assertEquals(emptyList<Pair<String, String>>(), apps.pairs)
    }

    @Test
    fun clearMemorySaysWhatIsFreeAndThenGoesBackToItsName() {
        tray.open()
        cabin.onNodeWithText("Clear memory").assertIsDisplayed()
        tray.tap("Clear memory")
        cabin.onNodeWithText("2.4 GB free").assertIsDisplayed()
        // What is in Now playing is kept.
        assertEquals(listOf(setOf(Samples.playing.packageName)), memory.kept)
        cabin.waitUntil(timeoutMillis = 10_000) { cabin.onAllNodesWithText("Clear memory").fetchSemanticsNodes().isNotEmpty() }
        cabin.onNodeWithText("2.4 GB free").assertDoesNotExist()
        tray.shows("Close notification tray")
    }
}

/** A unit that leaves less to Cabin: no word on its hotspot, and an Android that closes no other apps. */
@RunWith(AndroidJUnit4::class)
class TrayControlsOnAStricterUnitTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues {
        val status = FixedStatus(Samples.status, bluetooth = false, hotspot = null)
        it.copy(status = status, switches = FixedSwitches(status), memory = FixedMemory(allowed = false))
    }

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private val tray by lazy { Tray(cabin) }

    @Test
    fun aHotspotAndroidHasSaidNothingAboutIsNeitherOnNorOff() {
        tray.open()
        tray.shows("Hotspot")
        tray.gone("Hotspot: off")
        tray.gone("Hotspot: on")
        tray.shows("Bluetooth: off")
    }

    @Test
    fun clearMemoryIsDimmedAndIsNoButtonWhereAndroidWouldIgnoreIt() {
        tray.open()
        cabin.onNodeWithContentDescription("Clear memory: this Android does not let an app do it")
            .assertIsDisplayed()
            .assertHasNoClickAction()
        tray.gone("Clear memory")
        // The others are buttons as ever.
        cabin.onNodeWithContentDescription("Unit settings").assertHasClickAction()
    }
}

/** More notifications than there is room for under the controls. */
@RunWith(AndroidJUnit4::class)
class FullTrayTest {

    private val five = (1..5).map { TrayItem("n$it", TrayKind.Other, "N", "Notice $it", "Line $it", "9:0$it") }

    @get:Rule(order = 0)
    val values = FreshDesignValues { it.copy(tray = FixedTray(five)) }

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private val tray by lazy { Tray(cabin) }

    @Test
    fun fourRowsAreShownAndTheWholeTrayStaysOnTheScreen() {
        tray.open()
        for (n in 1..4) tray.shows("Notice $n. Line $n")
        tray.gone("Notice 5. Line 5")
        tray.shows("Bluetooth: on")

        val root = cabin.onRoot().fetchSemanticsNode().boundsInRoot
        val close = cabin.onNodeWithContentDescription("Close notification tray").fetchSemanticsNode().boundsInRoot
        assertTrue("the tray's buttons end at ${close.bottom} of ${root.height}", close.bottom <= root.height)
    }
}
