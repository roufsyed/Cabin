package com.roufsyed.cabin

import android.view.KeyEvent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roufsyed.cabin.apps.FixedApps
import com.roufsyed.cabin.data.CabinSettings
import com.roufsyed.cabin.data.InMemorySettings
import com.roufsyed.cabin.media.FixedMedia
import com.roufsyed.cabin.model.Slot
import com.roufsyed.cabin.model.SourceId
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

    /** Presses a key the way the unit delivers a steering wheel button to the app on screen. */
    private fun press(keyCode: Int) = cabin.runOnUiThread {
        cabin.activity.onKeyDown(keyCode, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        cabin.activity.onKeyUp(keyCode, KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    @Test
    fun settingsHasTheDesignsSixTilesAndSetup() {
        openSettings()
        for (tile in listOf(
            "Arrange apps", "Arrange sources", "Steering wheel keys", "Turn the screen off",
            "Bluetooth settings", "Wi-Fi settings", "Open setup",
        )) shows(tile)
        // The design's two "Prototype only" tiles are not part of the app.
        cabin.onNodeWithText("Incoming call").assertDoesNotExist()
        cabin.onNodeWithText("Headlights").assertDoesNotExist()
    }

    @Test
    fun screenOffTileShowsTheClock() {
        openSettings()
        tap("Turn the screen off")
        shows("Wake screen")
        tap("Wake screen")
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
    fun setupListsEverythingCabinAsksForAndTheTwoRailApps() {
        openSettings()
        tap("Open setup")
        cabin.onNodeWithText("Setup").assertIsDisplayed()
        for (tile in listOf(
            "Make Cabin the home app", "Allow notification access", "Allow changing system settings",
            "Allow location", "Allow microphone", "Allow nearby devices",
            "Choose the phone app", "Choose the phone link app",
        )) shows(tile)

        tap("Choose the phone app")
        cabin.onNodeWithText("Phone opens").assertIsDisplayed()
        tap("Maps")
        // Back on Setup, where the tile now names the chosen app.
        shows("Choose the phone app")
        cabin.waitUntil(2_000) { settings.bindings[Slot.Phone] == FixedApps.id("Maps") }

        tap("Finish setup")
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
        // Location is granted, microphone and nearby devices are not.
        cabin.onAllNodesWithText("Off").assertCountEquals(2)

        cabin.onNodeWithContentDescription("Finish setup").performClick()
        cabin.onNodeWithContentDescription("Open Maps").assertIsDisplayed()
        cabin.waitUntil(2_000) { values.container.settings.settings.value.setupDone }
    }
}
