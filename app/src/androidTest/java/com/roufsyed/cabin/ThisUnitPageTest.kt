package com.roufsyed.cabin

import android.view.KeyEvent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roufsyed.cabin.apps.SplitWay
import com.roufsyed.cabin.model.Samples
import com.roufsyed.cabin.system.CabinFacts
import com.roufsyed.cabin.system.FixedPermissions
import com.roufsyed.cabin.system.FixedUnit
import com.roufsyed.cabin.system.UnitRules
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThisUnitPageTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues {
        it.copy(unit = FixedUnit(Samples.unit.copy(release = "10", sdk = 29, patch = "2020-09-05", split = SplitWay.Toggle)))
    }

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private fun tap(description: String) = cabin.onNodeWithContentDescription(description).performClick()
    private fun shows(description: String) = cabin.onNodeWithContentDescription(description).assertIsDisplayed()
    private fun gone(description: String) = cabin.onNodeWithContentDescription(description).assertDoesNotExist()

    private fun openThisUnit() {
        for (step in listOf("All apps", "Settings", "Open setup", "Next setup page", "What Android says of this unit")) tap(step)
    }

    @Test
    fun setupLeadsToWhatAndroidSaysOfTheUnit() {
        openThisUnit()
        cabin.onNodeWithText("This unit").assertIsDisplayed()
        cabin.onNodeWithText("What Android says of it. Nothing here can be changed.").assertIsDisplayed()
        shows("Android: 10 (API 29), security patch 2020-09-05")
        shows("Screen: 1280 x 800 at 160 dpi")
        shows("Split screen: Android's older kind: Cabin asks for it through Accessibility (multi-window on, split screen on)")
        shows("Accessibility: Off: Cabin is not allowed under Accessibility just now")
    }

    @Test
    fun doneLeadsBackToSetupsSecondPage() {
        openThisUnit()
        tap("Back to setup")
        shows("Choose the music app")
        shows("What Android says of this unit")
        gone("Allow location")
    }

    @Test
    fun aUnitThatIsOnNoNetworkHasNoNetworkRow() {
        openThisUnit()
        shows("Accessibility: Off: Cabin is not allowed under Accessibility just now")
        cabin.onAllNodes(hasContentDescription("Network: ", substring = true)).assertCountEquals(0)
    }

    private fun await(description: String) = cabin.waitUntil(2_000) {
        cabin.onAllNodes(hasContentDescription(description)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun bottomOf(description: String): Float {
        val row = cabin.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot
        val root = cabin.onRoot().fetchSemanticsNode().boundsInRoot
        val scale = minOf(root.width / 1280f, root.height / 800f)
        return (row.bottom - (root.height - 800f * scale) / 2f) / scale
    }

    @Test
    fun theSecondPageSaysWhatCabinCanUseAndTheThirdWhatElseTheUnitHas() {
        openThisUnit()
        tap("Next page of this unit")
        cabin.onNodeWithText("What Cabin can use here, and what is ready").assertIsDisplayed()
        val container = values.container
        val cabinFacts = CabinFacts(
            home = container.homeRole.held.value, notifications = container.media.access.value,
            brightness = container.brightness.canWrite.value, granted = container.permissions.granted.value,
            speech = container.voice.available, bluetoothOn = container.status.bluetoothOn.value,
            headlights = container.headlights.on.value,
        )
        val ready = UnitRules.ready(container.unit.facts(), cabinFacts)
        for (line in ready) shows("${line.label}: ${line.value}")
        shows("Home button: Ready: Cabin is the home app")
        shows("Split screen: Not yet: allow Cabin under Accessibility in Setup")
        shows("Position: Allowed, but Android has no position yet")
        shows("Headlights: Ready: the unit reports them")
        shows("Keys and knob: None seen yet: press a wheel key or turn the knob")
        val last = bottomOf("Keys and knob: None seen yet: press a wheel key or turn the knob")
        assertTrue("the last row ends at $last, below the card's 618", last <= 618f)
        gone("Screen: 1280 x 800 at 160 dpi")

        cabin.runOnUiThread {
            for (key in listOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_F1)) {
                cabin.activity.onKeyDown(key, KeyEvent(KeyEvent.ACTION_DOWN, key))
            }
        }
        await("Keys and knob: Seen: volume up, next, key 131")

        (container.permissions as FixedPermissions).set(emptySet())
        await("Voice: Not yet: allow the microphone in Setup")
        shows("Position: Not yet: allow location in Setup")

        tap("Next page of this unit")
        cabin.onNodeWithText("What else the unit has, as Android lists it").assertIsDisplayed()
        for (line in UnitRules.hardware(container.unit.facts())) shows("${line.label}: ${line.value}")
        shows("Wi-Fi: On")
        shows("Touch: 5 or more fingers at once")
        shows("Knob: Not listed by Android. Its turns may still arrive as keys")
        shows("Phone modem: None")
        shows("Free windows: No")
        gone("Home button: Ready: Cabin is the home app")

        tap("Next page of this unit")
        cabin.onNodeWithText("What Android says of it. Nothing here can be changed.").assertIsDisplayed()
        shows("Screen: 1280 x 800 at 160 dpi")
    }

    @Test
    fun doneLeadsBackToSetupFromALaterPageToo() {
        openThisUnit()
        tap("Next page of this unit")
        tap("Next page of this unit")
        tap("Back to setup")
        shows("What Android says of this unit")
        tap("What Android says of this unit")
        cabin.onNodeWithText("What Android says of it. Nothing here can be changed.").assertIsDisplayed()
    }
}

@RunWith(AndroidJUnit4::class)
class ThisUnitOnANetworkTest {

    private val network = "Network: 192.168.100.120 on Wi-Fi. USB debugging is off; once on, try adb connect 192.168.100.120:5555"

    @get:Rule(order = 0)
    val values = FreshDesignValues {
        it.copy(
            unit = FixedUnit(
                Samples.unit.copy(
                    release = "10", sdk = 29, split = SplitWay.Toggle, multiWindow = null, splitScreen = null,
                    maker = "A maker with a long name", model = "A model with a long name of its own",
                    board = "a_long_board_name", hardware = "a_long_hardware_name",
                    serviceOn = true, offered = listOf(1, 3, 6),
                    wifiAddress = "192.168.100.120", debugging = false,
                ),
            ),
        )
    }

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private fun tap(description: String) = cabin.onNodeWithContentDescription(description).performClick()

    @Test
    fun theNetworkRowGivesTheAddressAndWhatToTypeAndThePageStillFitsAtItsFullest() {
        for (step in listOf("All apps", "Settings", "Open setup", "Next setup page", "What Android says of this unit")) tap(step)
        cabin.onNodeWithContentDescription(
            "Accessibility: On. Android offers back, recent apps, power menu; not split screen, screenshot",
        ).assertIsDisplayed()
        val row = cabin.onNodeWithContentDescription(network).assertIsDisplayed().fetchSemanticsNode().boundsInRoot

        val root = cabin.onRoot().fetchSemanticsNode().boundsInRoot
        val scale = minOf(root.width / 1280f, root.height / 800f)
        val top = (root.height - 800f * scale) / 2f
        val bottom = (row.bottom - top) / scale
        assertTrue("the last row ends at $bottom, below the card's 618", bottom <= 618f)
    }
}
