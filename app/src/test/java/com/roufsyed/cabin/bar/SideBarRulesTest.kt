package com.roufsyed.cabin.bar

import com.roufsyed.cabin.model.TuckAfter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SideBarRulesTest {

    @Test
    fun `the bar is up over another app when it is wanted`() {
        assertEquals(true, SideBarRules.shown(wanted = true, cabinShowing = false, keyboardUp = false))
    }

    @Test
    fun `it steps aside while Cabin's own screen is showing`() {
        assertEquals(false, SideBarRules.shown(wanted = true, cabinShowing = true, keyboardUp = false))
    }

    @Test
    fun `it gets out of the way of a keyboard`() {
        assertEquals(false, SideBarRules.shown(wanted = true, cabinShowing = false, keyboardUp = true))
    }

    @Test
    fun `switched off in Setup it is never up`() {
        for (cabinShowing in listOf(false, true)) {
            for (keyboardUp in listOf(false, true)) {
                assertEquals(false, SideBarRules.shown(wanted = false, cabinShowing = cabinShowing, keyboardUp = keyboardUp))
            }
        }
    }

    private fun view(
        wanted: Boolean = true, cabinShowing: Boolean = false, keyboardUp: Boolean = false,
        handle: Boolean = true, tucked: Boolean = false,
    ) = SideBarRules.view(wanted, cabinShowing, keyboardUp, handle, tucked)

    @Test
    fun `over another app the whole bar is drawn until it is tucked away`() {
        assertEquals(BarView.Bar, view())
        assertEquals(BarView.Handle, view(tucked = true))
    }

    @Test
    fun `with the handle switched off the bar is always whole`() {
        assertEquals(BarView.Bar, view(handle = false))
        assertEquals(BarView.Bar, view(handle = false, tucked = true))
    }

    @Test
    fun `tucked away or not, nothing is drawn over Cabin, under a keyboard or with the bar switched off`() {
        for (tucked in listOf(false, true)) {
            assertEquals(BarView.None, view(cabinShowing = true, tucked = tucked))
            assertEquals(BarView.None, view(keyboardUp = true, tucked = tucked))
            assertEquals(BarView.None, view(wanted = false, tucked = tucked))
        }
    }

    @Test
    fun `under Immediate an app starts with the bar tucked away, and under nothing else`() {
        assertEquals(true, SideBarRules.startsTucked(handle = true, after = TuckAfter.Immediate))
        for (after in TuckAfter.entries - TuckAfter.Immediate) {
            assertEquals(false, SideBarRules.startsTucked(handle = true, after = after))
        }
    }

    @Test
    fun `left untouched the bar tucks itself away after the time chosen`() {
        assertEquals(5_000L, SideBarRules.stayMs(handle = true, after = TuckAfter.FiveSeconds))
        assertEquals(10_000L, SideBarRules.stayMs(handle = true, after = TuckAfter.TenSeconds))
        // Brought out by its handle under Immediate, it stays as long as under the shortest wait.
        assertEquals(5_000L, SideBarRules.stayMs(handle = true, after = TuckAfter.Immediate))
    }

    @Test
    fun `under Never it stays until its grip is tapped`() {
        assertNull(SideBarRules.stayMs(handle = true, after = TuckAfter.Never))
    }

    @Test
    fun `without the handle the bar cannot be brought back, so it never tucks itself away`() {
        for (after in TuckAfter.entries) {
            assertEquals(false, SideBarRules.startsTucked(handle = false, after = after))
            assertNull(SideBarRules.stayMs(handle = false, after = after))
        }
    }

    @Test
    fun `a finger that comes and goes unseen still counts as a change`() {
        val state = SideBarState()
        val before = state.touch.value
        state.touched(true)
        assertEquals(true, state.touch.value.held)
        state.touched(false)
        // Not held, as before, and yet not equal to what it was: whoever waits on it is told.
        assertEquals(false, state.touch.value.held)
        assertNotEquals(before, state.touch.value)
    }

    @Test
    fun `the four waits are offered in the order asked for, each with a line that says it`() {
        assertEquals(listOf("Immediate", "5 sec", "10 sec", "Never"), TuckAfter.entries.map { it.label })
        assertEquals(TuckAfter.entries.size, TuckAfter.entries.map { it.hint }.toSet().size)
    }
}
