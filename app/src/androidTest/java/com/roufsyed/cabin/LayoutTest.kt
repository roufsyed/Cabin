package com.roufsyed.cabin

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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
