package com.roufsyed.cabin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roufsyed.cabin.model.ThemeId
import com.roufsyed.cabin.ui.theme.Slate
import com.roufsyed.cabin.ui.theme.Themes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DayColoursTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues { container ->
        container.also { runBlocking { it.settings.update { saved -> saved.copy(dayColours = true) } } }
    }

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private val settings get() = values.container.settings.settings.value
    private fun tap(description: String) = cabin.onNodeWithContentDescription(description).performClick()
    private fun palette() = cabin.runOnUiThread<com.roufsyed.cabin.ui.theme.Palette> { Slate.palette }

    @Test
    fun theScreenIsLightByDayAndAsDesignedAtNight() {
        cabin.onNodeWithText("Auto").assertIsDisplayed()
        cabin.waitUntil(2_000) { palette() == Themes.palette(ThemeId.Blue, day = true) }

        tap("Night mode: auto, day or night")
        cabin.onNodeWithText("Day").assertIsDisplayed()
        assertEquals(Themes.palette(ThemeId.Blue, day = true), palette())

        tap("Night mode: auto, day or night")
        cabin.onNodeWithText("Night").assertIsDisplayed()
        cabin.waitUntil(2_000) { palette() == Themes.palette(ThemeId.Blue) }
    }

    @Test
    fun theTileInThemeAndGesturesKeepsTheDesignsColoursByDayToo() {
        tap("All apps")
        tap("Settings")
        tap("Theme and gestures")
        tap("Day colours: on or off")
        cabin.waitUntil(2_000) { !settings.dayColours }
        cabin.waitUntil(2_000) { palette() == Themes.palette(ThemeId.Blue) }
        tap("Day colours: on or off")
        cabin.waitUntil(2_000) { settings.dayColours }
        cabin.waitUntil(2_000) { palette() == Themes.palette(ThemeId.Blue, day = true) }
    }
}
