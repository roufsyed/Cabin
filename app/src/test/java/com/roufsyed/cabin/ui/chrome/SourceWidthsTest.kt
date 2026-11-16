package com.roufsyed.cabin.ui.chrome

import com.roufsyed.cabin.model.SourceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceWidthsTest {

    private fun own(vararg sources: SourceId) = sources.map { it.baseWidth }

    @Test
    fun `the four default sources share the row exactly as the design does`() {
        val widths = sourceWidths(own(SourceId.Radio, SourceId.Bluetooth, SourceId.Usb, SourceId.Auto))
        assertEquals(listOf(138, 188, 120, 234), widths)
    }

    @Test
    fun `any set of sources fills the row with nothing left over`() {
        val all = SourceId.entries
        for (count in 1..all.size) {
            assertEquals(680, sourceWidths(all.take(count).map { it.baseWidth }).sum())
        }
    }

    @Test
    fun `a single source takes the whole row`() {
        assertEquals(listOf(680), sourceWidths(own(SourceId.Usb)))
    }

    @Test
    fun `no sources means no widths`() {
        assertEquals(emptyList<Int>(), sourceWidths(emptyList()))
    }

    @Test
    fun `the name of an app is given room like any other, and the row is still full`() {
        val widths = sourceWidths(listOf(68, 150, 50, 164))
        assertEquals(680, widths.sum())
        assertEquals(listOf(130, 212, 112, 226), widths)
    }

    @Test
    fun `names too long for the row are cut to one width, and the short ones are left whole`() {
        val names = listOf(300, 300, 50, 164)
        assertEquals(listOf(177, 177, 50, 164), fitNames(names, 680 - 4 * 2 * SOURCE_NAME_MARGIN))
        val widths = sourceWidths(names)
        assertEquals(680, widths.sum())
        assertEquals(listOf(205, 205, 78, 192), widths)
    }

    @Test
    fun `five long names share the row equally`() {
        val widths = sourceWidths(List(5) { 400 })
        assertEquals(680, widths.sum())
        assertTrue(widths.toString(), widths.all { it == 136 })
        assertEquals(List(5) { 108 }, fitNames(List(5) { 400 }, 680 - 5 * 2 * SOURCE_NAME_MARGIN))
    }

    @Test
    fun `names that fit are never cut`() {
        assertEquals(listOf(68, 118, 50, 164, 52), fitNames(SourceId.entries.map { it.baseWidth }, 680 - 5 * 2 * SOURCE_NAME_MARGIN))
        assertEquals(listOf(5, 45), fitNames(listOf(5, 100), 50))
        assertEquals(listOf(7, 7), fitNames(listOf(10, 10), 15))
    }
}
