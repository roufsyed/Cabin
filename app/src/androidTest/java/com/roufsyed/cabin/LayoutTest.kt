package com.roufsyed.cabin

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roufsyed.cabin.model.NavUi
import com.roufsyed.cabin.nav.FixedNavigation
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

    @Test
    fun homeMatchesTheDesignGrid() {
        // Panels are 328 x 600 with a 1 px border, so their buttons start one pixel in.
        at("Open Maps", 395f, 41f, 326f, 598f)
        at("Open music", 749f, 41f, 326f, 470f)
        at("Previous", 749f, 511f, 108f, 128f)
        at("Play or pause", 857f, 511f, 110f, 128f)
        at("Next", 967f, 511f, 108f, 128f)
    }

    @Test
    fun railMatchesTheDesignGrid() {
        at("Phone", 1109f, 41f, 130f, 136f)
        at("Phone link", 1109f, 177f, 130f, 136f)
        at("All apps", 1109f, 313f, 130f, 136f)
        at("Volume up", 1109f, 450f, 130f, 104f)
        at("Mute or unmute", 1109f, 555f, 130f, 99f)
        at("Volume down", 1109f, 655f, 130f, 104f)
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
    fun trayMatchesTheDesignGrid() {
        cabin.onRoot().performTouchInput {
            swipe(Offset(visibleSize.width * 0.4f, visibleSize.height * 0.15f), Offset(visibleSize.width * 0.4f, visibleSize.height * 0.45f), 200)
        }
        // A 1200-wide sheet from the top edge at x = 40: a 76-high heading, 104-high rows, 96-high buttons.
        at("Missed call. Meera · tap to call back", 41f, 76f, 1198f, 104f)
        at("Kabir. Running ten minutes late", 41f, 180f, 1198f, 104f)
        at("USB drive connected. Tap to play from USB", 41f, 284f, 1198f, 104f)
        at("Clear all notifications", 41f, 389f, 398f, 96f)
        at("Screen off", 440f, 389f, 398f, 96f)
        at("Close notification tray", 839f, 389f, 400f, 96f)
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
        at("Gesture settings", 40f, 527f, 511.5f, 112f)
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

        cabin.onNodeWithContentDescription("Next setup page").performClick()
        at("Previous setup page", 818f, 40f, 96f, 96f)
        at("Choose the phone app", 40f, 152f, 511.5f, 112f)
        at("Choose the maps app", 40f, 277f, 511.5f, 112f)
        at("Choose the music app", 564.5f, 277f, 511.5f, 112f)

        cabin.onNodeWithContentDescription("Finish setup").performClick()
        cabin.onNodeWithContentDescription("Gesture settings").performClick()
        at("Back to settings", 926f, 40f, 150f, 96f)
        at("Swipe down: tray or off", 40f, 152f, 511.5f, 112f)
        at("Swipe sideways: apps, tracks or off", 564.5f, 152f, 511.5f, 112f)
        at("Two-finger drag: volume, brightness or off", 40f, 277f, 511.5f, 112f)
        at("Long press on an app: split screen or off", 564.5f, 277f, 511.5f, 112f)
        // The rail and bottom row do not move.
        at("Home", 1109f, 313f, 130f, 136f)
        at("Radio source", 395f, 665f, 138f, 94f)
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
