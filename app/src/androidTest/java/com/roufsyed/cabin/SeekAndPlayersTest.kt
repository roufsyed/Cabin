package com.roufsyed.cabin

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roufsyed.cabin.media.FixedMedia
import com.roufsyed.cabin.media.MediaSource
import com.roufsyed.cabin.media.PlaybackProgress
import com.roufsyed.cabin.media.PlayerFacts
import com.roufsyed.cabin.media.PlayerLibrary
import com.roufsyed.cabin.media.PlayerRules
import com.roufsyed.cabin.model.Samples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** The design's own track, 1:24 into its 3:41, in a player that lets it be moved. */
private val movable = Samples.playing.copy(progress = Samples.playing.progress.copy(seekable = true))

/** What the classes here do to the Music screen's slider. */
private class Slider(private val cabin: CabinRule) {
    fun node() = cabin.onNodeWithContentDescription("Track position")

    fun openMusic() {
        cabin.onNodeWithContentDescription("Open music").performClick()
        cabin.onNodeWithText("Up next").assertIsDisplayed()
    }

    /** What the slider says of itself: "1:24 of 3:41". */
    fun says(state: String) =
        node().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state))

    fun text(text: String) = cabin.onNodeWithText(text).assertIsDisplayed()

    fun awaitText(text: String) =
        cabin.waitUntil(5_000) { cabin.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    /** A point given in design pixels, as a position on this screen. */
    fun at(x: Float, y: Float): Offset {
        val root = cabin.onRoot().fetchSemanticsNode().boundsInRoot
        val scale = minOf(root.width / 1280f, root.height / 800f)
        return Offset((root.width - 1280f * scale) / 2f + x * scale, (root.height - 800f * scale) / 2f + y * scale)
    }

    /** Where the slider is, in design pixels. */
    fun bounds(): Rect {
        val root = cabin.onRoot().fetchSemanticsNode().boundsInRoot
        val scale = minOf(root.width / 1280f, root.height / 800f)
        val left = (root.width - 1280f * scale) / 2f
        val top = (root.height - 800f * scale) / 2f
        val box = node().fetchSemanticsNode().boundsInRoot
        return Rect((box.left - left) / scale, (box.top - top) / scale, (box.right - left) / scale, (box.bottom - top) / scale)
    }
}

/**
 * A point [fraction] of the way along the slider's line, level with the middle of its reach. The
 * reach is 478 design pixels wide and the line is its middle 414: it runs 32 past the line at each end.
 */
private fun TouchInjectionScope.along(fraction: Float): Offset {
    val beside = width * 32f / 478f
    return Offset(beside + (width - 2f * beside) * fraction, centerY)
}

