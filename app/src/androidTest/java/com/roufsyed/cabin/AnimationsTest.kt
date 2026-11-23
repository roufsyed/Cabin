package com.roufsyed.cabin

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnimationsTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues { container ->
        container.also { runBlocking { it.settings.update { saved -> saved.copy(animations = true) } } }
    }

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private fun tap(description: String) = cabin.onNodeWithContentDescription(description).performClick()
    private fun shows(description: String) = cabin.onNodeWithContentDescription(description).assertIsDisplayed()
    private fun gone(description: String) = cabin.onNodeWithContentDescription(description).assertDoesNotExist()
    private fun left(description: String) =
        cabin.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot.left

    private fun at(x: Float, y: Float): Offset {
        val root = cabin.onRoot().fetchSemanticsNode().boundsInRoot
        val scale = minOf(root.width / 1280f, root.height / 800f)
        return Offset((root.width - 1280f * scale) / 2f + x * scale, (root.height - 800f * scale) / 2f + y * scale)
    }

    private fun swipeLeft() = cabin.onRoot().performTouchInput { swipe(at(900f, 340f), at(400f, 340f), durationMillis = 200) }
    private fun swipeRight() = cabin.onRoot().performTouchInput { swipe(at(400f, 340f), at(900f, 340f), durationMillis = 200) }
    private fun swipeDown() = cabin.onRoot().performTouchInput { swipe(at(520f, 120f), at(520f, 330f), durationMillis = 200) }

    @Test
    fun homeAndAppsSlidePastEachOtherAndSettle() {
        val rest = left("Open Maps")
        cabin.mainClock.autoAdvance = false
        swipeLeft()
        cabin.mainClock.advanceTimeBy(90)
        cabin.onNodeWithContentDescription("Browser").assertExists()
        assertTrue("Home on its way out to the left", left("Open Maps") < rest)

        cabin.mainClock.autoAdvance = true
        shows("Browser")
        gone("Open Maps")

        swipeRight()
        shows("Open Maps")
        gone("Browser")
        assertEquals(rest, left("Open Maps"), 0.5f)
    }

    @Test
    fun theTraySlidesDownAndBackUp() {
        shows("Open Maps")
        cabin.mainClock.autoAdvance = false
        swipeDown()
        var frames = 0
        while (cabin.onAllNodesWithContentDescription("Close notification tray").fetchSemanticsNodes().isEmpty() && frames++ < 30) {
            cabin.mainClock.advanceTimeByFrame()
        }
        repeat(3) { cabin.mainClock.advanceTimeByFrame() }
        val onTheWay = cabin.onNodeWithContentDescription("Close notification tray").fetchSemanticsNode().boundsInRoot.top
        cabin.mainClock.autoAdvance = true
        shows("Close notification tray")
        val down = cabin.onNodeWithContentDescription("Close notification tray").fetchSemanticsNode().boundsInRoot.top
        assertTrue("part of the way down at first: $onTheWay of $down", onTheWay < down)

        cabin.onNodeWithContentDescription("Close notification tray").performTouchInput {
            advanceEventTime(1_000)
            click()
        }
        gone("Close notification tray")
        shows("Open Maps")
    }

    @Test
    fun theScreenFadesToBlackAndWakesAtATap() {
        swipeDown()
        shows("Close notification tray")
        cabin.onNodeWithContentDescription("Screen off").performTouchInput {
            advanceEventTime(1_000)
            click()
        }
        shows("Wake screen")
        gone("Close notification tray")
        tap("Wake screen")
        gone("Wake screen")
        shows("Open Maps")
    }

    @Test
    fun theVolumeReadoutComesAndGoes() {
        cabin.onRoot().performTouchInput {
            down(0, at(500f, 500f))
            down(1, at(560f, 500f))
            moveTo(0, at(500f, 452f))
            moveTo(0, at(500f, 404f))
        }
        shows("Volume 16")
        cabin.onRoot().performTouchInput {
            up(1)
            up(0)
        }
        gone("Volume 16")
        assertEquals(16, values.container.volume.volume.value.level)
    }
}

@RunWith(AndroidJUnit4::class)
class AnimationsSwitchTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues()

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private val settings get() = values.container.settings.settings.value
    private fun tap(description: String) = cabin.onNodeWithContentDescription(description).performClick()

    @Test
    fun oneTileInThemeAndGesturesSwitchesEveryAnimation() {
        tap("All apps")
        tap("Settings")
        tap("Theme and gestures")
        tap("Animations: on or off")
        cabin.waitUntil(2_000) { settings.animations }
        tap("Animations: on or off")
        cabin.waitUntil(2_000) { !settings.animations }
    }
}
