package com.roufsyed.cabin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roufsyed.cabin.apps.FixedApps
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The main flows, driven through the real activity with the design's own values. */
@RunWith(AndroidJUnit4::class)
class MainFlowTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues()

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private fun tap(description: String) = cabin.onNodeWithContentDescription(description).performClick()

    private fun shows(description: String) = cabin.onNodeWithContentDescription(description).assertIsDisplayed()

    private fun gone(description: String) = cabin.onNodeWithContentDescription(description).assertDoesNotExist()

    @Test
    fun homeShowsTheThreePanelsRailAndBottomRow() {
        cabin.onNodeWithText("Thursday").assertIsDisplayed()
        cabin.onNodeWithText("12 November").assertIsDisplayed()
        cabin.onNodeWithText("350 m").assertIsDisplayed()
        cabin.onNodeWithText("Ring Road").assertIsDisplayed()
        cabin.onNodeWithText("Open Road").assertIsDisplayed()
        for (control in listOf(
            "Open Maps", "Open music", "Previous", "Play or pause", "Next",
            "Phone", "Phone link", "All apps", "Go back", "Volume up", "Mute or unmute", "Volume down",
            "Brightness down", "Night mode: auto, day or night", "Brightness up",
            "Radio source", "Bluetooth source", "USB source", "Android Auto source",
        )) shows(control)
        // AUX is hidden until it is switched on in Settings.
        gone("AUX source")
    }

    @Test
    fun backOnTheRailGoesOneStepBackAndIsOffOnHome() {
        // Home has nothing behind it.
        cabin.onNodeWithContentDescription("Go back").assertIsNotEnabled()
        tap("All apps")
        cabin.onNodeWithContentDescription("Go back").assertIsEnabled()
        tap("Settings")
        tap("Theme and gestures")
        tap("Choose a theme")
        shows("Back to theme and gestures")
        // Each press is one step: the theme chooser, its page, Settings, the apps, Home.
        tap("Go back")
        shows("Choose a theme")
        tap("Go back")
        shows("Theme and gestures")
        tap("Go back")
        shows("Browser")
        tap("Go back")
        shows("Open Maps")
        cabin.onNodeWithContentDescription("Go back").assertIsNotEnabled()
    }

    @Test
    fun backOnTheRailLeavesArrangingAndTheAppChooser() {
        tap("All apps")
        tap("Settings")
        tap("Arrange sources")
        cabin.onNodeWithText("Arrange sources").assertIsDisplayed()
        tap("Go back")
        shows("Arrange sources")
        tap("Open setup")
        tap("Next setup page")
        tap("Choose the maps app")
        cabin.onNodeWithText("Maps opens").assertIsDisplayed()
        tap("Go back")
        shows("Choose the maps app")
    }

    @Test
    fun railButtonMovesBetweenHomeAndApps() {
        tap("All apps")
        shows("Browser")
        shows("Weather")
        gone("Open Maps")
        // On every other screen the same button leads Home.
        gone("All apps")
        tap("Home")
        shows("Open Maps")
        shows("All apps")
    }

    @Test
    fun tappingAnAppLaunchesIt() {
        tap("All apps")
        tap("Maps")
        cabin.waitForIdle()
        assertEquals(listOf(FixedApps.id("Maps")), (values.container.apps as FixedApps).launched)
    }

    @Test
    fun cabinTileOpensSettingsInsteadOfLaunching() {
        tap("All apps")
        tap("Settings")
        shows("Arrange apps")
        assertEquals(emptyList<String>(), (values.container.apps as FixedApps).launched)
    }

    @Test
    fun arrangingHidesAnAppAndShowsItAgain() {
        tap("All apps")
        tap("Settings")
        tap("Arrange apps")
        cabin.onNodeWithText("Arrange apps").assertIsDisplayed()

        tap("Clock")
        tap("Hide or show")
        // Still on screen while arranging, so it can be shown again.
        shows("Clock")
        tap("Done arranging")
        gone("Clock")
        shows("Browser")

        tap("Settings")
        tap("Arrange apps")
        tap("Clock")
        tap("Hide or show")
        tap("Done arranging")
        shows("Clock")
    }

    @Test
    fun arrangingMovesAnApp() {
        tap("All apps")
        tap("Settings")
        tap("Arrange apps")
        tap("Browser")
        tap("Move later")
        tap("Done arranging")
        cabin.waitForIdle()
        val order = values.container.settings.settings.value.appOrder
        assertEquals(listOf(FixedApps.id("Clock"), FixedApps.id("Browser")), order.take(2))
    }

    @Test
    fun cabinsOwnTileCannotBeHidden() {
        tap("All apps")
        tap("Settings")
        tap("Arrange apps")
        tap("Settings")
        tap("Hide or show")
        tap("Done arranging")
        shows("Settings")
    }

    @Test
    fun backNeverLeavesCabin() {
        tap("All apps")
        cabin.runOnUiThread { cabin.activity.onBackPressedDispatcher.onBackPressed() }
        shows("Open Maps")
        cabin.runOnUiThread { cabin.activity.onBackPressedDispatcher.onBackPressed() }
        shows("Open Maps")
    }
}