/** The slider that the Music screen's progress line becomes for a track its player lets be moved. */
@RunWith(AndroidJUnit4::class)
class SeekTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues { it.copy(media = FixedMedia(movable)) }

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private val media get() = values.container.media as FixedMedia
    private val slider by lazy { Slider(cabin) }

    /** Where the player was last told to move its track to. */
    private fun sought(): Long = media.calls.last { it.startsWith("seek:") }.removePrefix("seek:").toLong()
    private fun seeks(): Int = media.calls.count { it.startsWith("seek:") }

    private fun assertNear(expected: Long, actual: Long) =
        assertTrue("expected about $expected but was $actual", abs(expected - actual) <= 1_000)

    @Test
    fun theLineIsASliderWithoutTakingAnyMoreRoom() {
        slider.openMusic()
        slider.says("1:24 of 3:41")
        slider.text("1:24")
        // The line is where it always was, 414 wide from x 73 in the left card. The finger's reach
        // around it is as wide as the card and 96 high, 40 of that above the line.
        val box = slider.bounds()
        assertEquals(Rect(41f, 359f, 519f, 455f).toString(), Rect(box.left.round(), box.top.round(), box.right.round(), box.bottom.round()).toString())
        // And the buttons under it have not moved.
        val previous = cabin.onNodeWithContentDescription("Previous").fetchSemanticsNode().boundsInRoot
        assertEquals(slider.at(108f, 519f).y, previous.top, 1.01f)
    }

    private fun Float.round() = Math.round(this).toFloat()

    @Test
    fun draggingTheKnobMovesTheTrackWhenTheFingerLifts() {
        slider.openMusic()
        slider.node().performTouchInput {
            down(along(0.38f))
            moveTo(along(0.6f))
            moveTo(along(0.75f))
        }
        // The time under the finger is shown as it goes, and the player is told nothing yet.
        slider.text("2:45")
        slider.says("2:45 of 3:41")
        assertEquals(0, seeks())

        slider.node().performTouchInput { up() }
        cabin.waitForIdle()
        assertEquals(1, seeks())
        assertNear(165_750, sought())
        slider.says("2:45 of 3:41")
        // A finger drawn that far to the right anywhere else leaves for Home; here it does not.
        slider.text("Up next")
    }

    @Test
    fun aTapAnywhereAlongTheLinePutsTheTrackThere() {
        slider.openMusic()
        slider.node().performTouchInput { click(along(0.5f)) }
        cabin.waitForIdle()
        assertNear(110_500, sought())
        slider.says("1:50 of 3:41")

        // Either end of the line is the start and the end of the track.
        slider.node().performTouchInput { click(Offset(0f, centerY)) }
        cabin.waitForIdle()
        assertEquals(0L, sought())
        slider.says("0:00 of 3:41")
    }

    @Test
    fun aFingerRunPastAnEndOfTheLineStaysOnIt() {
        slider.openMusic()
        slider.node().performTouchInput {
            down(along(0.5f))
            moveTo(Offset(width + 300f, centerY))
        }
        slider.says("3:41 of 3:41")
        slider.node().performTouchInput {
            moveTo(Offset(-300f, centerY))
            up()
        }
        cabin.waitForIdle()
        assertEquals(0L, sought())
    }

    @Test
    fun theSlidersReachIsLargerThanTheLine() {
        slider.openMusic()
        // 35 above the line, where nothing is drawn.
        slider.node().performTouchInput { click(Offset(along(0.25f).x, 5f)) }
        cabin.waitForIdle()
        assertNear(55_250, sought())
        // On the times under it, and just below them.
        slider.node().performTouchInput { click(Offset(along(0.75f).x, height - 5f)) }
        cabin.waitForIdle()
        assertNear(165_750, sought())
        // Beside either end of the line, which is where the knob is as a track starts and ends: a
        // finger there has the slider, at that end, and has not begun a swipe.
        slider.node().performTouchInput {
            down(Offset(5f, centerY))
            moveTo(along(0.5f))
            up()
        }
        cabin.waitForIdle()
        assertNear(110_500, sought())
        slider.text("Up next")
        slider.node().performTouchInput { click(Offset(width - 5f, centerY)) }
        cabin.waitForIdle()
        assertEquals(221_000L, sought())
    }

    @Test
    fun noGestureIsReadFromAFingerOnTheSlider() {
        slider.openMusic()
        // Straight down from the line: anywhere else this opens the tray.
        slider.node().performTouchInput {
            down(along(0.5f))
            moveBy(Offset(0f, 120f))
            moveBy(Offset(0f, 120f))
            up()
        }
        cabin.waitForIdle()
        cabin.onNodeWithText("Notifications").assertDoesNotExist()
        slider.text("Up next")
        assertNear(110_500, sought())
    }

    @Test
    fun twoFingersOnTheSliderMoveNothing() {
        slider.openMusic()
        slider.node().performTouchInput {
            down(0, along(0.6f))
            down(1, along(0.8f))
            moveBy(0, Offset(0f, -60f))
            moveBy(1, Offset(0f, -60f))
            up(0)
            up(1)
        }
        cabin.waitForIdle()
        assertEquals(0, seeks())
        slider.says("1:24 of 3:41")
        // Nor is it the two-finger drag that changes the volume: the touch began as the slider's.
        assertEquals(12, values.container.volume.volume.value.level)

        // The next touch is an ordinary one again.
        slider.node().performTouchInput {
            advanceEventTime(1_000)
            click(along(0.5f))
        }
        cabin.waitForIdle()
        assertEquals(1, seeks())
    }

    @Test
    fun aSwipeBesideTheSliderStillLeavesForHome() {
        slider.openMusic()
        cabin.onRoot().performTouchInput { swipe(slider.at(600f, 300f), slider.at(1000f, 300f), durationMillis = 200) }
        cabin.waitForIdle()
        cabin.onNodeWithContentDescription("Open music").assertIsDisplayed()
        assertEquals(0, seeks())
    }

    @Test
    fun theSliderFollowsThePlayer() {
        slider.openMusic()
        // The player moves on by itself, as it does when a track is wound in its own app.
        media.set(movable.copy(progress = movable.progress.copy(positionMs = 200_000)))
        cabin.waitForIdle()
        slider.says("3:20 of 3:41")
        // The next track is shorter, and the slider is that track's.
        media.set(movable.copy(title = "Night Signals", progress = movable.progress.copy(positionMs = 30_000, durationMs = 120_000)))
        cabin.waitForIdle()
        slider.says("0:30 of 2:00")
        slider.node().performTouchInput { click(along(0.5f)) }
        cabin.waitForIdle()
        assertNear(60_000, sought())
    }

    @Test
    fun aTrackItsPlayerWillNotMoveHasThePlainLine() {
        slider.openMusic()
        media.set(Samples.playing)
        cabin.waitForIdle()
        slider.node().assertDoesNotExist()
        slider.text("1:24")

        // Nor has a stream with no length anything to slide along, whatever its player says.
        media.set(movable.copy(progress = PlaybackProgress(5_000, 0, 1f, playing = true, atElapsedMs = 0, seekable = false)))
        cabin.waitForIdle()
        slider.node().assertDoesNotExist()
        slider.text("Live")

        // A finger drawn along the plain line is a swipe like any other.
        cabin.onRoot().performTouchInput { swipe(slider.at(100f, 400f), slider.at(450f, 400f), durationMillis = 200) }
        cabin.waitForIdle()
        cabin.onNodeWithContentDescription("Open music").assertIsDisplayed()
        assertEquals(0, seeks())
    }

    @Test
    fun homeKeepsItsPlainLineAndItsOneButton() {
        slider.node().assertDoesNotExist()
        slider.text("1:24")
        // The line is part of the button that opens the Music screen, where the slider is.
        cabin.onNodeWithText("1:24").performClick()
        slider.text("Up next")
        slider.node().assertIsDisplayed()
    }
}

