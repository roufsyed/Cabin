package com.roufsyed.cabin.ui.chrome

import com.roufsyed.cabin.model.SourceId
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceWidthsTest {

    @Test
    fun `the four default sources share the row exactly as the design does`() {
        val widths = sourceWidths(listOf(SourceId.Radio, SourceId.Bluetooth, SourceId.Usb, SourceId.Auto))
        assertEquals(listOf(138, 188, 120, 234), widths)
    }

    @Test
    fun `any set of sources fills the row with nothing left over`() {
        val all = SourceId.entries
        for (count in 1..all.size) {
            assertEquals(680, sourceWidths(all.take(count)).sum())
        }
    }

    @Test
    fun `a single source takes the whole row`() {
        assertEquals(listOf(680), sourceWidths(listOf(SourceId.Usb)))
    }

    @Test
    fun `no sources means no widths`() {
        assertEquals(emptyList<Int>(), sourceWidths(emptyList()))
    }
}
