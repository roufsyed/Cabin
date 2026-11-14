package com.roufsyed.cabin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roufsyed.cabin.model.VolumeUi
import com.roufsyed.cabin.ui.chrome.SideBar
import com.roufsyed.cabin.ui.chrome.SideBarHandle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The bar that stays over other apps, drawn on its own at the size it has on the design's screen: 66
 * wide and 800 high. The window it lives in over other apps belongs to an accessibility service, which
 * Android switches off while tests run, so that part is checked by hand (see NOTES.md).
 *
 * The test's own window is a little under 800 high, so the bar is drawn at nine tenths and measured
 * back in design pixels.
 */
@RunWith(AndroidJUnit4::class)
class SideBarTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private var volume by mutableStateOf(VolumeUi(level = 7, muted = false))

    private companion object {
        const val SCALE = 0.9f
    }

    private val buttons = listOf("Phone", "Phone link", "Home", "Go back", "Volume up", "Mute or unmute", "Volume down")

    private fun show(split: Boolean = false, handle: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = SCALE, fontScale = 1f)) {
                Box(Modifier.size(66.dp, 800.dp)) {
                    SideBar(
                        volume = volume,
                        onPhone = { calls += "phone" },
                        onLink = { calls += "link" },
                        onSplit = if (split) ({ calls += "split" }) else null,
                        onHome = { calls += "home" },
                        onRecents = { calls += "recents" },
                        onBack = { calls += "back" },
                        onVolumeUp = { calls += "up" },
                        onMute = { calls += "mute" },
                        onVolumeDown = { calls += "down" },
                        onTuck = if (handle) ({ calls += "tuck" }) else null,
                    )
                }
            }
        }
    }

    /** Where a button is, in design pixels. */
    private fun box(description: String): Rect {
        val box = compose.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot
        return Rect(box.left / SCALE, box.top / SCALE, box.right / SCALE, box.bottom / SCALE)
    }

    /**
     * Checks the buttons run from the top of the bar to the bottom, each [height] tall, with [gap]
     * between Back and the volume buttons: the one rule on the bar, or the grip.
     */
    private fun assertStacked(buttons: List<String>, height: Float, gap: Float = 1f) {
        var top = 0f
        for (button in buttons) {
            val box = box(button)
            // Half the rail wide, less the hairline on the edge that meets the app.
            assertEquals("$button left", 1f, box.left, 1f)
            assertEquals("$button width", 65f, box.width, 1f)
            assertEquals("$button height", height, box.height, 1.5f)
            assertEquals("$button top", top, box.top, 1.5f)
            top = box.bottom
            if (button == "Go back") top += gap
        }
        assertEquals(800f, top, 2f)
    }

    @Test
    fun withoutSplitScreenTheBarHasTheRailsSevenButtons() {
        show()
        compose.onNodeWithContentDescription("Split screen").assertDoesNotExist()
        // Seven share the height: 114 each, where the design asks at least 96 of anything touched.
        assertStacked(buttons, 114f)
    }

    @Test
    fun withSplitScreenThereIsASplitButtonAboveHome() {
        show(split = true)
        // Eight share the height: 100 each.
        assertStacked(buttons.take(2) + "Split screen" + buttons.drop(2), 100f)
    }

    @Test
    fun withTheHandleThereIsAGripBetweenTheTwoGroupsAndEveryButtonIs96Tall() {
        show(split = true, handle = true)
        assertStacked(buttons.take(2) + "Split screen" + buttons.drop(2), 96f, gap = 32f)
        val grip = box("Tuck the side bar away")
        assertEquals(480f, grip.top, 1.5f)
        assertEquals(32f, grip.height, 1f)
        assertEquals(65f, grip.width, 1f)
    }

    @Test
    fun withTheHandleAndNoSplitTheSevenButtonsShareWhatTheGripLeaves() {
        show(handle = true)
        assertStacked(buttons, 109.7f, gap = 32f)
    }

    @Test
    fun withoutTheHandleThereIsNoGrip() {
        show(split = true)
        compose.onNodeWithContentDescription("Tuck the side bar away").assertDoesNotExist()
    }

    @Test
    fun theGripTucksTheBarAway() {
        show(split = true, handle = true)
        compose.onNodeWithContentDescription("Tuck the side bar away").performClick()
        compose.waitForIdle()
        assertEquals(listOf("tuck"), calls)
    }

    @Test
    fun theHandleIsASmallTabThatBringsTheBarBack() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = SCALE, fontScale = 1f)) {
                SideBarHandle(onClick = { calls += "back out" })
            }
        }
        val handle = box("Bring the side bar back")
        assertEquals(28f, handle.width, 1f)
        assertEquals(96f, handle.height, 1f)
        compose.onNodeWithContentDescription("Bring the side bar back").performClick()
        compose.waitForIdle()
        assertEquals(listOf("back out"), calls)
    }

    @Test
    fun eachButtonDoesItsOwnThing() {
        show(split = true)
        for (button in buttons.take(2) + "Split screen" + buttons.drop(2)) {
            compose.onNodeWithContentDescription(button).performClick()
        }
        compose.waitForIdle()
        assertEquals(listOf("phone", "link", "split", "home", "back", "up", "mute", "down"), calls)
    }

    @Test
    fun holdingHomeShowsTheRecentApps() {
        show()
        compose.onNodeWithContentDescription("Home").performTouchInput { longClick() }
        compose.waitForIdle()
        // A hold is not also a tap.
        assertEquals(listOf("recents"), calls)
    }

    @Test
    fun theBarShowsTheVolumeLevelAndFollowsIt() {
        show()
        compose.onNodeWithText("7").assertIsDisplayed()
        volume = VolumeUi(level = 12, muted = true)
        compose.waitForIdle()
        compose.onNodeWithText("12").assertIsDisplayed()
    }
}
