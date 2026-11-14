package com.roufsyed.cabin.model

import com.roufsyed.cabin.media.PlaybackProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenOffUiTest {

    private val everything = ScreenOffShows(clock = true, date = true, playing = true, turn = true, status = true)
    private val playing = nowPlayingUi(Samples.playing, access = true)
    private val paused = playing.copy(progress = playing.progress.copy(playing = false))

    private fun ui(
        shows: ScreenOffShows = ScreenOffShows(),
        nav: NavUi = Samples.nav,
        nowPlaying: NowPlayingUi = playing,
        status: StatusUi = Samples.status,
        night: Boolean = true,
    ) = screenOffUi(shows, Samples.clock, status, nav, nowPlaying, night)

    @Test
    fun `left alone it is the design's clock, date and hint, whatever else is going on`() {
        val ui = ui()
        assertEquals("10:10", ui.time)
        assertEquals("Thursday 12 November", ui.date)
        assertTrue(ui.hint)
        // A route is being followed and a track is playing, but neither was asked for.
        assertNull(ui.turn)
        assertNull(ui.playing)
        assertNull(ui.status)
    }

    @Test
    fun `each switch adds its own line`() {
        val ui = ui(everything)
        assertEquals("10:10", ui.time)
        assertEquals("Thursday 12 November", ui.date)
        assertEquals("350 m · Ring Road", ui.turn)
        assertEquals("Open Road · Aster Lane", ui.playing)
        assertEquals(Samples.status, ui.status)
        assertTrue(ui.hint)
    }

    @Test
    fun `each switch takes only its own line away`() {
        assertNull(ui(everything.copy(clock = false)).time)
        assertEquals("Thursday 12 November", ui(everything.copy(clock = false)).date)
        assertNull(ui(everything.copy(date = false)).date)
        assertEquals("10:10", ui(everything.copy(date = false)).time)
        assertNull(ui(everything.copy(playing = false)).playing)
        assertNull(ui(everything.copy(turn = false)).turn)
        assertNull(ui(everything.copy(status = false)).status)
    }

    @Test
    fun `with every switch off there is nothing at all, not even the hint`() {
        val ui = ui(ScreenOffShows(clock = false, date = false))
        assertEquals(ScreenOffUi(null, null, null, null, null, null, hint = false, night = true), ui)
        assertTrue(ScreenOffShows(clock = false, date = false).nothing)
        assertFalse(ScreenOffShows().nothing)
    }

    @Test
    fun `one switch left on keeps the hint, even while its line has nothing to say`() {
        val onlyTurn = ScreenOffShows(clock = false, date = false, turn = true)
        val ui = ui(onlyTurn, nav = NavUi.Idle)
        assertNull(ui.turn)
        assertTrue(ui.hint)
    }

    @Test
    fun `the track is shown only while it plays`() {
        assertNull(ui(everything, nowPlaying = paused).playing)
        assertNull(ui(everything, nowPlaying = nowPlayingUi(null, access = true)).playing)
        // Without notification access Cabin cannot see what is playing: no line, and no message either.
        assertNull(ui(everything, nowPlaying = nowPlayingUi(Samples.playing, access = false)).playing)
    }

    @Test
    fun `a track without an artist is its title alone`() {
        val radio = playing.copy(artist = "", progress = PlaybackProgress(0, 0, 1f, playing = true, atElapsedMs = 0))
        assertEquals("Open Road", ui(everything, nowPlaying = radio).playing)
        assertEquals("Open Road", ui(everything, nowPlaying = radio.copy(artist = "  ")).playing)
    }

    @Test
    fun `the turn is shown only while a route is being followed`() {
        assertNull(ui(everything, nav = NavUi.Idle).turn)
        assertEquals("350 m · Ring Road", ui(everything).turn)
    }

    @Test
    fun `a turn with only a distance or only a road shows what there is`() {
        assertEquals("350 m", ui(everything, nav = Samples.nav.copy(road = "")).turn)
        assertEquals("Ring Road", ui(everything, nav = Samples.nav.copy(distance = " ")).turn)
        assertNull(ui(everything, nav = Samples.nav.copy(distance = "", road = "")).turn)
    }

    @Test
    fun `the status marks are passed on as they are`() {
        val off = StatusUi(phone = false, wifi = true, gps = false)
        assertEquals(off, ui(everything, status = off).status)
    }

    @Test
    fun `the text is dim at night and lighter by day`() {
        assertTrue(ui(night = true).night)
        assertFalse(ui(night = false).night)
    }
}
