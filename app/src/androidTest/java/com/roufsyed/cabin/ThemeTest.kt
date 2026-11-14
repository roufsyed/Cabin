package com.roufsyed.cabin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roufsyed.cabin.data.CabinSettings
import com.roufsyed.cabin.data.InMemorySettings
import com.roufsyed.cabin.model.ThemeId
import com.roufsyed.cabin.ui.theme.Slate
import com.roufsyed.cabin.ui.theme.Themes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Theme section: ten presets, and what choosing one does. */
@RunWith(AndroidJUnit4::class)
class ThemeTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues()

    @get:Rule(order = 1)
    val cabin = cabinRule()

    private val settings get() = values.container.settings.settings.value
    private val blue = Themes.palette(ThemeId.Blue)
    private val amber = Themes.palette(ThemeId.Amber)

    private fun tap(description: String) = cabin.onNodeWithContentDescription(description).performClick()
    private fun shows(description: String) = cabin.onNodeWithContentDescription(description).assertIsDisplayed()

    private fun openThemes() {
        tap("All apps")
        tap("Settings")
        tap("Theme and gestures")
        tap("Choose a theme")
    }

    @Test
    fun cabinStartsInBlueWhichIsTheDesignsOwn() {
        assertEquals(ThemeId.Blue, settings.theme)
        assertEquals(blue, Slate.palette)
        // The minutes of the clock, the active source and the play ring are drawn in the accent.
        val (inBlue, inAmber) = cabin.pixelsOf(listOf(blue.accent, amber.accent))
        assertTrue("only $inBlue pixels in the accent", inBlue > 1_000)
        assertEquals(0, inAmber)
    }

    @Test
    fun theTenThemesAreOfferedAndTheOneInUseIsMarked() {
        openThemes()
        cabin.onNodeWithText("Theme").assertIsDisplayed()
        for (id in ThemeId.entries) shows("${id.label} theme")
        cabin.onNodeWithContentDescription("Blue theme").assertIsSelected()
        cabin.onNodeWithContentDescription("Amber theme").assertIsNotSelected()
    }

    @Test
    fun choosingAThemeRecoloursCabinAtOnceAndIsSaved() {
        openThemes()
        tap("Amber theme")
        cabin.waitUntil(2_000) { settings.theme == ThemeId.Amber && Slate.palette == amber }
        cabin.onNodeWithContentDescription("Amber theme").assertIsSelected()
        cabin.onNodeWithContentDescription("Blue theme").assertIsNotSelected()

        // Back on "Theme and gestures" the tile names the new theme.
        tap("Back to theme and gestures")
        cabin.onNodeWithText("Amber").assertIsDisplayed()

        // And Home is drawn in it: amber where the accent goes, the design's blue nowhere.
        tap("Home")
        shows("Open Maps")
        val (inAmber, inBlue, amberPanels, bluePanels) =
            cabin.pixelsOf(listOf(amber.accent, blue.accent, amber.surface, blue.surface))
        assertTrue("only $inAmber pixels in the accent", inAmber > 1_000)
        assertEquals(0, inBlue)
        // The panels behind have taken the theme's own dark surface as well.
        assertTrue("only $amberPanels pixels of panel", amberPanels > 100_000)
        assertEquals(0, bluePanels)
    }

    @Test
    fun goingBackToBlueRestoresTheDesignsColours() {
        openThemes()
        tap("Ruby theme")
        cabin.waitUntil(2_000) { Slate.palette == Themes.palette(ThemeId.Ruby) }
        tap("Blue theme")
        cabin.waitUntil(2_000) { settings.theme == ThemeId.Blue && Slate.palette == blue }
        tap("Back to theme and gestures")
        tap("Back to settings")
        shows("Arrange apps")
        assertTrue(cabin.pixelsOf(listOf(blue.surface)).single() > 100_000)
    }
}

/** A unit on which another theme was chosen earlier: Cabin opens in it. */
@RunWith(AndroidJUnit4::class)
class SavedThemeTest {

    @get:Rule(order = 0)
    val values = FreshDesignValues {
        it.copy(settings = InMemorySettings(CabinSettings(loaded = true, setupDone = true, theme = ThemeId.Emerald)))
    }

    @get:Rule(order = 1)
    val cabin = cabinRule()

    @Test
    fun cabinOpensInTheSavedTheme() {
        val emerald = Themes.palette(ThemeId.Emerald)
        cabin.onNodeWithContentDescription("Open Maps").assertIsDisplayed()
        assertEquals(emerald, Slate.palette)
        val (inEmerald, inBlue) = cabin.pixelsOf(listOf(emerald.accent, Themes.palette(ThemeId.Blue).accent))
        assertTrue("only $inEmerald pixels in the accent", inEmerald > 1_000)
        assertEquals(0, inBlue)
    }
}
