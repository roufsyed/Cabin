package com.roufsyed.cabin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roufsyed.cabin.apps.FixedApps
import com.roufsyed.cabin.media.FixedMedia
import com.roufsyed.cabin.media.NowPlaying
import com.roufsyed.cabin.media.PlaybackProgress
import com.roufsyed.cabin.model.Samples
import com.roufsyed.cabin.model.Slot
import com.roufsyed.cabin.model.SourceId
import com.roufsyed.cabin.ui.theme.Slate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Now playing, the Music screen and the source row, with the design's own track and apps. */
@RunWith(AndroidJUnit4::class)
class MediaAndSourcesTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues()

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private val media get() = values.container.media as FixedMedia
    private val apps get() = values.container.apps as FixedApps
    private val settings get() = values.container.settings.settings.value

    private fun tap(description: String) = cabin.onNodeWithContentDescription(description).performClick()
    private fun shows(description: String) = cabin.onNodeWithContentDescription(description).assertIsDisplayed()
    private fun gone(description: String) = cabin.onNodeWithContentDescription(description).assertDoesNotExist()
    private fun text(text: String) = cabin.onNodeWithText(text).assertIsDisplayed()

    /** The cover as its own node: on Home it sits inside the button that opens the Music screen. */
    private fun cover() = cabin.onNodeWithContentDescription("Cover art", useUnmergedTree = true)

    private fun play(track: NowPlaying?) {
        media.set(track)
        cabin.waitForIdle()
    }

    private fun pixelsOf(colour: Color): Int = cabin.pixelsOf(listOf(colour)).single()

    @Test
    fun homeShowsATracksCoverOnlyWhenItsPlayerSuppliesOne() {
        // The design's own track comes without one, and Home is then exactly the design.
        cover().assertDoesNotExist()

        play(Samples.playing.copy(art = plainCover(Color.Magenta)))
        cover().assertIsDisplayed()
        // 84 x 84 less its rounded corners.
        assertTrue("${pixelsOf(Color.Magenta)} magenta", pixelsOf(Color.Magenta) in 6750..7056)

        // The next track brings its own.
        play(Samples.playing.copy(title = "Night Signals", artist = "Vela North", art = plainCover(Color.Cyan)))
        assertEquals(0, pixelsOf(Color.Magenta))
        assertTrue("${pixelsOf(Color.Cyan)} cyan", pixelsOf(Color.Cyan) in 6750..7056)

        // A track without one takes it down again, and so does the player going away.
        play(Samples.playing)
        cover().assertDoesNotExist()
        assertEquals(0, pixelsOf(Color.Cyan))
        play(Samples.playing.copy(art = plainCover(Color.Magenta)))
        play(null)
        cover().assertDoesNotExist()
        text("Nothing playing")
    }

    @Test
    fun theCoverIsPartOfTheButtonThatOpensMusic() {
        play(Samples.playing.copy(art = plainCover(Color.Magenta)))
        cover().performClick()
        text("Up next")
    }

    @Test
    fun musicScreenShowsTheCoverInPlaceOfTheDesignsStandIn() {
        tap("Open music")
        shows("Cover art placeholder")
        gone("Cover art")

        play(Samples.playing.copy(art = plainCover(Color.Magenta)))
        shows("Cover art")
        gone("Cover art placeholder")
        // 150 x 150 less its rounded corners.
        assertTrue("${pixelsOf(Color.Magenta)} magenta", pixelsOf(Color.Magenta) in 22000..22500)

        play(Samples.playing)
        shows("Cover art placeholder")
        assertEquals(0, pixelsOf(Color.Magenta))
    }

    @Test
    fun homeTransportControlsThePlayer() {
        tap("Next")
        tap("Previous")
        tap("Play or pause")
        cabin.waitForIdle()
        assertEquals(listOf("next", "queue:2", "previous", "queue:1", "playPause"), media.calls)
    }

    @Test
    fun musicScreenShowsTheTrackAndItsQueue() {
        tap("Open music")
        text("Bluetooth audio")
        text("Up next")
        shows("Play Open Road")
        shows("Play Night Signals")
        shows("Play Paper Lanterns")
        shows("Play Slow Tide")
        // The mini player would repeat what the screen already shows, so it is left out here.
        gone("Open music")
    }

    @Test
    fun tappingAQueueItemPlaysIt() {
        tap("Open music")
        tap("Play Paper Lanterns")
        cabin.waitForIdle()
        assertEquals(listOf("queue:3"), media.calls)
        // Now both the track card and its queue row name the artist.
        cabin.onAllNodesWithText("June Harbor").assertCountEquals(2)
    }

    @Test
    fun miniPlayerAppearsOnOtherScreensAndOpensMusic() {
        tap("All apps")
        shows("Open music")
        tap("Next")
        cabin.waitForIdle()
        assertTrue("next" in media.calls)
        tap("Open music")
        text("Up next")
    }

    @Test
    fun aPlayerWithNoLengthIsOnlyCalledLiveWhileItPlays() {
        // What a phone-link app leaves up while nothing plays: its name, no length, and stopped.
        val idle = Samples.playing.copy(
            title = "Android Auto", artist = "Connected", queue = emptyList(),
            progress = PlaybackProgress(positionMs = 0, durationMs = 0, speed = 0f, playing = false, atElapsedMs = 0),
        )
        play(idle)
        text("Connected")
        cabin.onNodeWithText("Live").assertDoesNotExist()
        tap("Open music")
        text("Connected")
        cabin.onNodeWithText("Live").assertDoesNotExist()
        // The accent is on screen for the play button's ring and little else: the line is empty.
        val idleAccent = pixelsOf(Slate.Accent)

        // Something of no length that really is playing, as radio is, reads "Live" over a full line.
        play(idle.copy(title = "Radio One", artist = "", progress = idle.progress.copy(playing = true, speed = 1f)))
        text("Live")
        assertTrue("the line should have filled", pixelsOf(Slate.Accent) > idleAccent + 1_000)
    }

    @Test
    fun activeSourceOpensItsAppAndAnotherIsResumedInPlace() {
        // The design's track plays in Music, which Bluetooth is set to; Bluetooth is therefore active.
        tap("Bluetooth source")
        cabin.waitForIdle()
        assertEquals(listOf(FixedApps.id("Music")), apps.launched)

        // USB uses the same app. It already has a session, so it is told to play and nothing opens.
        tap("USB source")
        cabin.waitForIdle()
        assertEquals(listOf(FixedApps.id("Music")), apps.launched)
        assertEquals(listOf("play:design.music"), media.calls)
        cabin.waitUntil(2_000) { settings.activeSource == SourceId.Usb }
    }

    @Test
    fun sourceWhoseAppHasNoSessionOpensIt() {
        tap("Radio source")
        cabin.waitForIdle()
        assertEquals(listOf(FixedApps.id("Radio")), apps.launched)
    }

    @Test
    fun railButtonsOpenTheUnitsOwnApps() {
        tap("Phone")
        tap("Phone link")
        cabin.waitForIdle()
        assertEquals(listOf(FixedApps.id("Phone"), FixedApps.id("Phone link")), apps.launched)
    }

    @Test
    fun hidingASourceRemovesItFromTheRow() {
        tap("All apps")
        tap("Settings")
        tap("Arrange sources")
        tap("Select USB")
        text("Opens Music")
        tap("Hide or show")
        tap("Done arranging")
        tap("Home")
        gone("USB source")
        shows("Radio source")
    }

    @Test
    fun showingAuxAddsItToTheRow() {
        gone("AUX source")
        tap("All apps")
        tap("Settings")
        tap("Arrange sources")
        tap("Select AUX")
        text("No app chosen yet")
        tap("Hide or show")
        tap("Done arranging")
        tap("Home")
        shows("AUX source")
    }

    @Test
    fun aSourceCanBeGivenAnAppAndReturnedToAutomatic() {
        tap("All apps")
        tap("Settings")
        tap("Arrange sources")
        tap("Select Radio")
        text("Opens Radio")
        tap("Choose the app for this source")
        text("Radio opens")
        tap("Maps")
        // Back on the arranging screen with Radio still selected.
        text("Opens Maps")
        assertEquals(FixedApps.id("Maps"), settings.bindings[Slot.Radio])

        tap("Choose the app for this source")
        tap("Choose automatically")
        text("Opens Radio")
        assertEquals(null, settings.bindings[Slot.Radio])
    }
}
