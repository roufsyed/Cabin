package com.roufsyed.cabin.ui.apps

import com.roufsyed.cabin.model.AppEntry
import com.roufsyed.cabin.model.AppTile
import com.roufsyed.cabin.model.Glyph
import org.junit.Assert.assertEquals
import org.junit.Test

class AppsUiTest {

    private fun tiles(count: Int, hidden: Set<Int> = emptySet()) = AppsUi(
        (0 until count).map { AppTile(AppEntry("app$it", "App $it", Glyph.Letter), hidden = it in hidden) },
    )

    @Test
    fun `fifteen tiles to a page`() {
        val ui = tiles(20)
        assertEquals(2, ui.pageCount(editing = false))
        assertEquals(15, ui.page(0, editing = false).size)
        assertEquals(5, ui.page(1, editing = false).size)
        assertEquals("app15", ui.page(1, editing = false).first().app.id)
    }

    @Test
    fun `exactly fifteen is one page and none is still one page`() {
        assertEquals(1, tiles(15).pageCount(editing = false))
        assertEquals(1, tiles(0).pageCount(editing = false))
        assertEquals(2, tiles(16).pageCount(editing = false))
    }

    @Test
    fun `hidden tiles leave the grid but stay while arranging`() {
        val ui = tiles(16, hidden = setOf(0, 3))
        assertEquals(1, ui.pageCount(editing = false))
        assertEquals(listOf("app1", "app2", "app4"), ui.page(0, editing = false).take(3).map { it.app.id })
        assertEquals(2, ui.pageCount(editing = true))
        assertEquals("app0", ui.page(0, editing = true).first().app.id)
    }

    @Test
    fun `a tile's page while arranging comes from its place in the full order`() {
        val ui = tiles(40)
        assertEquals(0, ui.pageOf("app14"))
        assertEquals(1, ui.pageOf("app15"))
        assertEquals(2, ui.pageOf("app39"))
    }
}
