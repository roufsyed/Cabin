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
    fun `a track of unknown length has no line and no times while it plays`() {
        val radio = PlaybackProgress(positionMs = 5_000, durationMs = 0, speed = 1f, playing = true, atElapsedMs = 0)
        assertTrue(radio.live)
        assertFalse(radio.hasLength)
        assertEquals("", radio.positionLabel(60_000))
        assertEquals("", radio.durationLabel)
        assertEquals(0f, radio.fractionAt(60_000), 0f)
        assertFalse(radio.copy(durationMs = -1).hasLength)
        assertEquals("", radio.copy(durationMs = -1).positionLabel(60_000))
    }

    @Test
    fun `a player that gives no length and is not playing has none either`() {
        // What a phone-link app leaves up while nothing plays: a name, no length, and stopped.
        val idle = PlaybackProgress(positionMs = 0, durationMs = 0, speed = 0f, playing = false, atElapsedMs = 0)
        assertTrue(idle.live)
        assertFalse(idle.hasLength)
        assertEquals("", idle.positionLabel(60_000))
        assertEquals("", idle.durationLabel)
        assertEquals(0f, idle.fractionAt(60_000), 0f)
        // The same goes for a stream that has been paused.
        val radio = PlaybackProgress(positionMs = 5_000, durationMs = 0, speed = 1f, playing = true, atElapsedMs = 0)
        val paused = radio.copy(playing = false)
        assertFalse(paused.hasLength)
        assertEquals("", paused.positionLabel(60_000))
        assertEquals(0f, paused.fractionAt(60_000), 0f)
        // A track with a length has its line and its times, playing or not.
        assertTrue(playing.hasLength)
        assertTrue(playing.copy(playing = false).hasLength)
        assertEquals("1:24", playing.copy(playing = false).positionLabel(60_000))
    }

    @Test
    fun `with nothing playing there is no line and no times are shown`() {
        val none = PlaybackProgress.None
        assertFalse(none.live)
        assertFalse(none.hasLength)
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
    fun `a playing track is shown with its title and artist`() {
        val ui = nowPlayingUi(track, access = true)
        assertEquals("Open Road", ui.title)
        assertEquals("Aster Lane", ui.artist)
        assertTrue(ui.playing)
    }

    @Test
    fun `a session with no title falls back to the app's name`() {
        assertEquals("Player", nowPlayingUi(track.copy(title = " "), access = true).title)
    }
}
