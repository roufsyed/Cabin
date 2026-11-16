package com.roufsyed.cabin.media

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaRulesTest {

    private fun session(packageName: String, active: Boolean = false, paused: Boolean = false, titled: Boolean = true) =
        SessionState(packageName, active, paused, titled)

    // Roughly what the emulator holds after a start: a phone-link app that keeps an idle session with
    // a title, and Bluetooth sessions with nothing in them.
    private val linkIdle = session("link")
    private val bluetoothEmpty = session("bluetooth", titled = false)

    private fun shown(sessions: List<SessionState>, lastPlayed: Int = -1, music: String? = null): String? =
        sessions.getOrNull(MediaRules.shown(sessions, lastPlayed, music))?.packageName

    @Test
    fun `whatever is playing is shown, whichever app was chosen for music`() {
        val sessions = listOf(linkIdle, session("radio", active = true), session("player", paused = true))
        assertEquals("radio", shown(sessions))
        assertEquals("radio", shown(sessions, music = "player"))
        assertEquals("radio", shown(sessions, lastPlayed = 2, music = "player"))
    }

    @Test
    fun `with no music app chosen the first session with a title is shown, as before`() {
        assertEquals("link", shown(listOf(bluetoothEmpty, linkIdle, session("player", paused = true))))
        // With no titles at all the first session still stands in, under its app's name.
        assertEquals("bluetooth", shown(listOf(bluetoothEmpty, session("other", titled = false))))
        assertEquals(null, shown(emptyList()))
    }

    @Test
    fun `with a music app chosen, idle sessions other apps leave about are not shown`() {
        // Nothing to show means play starts the music app.
        assertEquals(null, shown(listOf(bluetoothEmpty, linkIdle), music = "player"))
        assertEquals(null, shown(emptyList(), music = "player"))
    }

    @Test
    fun `with a music app chosen, its own session is shown even when idle`() {
        assertEquals("player", shown(listOf(linkIdle, session("player")), music = "player"))
        assertEquals("player", shown(listOf(linkIdle, session("player", titled = false)), music = "player"))
    }

    @Test
    fun `a paused session can be resumed, so it is shown ahead of the music app's idle one`() {
        val sessions = listOf(session("player"), linkIdle, session("phone audio", paused = true))
        assertEquals("phone audio", shown(sessions, music = "player"))
        // A paused session with nothing in it is not worth showing.
        val untitled = listOf(session("player"), session("phone audio", paused = true, titled = false))
        assertEquals("player", shown(untitled, music = "player"))
    }

    @Test
    fun `what played last stays on show after it stops, so play brings it back`() {
        // A radio app that reports "stopped" rather than "paused" when its stream is paused.
        val sessions = listOf(session("player", paused = true), session("radio"))
        assertEquals("radio", shown(sessions, lastPlayed = 1, music = "player"))
        // Once that session has gone, the index no longer points at anything.
        assertEquals("player", shown(sessions.take(1), lastPlayed = 1, music = "player"))
    }

    @Test
    fun `a track can be moved when its player gives its length and says it takes being moved`() {
        val seek = PlayerRules.SEEK_TO
        assertEquals(true, MediaRules.seekable(221_000, seek))
        assertEquals(true, MediaRules.seekable(221_000, PlayerRules.TRANSPORT or seek))
        // Either one alone is not enough: there is no line to put a knob on, or no one to tell.
        assertEquals(false, MediaRules.seekable(0, PlayerRules.TRANSPORT or seek))
        assertEquals(false, MediaRules.seekable(-1, seek))
        assertEquals(false, MediaRules.seekable(221_000, PlayerRules.TRANSPORT))
        // Winding is another thing a player can say, and is not the same as taking a position.
        assertEquals(false, MediaRules.seekable(221_000, PlayerRules.FAST_FORWARD or PlayerRules.REWIND))
        assertEquals(false, MediaRules.seekable(221_000, 0))
    }

    @Test
    fun `a cover is read from the first address that is on this unit`() {
        val inPlayer = "content://player.covers/track/7"
        assertEquals(inPlayer, MediaRules.coverAddress(listOf(inPlayer, "content://player.covers/album/2")))
        assertEquals(inPlayer, MediaRules.coverAddress(listOf(null, "", inPlayer)))
        assertEquals("android.resource://player/drawable/cover", MediaRules.coverAddress(listOf("android.resource://player/drawable/cover")))
        assertEquals("CONTENT://player.covers/1", MediaRules.coverAddress(listOf("CONTENT://player.covers/1")))
    }

    @Test
    fun `an address on the internet or in a file is never followed`() {
        val inPlayer = "content://player.covers/track/7"
        assertEquals(inPlayer, MediaRules.coverAddress(listOf("https://covers.example/7.jpg", inPlayer)))
        assertEquals(null, MediaRules.coverAddress(listOf("https://covers.example/7.jpg", "http://covers.example/7.jpg")))
        assertEquals(null, MediaRules.coverAddress(listOf("file:///sdcard/Music/cover.jpg", "/sdcard/Music/cover.jpg")))
        assertEquals(null, MediaRules.coverAddress(listOf(null, "", "   ")))
        assertEquals(null, MediaRules.coverAddress(emptyList()))
    }
}
