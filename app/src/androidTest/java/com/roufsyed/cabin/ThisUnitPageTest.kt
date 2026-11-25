package com.roufsyed.cabin

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
import com.roufsyed.cabin.system.FixedUnit
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
