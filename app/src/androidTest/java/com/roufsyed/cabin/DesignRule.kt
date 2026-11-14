package com.roufsyed.cabin

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.AndroidComposeTestRule as composeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource

/** Gives each test a fresh copy of the design's values, before the activity starts. */
class FreshDesignValues(private val configure: (AppContainer) -> AppContainer = { it }) : ExternalResource() {
    lateinit var container: AppContainer
        private set

    override fun before() {
        // "Where to?" asks for the microphone on first use; a system dialog would stall a test.
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            ApplicationProvider.getApplicationContext<CabinApp>().packageName,
            Manifest.permission.RECORD_AUDIO,
        )
        container = configure(AppContainer.design())
        ApplicationProvider.getApplicationContext<CabinApp>().replaceContainer(container)
    }
}

typealias CabinRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

/** Runs the real activity, full screen, as the unit would. */
fun cabinRule(): CabinRule = composeRule(
    activityRule = ActivityScenarioRule(
        Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java),
    ),
    activityProvider = { rule ->
        var activity: MainActivity? = null
        rule.scenario.onActivity { activity = it }
        activity!!
    },
)

/**
 * Everything on screen, one number per pixel. The screen is read once, as plain numbers: going through
 * it pixel by pixel as colours takes minutes.
 */
fun CabinRule.pixels(): IntArray {
    val bitmap = onRoot().captureToImage().asAndroidBitmap()
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    return pixels
}

/** How many pixels of what is on screen are exactly each of [colours], in the order given. */
fun CabinRule.pixelsOf(colours: List<Color>): List<Int> {
    val wanted = IntArray(colours.size) { colours[it].toArgb() }
    val counts = IntArray(colours.size)
    for (pixel in pixels()) {
        for (i in wanted.indices) if (pixel == wanted[i]) counts[i]++
    }
    return counts.toList()
}

/** A cover of one flat colour, the size Cabin keeps covers at, standing in for a player's own. */
fun plainCover(colour: Color, side: Int = 300): ImageBitmap =
    Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888).apply { eraseColor(colour.toArgb()) }.asImageBitmap()
