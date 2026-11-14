package com.roufsyed.cabin.model

import com.roufsyed.cabin.model.HomePanel.Clock
import com.roufsyed.cabin.model.HomePanel.Navigation
import com.roufsyed.cabin.model.HomePanel.NowPlaying
import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutRulesTest {

    @Test
    fun `the design's order is clock, navigation, now playing`() {
        assertEquals(listOf(Clock, Navigation, NowPlaying), LayoutRules.DESIGN_ORDER)
    }

    @Test
    fun `a move takes a panel one place to the right and the other two keep their order`() {
        assertEquals(listOf(Navigation, Clock, NowPlaying), LayoutRules.moved(LayoutRules.DESIGN_ORDER, Clock))
        assertEquals(listOf(Clock, NowPlaying, Navigation), LayoutRules.moved(LayoutRules.DESIGN_ORDER, Navigation))
    }

    @Test
    fun `from the right-hand place a panel goes round to the left`() {
        assertEquals(listOf(NowPlaying, Clock, Navigation), LayoutRules.moved(LayoutRules.DESIGN_ORDER, NowPlaying))
    }

    @Test
    fun `three moves of one panel put everything back`() {
        for (panel in HomePanel.entries) {
            var order = LayoutRules.DESIGN_ORDER
            repeat(3) { order = LayoutRules.moved(order, panel) }
            assertEquals(LayoutRules.DESIGN_ORDER, order)
        }
    }

    @Test
    fun `each panel's place has a name`() {
        val order = listOf(NowPlaying, Clock, Navigation)
        assertEquals("Left", LayoutRules.place(order, NowPlaying))
        assertEquals("Middle", LayoutRules.place(order, Clock))
        assertEquals("Right", LayoutRules.place(order, Navigation))
    }

    @Test
    fun `a saved order that does not name each panel once is not used`() {
        assertEquals(listOf(NowPlaying, Clock, Navigation), LayoutRules.valid(listOf(NowPlaying, Clock, Navigation)))
        assertEquals(LayoutRules.DESIGN_ORDER, LayoutRules.valid(listOf(Clock, Clock, Navigation)))
        assertEquals(LayoutRules.DESIGN_ORDER, LayoutRules.valid(listOf(Clock, Navigation)))
        assertEquals(LayoutRules.DESIGN_ORDER, LayoutRules.valid(emptyList()))
    }
}
