package com.roufsyed.cabin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Now playing and the source row, with the design's own track and apps. */
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

    /** The cover as its own node: on Home it sits inside the button that opens the playing app. */
    private fun cover() = cabin.onNodeWithContentDescription("Cover art", useUnmergedTree = true)

    private fun play(track: NowPlaying?) {
        media.set(track)
        cabin.waitForIdle()
    }

    private fun pixelsOf(colour: Color): Int = cabin.pixelsOf(listOf(colour)).single()

    @Test
    fun homeShowsATracksCoverOnlyWhenItsPlayerSuppliesOne() {
        // The design's own track comes without one, and a record is drawn in its place.
        cover().assertDoesNotExist()
        assertTheRecord(drawn = true)

        play(Samples.playing.copy(art = plainCover(Color.Magenta)))
        cover().assertIsDisplayed()
        assertTheRecord(drawn = false)
        // 262 x 262 less its rounded corners.
        assertTrue("${pixelsOf(Color.Magenta)} magenta", pixelsOf(Color.Magenta) in 68_000..68_644)

        // The next track brings its own.
        play(Samples.playing.copy(title = "Night Signals", artist = "Vela North", art = plainCover(Color.Cyan)))
        assertEquals(0, pixelsOf(Color.Magenta))
        assertTrue("${pixelsOf(Color.Cyan)} cyan", pixelsOf(Color.Cyan) in 68_000..68_644)

        // A track without one takes it down again, and so does the player going away.
        play(Samples.playing)
        cover().assertDoesNotExist()
        assertEquals(0, pixelsOf(Color.Cyan))
        assertTheRecord(drawn = true)
        play(Samples.playing.copy(art = plainCover(Color.Magenta)))
        play(null)
        cover().assertDoesNotExist()
        assertTheRecord(drawn = true)
        text("Nothing playing")
    }

    @Test
    fun theCoverIsPartOfTheButtonThatOpensThePlayingApp() {
        play(Samples.playing.copy(art = plainCover(Color.Magenta)))
        cover().performClick()
        cabin.waitForIdle()
        assertEquals(listOf("open"), media.calls)
    }

    @Test
    fun homeTransportControlsThePlayer() {
        tap("Next")
        text("Night Signals")
        tap("Previous")
        text("Open Road")
        tap("Play or pause")
        cabin.waitForIdle()
        assertEquals(listOf("next", "previous", "playPause"), media.calls)
    }

    @Test
    fun aTapOnNowPlayingOpensThePlayingAppsOwnScreen() {
        tap("Open music")
        cabin.waitForIdle()
        assertEquals(listOf("open"), media.calls)
        assertEquals(emptyList<String>(), apps.launched)
        shows("Open Maps")
    }

    @Test
    fun miniPlayerAppearsOnOtherScreensAndOpensThePlayingApp() {
        tap("All apps")
        shows("Open music")
        tap("Next")
        cabin.waitForIdle()
        assertTrue("next" in media.calls)
        tap("Open music")
        cabin.waitForIdle()
        assertEquals("open", media.calls.last())
        shows("Browser")
    }

    private fun box(line: String) =
        cabin.onNodeWithText(line, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun wordsOnNowPlaying(): List<String> =
        cabin.onNodeWithContentDescription("Open music").fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }.filter { it.isNotEmpty() }

    private fun pixelsOn(description: String, colour: Color): Int {
        val image = cabin.onNodeWithContentDescription(description).captureToImage().asAndroidBitmap()
        val pixels = IntArray(image.width * image.height)
        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        return pixels.count { it == colour.toArgb() }
    }

    private fun assertTheRecord(drawn: Boolean) {
        val label = pixelsOn("Open music", Slate.Raised)
        if (drawn) {
            assertTrue("the record's label is $label pixels", label >= 2_500)
        } else {
            assertTrue("no record should be drawn, and $label pixels of its label are", label < 50)
        }
    }

    private fun played(): Float? =
        cabin.onNodeWithContentDescription("Play or pause").fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.current

    private fun assertTheRing(arc: Boolean) {
        val accent = pixelsOn("Play or pause", Slate.Accent)
        val rest = pixelsOn("Play or pause", Slate.Outline)
        if (arc) {
            assertTrue("the played part of the ring is $accent pixels", accent in 250..700)
            assertTrue("the rest of the ring is $rest pixels", rest >= 400)
        } else {
            assertTrue("the ring should be whole, and $accent pixels of it are", accent >= 900)
            assertTrue("no track should be drawn under the ring, and $rest pixels are", rest < 50)
            assertNull(played())
        }
    }

    @Test
    fun onlyAPlayerThatGivesALengthGetsItsProgressOnTheRing() {
        val lines = listOf("Now playing", "Open Road", "Aster Lane")
        assertEquals(lines, wordsOnNowPlaying())
        assertTheRing(arc = true)
        assertEquals(0.38f, played()!!, 0.001f)
        val before = lines.map(::box)

        // Something of no length that really is playing, as radio is, and as the unit's own player is.
        val noLength = PlaybackProgress(
            positionMs = 5_000, durationMs = 0, speed = 1f, playing = true, atElapsedMs = 0,
        )
        play(Samples.playing.copy(progress = noLength))
        assertEquals(lines, wordsOnNowPlaying())
        assertTheRing(arc = false)
        assertEquals(before, lines.map(::box))

        // What a phone-link app leaves up while nothing plays: its name, no length, and stopped.
        val stopped = noLength.copy(playing = false)
        play(Samples.playing.copy(title = "Android Auto", artist = "Connected", progress = stopped))
        assertEquals(listOf("Now playing", "Android Auto", "Connected"), wordsOnNowPlaying())
        assertTheRing(arc = false)

        play(null)
        assertEquals(listOf("Now playing", "Nothing playing"), wordsOnNowPlaying())
        assertTheRing(arc = false)

        play(Samples.playing)
        assertEquals(lines, wordsOnNowPlaying())
        assertTheRing(arc = true)
        assertEquals(before, lines.map(::box))
    }

    @Test
    fun theRingFollowsTheTrackFromEmptyToWhole() {
        fun move(positionMs: Long) =
            play(Samples.playing.copy(progress = Samples.playing.progress.copy(positionMs = positionMs)))
        fun accent() = pixelsOn("Play or pause", Slate.Accent)

        move(0)
        assertEquals(0f, played()!!, 0f)
        assertTrue("at its start the ring has no played part, and ${accent()} pixels are", accent() < 30)

        move(110_500)
        assertEquals(0.5f, played()!!, 0.001f)
        assertTrue("half way the played part is ${accent()} pixels", accent() in 400..700)

        move(221_000)
        assertEquals(1f, played()!!, 0f)
        assertTrue("at its end the ring is whole, and ${accent()} pixels of it are", accent() >= 900)
    }

    @Test
    fun withNothingPlayingPreviousAndNextAreGreyedAndDoNothing() {
        play(null)
        val skips = listOf("Previous", "Next")
        for (skip in skips) {
            cabin.onNodeWithContentDescription(skip).assertIsNotEnabled()
            assertTrue("$skip should be drawn greyed", pixelsOn(skip, Slate.Disabled) > 20)
            assertEquals("$skip should not be drawn in white", 0, pixelsOn(skip, Slate.Text))
            tap(skip)
        }
        cabin.waitForIdle()
        assertEquals(emptyList<String>(), media.calls)
        tap("Play or pause")
        cabin.waitForIdle()
        assertEquals("playPause", media.calls.first())

        play(Samples.playing)
        for (skip in skips) {
            cabin.onNodeWithContentDescription(skip).assertIsEnabled()
            assertTrue("$skip should be drawn in white", pixelsOn(skip, Slate.Text) > 20)
            assertEquals("$skip should not be drawn greyed", 0, pixelsOn(skip, Slate.Disabled))
        }
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

@RunWith(AndroidJUnit4::class)
class PlayerWithNoScreenTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues { it.copy(media = FixedMedia(Samples.playing, ownScreen = false)) }

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private val media get() = values.container.media as FixedMedia
    private val apps get() = values.container.apps as FixedApps
    private val settings get() = values.container.settings.settings.value

    private fun tap(description: String) = cabin.onNodeWithContentDescription(description).performClick()

    @Test
    fun aTapOnNowPlayingOpensItsAppFromTheGrid() {
        tap("Open music")
        cabin.waitForIdle()
        assertEquals(listOf(FixedApps.id("Music")), apps.launched)
    }

    @Test
    fun aPlayerThatIsNoAppGivesWayToTheMusicAppAndWithNoneChosenToChoosingOne() {
        media.set(Samples.playing.copy(packageName = "com.unit.media", appLabel = "Unit player"))
        cabin.waitUntil(2_000) { settings.lastPlayer == "com.unit.media" }

        tap("Open music")
        cabin.onNodeWithText("Music opens").assertIsDisplayed()
        assertEquals(emptyList<String>(), apps.launched)

        tap("Radio")
        cabin.waitUntil(2_000) { values.container.sources.state.value.bindings[Slot.Music]?.label == "Radio" }
        tap("Open music")
        cabin.waitForIdle()
        assertEquals(listOf(FixedApps.id("Radio")), apps.launched)
    }
}

