package com.roufsyed.cabin.media

import com.roufsyed.cabin.model.Samples
import org.junit.Assert.assertEquals
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
        queueSize = 12,
        extras = emptyList(),
    )

    /** What the unit's own player looked like from the Music screen: a title and a cover, and no length. */
    private val bare = full.copy(
        packageName = "com.unit.media", appLabel = "Unit player", actions = PlayerRules.TRANSPORT, durationMs = null,
        details = listOf("android.media.metadata.ALBUM_ART", "android.media.metadata.ARTIST", "android.media.metadata.TITLE"),
        queueSize = 0,
    )

    private fun PlayerReport.line(label: String) = lines.single { it.label == label }.value

    @Test
    fun `a player's facts are put into words, a row to each`() {
        val report = PlayerRules.report(full)
        assertEquals("Player", report.appLabel)
        assertTrue(report.shown)
        assertEquals(
            listOf("App", "Slider", "State", "Length", "Can be told to", "Track details", "Queue", "Also sends"),
            report.lines.map { it.label },
        )
        assertEquals("com.player", report.line("App"))
        assertEquals("Yes", report.line("Slider"))
        assertEquals("Playing, at 1:24, speed 1.0, said 3 s ago", report.line("State"))
        assertEquals("3:41", report.line("Length"))
        assertEquals("Play, pause, next, previous, move within the track (0x136)", report.line("Can be told to"))
        assertEquals("album art, artist, duration, title", report.line("Track details"))
        assertEquals("12 items", report.line("Queue"))
        assertEquals("Nothing", report.line("Also sends"))
    }

    @Test
    fun `the rows fill the page and no more`() {
        assertEquals(PlayerRules.PAGE_LINES, PlayerRules.report(full).lines.sumOf { it.lines })
    }

    @Test
    fun `the page says which of the two things a slider needs is missing`() {
        assertEquals("No: it gives no length for the track", PlayerRules.slider(bare))
        assertEquals("No: it gives no length for the track", PlayerRules.slider(full.copy(durationMs = 0)))
        // A length alone is not enough either: the player has to say it takes being moved.
        assertEquals(
            "No: it does not say the track can be moved",
            PlayerRules.slider(full.copy(actions = PlayerRules.TRANSPORT)),
        )
        assertEquals("Yes", PlayerRules.slider(full))
    }

    @Test
    fun `a player that says little is reported as saying little`() {
        val report = PlayerRules.report(bare.copy(state = null, positionMs = -1, toldAgoMs = null, actions = 0, details = emptyList()))
        assertEquals("Not given", report.line("State"))
        assertEquals("Not given", report.line("Length"))
        assertEquals("Nothing: it lists no actions", report.line("Can be told to"))
        assertEquals("None", report.line("Track details"))
        assertEquals("None", report.line("Queue"))
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
    fun `the queue is counted`() {
        assertEquals("None", PlayerRules.queue(0))
        assertEquals("1 item", PlayerRules.queue(1))
        assertEquals("4 items", PlayerRules.queue(4))
    }

    @Test
    fun `the stand-in player reports the track it holds`() {
        val media = FixedMedia(Samples.playing)
        val report = PlayerRules.report(media.players().single())
        assertEquals("Bluetooth audio", report.appLabel)
        assertEquals("design.music", report.line("App"))
        // The design's own track is drawn with a plain line, so its stand-in player takes no position.
        assertEquals("No: it does not say the track can be moved", report.line("Slider"))
        assertEquals("3:41", report.line("Length"))
        assertEquals("4 items", report.line("Queue"))
        assertEquals(emptyList<PlayerFacts>(), FixedMedia(null).players())
    }

    @Test
    fun `the stand-in player can be moved only when its track says so`() {
        val still = FixedMedia(Samples.playing)
        still.seekTo(120_000)
        assertEquals(84_000L, still.nowPlaying.value?.progress?.positionMs)
        assertEquals(listOf("seek:120000"), still.calls)

        val seekable = Samples.playing.copy(progress = Samples.playing.progress.copy(seekable = true, speed = 1f))
        val media = FixedMedia(seekable, elapsed = { 5_000 })
        media.seekTo(120_000)
        val progress = media.nowPlaying.value!!.progress
        assertEquals(120_000, progress.positionMs)
        // Timed from the moment it was moved, so it carries on from there.
        assertEquals(121_000, progress.positionAt(6_000))
        assertEquals("Yes", PlayerRules.slider(media.players().single()))
        // Past the end is the end.
        media.seekTo(999_000)
        assertEquals(221_000L, media.nowPlaying.value?.progress?.positionMs)
    }
}
