package com.roufsyed.cabin.apps

import com.roufsyed.cabin.model.AppEntry
import com.roufsyed.cabin.model.AppTile
import com.roufsyed.cabin.model.Glyph
import org.junit.Assert.assertEquals
import org.junit.Test

class SplitChoicesTest {

    private fun app(id: String, label: String) = AppTile(AppEntry(id, label, Glyph.Letter), hidden = false)

    private val cabin = "com.roufsyed.cabin/.MainActivity"
    private val grid = listOf(
        app("com.maps/.Main", "Maps"),
        app(cabin, "Cabin"),
        app("com.player/.Main", "Player"),
        app("com.player/.Settings", "Player settings"),
        app("com.radio/.Main", "Radio"),
    )

    private fun labels(onScreen: List<String>) = SplitChoices.of(grid, cabin, onScreen).map { it.label }

    @Test
    fun `the chooser offers the grid's apps in its order, without Cabin`() {
        assertEquals(listOf("Maps", "Player", "Player settings", "Radio"), labels(emptyList()))
    }

    @Test
    fun `the app in front is left out, with everything else of its package`() {
        assertEquals(listOf("Maps", "Radio"), labels(listOf("com.player")))
    }

    @Test
    fun `with the screen already split both apps on it are left out`() {
        assertEquals(listOf("Radio"), labels(listOf("com.maps", "com.player")))
        // A package that is on screen but not in the grid changes nothing.
        assertEquals(listOf("Maps", "Player", "Player settings", "Radio"), labels(listOf("com.unknown")))
    }

    @Test
    fun `half a screen less the side bar takes three tiles across`() {
        // Half of 1280 beside the divider, less 16 each side and the bar's 66: about 530 wide. The
        // emulator leaves about 620 of height under the chooser's heading.
        assertEquals(SplitChoices.Grid(columns = 3, rows = 3), SplitChoices.grid(530f, 620f))
        // Without the bar there is still not room for a fourth.
        assertEquals(3, SplitChoices.grid(596f, 620f).columns)
    }

    @Test
    fun `a screen 800 high takes four rows under the heading`() {
        // 800 less 16 above and below, the 96 of the heading and the 16 under it.
        assertEquals(4, SplitChoices.grid(530f, 800f - 32f - 96f - 16f).rows)
        // A status bar along the top leaves three.
        assertEquals(3, SplitChoices.grid(530f, 776f - 32f - 96f - 16f).rows)
    }

    @Test
    fun `a whole screen gets the Apps grid's five across and no more`() {
        assertEquals(5, SplitChoices.grid(1240f, 600f).columns)
        assertEquals(5, SplitChoices.grid(2400f, 600f).columns)
    }

    @Test
    fun `however little room there is, one tile is shown`() {
        assertEquals(SplitChoices.Grid(columns = 1, rows = 1), SplitChoices.grid(90f, 40f))
    }

    @Test
    fun `the apps are dealt into pages of as many as fit`() {
        val three = SplitChoices.Grid(columns = 3, rows = 3)
        assertEquals(9, three.perPage)
        assertEquals(1, three.pages(0))
        assertEquals(1, three.pages(9))
        assertEquals(2, three.pages(10))
        assertEquals(3, three.pages(19))
        val apps = (1..19).toList()
        assertEquals((1..9).toList(), three.page(apps, 0))
        assertEquals((10..18).toList(), three.page(apps, 1))
        assertEquals(listOf(19), three.page(apps, 2))
    }
}