@RunWith(AndroidJUnit4::class)
class NowPlayingWithoutAccessTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues { it.copy(media = FixedMedia(null, hasAccess = false)) }

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private val media get() = values.container.media as FixedMedia

    private fun button(description: String) = cabin.onNodeWithContentDescription(description)

    @Test
    fun theHintIsWholeOnItsTwoLinesAndClearOfTheRing() {
        val hint = cabin.onNodeWithText("Tap to let Cabin see what is playing", useUnmergedTree = true).fetchSemanticsNode()
        val laidOut = mutableListOf<TextLayoutResult>()
        hint.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(laidOut)
        assertEquals(2, laidOut.single().lineCount)
        assertFalse("the hint should not be cut short", laidOut.single().hasVisualOverflow)

        val root = cabin.onRoot().fetchSemanticsNode().boundsInRoot
        val scale = minOf(root.width / 1280f, root.height / 800f)
        val ringTop = button("Play or pause").fetchSemanticsNode().boundsInRoot.top
        assertTrue("gap between the hint and the ring", ringTop - hint.boundsInRoot.bottom >= 12f * scale)
        cabin.onNodeWithText("No access yet").assertIsDisplayed()
    }

    @Test
    fun previousAndNextStayOnAsCabinCannotTellWhatPlays() {
        for (skip in listOf("Previous", "Next")) {
            button(skip).assertIsEnabled()
            button(skip).performClick()
        }
        cabin.waitForIdle()
        assertEquals(listOf("previous", "next"), media.calls)
    }
}
