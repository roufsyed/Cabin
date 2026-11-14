package com.roufsyed.cabin.bar

import org.junit.Assert.assertEquals
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
    fun `the handle sits where the grip was`() {
        // Eight buttons of 96 and a grip of 32 on a screen 800 high: the grip runs from 480 to 512,
        // and a handle 96 high centred on it from 448.
        assertEquals(448, SideBarRules.handleTop(screenHeight = 800, grip = 32f, handleHeight = 96f, buttons = 8, above = 5))
        // Without Split there are seven, four of them above the grip.
        assertEquals(406, SideBarRules.handleTop(screenHeight = 800, grip = 32f, handleHeight = 96f, buttons = 7, above = 4))
        // A smaller screen draws everything at nine tenths.
        assertEquals(403, SideBarRules.handleTop(screenHeight = 720, grip = 28.8f, handleHeight = 86.4f, buttons = 8, above = 5))
    }

    @Test
    fun `the handle never leaves the screen`() {
        assertEquals(0, SideBarRules.handleTop(screenHeight = 60, grip = 32f, handleHeight = 96f, buttons = 8, above = 5))
        assertEquals(4, SideBarRules.handleTop(screenHeight = 100, grip = 32f, handleHeight = 96f, buttons = 8, above = 8))
    }
}
