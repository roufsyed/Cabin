package com.roufsyed.cabin.media

import com.roufsyed.cabin.model.Samples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayersTest {

    /** A player that does everything by the book: a length, a position, and it takes being moved. */
    private val full = PlayerFacts(
        packageName = "com.player",
        appLabel = "Player",
        shown = true,
        state = PlayerRules.STATE_PLAYING,
        positionMs = 84_000,
        speed = 1f,
        toldAgoMs = 3_400,
        actions = PlayerRules.TRANSPORT or PlayerRules.SEEK_TO,
        durationMs = 221_000,
        details = listOf(
            "android.media.metadata.ALBUM_ART", "android.media.metadata.ARTIST",
            "android.media.metadata.DURATION", "android.media.metadata.TITLE",
        ),
        extras = emptyList(),
        screen = true,
    )

    /** What the unit's own player looked like: a title and a cover, no length, and no screen of its own. */
    private val bare = full.copy(
        packageName = "com.unit.media", appLabel = "Unit player", actions = PlayerRules.TRANSPORT, durationMs = null,
        details = listOf("android.media.metadata.ALBUM_ART", "android.media.metadata.ARTIST", "android.media.metadata.TITLE"),
        screen = false,
    )

    private fun PlayerReport.line(label: String) = lines.single { it.label == label }.value

    @Test
    fun `a player's facts are put into words, a row to each`() {
        val report = PlayerRules.report(full)
        assertEquals("Player", report.appLabel)
        assertTrue(report.shown)
        assertEquals(
            listOf("App", "A tap opens", "State", "Length", "Can be told to", "Track details", "Also sends"),
            report.lines.map { it.label },
        )
        assertEquals("com.player", report.line("App"))
        assertEquals("Its own screen", report.line("A tap opens"))
        assertEquals("Playing, at 1:24, speed 1.0, said 3 s ago", report.line("State"))
        assertEquals("3:41", report.line("Length"))
        assertEquals("Play, pause, next, previous, move within the track (0x136)", report.line("Can be told to"))
        assertEquals("album art, artist, duration, title", report.line("Track details"))
        assertEquals("Nothing", report.line("Also sends"))
    }

    @Test
    fun `the rows fill the page and no more`() {
        assertEquals(PlayerRules.PAGE_LINES, PlayerRules.report(full).lines.sumOf { it.lines })
    }

    @Test
    fun `the page says what a tap on Now playing opens for the player`() {
        assertEquals("Its own screen", PlayerRules.opens(full, inApps = false))
        assertEquals("Its own screen", PlayerRules.opens(full, inApps = true))
        assertEquals("Its app", PlayerRules.opens(bare, inApps = true))
        assertEquals("The music app from Setup: it has no screen of its own", PlayerRules.opens(bare, inApps = false))
        assertEquals("Its app", PlayerRules.report(bare, inApps = true).line("A tap opens"))
        assertEquals("The music app from Setup: it has no screen of its own", PlayerRules.report(bare).line("A tap opens"))
    }

    @Test
    fun `a player that says little is reported as saying little`() {
        val report = PlayerRules.report(bare.copy(state = null, positionMs = -1, toldAgoMs = null, actions = 0, details = emptyList()))
        assertEquals("Not given", report.line("State"))
        assertEquals("Not given", report.line("Length"))
        assertEquals("Nothing: it lists no actions", report.line("Can be told to"))
        assertEquals("None", report.line("Track details"))
    }

    @Test
    fun `where the track is, how fast it moves and how old that is`() {
        assertEquals("at 1:24, speed 1.0, said 3 s ago", PlayerRules.position(full))
        // A player that has never said when is not given an age.
        assertEquals("at 0:00, speed 0.0", PlayerRules.position(full.copy(positionMs = 0, speed = 0f, toldAgoMs = null)))
        assertEquals("position not given", PlayerRules.position(full.copy(positionMs = -1)))
        assertEquals("Paused, at 1:24, speed 0.0, said 61 s ago", PlayerRules.state(full.copy(state = 2, speed = 0f, toldAgoMs = 61_999)))
    }

    @Test
    fun `every state Android has is named, and any other is given as its number`() {
        assertEquals("None", PlayerRules.state(0))
        assertEquals("Stopped", PlayerRules.state(1))
        assertEquals("Paused", PlayerRules.state(2))
        assertEquals("Playing", PlayerRules.state(3))
        assertEquals("Buffering", PlayerRules.state(6))
        assertEquals("Error", PlayerRules.state(7))
        assertEquals("Number 42", PlayerRules.state(42))
    }

    @Test
    fun `a length that is given but is none is shown as it came`() {
        assertEquals("Not given", PlayerRules.length(null))
        assertEquals("Given as 0", PlayerRules.length(0))
        assertEquals("Given as -1", PlayerRules.length(-1))
        assertEquals("1:02:03", PlayerRules.length(3_723_000))
    }

    @Test
    fun `what a player can be told to do is listed in words, with Android's own number after it`() {
        assertEquals("Wind forward, wind back (0x48)", PlayerRules.actions(PlayerRules.FAST_FORWARD or PlayerRules.REWIND))
        assertEquals("Play or pause, stop (0x201)", PlayerRules.actions((1L shl 9) or 1L))
        // Several of Android's flags mean one thing to a reader, and are named once.
        assertEquals("Shuffle (0x280000)", PlayerRules.actions((1L shl 19) or (1L shl 21)))
        // A flag with no name still shows in the number.
        assertEquals("Nothing Android has a name for (0x40000000)", PlayerRules.actions(1L shl 30))
        assertEquals("Play (0x40000004)", PlayerRules.actions((1L shl 30) or (1L shl 2)))
    }

    @Test
    fun `Android's names for track details are shortened, a player's own are left as they came`() {
        assertEquals("album art", PlayerRules.detail("android.media.metadata.ALBUM_ART"))
        assertEquals("display subtitle", PlayerRules.detail("android.media.metadata.DISPLAY_SUBTITLE"))
        assertEquals("vendor.TRACK_LENGTH=215000", PlayerRules.detail("vendor.TRACK_LENGTH=215000"))
        val report = PlayerRules.report(bare.copy(details = bare.details + "total_time=215", extras = listOf("button repeat", "source=usb")))
        assertEquals("album art, artist, title, total_time=215", report.line("Track details"))
        assertEquals("button repeat, source=usb", report.line("Also sends"))
        // Android's own come first however the names sort, since the row has only so much room.
        assertEquals(
            "title, duration, AddDate=1794685109, File=/storage",
            PlayerRules.details(
                listOf("AddDate=1794685109", "File=/storage", "android.media.metadata.TITLE", "android.media.metadata.DURATION"),
            ),
        )
        assertEquals("None", PlayerRules.details(emptyList()))
    }

    @Test
    fun `the stand-in player reports the track it holds`() {
        val media = FixedMedia(Samples.playing)
        val report = PlayerRules.report(media.players().single())
        assertEquals("Bluetooth audio", report.appLabel)
        assertEquals("design.music", report.line("App"))
        assertEquals("Its own screen", report.line("A tap opens"))
        assertEquals("3:41", report.line("Length"))
        assertEquals("Play, pause, next, previous (0x36)", report.line("Can be told to"))
        assertEquals(emptyList<PlayerFacts>(), FixedMedia(null).players())
    }

    @Test
    fun `the stand-in player moves through its tracks, and opens its own screen when it has one`() {
        val media = FixedMedia(Samples.playing, tracks = Samples.tracks)
        media.next()
        assertEquals("Night Signals", media.nowPlaying.value?.title)
        assertEquals("Vela North", media.nowPlaying.value?.artist)
        media.previous()
        media.previous()
        assertEquals("Slow Tide", media.nowPlaying.value?.title)
        assertTrue(media.openPlayer())
        assertEquals(listOf("next", "previous", "previous", "open"), media.calls)

        val bare = FixedMedia(Samples.playing, ownScreen = false)
        assertFalse(bare.openPlayer())
        assertEquals("The music app from Setup: it has no screen of its own", PlayerRules.report(bare.players().single()).line("A tap opens"))
        assertFalse(FixedMedia(null).openPlayer())
    }
}
