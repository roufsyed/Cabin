package com.roufsyed.cabin

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roufsyed.cabin.media.FixedMedia
import com.roufsyed.cabin.model.Samples
import com.roufsyed.cabin.model.StatusUi
import com.roufsyed.cabin.notify.FixedTray
import com.roufsyed.cabin.system.FixedClock
import com.roufsyed.cabin.system.FixedStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The notification tray, the screen-off clock and the status marks, with the design's values. */
@RunWith(AndroidJUnit4::class)
class TrayAndStatusTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues()

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private val tray get() = values.container.tray as FixedTray

    /**
     * A tap made a moment after whatever came before. Cabin ignores a tap within 0.4 s of a swipe, and
     * each injected gesture starts its clock afresh, so the pause has to be part of the tap itself.
     */
    private fun tap(description: String) = cabin.onNodeWithContentDescription(description).performTouchInput {
        advanceEventTime(1_000)
        click()
    }

    private fun shows(description: String) = cabin.onNodeWithContentDescription(description).assertIsDisplayed()
    private fun gone(description: String) = cabin.onNodeWithContentDescription(description).assertDoesNotExist()

    private fun at(x: Float, y: Float): Offset {
        val root = cabin.onRoot().fetchSemanticsNode().boundsInRoot
        val scale = minOf(root.width / 1280f, root.height / 800f)
        return Offset((root.width - 1280f * scale) / 2f + x * scale, (root.height - 800f * scale) / 2f + y * scale)
    }

    // A tap within 0.4 s of a swipe is ignored by design, so each swipe is followed by a pause, as a
    // person would leave before touching what the swipe revealed.
    private fun swipeDown() = cabin.onRoot().performTouchInput {
        swipe(at(520f, 120f), at(520f, 330f), durationMillis = 200)
        advanceEventTime(600)
    }

    private fun swipeUp() = cabin.onRoot().performTouchInput {
        swipe(at(520f, 620f), at(520f, 420f), durationMillis = 200)
        advanceEventTime(600)
    }

    private val missedCall = "Missed call. Meera · tap to call back"

    @Test
    fun oneFingerDownOpensTheTrayAndUpClosesIt() {
        gone("Close notification tray")
        swipeDown()
        cabin.onNodeWithText("Notifications").assertIsDisplayed()
        shows(missedCall)
        shows("Kabir. Running ten minutes late")
        shows("USB drive connected. Tap to play from USB")
        swipeUp()
        gone("Close notification tray")
    }

    @Test
    fun closeAndATapOnTheDimmedScreenBothCloseTheTray() {
        swipeDown()
        tap("Close notification tray")
        gone(missedCall)
        swipeDown()
        // Below the sheet, which with three notifications ends at 696 of the 800.
        cabin.onNodeWithContentDescription("Close notifications").performTouchInput {
            advanceEventTime(1_000)
            click(at(640f, 760f))
        }
        gone(missedCall)
    }

    @Test
    fun tappingANotificationOpensItAndClosesTheTray() {
        swipeDown()
        tap(missedCall)
        cabin.waitForIdle()
        assertEquals(listOf("call"), tray.opened)
        gone("Close notification tray")
        swipeDown()
        gone(missedCall)
        shows("Kabir. Running ten minutes late")
    }

    @Test
    fun clearAllEmptiesTheTray() {
        swipeDown()
        tap("Clear all notifications")
        cabin.onNodeWithText("No notifications").assertIsDisplayed()
        gone("Clear all notifications")
        shows("Screen off")
    }

    @Test
    fun screenOffInTheTrayDarkensTheScreenInOneTapUntilItIsTapped() {
        swipeDown()
        tap("Screen off")
        shows("Wake screen")
        cabin.onNodeWithText("10:10").assertIsDisplayed()
        cabin.onNodeWithText("Tap to wake").assertIsDisplayed()
        cabin.onNodeWithText("Thursday 12 November").assertIsDisplayed()
        tap("Wake screen")
        gone("Wake screen")
        shows("Open Maps")
    }

    @Test
    fun theTrayOpensOverAnyScreenAndASwipeUnderItDoesNotChangePage() {
        tap("All apps")
        swipeDown()
        shows(missedCall)
        cabin.onRoot().performTouchInput {
            swipe(at(400f, 600f), at(900f, 600f), durationMillis = 200)
            advanceEventTime(600)
        }
        tap("Close notification tray")
        // Still on Apps: the sideways swipe was ignored while the tray was open.
        shows("Browser")
    }

    @Test
    fun statusMarksFollowTheUnit() {
        shows("Phone connected, Wi-Fi on, Hotspot off, GPS ready")
        cabin.runOnUiThread { (values.container.status as FixedStatus).set(StatusUi(phone = false, wifi = false, gps = true)) }
        shows("Phone not connected, Wi-Fi off, Hotspot off, GPS ready")
        tap("All apps")
        // The same marks sit under the date on every other screen.
        shows("Phone not connected, Wi-Fi off, Hotspot off, GPS ready")
    }
}

@RunWith(AndroidJUnit4::class)
class TrayWithoutAccessTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues { it.copy(media = FixedMedia(null, hasAccess = false), tray = FixedTray()) }

    @get:Rule(order = 1)
    val cabin = cabinRule()

    @Test
    fun withoutNotificationAccessTheTraySaysHowToGetIt() {
        cabin.onRoot().performTouchInput {
            val bounds = visibleSize
            swipe(Offset(bounds.width * 0.4f, bounds.height * 0.15f), Offset(bounds.width * 0.4f, bounds.height * 0.45f), 200)
        }
        cabin.onNodeWithContentDescription("Allow notification access to see them here").assertIsDisplayed()
        cabin.onNodeWithContentDescription("Clear all notifications").assertDoesNotExist()
        cabin.onNodeWithText("No access yet").assertIsDisplayed()
    }
}

@RunWith(AndroidJUnit4::class)
class LongestWeekdayTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues { it.copy(clock = FixedClock(Samples.clock.copy(weekday = "Wednesday"))) }

    @get:Rule(order = 1)
    val cabin = cabinRule()

    @Test
    fun theLongestWeekdayIsWholeBesideTheFourMarks() {
        val day = cabin.onNodeWithText("Wednesday", useUnmergedTree = true)
        day.assertIsDisplayed()
        val text = day.fetchSemanticsNode()
        val marks = cabin.onNodeWithContentDescription("Phone connected, Wi-Fi on, Hotspot off, GPS ready")
            .fetchSemanticsNode().boundsInRoot
        assertTrue("day ${text.boundsInRoot} beside marks $marks", text.boundsInRoot.right <= marks.left)
        val layout = text.config.getOrNull(SemanticsActions.GetTextLayoutResult)
        val results = mutableListOf<TextLayoutResult>()
        layout?.action?.invoke(results)
        assertTrue("layout read", results.isNotEmpty())
        val line = results[0]
        assertTrue(
            "not cut short: needs ${line.multiParagraph.maxIntrinsicWidth} of ${line.size.width}, ellipsis ${line.isLineEllipsized(0)}",
            !line.isLineEllipsized(0),
        )
    }
}
