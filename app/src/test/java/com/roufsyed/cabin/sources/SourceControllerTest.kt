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
