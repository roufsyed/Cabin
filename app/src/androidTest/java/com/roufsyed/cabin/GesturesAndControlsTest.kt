package com.roufsyed.cabin

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roufsyed.cabin.apps.FixedApps
import com.roufsyed.cabin.media.FixedMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Volume, brightness and the gestures, on the real activity with the design's values. */
@RunWith(AndroidJUnit4::class)
class GesturesAndControlsTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues()

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private val apps get() = values.container.apps as FixedApps
    private val media get() = values.container.media as FixedMedia
    private val volume get() = values.container.volume.volume.value

    private fun tap(description: String) = cabin.onNodeWithContentDescription(description).performClick()
    private fun shows(description: String) = cabin.onNodeWithContentDescription(description).assertIsDisplayed()
    private fun gone(description: String) = cabin.onNodeWithContentDescription(description).assertDoesNotExist()

    /** A point given in design pixels, as a position on this screen. */
    private fun at(x: Float, y: Float): Offset {
        val root = cabin.onRoot().fetchSemanticsNode().boundsInRoot
        val scale = minOf(root.width / 1280f, root.height / 800f)
        return Offset((root.width - 1280f * scale) / 2f + x * scale, (root.height - 800f * scale) / 2f + y * scale)
    }

    @Test
    fun volumeButtonsMoveOneStepPerTapAndMuteKeepsTheLevel() {
        tap("Volume up")
        cabin.onNodeWithText("13").assertIsDisplayed()
        tap("Volume down")
        tap("Volume down")
        cabin.onNodeWithText("11").assertIsDisplayed()
        tap("Mute or unmute")
        cabin.onNodeWithText("11").assertIsDisplayed()
        assertTrue(volume.muted)
        tap("Volume up")
        assertEquals(12, volume.level)
        assertTrue(!volume.muted)
    }

    @Test
    fun brightnessStepsAndCyclesAutoDayNight() {
        tap("Brightness up")
        cabin.onNodeWithText("9").assertIsDisplayed()
        cabin.onNodeWithText("Auto").assertIsDisplayed()
        tap("Night mode: auto, day or night")
        cabin.onNodeWithText("Day").assertIsDisplayed()
        tap("Night mode: auto, day or night")
        cabin.onNodeWithText("Night").assertIsDisplayed()
        // Night has its own level, untouched by the step made in daylight.
        cabin.onNodeWithText("3").assertIsDisplayed()
        tap("Brightness down")
        cabin.onNodeWithText("2").assertIsDisplayed()
        tap("Night mode: auto, day or night")
        cabin.onNodeWithText("Auto").assertIsDisplayed()
        cabin.onNodeWithText("9").assertIsDisplayed()
    }

    @Test
    fun oneFingerLeftAndRightMoveBetweenHomeAndApps() {
        cabin.onRoot().performTouchInput { swipe(at(900f, 340f), at(400f, 340f), durationMillis = 200) }
        shows("Browser")
        gone("Open Maps")
        cabin.onRoot().performTouchInput { swipe(at(400f, 340f), at(900f, 340f), durationMillis = 200) }
        shows("Open Maps")
    }

    @Test
    fun aSwipeThatStartsOnAButtonNeverPressesIt() {
        // Starts in the middle of the Now playing panel, which opens Music when tapped.
        cabin.onRoot().performTouchInput { swipe(at(912f, 250f), at(500f, 250f), durationMillis = 200) }
        shows("Browser")
        gone("Play Open Road")
        assertEquals(emptyList<String>(), media.calls)
    }

    @Test
    fun aShortDragOnAButtonIsNeitherATapNorASwipe() {
        cabin.onRoot().performTouchInput {
            down(at(912f, 250f))
            moveTo(at(930f, 250f))
            moveTo(at(945f, 250f))
            up()
        }
        shows("Open Maps")
        gone("Play Open Road")
    }

    @Test
    fun aTapStraightAfterASwipeIsIgnoredAndALaterOneWorks() {
        val mapsTile = at(138f, 396f)
        cabin.onRoot().performTouchInput {
            swipe(at(900f, 340f), at(400f, 340f), durationMillis = 150)
            advanceEventTime(60)
            down(mapsTile)
            advanceEventTime(40)
            up()
        }
        shows("Maps")
        assertEquals(emptyList<String>(), apps.launched)

        cabin.onRoot().performTouchInput {
            advanceEventTime(600)
            down(mapsTile)
            advanceEventTime(40)
            up()
        }
        cabin.waitForIdle()
        assertEquals(listOf(FixedApps.id("Maps")), apps.launched)
    }

    @Test
    fun twoFingersDragTheVolumeUnderALargeReadout() {
        cabin.onRoot().performTouchInput {
            down(0, at(500f, 500f))
            down(1, at(560f, 500f))
            // 96 design pixels up: four steps of 24.
            moveTo(0, at(500f, 452f))
            moveTo(0, at(500f, 404f))
        }
        shows("Volume 16")
        val readout = cabin.onNodeWithContentDescription("Volume 16").fetchSemanticsNode().boundsInRoot
        val expected = at(500f, 40f)
        assertTrue("readout at $readout", abs(readout.left - expected.x) <= 1.01f && abs(readout.top - expected.y) <= 1.01f)

        cabin.onRoot().performTouchInput {
            moveTo(0, at(500f, 500f))
            up(1)
            up(0)
        }
        // The readout goes when the fingers lift; the level it left stays on the rail.
        gone("Volume 12")
        gone("Volume 16")
        assertEquals(12, volume.level)
        shows("Open Maps")
    }

    @Test
    fun twoFingersNeverPressWhatIsUnderThem() {
        cabin.onRoot().performTouchInput {
            down(0, at(912f, 250f))
            down(1, at(980f, 250f))
            up(1)
            up(0)
        }
        shows("Open Maps")
        gone("Play Open Road")
    }
}
