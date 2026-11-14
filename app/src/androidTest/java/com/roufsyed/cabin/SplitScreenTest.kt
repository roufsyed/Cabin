package com.roufsyed.cabin

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.roufsyed.cabin.apps.FixedApps
import com.roufsyed.cabin.apps.SplitScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Holding an app in the grid, then tapping a second one, opens the two side by side. */
@RunWith(AndroidJUnit4::class)
class SplitScreenTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues()

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private val apps get() = values.container.apps as FixedApps

    private fun tap(description: String) = cabin.onNodeWithContentDescription(description).performClick()
    private fun hold(description: String) = cabin.onNodeWithContentDescription(description).performTouchInput { longClick() }
    private fun shows(description: String) = cabin.onNodeWithContentDescription(description).assertIsDisplayed()
    private fun gone(description: String) = cabin.onNodeWithContentDescription(description).assertDoesNotExist()

    @Test
    fun holdingAnAppThenTappingAnotherOpensThemSideBySide() {
        tap("All apps")
        hold("Maps")
        cabin.onNodeWithText("Split screen").assertIsDisplayed()
        cabin.onNodeWithText("Maps on the left. Tap the app for the right.").assertIsDisplayed()
        // The app that was held and Cabin's own tile are not offered as the second one.
        gone("Maps")
        gone("Settings")
        // The hold itself opened nothing.
        assertEquals(emptyList<String>(), apps.launched)

        tap("Music")
        cabin.waitForIdle()
        assertEquals(listOf(FixedApps.id("Maps") to FixedApps.id("Music")), apps.pairs)
        assertEquals(emptyList<String>(), apps.launched)
        // Cabin is back on the grid for when it is next shown.
        shows("Maps")
        shows("Settings")
        gone("Cancel split screen")
    }

    @Test
    fun backLeavesTheChooserWithoutOpeningAnything() {
        tap("All apps")
        hold("Radio")
        shows("Cancel split screen")
        tap("Cancel split screen")
        shows("Radio")
        gone("Cancel split screen")

        hold("Radio")
        cabin.runOnUiThread { cabin.activity.onBackPressedDispatcher.onBackPressed() }
        shows("Radio")
        gone("Cancel split screen")
        assertEquals(emptyList<Pair<String, String>>(), apps.pairs)
        assertEquals(emptyList<String>(), apps.launched)
    }

    @Test
    fun aPlainTapStillOpensTheAppOnItsOwn() {
        tap("All apps")
        tap("Maps")
        cabin.waitForIdle()
        assertEquals(listOf(FixedApps.id("Maps")), apps.launched)
        assertEquals(emptyList<Pair<String, String>>(), apps.pairs)
    }

    @Test
    fun cabinsOwnTileCannotBeSplit() {
        tap("All apps")
        hold("Settings")
        gone("Cancel split screen")
        // Holding it is not a tap either: Cabin's settings stay closed.
        shows("Browser")
        gone("Arrange apps")
    }

    @Test
    fun whileArrangingAHoldIsJustATap() {
        tap("All apps")
        tap("Settings")
        tap("Arrange apps")
        hold("Maps")
        gone("Cancel split screen")
        shows("Done arranging")
        assertEquals(emptyList<Pair<String, String>>(), apps.pairs)
    }

    @Test
    fun withTheLongPressSwitchedOffAHoldOpensTheAppAsATapDoes() {
        runBlocking { values.container.settings.update { it.copy(longPressSplit = false) } }
        tap("All apps")
        cabin.waitForIdle()
        hold("Maps")
        cabin.waitForIdle()
        gone("Cancel split screen")
        assertEquals(listOf(FixedApps.id("Maps")), apps.launched)
    }
}

/** A unit whose Android has no split screen. */
@RunWith(AndroidJUnit4::class)
class NoSplitScreenTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues { it.copy(apps = FixedApps(splitScreen = false)) }

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private val apps get() = values.container.apps as FixedApps
    private fun tap(description: String) = cabin.onNodeWithContentDescription(description).performClick()

    @Test
    fun aHoldOpensTheAppAndGesturesSaysWhy() {
        tap("All apps")
        cabin.onNodeWithContentDescription("Maps").performTouchInput { longClick() }
        cabin.waitForIdle()
        cabin.onNodeWithContentDescription("Cancel split screen").assertDoesNotExist()
        assertEquals(listOf(FixedApps.id("Maps")), apps.launched)

        tap("Settings")
        tap("Gesture settings")
        cabin.onNodeWithText("Unit has no split screen").assertIsDisplayed()
        // Tapping the tile changes nothing: there is nothing to switch on.
        tap("Long press on an app: split screen or off")
        cabin.waitForIdle()
        cabin.onNodeWithText("Unit has no split screen").assertIsDisplayed()
        assertTrue(values.container.settings.settings.value.longPressSplit)
    }
}

/** What is really sent to Android for a pair, and whether this device says it can split. */
@RunWith(AndroidJUnit4::class)
class SplitScreenIntentsTest {

    @Test
    fun aPairOpensBothAppsTheOrdinaryWayThenMovesTheRightOneBesideTheLeft() {
        val pair = SplitScreen.pair("org.example.maps/.Main", "org.example.music/org.example.music.Player")
        assertNotNull(pair)
        val (right, left) = pair!!.first
        // The right app first, then the left one over it, so the left one is in front.
        assertEquals("org.example.music.Player", right.component?.className)
        assertEquals("org.example.maps", left.component?.packageName)
        assertEquals("org.example.maps.Main", left.component?.className)
        assertEquals(2, pair.first.size)
        val then = pair.then
        assertEquals(right.component, then.component)

        for (intent in listOf(right, left, then)) {
            assertEquals(Intent.ACTION_MAIN, intent.action)
            assertTrue(intent.hasCategory(Intent.CATEGORY_LAUNCHER))
            assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        }
        // Only the second step asks for the place beside what is in front.
        assertEquals(0, right.flags and Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        assertEquals(0, left.flags and Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        assertTrue(then.flags and Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT != 0)
    }

    @Test
    fun somethingThatIsNotAnAppGivesNoPair() {
        assertNull(SplitScreen.pair("not a component", "org.example.music/.Player"))
        assertNull(SplitScreen.pair("org.example.maps/.Main", ""))
    }

    @Test
    fun whatCabinReadsAboutSplitScreenAgreesWithAndroidItself() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val pipe = automation.executeShellCommand("cmd activity supports-split-screen-multi-window")
        val answer = ParcelFileDescriptor.AutoCloseInputStream(pipe).bufferedReader().use { it.readText().trim() }
        assertEquals("Android says: $answer", answer == "true", SplitScreen.supported())
    }
}
