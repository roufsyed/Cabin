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
}
