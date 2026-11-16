package com.roufsyed.cabin

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roufsyed.cabin.data.CabinSettings
import com.roufsyed.cabin.media.FixedMedia
import com.roufsyed.cabin.model.BarSide
import com.roufsyed.cabin.model.HomePanel
import com.roufsyed.cabin.model.NavUi
import com.roufsyed.cabin.model.Samples
import com.roufsyed.cabin.model.ScreenOffShows
import com.roufsyed.cabin.nav.FixedNavigation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Checks where things are on screen against the numbers in the design (1280 x 800). On a window of any
 * other size the expectations are scaled the same way the app scales itself.
 */
@RunWith(AndroidJUnit4::class)
class LayoutTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues()

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private fun bounds(description: String): Rect =
        cabin.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot

    /** Asserts [description] sits at ([x], [y]) with size [w] x [h], in design pixels, within one pixel. */
    private fun at(description: String, x: Float, y: Float, w: Float, h: Float) {
        val root = cabin.onRoot().fetchSemanticsNode().boundsInRoot
        val scale = minOf(root.width / 1280f, root.height / 800f)
        val left = (root.width - 1280f * scale) / 2f
        val top = (root.height - 800f * scale) / 2f
        val actual = bounds(description)
        val expected = Rect(left + x * scale, top + y * scale, left + (x + w) * scale, top + (y + h) * scale)
        val worst = maxOf(
            abs(actual.left - expected.left), abs(actual.top - expected.top),
            abs(actual.right - expected.right), abs(actual.bottom - expected.bottom),
        )
        assertEquals("$description expected $expected but was $actual", 0f, worst, 1.01f)
    }

    /** [box], which is in this screen's pixels, in design pixels. */
    private fun inDesign(box: Rect): Rect {
        val root = cabin.onRoot().fetchSemanticsNode().boundsInRoot
        val scale = minOf(root.width / 1280f, root.height / 800f)
        val left = (root.width - 1280f * scale) / 2f
        val top = (root.height - 800f * scale) / 2f
        return Rect((box.left - left) / scale, (box.top - top) / scale, (box.right - left) / scale, (box.bottom - top) / scale)
    }

    /** The box of one line of text, in design pixels, wherever it sits inside a button. */
    private fun line(text: String): Rect =
        inDesign(cabin.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)

    @Test
    fun homeMatchesTheDesignGrid() {
        // Panels are 328 x 600 with a 1 px border, so their buttons start one pixel in.
        at("Open Maps", 395f, 41f, 326f, 598f)
        at("Open music", 749f, 41f, 326f, 470f)
        at("Previous", 749f, 511f, 108f, 128f)
        at("Play or pause", 857f, 511f, 110f, 128f)
        at("Next", 967f, 511f, 108f, 128f)
    }

    /** The cover's own box in design pixels, wherever it sits inside a button. */
    private fun cover(): Rect =
        inDesign(cabin.onNodeWithContentDescription("Cover art", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)

    private fun assertBox(what: String, expected: Rect, actual: Rect) {
        val worst = maxOf(
            abs(actual.left - expected.left), abs(actual.top - expected.top),
            abs(actual.right - expected.right), abs(actual.bottom - expected.bottom),
        )
        assertEquals("$what expected $expected but was $actual", 0f, worst, 1.01f)
    }

    @Test
    fun theCoverHangsInTheCornerOfNowPlayingAndMovesNothing() {
        val media = values.container.media as FixedMedia
        val lines = listOf("Now playing", "Open Road", "Aster Lane", "1:24", "3:41")
        val before = lines.map { line(it) }

        media.set(Samples.playing.copy(art = plainCover(Color.Magenta)))
        cabin.waitForIdle()

        // 84 square, its top level with "Now playing" and its right edge on the panel's text margin.
        assertBox("cover", Rect(959f, 69f, 1043f, 153f), cover())
        assertEquals(before, lines.map { line(it) })
        // The panel's buttons are where the design has them.
        homeMatchesTheDesignGrid()
    }

    @Test
    fun theLongestTitleAndArtistStillClearTheCover() {
        val media = values.container.media as FixedMedia
        val title = "A Title Long Enough To Fill All Three Of Its Lines And More"
        val artist = "Several Artists Whose Names Run On To A Second Line"
        media.set(Samples.playing.copy(title = title, artist = artist, art = plainCover(Color.Magenta)))
        cabin.waitForIdle()

        val cover = cover()
        val titleBox = line(title)
        val artistBox = line(artist)
        // Three lines of title and two of artist, as without a cover.
        assertEquals(152f, titleBox.height, 3f)
        assertEquals(68f, artistBox.height, 3f)
        // The first line runs under the cover, with room between them.
        assertEquals(true, titleBox.right > cover.left)
        assertEquals("gap between the cover and the title", true, titleBox.top - cover.bottom >= 12f)
        // And the artist ends clear of the progress line.
        assertEquals("gap between the artist and the progress line", true, line("1:24").top - 17f - artistBox.bottom >= 12f)
    }

    @Test
    fun theCoverOnTheMusicScreenTakesTheStandInsPlace() {
        cabin.onNodeWithContentDescription("Open music").performClick()
        val standIn = inDesign(bounds("Cover art placeholder"))
        assertEquals(150f, standIn.width, 1.01f)
        assertEquals(150f, standIn.height, 1.01f)
        assertEquals(73f, standIn.left, 1.01f)

        (values.container.media as FixedMedia).set(Samples.playing.copy(art = plainCover(Color.Magenta)))
        cabin.waitForIdle()
        assertBox("cover", standIn, cover())
    }

    @Test
    fun railMatchesTheDesignGrid() {
        at("Phone", 1109f, 41f, 130f, 136f)
        at("Phone link", 1109f, 177f, 130f, 136f)
        at("All apps", 1109f, 313f, 130f, 136f)
        at("Volume up", 1109f, 450f, 130f, 104f)
        at("Mute or unmute", 1109f, 555f, 130f, 99f)
        at("Volume down", 1109f, 655f, 130f, 104f)
        // Back is on the bar over other apps only.
        cabin.onNodeWithContentDescription("Go back").assertDoesNotExist()
    }

    private fun place(transform: (CabinSettings) -> CabinSettings) {
        runBlocking { values.container.settings.update(transform) }
        cabin.waitForIdle()
    }

    @Test
    fun withTheSideBarOnTheLeftTheRailAndTheMainColumnChangePlaces() {
        place { it.copy(barSide = BarSide.Left) }
        // The rail takes the left margin, and the main column starts 164 further right than designed.
        at("Phone", 41f, 41f, 130f, 136f)
        at("Phone link", 41f, 177f, 130f, 136f)
        at("All apps", 41f, 313f, 130f, 136f)
        at("Volume up", 41f, 450f, 130f, 104f)
        at("Volume down", 41f, 655f, 130f, 104f)
        at("Open Maps", 559f, 41f, 326f, 598f)
        at("Open music", 913f, 41f, 326f, 470f)
        at("Brightness down", 205f, 665f, 96f, 94f)
        at("Radio source", 559f, 665f, 138f, 94f)
        // The same on another screen.
        cabin.onNodeWithContentDescription("All apps").performClick()
        at("Home", 41f, 313f, 130f, 136f)
        at("Browser", 204f, 152f, 196.8f, 154f)

        place { it.copy(barSide = BarSide.Right) }
        at("Home", 1109f, 313f, 130f, 136f)
        at("Browser", 40f, 152f, 196.8f, 154f)
    }

    @Test
    fun homesPanelsGoWhereTheyArePlaced() {
        // The design: clock, navigation, now playing.
        assertEquals(73f, line("Thursday").left, 1.5f)
        at("Open Maps", 395f, 41f, 326f, 598f)
        at("Open music", 749f, 41f, 326f, 470f)

        place { it.copy(homeOrder = listOf(HomePanel.NowPlaying, HomePanel.Clock, HomePanel.Navigation)) }
        // The three panels keep their size and the 26 between them; only who is where changes.
        at("Open music", 41f, 41f, 326f, 470f)
        at("Previous", 41f, 511f, 108f, 128f)
        assertEquals(427f, line("Thursday").left, 1.5f)
        at("Open Maps", 749f, 41f, 326f, 598f)
    }

    @Test
    fun bottomRowMatchesTheDesignGrid() {
        at("Brightness down", 41f, 665f, 96f, 94f)
        at("Night mode: auto, day or night", 138f, 665f, 132f, 94f)
        at("Brightness up", 271f, 665f, 96f, 94f)
        at("Radio source", 395f, 665f, 138f, 94f)
        at("Bluetooth source", 533f, 665f, 188f, 94f)
        at("USB source", 721f, 665f, 120f, 94f)
        at("Android Auto source", 841f, 665f, 234f, 94f)
    }

    @Test
    fun appsGridMatchesTheDesignGrid() {
        cabin.onNodeWithContentDescription("All apps").performClick()
        // Five columns across 1036 with 13 gaps: 196.8 each. Rows are 154 high, 13 apart, under the strip.
        at("Browser", 40f, 152f, 196.8f, 154f)
        at("Clock", 249.8f, 152f, 196.8f, 154f)
        at("Maps", 40f, 319f, 196.8f, 154f)
        at("Weather", 879.2f, 486f, 196.8f, 154f)
        // The rail and bottom row do not move between screens.
        at("Home", 1109f, 313f, 130f, 136f)
        at("Radio source", 395f, 665f, 138f, 94f)
    }

    @Test
    fun musicScreenMatchesTheDesignGrid() {
        cabin.onNodeWithContentDescription("Open music").performClick()
        // Left card 480 x 488 at (40, 152): three 96 px buttons, 28 apart, centred in a 112-high row at its foot.
        at("Previous", 108f, 519f, 96f, 96f)
        at("Play or pause", 232f, 519f, 96f, 96f)
        at("Next", 356f, 519f, 96f, 96f)
        // Right card 543 x 488 at (533, 152): a 72-high heading, then 96-high rows.
        at("Play Open Road", 534f, 225f, 541f, 96f)
        at("Play Slow Tide", 534f, 513f, 541f, 96f)
    }

    @Test
    fun miniPlayerMatchesTheDesignGrid() {
        cabin.onNodeWithContentDescription("All apps").performClick()
        // The 600 x 96 mini player ends at the main column's right edge.
        at("Open music", 477f, 41f, 310f, 94f)
        at("Previous", 787f, 41f, 96f, 94f)
        at("Play or pause", 883f, 41f, 96f, 94f)
        at("Next", 979f, 41f, 96f, 94f)
    }

    @Test
    fun idleNavigationPanelAndVoiceDialogMatchTheDesignGrid() {
        cabin.runOnUiThread { (values.container.navigation as FixedNavigation).set(NavUi.Idle) }
        at("Where to?", 395f, 41f, 326f, 374f)
        at("Navigate home", 395f, 415f, 326f, 112f)
        at("Navigate to office", 395f, 527f, 326f, 112f)

        cabin.onNodeWithContentDescription("Where to?").performClick()
        // A 680 x 408 dialog at (300, 196); its three 96-high buttons share the 622 px inside it, 12 apart.
        at("Listen again", 592f, 229f, 96f, 96f)
        at("Navigate to Airport", 329f, 479f, 199.33f, 96f)
        at("Navigate to City Mall", 540.33f, 479f, 199.33f, 96f)
        at("Cancel", 751.67f, 479f, 199.33f, 96f)
    }

    @Test
    fun trayKeepsTheDesignsGridUnderItsControls() {
        cabin.onRoot().performTouchInput {
            swipe(Offset(visibleSize.width * 0.4f, visibleSize.height * 0.15f), Offset(visibleSize.width * 0.4f, visibleSize.height * 0.45f), 200)
        }
        // A 1200-wide sheet from the top edge at x = 40. The controls, which the design does not have,
        // come first: two rows of five, each 96 high with a 1 px line under it, the first with 16 more
        // at its top. The five share the sheet's 1198 with four 1 px dividers between them, 238.8 each.
        at("Bluetooth: on", 41f, 0f, 238.8f, 112f)
        at("Wi-Fi: on", 280.8f, 0f, 238.8f, 112f)
        at("Recent apps: allow Cabin under Accessibility first", 1000.2f, 0f, 238.8f, 112f)
        at("Split screen: choose two apps", 41f, 113f, 238.8f, 96f)
        at("Unit settings", 520.6f, 113f, 238.8f, 96f)
        at("Restart: allow Cabin under Accessibility first", 1000.2f, 113f, 238.8f, 96f)
        // In the first row the names sit where they would in a 96-high button, clear of Android's own
        // bar, which is 24 high when a swipe from the screen's edge brings it back for a moment.
        assertEquals(line("Wi-Fi").bottom, line("Unit settings").bottom - 97f, 1.01f)
        // Under them is the design's tray, 210 lower: a 76-high heading, 104-high rows, 96-high buttons.
        at("Missed call. Meera · tap to call back", 41f, 286f, 1198f, 104f)
        at("Kabir. Running ten minutes late", 41f, 390f, 1198f, 104f)
        at("USB drive connected. Tap to play from USB", 41f, 494f, 1198f, 104f)
        at("Clear all notifications", 41f, 599f, 398f, 96f)
        at("Screen off", 440f, 599f, 398f, 96f)
        at("Close notification tray", 839f, 599f, 400f, 96f)
    }

    @Test
    fun settingsAndKeysMatchTheDesignGrid() {
        cabin.onNodeWithContentDescription("All apps").performClick()
        cabin.onNodeWithContentDescription("Settings").performClick()
        // Two columns across 1036 with a 13 gap: 511.5 each. Rows are 112 high, 13 apart.
        at("Arrange apps", 40f, 152f, 511.5f, 112f)
        at("Arrange sources", 564.5f, 152f, 511.5f, 112f)
        at("Steering wheel keys", 40f, 277f, 511.5f, 112f)
        at("Wi-Fi settings", 564.5f, 402f, 511.5f, 112f)
        at("Theme and gestures", 40f, 527f, 511.5f, 112f)
        at("Open setup", 564.5f, 527f, 511.5f, 112f)

        cabin.onNodeWithContentDescription("Steering wheel keys").performClick()
        // A 96-high heading row, then two rows of three 183-high tiles, 13 apart.
        at("Back to settings", 40f, 152f, 200f, 96f)
        at("Steering key: volume up", 40f, 261f, 336.67f, 183f)
        at("Steering key: voice", 739.33f, 261f, 336.67f, 183f)
        at("Steering key: mode", 739.33f, 457f, 336.67f, 183f)
    }

    @Test
    fun setupAndGesturesUseTheSettingsGridUnderATitleBar() {
        cabin.onNodeWithContentDescription("All apps").performClick()
        cabin.onNodeWithContentDescription("Settings").performClick()
        cabin.onNodeWithContentDescription("Open setup").performClick()
        // The bar is 96 high at the top of the main column: a 96 x 96 page button, 12 from a 150-wide Done.
        at("Next setup page", 818f, 40f, 96f, 96f)
        at("Finish setup", 926f, 40f, 150f, 96f)
        at("Make Cabin the home app", 40f, 152f, 511.5f, 112f)
        at("Dim with the headlights", 564.5f, 277f, 511.5f, 112f)
        at("Allow nearby devices", 40f, 527f, 511.5f, 112f)
        at("Keep the side bar over other apps", 564.5f, 527f, 511.5f, 112f)

        cabin.onNodeWithContentDescription("Next setup page").performClick()
        at("Previous setup page", 818f, 40f, 96f, 96f)
        at("Choose the phone app", 40f, 152f, 511.5f, 112f)
        at("Choose the maps app", 40f, 277f, 511.5f, 112f)
        at("Choose the music app", 564.5f, 277f, 511.5f, 112f)
        at("What the players tell Cabin", 40f, 402f, 511.5f, 112f)

        cabin.onNodeWithContentDescription("Finish setup").performClick()
        cabin.onNodeWithContentDescription("Theme and gestures").performClick()
        at("Back to settings", 926f, 40f, 150f, 96f)
        at("Choose a theme", 40f, 152f, 511.5f, 112f)
        at("Swipe down: tray or off", 564.5f, 152f, 511.5f, 112f)
        at("Swipe sideways: apps, tracks or off", 40f, 277f, 511.5f, 112f)
        at("Two-finger drag: volume, brightness or off", 564.5f, 277f, 511.5f, 112f)
        at("Long press on an app: split screen or off", 40f, 402f, 511.5f, 112f)
        // Its second page, Layout, is reached by the same kind of button as Setup's.
        at("Layout page", 818f, 40f, 96f, 96f)
        cabin.onNodeWithContentDescription("Layout page").performClick()
        at("Theme and gestures page", 818f, 40f, 96f, 96f)
        at("Side bar over other apps: on or off", 40f, 152f, 511.5f, 112f)
        at("Side bar handle: on or off", 564.5f, 152f, 511.5f, 112f)
        at("Side bar tucks away: immediate, 5 sec, 10 sec or never", 40f, 277f, 511.5f, 112f)
        at("Side bar position: right or left", 564.5f, 277f, 511.5f, 112f)
        at("Clock and date on Home: left, middle or right", 40f, 402f, 511.5f, 112f)
        at("Navigation on Home: left, middle or right", 564.5f, 402f, 511.5f, 112f)
        at("Now playing on Home: left, middle or right", 40f, 527f, 511.5f, 112f)
        at("Reset positions", 564.5f, 527f, 511.5f, 112f)
        // The list of waits drops over the whole of its tile's column: four rows that share the
        // grid's height inside the list's 1 px border, with a 1 px rule between them.
        cabin.onNodeWithContentDescription("Side bar tucks away: immediate, 5 sec, 10 sec or never").performClick()
        at("Tuck away: Immediate", 41f, 153f, 509.5f, 120.75f)
        at("Tuck away: 5 sec", 41f, 274.75f, 509.5f, 120.75f)
        at("Tuck away: 10 sec", 41f, 396.5f, 509.5f, 120.75f)
        at("Tuck away: Never", 41f, 518.25f, 509.5f, 120.75f)
        cabin.onNodeWithContentDescription("Tuck away: 5 sec").performClick()
        cabin.onNodeWithContentDescription("Theme and gestures page").performClick()
        // The rail and bottom row do not move.
        at("Home", 1109f, 313f, 130f, 136f)
        at("Radio source", 395f, 665f, 138f, 94f)

        cabin.onNodeWithContentDescription("Choose a theme").performClick()
        // Two rows of five tiles the size of the Apps grid's, under the same kind of bar.
        at("Back to theme and gestures", 926f, 40f, 150f, 96f)
        at("Blue theme", 40f, 152f, 196.8f, 154f)
        at("Amber theme", 879.2f, 152f, 196.8f, 154f)
        at("Copper theme", 40f, 319f, 196.8f, 154f)
        at("Platinum theme", 879.2f, 319f, 196.8f, 154f)
    }

    private fun openScreenOffPage() {
        cabin.onNodeWithContentDescription("All apps").performClick()
        cabin.onNodeWithContentDescription("Settings").performClick()
        cabin.onNodeWithContentDescription("Screen off options").performClick()
    }

    @Test
    fun screenOffPageUsesTheSettingsGridAndTheDarkScreenIsWhereTheDesignHasIt() {
        openScreenOffPage()
        at("Back to settings", 926f, 40f, 150f, 96f)
        at("Turn the screen off", 40f, 152f, 511.5f, 112f)
        at("Show the clock: on or off", 564.5f, 152f, 511.5f, 112f)
        // The switches come in the order of the lines on the dark screen.
        at("Show the date: on or off", 40f, 277f, 511.5f, 112f)
        at("Show the status marks: on or off", 564.5f, 277f, 511.5f, 112f)
        at("Show the next turn: on or off", 40f, 402f, 511.5f, 112f)
        at("Show what is playing: on or off", 564.5f, 402f, 511.5f, 112f)
        // The rail and bottom row do not move.
        at("Home", 1109f, 313f, 130f, 136f)
        at("Radio source", 395f, 665f, 138f, 94f)

        cabin.onNodeWithContentDescription("Turn the screen off").performClick()
        // One button over the whole screen. The design's three lines are 168, 33.6 and 28.8 high, 18
        // apart and centred as a group, so they start at 266.8, 452.8 and 504.4.
        at("Wake screen", 0f, 0f, 1280f, 800f)
        val time = line("10:10")
        val date = line("Thursday 12 November")
        val hint = line("Tap to wake")
        assertEquals(266.8f, time.top, 1.5f)
        assertEquals(434.8f, time.bottom, 1.5f)
        assertEquals(452.8f, date.top, 1.5f)
        assertEquals(486.4f, date.bottom, 1.5f)
        assertEquals(504.4f, hint.top, 1.5f)
        assertEquals(533.2f, hint.bottom, 1.5f)
        for (box in listOf(time, date, hint)) assertEquals(640f, box.center.x, 1.5f)
    }

    @Test
    fun theDarkScreenWithEveryLineKeepsTheDesignsSpacingAndStaysCentred() {
        runBlocking {
            values.container.settings.update {
                it.copy(screenOff = ScreenOffShows(clock = true, date = true, playing = true, turn = true, status = true))
            }
        }
        openScreenOffPage()
        cabin.onNodeWithContentDescription("Turn the screen off").performClick()
        // Top to bottom: the time, the date, the marks, the next turn, the track, the hint.
        val rows = listOf(
            line("10:10"),
            line("Thursday 12 November"),
            inDesign(bounds("Phone connected, Wi-Fi on, GPS ready")),
            line("350 m · Ring Road"),
            line("Open Road · Aster Lane"),
            line("Tap to wake"),
        )
        for ((upper, lower) in rows.zipWithNext()) assertEquals(18f, lower.top - upper.bottom, 1f)
        // The group as a whole is centred up and down, and each line across, the turn with its arrow.
        assertEquals(800f, rows.first().top + rows.last().bottom, 2f)
        for (row in rows - rows[3]) assertEquals(640f, row.center.x, 1.5f)
        // The arrow is 28 wide and 16 from its text, so the text sits 22 right of centre.
        assertEquals(662f, rows[3].center.x, 1.5f)
        // No line is smaller than the design's smallest text, 24 px, whose line is 28.8 high.
        for (row in rows - rows[2]) assert(row.height >= 28f) { "a line is only ${row.height} high" }
    }

    @Test
    fun splitChooserIsTheAppsGridUnderATitleBar() {
        cabin.onNodeWithContentDescription("All apps").performClick()
        cabin.onNodeWithContentDescription("Maps").performTouchInput { longClick() }
        at("Cancel split screen", 926f, 40f, 150f, 96f)
        // The same grid, without the app that was held and without Cabin's own tile.
        at("Browser", 40f, 152f, 196.8f, 154f)
        at("Music", 40f, 319f, 196.8f, 154f)
        at("Home", 1109f, 313f, 130f, 136f)
        at("Radio source", 395f, 665f, 138f, 94f)
    }

    @Test
    fun everyTouchTargetIsAtLeast94PixelsEachWay() {
        // The design's rule is 96 px including the 1 px borders the targets sit inside.
        val root = cabin.onRoot().fetchSemanticsNode().boundsInRoot
        val scale = minOf(root.width / 1280f, root.height / 800f)
        for (control in listOf(
            "Open Maps", "Open music", "Previous", "Play or pause", "Next", "Phone", "Phone link", "All apps",
            "Volume up", "Mute or unmute", "Volume down", "Brightness down", "Brightness up",
            "Night mode: auto, day or night", "Radio source", "Bluetooth source", "USB source", "Android Auto source",
        )) {
            val b = bounds(control)
            assert(b.width >= 94f * scale - 1f && b.height >= 94f * scale - 1f) { "$control is ${b.width} x ${b.height}" }
        }
    }
}