/** A player that says its track can be moved and then takes no notice. */
private class DeafMedia(inner: FixedMedia) : MediaSource by inner {
    val asked = mutableListOf<Long>()

    override fun seekTo(positionMs: Long) {
        asked += positionMs
    }
}

/** What the slider does about a player that does not follow. */
@RunWith(AndroidJUnit4::class)
class SeekIgnoredTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues { it.copy(media = DeafMedia(FixedMedia(movable))) }

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private val slider by lazy { Slider(cabin) }

    @Test
    fun theKnobWaitsWhereItWasPutAndThenGoesBack() {
        slider.openMusic()
        slider.node().performTouchInput { click(along(0.75f)) }
        cabin.waitForIdle()
        assertEquals(1, (values.container.media as DeafMedia).asked.size)
        // No springing back to 1:24 the moment the finger lifts: the player is given time to answer.
        slider.says("2:45 of 3:41")
        // It never does, so after a moment the knob is back where the track really is.
        slider.awaitText("1:24")
        slider.says("1:24 of 3:41")
    }
}

/** Two players at once, the second of them the unit's own kind: a title, and neither a length nor a queue. */
private class TwoPlayers(inner: FixedMedia) : MediaSource by inner {
    private val first = inner.players().single()

    override fun players(): List<PlayerFacts> = listOf(
        first,
        first.copy(
            packageName = "com.unit.media", appLabel = "Unit player", shown = false,
            state = PlayerRules.STATE_PAUSED, positionMs = 0, speed = 0f, actions = PlayerRules.TRANSPORT,
            durationMs = null, details = listOf("android.media.metadata.TITLE", "length=215"), queueSize = 0,
            library = PlayerLibrary.Closed,
        ),
    )
}

