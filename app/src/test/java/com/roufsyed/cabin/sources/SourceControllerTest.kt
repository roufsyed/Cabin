package com.roufsyed.cabin.sources

import com.roufsyed.cabin.apps.FixedApps
import com.roufsyed.cabin.data.CabinSettings
import com.roufsyed.cabin.data.InMemorySettings
import com.roufsyed.cabin.media.FixedMedia
import com.roufsyed.cabin.media.NowPlaying
import com.roufsyed.cabin.media.PlaybackProgress
import com.roufsyed.cabin.model.Slot
import com.roufsyed.cabin.model.SourceId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SourceControllerTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val apps = FixedApps()
    private val settings = InMemorySettings()

    @After
    fun tearDown() = scope.cancel()

    private fun track(packageName: String, playing: Boolean) = NowPlaying(
        packageName = packageName, appLabel = "Player", title = "Track", artist = "Artist",
        progress = PlaybackProgress(0, 100_000, 1f, playing, 0),
    )

    private fun controller(media: FixedMedia) = SourceController(settings, apps, media, scope)

    /** Waits for the controller's state, which is rebuilt on a background thread, to satisfy [condition]. */
    private fun SourceController.await(condition: (SourceController.State) -> Boolean): SourceController.State =
        runBlocking { withTimeout(5_000) { state.first(condition) } }

    private fun SourceController.active(): SourceId? = state.value.row.firstOrNull { it.active }?.id

    /** Chooses the design's Music app for music and waits until the media source has been told. */
    private fun SourceController.chooseMusic(media: FixedMedia) {
        bind(Slot.Music, FixedApps.id("Music"))
        await { it.bindings[Slot.Music]?.label == "Music" }
        val deadline = System.nanoTime() + 5_000_000_000L
        while (media.musicApp != "design.music") {
            check(System.nanoTime() < deadline) { "the media source was not told about the music app" }
            Thread.sleep(5)
        }
    }

    @Test
    fun `with no music app chosen, play is left to the media source`() {
        val media = FixedMedia(null)
        val sources = controller(media)
        sources.await { it.row.isNotEmpty() }
        assertNull(sources.state.value.bindings[Slot.Music])

        sources.playPause()
        assertEquals(listOf("playPause"), media.calls)
        assertEquals(emptyList<String>(), apps.launched)
    }

    @Test
    fun `with nothing to resume, play starts the chosen music app and opens it if that fails`() {
        val media = FixedMedia(null)
        val sources = controller(media)
        sources.chooseMusic(media)

        sources.playPause()
        assertEquals(listOf("playPause", "start:design.music"), media.calls)
        assertEquals(listOf(FixedApps.id("Music")), apps.launched)
    }

    @Test
    fun `the app that plays is remembered as the last player`() {
        controller(FixedMedia(track("design.radio", playing = true)))
        runBlocking { withTimeout(5_000) { settings.settings.first { it.lastPlayer == "design.radio" } } }
    }

    @Test
    fun `a paused app does not become the last player`() {
        runBlocking { settings.update { it.copy(lastPlayer = "design.radio") } }
        val sources = controller(FixedMedia(track("design.music", playing = false)))
        sources.await { it.row.isNotEmpty() }
        // Give a wrongly made change the chance to show up before checking it did not happen.
        sources.move(SourceId.Usb, -1)
        sources.await { it.row[1].id == SourceId.Usb }
        assertEquals("design.radio", settings.settings.value.lastPlayer)
    }

    @Test
    fun `a music app that was the last to play is started with that said`() {
        runBlocking { settings.update { it.copy(lastPlayer = "design.music") } }
        val media = FixedMedia(null)
        val sources = controller(media)
        sources.chooseMusic(media)

        sources.playPause()
        assertEquals(listOf("playPause", "start:design.music:last"), media.calls)
    }

    @Test
    fun `a music app that can be started from outside is not opened`() {
        val media = FixedMedia(null, startable = setOf("design.music"))
        val sources = controller(media)
        sources.chooseMusic(media)

        sources.playPause()
        assertEquals(listOf("playPause", "start:design.music"), media.calls)
        assertEquals(emptyList<String>(), apps.launched)
    }

    @Test
    fun `whatever is playing is paused, whichever app was chosen for music`() {
        val media = FixedMedia(track("design.radio", playing = true))
        val sources = controller(media)
        sources.chooseMusic(media)

        sources.playPause()
        assertEquals(listOf("playPause"), media.calls)
        assertFalse(media.nowPlaying.value!!.playing)
        assertEquals(emptyList<String>(), apps.launched)
    }

    @Test
    fun `the maps app is found without being chosen`() {
        val sources = controller(FixedMedia(null))
        val state = sources.await { it.bindings[Slot.Maps] != null }
        assertEquals("Maps", state.bindings[Slot.Maps]?.label)
    }

    @Test
    fun `the row shows the visible sources in order`() {
        val sources = controller(FixedMedia(null))
        val state = sources.await { it.row.isNotEmpty() }
        assertEquals(listOf(SourceId.Radio, SourceId.Bluetooth, SourceId.Usb, SourceId.Auto), state.row.map { it.id })
        assertEquals(listOf(false, false, false, false, true), state.tiles.map { it.hidden })
    }

    @Test
    fun `a source with no app asks for one`() {
        val sources = controller(FixedMedia(null))
        sources.await { it.row.isNotEmpty() }
        // The design's fifteen apps include nothing for Bluetooth audio.
        assertEquals(Opened.NeedsApp, sources.select(SourceId.Bluetooth))
        assertEquals(emptyList<String>(), apps.launched)
    }

    @Test
    fun `a source whose app has no session opens the app`() {
        val sources = controller(FixedMedia(null))
        sources.await { it.row.isNotEmpty() }
        assertEquals(Opened.Launched, sources.select(SourceId.Radio))
        assertEquals(listOf(FixedApps.id("Radio")), apps.launched)
        sources.await { state -> state.row.any { it.id == SourceId.Radio && it.active } }
    }

    @Test
    fun `a source whose app has a paused session is resumed in place, and opened on the second tap`() {
        val media = FixedMedia(track("design.music", playing = false))
        val sources = controller(media)
        sources.await { it.row.isNotEmpty() }

        assertEquals(Opened.Played, sources.select(SourceId.Usb))
        assertEquals(emptyList<String>(), apps.launched)
        assertEquals(listOf("play:design.music"), media.calls)

        sources.await { state -> state.row.any { it.id == SourceId.Usb && it.active } }
        assertEquals(Opened.Launched, sources.select(SourceId.Usb))
        assertEquals(listOf(FixedApps.id("Music")), apps.launched)
    }

    @Test
    fun `Android Auto always opens its app`() {
        val media = FixedMedia(track("design.phonelink", playing = false))
        val sources = controller(media)
        sources.await { it.row.isNotEmpty() }
        assertEquals(Opened.Launched, sources.select(SourceId.Auto))
        assertEquals(listOf(FixedApps.id("Phone link")), apps.launched)
    }

    @Test
    fun `the playing app's source is highlighted`() {
        val sources = controller(FixedMedia(track("design.radio", playing = true)))
        sources.await { state -> state.row.any { it.active } }
        assertEquals(SourceId.Radio, sources.active())
    }

    @Test
    fun `Mode selects the source after the active one`() {
        runBlocking { settings.update { it.copy(activeSource = SourceId.Usb) } }
        val sources = controller(FixedMedia(null))
        sources.await { state -> state.row.any { it.id == SourceId.Usb && it.active } }
        sources.selectNext()
        assertEquals(listOf(FixedApps.id("Phone link")), apps.launched)
    }

    @Test
    fun `the last visible source cannot be hidden`() {
        val sources = controller(FixedMedia(null))
        sources.await { it.row.isNotEmpty() }
        for (source in listOf(SourceId.Radio, SourceId.Bluetooth, SourceId.Usb)) sources.toggleHidden(source)
        sources.await { it.row.size == 1 }
        sources.toggleHidden(SourceId.Auto)
        // Give a wrongly applied change the chance to show up before checking it did not happen.
        sources.move(SourceId.Auto, -1)
        val state = sources.await { it.tiles.indexOfFirst { tile -> tile.id == SourceId.Auto } == 2 }
        assertEquals(listOf(SourceId.Auto), state.row.map { it.id })
    }

    @Test
    fun `moving a source changes the row`() {
        val sources = controller(FixedMedia(null))
        sources.await { it.row.isNotEmpty() }
        sources.move(SourceId.Usb, -1)
        val state = sources.await { it.row.firstOrNull()?.id == SourceId.Radio && it.row[1].id == SourceId.Usb }
        assertEquals(listOf(SourceId.Radio, SourceId.Usb, SourceId.Bluetooth, SourceId.Auto), state.row.map { it.id })
    }

    @Test
    fun `binding a slot to an app, and back to automatic`() {
        val sources = controller(FixedMedia(null))
        sources.await { it.row.isNotEmpty() }
        assertNull(sources.state.value.bindings[Slot.Bluetooth])

        sources.bind(Slot.Bluetooth, FixedApps.id("Music"))
        sources.await { it.bindings[Slot.Bluetooth]?.label == "Music" }

        sources.bind(Slot.Bluetooth, null)
        sources.await { it.bindings[Slot.Bluetooth] == null }
    }

    private fun SourceController.State.names() = row.map { it.label }

    @Test
    fun `a source given an app of the user's choosing takes that app's name, in the row and while arranging`() {
        val sources = controller(FixedMedia(null))
        val start = sources.await { it.row.isNotEmpty() }
        assertEquals(listOf("Radio", "Bluetooth", "USB", "Android Auto"), start.names())

        sources.bind(Slot.Bluetooth, FixedApps.id("Video"))
        val renamed = sources.await { it.bindings[Slot.Bluetooth]?.label == "Video" }
        assertEquals(listOf("Radio", "Video", "USB", "Android Auto"), renamed.names())
        val tile = renamed.tiles.first { it.id == SourceId.Bluetooth }
        assertEquals("Video", tile.label)
        assertEquals("Video", tile.app)
        assertEquals(listOf(SourceId.Radio, SourceId.Bluetooth, SourceId.Usb, SourceId.Auto), renamed.row.map { it.id })

        sources.bind(Slot.Bluetooth, null)
        val back = sources.await { it.bindings[Slot.Bluetooth] == null }
        assertEquals(listOf("Radio", "Bluetooth", "USB", "Android Auto"), back.names())
    }

    @Test
    fun `in the design's own set every source keeps its name whatever it opens`() {
        val sources = SourceController(settings, apps, FixedMedia(null), scope, namedAfterApps = false)
        sources.await { it.row.isNotEmpty() }
        sources.bind(Slot.Bluetooth, FixedApps.id("Music"))
        val state = sources.await { it.bindings[Slot.Bluetooth]?.label == "Music" }
        assertEquals(listOf("Radio", "Bluetooth", "USB", "Android Auto"), state.names())
        assertEquals("Bluetooth", state.tiles.first { it.id == SourceId.Bluetooth }.label)
    }

    @Test
    fun `rail buttons open their app or ask for one`() {
        val sources = controller(FixedMedia(null))
        sources.await { it.row.isNotEmpty() }
        assertEquals(Opened.Launched, sources.open(Slot.Phone))
        assertEquals(listOf(FixedApps.id("Phone")), apps.launched)

        val bare = SourceController(InMemorySettings(CabinSettings(loaded = true)), FixedApps(emptyList()), FixedMedia(null), scope)
        assertEquals(Opened.NeedsApp, bare.open(Slot.Phone))
    }
}
