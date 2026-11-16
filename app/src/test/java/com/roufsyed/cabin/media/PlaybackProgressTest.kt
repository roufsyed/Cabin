package com.roufsyed.cabin.media

import com.roufsyed.cabin.model.nowPlayingUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProgressTest {

    private val playing = PlaybackProgress(positionMs = 84_000, durationMs = 221_000, speed = 1f, playing = true, atElapsedMs = 10_000)

    @Test
    fun `a playing track advances with the clock`() {
        assertEquals(84_000, playing.positionAt(10_000))
        assertEquals(89_500, playing.positionAt(15_500))
        assertEquals("1:24", playing.positionLabel(10_000))
        assertEquals("1:29", playing.positionLabel(15_500))
        assertEquals("3:41", playing.durationLabel)
    }

    @Test
    fun `a paused track stays where it is`() {
        val paused = playing.copy(playing = false)
        assertEquals(84_000, paused.positionAt(999_000))
    }

    @Test
    fun `speed scales the advance`() {
        assertEquals(94_000, playing.copy(speed = 2f).positionAt(15_000))
    }

    @Test
    fun `position never runs past the end or before the start`() {
        assertEquals(221_000, playing.positionAt(10_000_000))
        assertEquals(0, playing.copy(positionMs = -5).positionAt(10_000))
        assertEquals(1f, playing.fractionAt(10_000_000), 0f)
    }

    @Test
    fun `fraction is the played share of the track`() {
        assertEquals(0.38f, playing.fractionAt(10_000), 0.001f)
    }

    @Test
    fun `a finger along the line stands for a position in the track`() {
        assertEquals(0, playing.positionFor(0f))
        assertEquals(110_500, playing.positionFor(0.5f))
        assertEquals(221_000, playing.positionFor(1f))
        // Run past either end, the finger counts as on it.
        assertEquals(0, playing.positionFor(-0.2f))
        assertEquals(221_000, playing.positionFor(1.4f))
        // An hour-long track is still placed to the millisecond.
        assertEquals(2_700_000, playing.copy(durationMs = 3_600_000).positionFor(0.75f))
        // A track of no known length has nowhere to be moved to.
        assertEquals(0, playing.copy(durationMs = 0).positionFor(0.5f))
    }

    @Test
    fun `a track is not a slider unless its player says so`() {
        assertFalse(playing.seekable)
        assertFalse(PlaybackProgress.None.seekable)
    }

    @Test
    fun `a stream of unknown length reads as live with a full bar`() {
        val radio = PlaybackProgress(positionMs = 5_000, durationMs = 0, speed = 1f, playing = true, atElapsedMs = 0)
        assertTrue(radio.live)
        assertEquals("Live", radio.positionLabel(60_000))
        assertEquals("", radio.durationLabel)
        assertEquals(1f, radio.fractionAt(60_000), 0f)
    }

    @Test
    fun `with nothing playing the line is empty and no times are shown`() {
        val none = PlaybackProgress.None
        assertFalse(none.live)
        assertEquals(0f, none.fractionAt(60_000), 0f)
        assertEquals("", none.positionLabel(60_000))
        assertEquals("", none.durationLabel)
        // The panel for "nothing playing" and for "no access yet" both use it.
        assertEquals(none, nowPlayingUi(null, access = true).progress)
        assertEquals(none, nowPlayingUi(null, access = false).progress)
    }

    @Test
    fun `times are written as minutes and seconds, with hours when needed`() {
        assertEquals("0:00", PlaybackProgress.formatTime(0))
        assertEquals("0:09", PlaybackProgress.formatTime(9_999))
        assertEquals("12:05", PlaybackProgress.formatTime(725_000))
        assertEquals("1:02:03", PlaybackProgress.formatTime(3_723_000))
        assertEquals("0:00", PlaybackProgress.formatTime(-50))
    }

    @Test
    fun `the queue shows the current item and the ones after it`() {
        val items = (0 until 10).toList()
        assertEquals(listOf(3, 4, 5, 6), queueWindow(items, currentIndex = 3))
        assertEquals(listOf(0, 1, 2, 3), queueWindow(items, currentIndex = 0))
        // Near the end the last rows are shown, so the list is never short while more exist.
        assertEquals(listOf(6, 7, 8, 9), queueWindow(items, currentIndex = 9))
        // Unknown current item: start from the top.
        assertEquals(listOf(0, 1, 2, 3), queueWindow(items, currentIndex = -1))
        assertEquals(listOf(0, 1), queueWindow(listOf(0, 1), currentIndex = 1))
    }

    private val track = NowPlaying(
        packageName = "com.player", appLabel = "Player", title = "Open Road", artist = "Aster Lane", progress = playing,
    )

    @Test
    fun `without notification access the panel asks for it`() {
        val ui = nowPlayingUi(track, access = false)
        assertTrue(ui.needsAccess)
        assertFalse(ui.playing)
        assertEquals("No access yet", ui.title)
    }

    @Test
    fun `with nothing playing the panel says so`() {
        val ui = nowPlayingUi(null, access = true)
        assertFalse(ui.needsAccess)
        assertEquals("Nothing playing", ui.title)
        assertEquals("", ui.artist)
    }

    @Test
    fun `a playing track is shown with its app name as the source`() {
        val ui = nowPlayingUi(track, access = true)
        assertEquals("Open Road", ui.title)
        assertEquals("Aster Lane", ui.artist)
        assertEquals("Player", ui.source)
        assertTrue(ui.playing)
    }

    @Test
    fun `a session with no title falls back to the app's name`() {
        assertEquals("Player", nowPlayingUi(track.copy(title = " "), access = true).title)
    }
}