/** Setup > Players: what each running player tells Cabin about itself. */
@RunWith(AndroidJUnit4::class)
class PlayersPageTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues { it.copy(media = TwoPlayers(FixedMedia(movable))) }

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private fun tap(description: String) = cabin.onNodeWithContentDescription(description).performClick()
    private fun shows(description: String) = cabin.onNodeWithContentDescription(description).assertIsDisplayed()
    private fun gone(description: String) = cabin.onNodeWithContentDescription(description).assertDoesNotExist()
    private fun text(text: String) = cabin.onNodeWithText(text).assertIsDisplayed()

    private fun openPlayers() {
        tap("All apps")
        tap("Settings")
        tap("Open setup")
        tap("Next setup page")
        tap("What the players tell Cabin")
    }

    @Test
    fun eachPlayerHasAPageSayingWhatItTellsCabin() {
        openPlayers()
        text("Bluetooth audio")
        text("Player 1 of 2, the one Cabin shows")
        shows("App: design.music")
        shows("Slider: Yes")
        shows("State: Playing, at 1:24, speed 0.0")
        shows("Length: 3:41")
        shows("Can be told to: Play, pause, next, previous, move within the track (0x136)")
        shows("Track details: title, artist, duration")
        shows("Lists: Queue of 4; no library to browse")
        shows("Also sends: Nothing")

        tap("Next player")
        text("Unit player")
        text("Player 2 of 2")
        shows("App: com.unit.media")
        // Why this one's line is no slider, and what else it sent that might be the length.
        shows("Slider: No: it gives no length for the track")
        shows("State: Paused, at 0:00, speed 0.0")
        shows("Length: Not given")
        shows("Can be told to: Play, pause, next, previous (0x36)")
        shows("Track details: title, length=215")
        shows("Lists: No queue; a library that turns Cabin away")

        // The button goes round, and a swipe turns the pages as well.
        tap("Next player")
        text("Bluetooth audio")
        cabin.onRoot().performTouchInput {
            advanceEventTime(1_000)
            swipe(Offset(centerX + 200f, centerY), Offset(centerX - 200f, centerY), durationMillis = 200)
        }
        cabin.waitForIdle()
        text("Unit player")
    }

    @Test
    fun doneLeadsBackToSetupsSecondPage() {
        openPlayers()
        tap("Next player")
        tap("Back to setup")
        shows("Choose the music app")
        shows("What the players tell Cabin")
        gone("Allow location")
    }
}

/** The same page with nothing to show on it. */
@RunWith(AndroidJUnit4::class)
class PlayersPageWithNoPlayerTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues()

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private val media get() = values.container.media as FixedMedia

    @Test
    fun aPlayerThatStopsOrStartsShowsWithinAMoment() {
        for (step in listOf("All apps", "Settings", "Open setup", "Next setup page", "What the players tell Cabin")) {
            cabin.onNodeWithContentDescription(step).performClick()
        }
        // One player, so there is no page to turn to.
        cabin.onNodeWithText("Player 1 of 1, the one Cabin shows").assertIsDisplayed()
        cabin.onNodeWithContentDescription("Next player").assertDoesNotExist()
        // The design's own track is drawn with a plain line.
        cabin.onNodeWithContentDescription("Slider: No: it does not say the track can be moved").assertIsDisplayed()

        media.set(null)
        cabin.waitUntil(5_000) { cabin.onAllNodesWithText("No player is running").fetchSemanticsNodes().isNotEmpty() }
        cabin.onNodeWithText("Players").assertIsDisplayed()

        media.set(movable)
        cabin.waitUntil(5_000) { cabin.onAllNodesWithText("Bluetooth audio").fetchSemanticsNodes().isNotEmpty() }
        cabin.onNodeWithContentDescription("Slider: Yes").assertIsDisplayed()
    }
}

/** And before Cabin has been given notification access, which is what lets it see players at all. */
@RunWith(AndroidJUnit4::class)
class PlayersPageWithoutAccessTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues { it.copy(media = FixedMedia(null, hasAccess = false)) }

    @get:Rule(order = 1)
    val cabin = cabinRule()

    @Test
    fun thePageSaysWhatIsMissing() {
        for (step in listOf("All apps", "Settings", "Open setup", "Next setup page", "What the players tell Cabin")) {
            cabin.onNodeWithContentDescription(step).performClick()
        }
        cabin.onNodeWithText("Cabin cannot see the players yet").assertIsDisplayed()
        cabin.onNodeWithText("Allow notification access on Setup's first page.").assertIsDisplayed()
    }
}
