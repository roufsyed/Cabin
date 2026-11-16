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
    fun `a bar that was allowed and is found switched off at a start went while Cabin was not running`() {
        assertEquals(true, SideBarRules.wentMissing(wanted = true, granted = true, enabled = false))
        assertEquals(false, SideBarRules.wentMissing(wanted = true, granted = true, enabled = true))
        assertEquals(false, SideBarRules.wentMissing(wanted = true, granted = false, enabled = false))
        assertEquals(false, SideBarRules.wentMissing(wanted = false, granted = true, enabled = false))
    }

    @Test
    fun `a Cabin that may switches its bar back on and says nothing`() {
        val access = FixedSideBarAccess(isEnabled = false, canRestore = true, stopped = 1_000L)
        assertNull(SideBarRules.recover(wanted = true, granted = true, access))
        assertEquals(1, access.restored)
        assertEquals(true, access.enabled())
    }

    @Test
    fun `a Cabin that may not says what happened, with when the unit stopped it if Android knows`() {
        val stopped = FixedSideBarAccess(isEnabled = false, canRestore = false, stopped = 1_000L)
        assertEquals(SideBarLoss(stoppedAtMs = 1_000L), SideBarRules.recover(wanted = true, granted = true, stopped))
        assertEquals(false, stopped.enabled())
        val cleared = FixedSideBarAccess(isEnabled = false, canRestore = false, stopped = null)
        assertEquals(SideBarLoss(stoppedAtMs = null), SideBarRules.recover(wanted = true, granted = true, cleared))
    }

    @Test
    fun `a bar that is switched on, or is not wanted, is never touched`() {
        val on = FixedSideBarAccess(isEnabled = true, canRestore = true)
        assertNull(SideBarRules.recover(wanted = true, granted = true, on))
        val unwanted = FixedSideBarAccess(isEnabled = false, canRestore = true)
        assertNull(SideBarRules.recover(wanted = false, granted = true, unwanted))
        assertEquals(0, on.restored + unwanted.restored)
        assertEquals(false, unwanted.enabled())
    }

    @Test
    fun `a Cabin with the means keeps a wanted bar on, allowed before or not`() {
        val never = FixedSideBarAccess(isEnabled = false, canRestore = true)
        assertNull(SideBarRules.recover(wanted = true, granted = false, never))
        assertEquals(1, never.restored)
    }

    @Test
    fun `a Cabin without the means says nothing about a bar that was never allowed or was switched off by hand`() {
        val never = FixedSideBarAccess(isEnabled = false, canRestore = false, stopped = 1_000L)
        assertNull(SideBarRules.recover(wanted = true, granted = false, never))
        assertEquals(false, never.enabled())
    }

    @Test
    fun `Setup says in one line why the bar is off again`() {
        assertEquals("Side bar off: the unit stopped Cabin at 21:05", SideBarRules.lossText("21:05"))
        assertEquals("Side bar off: the unit stopped Cabin at 15 Nov, 21:05", SideBarRules.lossText("15 Nov, 21:05"))
        assertEquals("Side bar off: the unit switched it off, not Cabin", SideBarRules.lossText(null))
        assertEquals(true, SideBarRules.lossText("15 Nov, 21:05").length <= 60)
    }

    @Test
    fun `the four waits are offered in the order asked for, each with a line that says it`() {
        assertEquals(listOf("Immediate", "5 sec", "10 sec", "Never"), TuckAfter.entries.map { it.label })
        assertEquals(TuckAfter.entries.size, TuckAfter.entries.map { it.hint }.toSet().size)
    }
}
