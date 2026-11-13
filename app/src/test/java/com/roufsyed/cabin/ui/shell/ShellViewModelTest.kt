package com.roufsyed.cabin.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShellViewModelTest {

    private val shell = ShellViewModel()
    private val state get() = shell.state.value

    @Test
    fun `starts on Home`() {
        assertEquals(Shell(), state)
    }

    @Test
    fun `back from Home stays on Home`() {
        shell.back()
        assertEquals(Shell(), state)
    }

    @Test
    fun `back walks out one level at a time`() {
        shell.open(Screen.Settings)
        shell.arrangeApps()
        shell.pick("a")

        shell.back()
        assertEquals(Screen.Apps, state.screen)
        assertNull(state.edit)

        shell.back()
        assertEquals(Screen.Home, state.screen)
    }

    @Test
    fun `back closes an overlay before anything else`() {
        shell.open(Screen.Apps)
        shell.show(Overlay.Tray)
        shell.back()
        assertEquals(Overlay.None, state.overlay)
        assertEquals(Screen.Apps, state.screen)
    }

    @Test
    fun `picking only works while arranging`() {
        shell.open(Screen.Apps)
        shell.pick("a")
        assertNull(state.picked)

        shell.arrangeApps()
        shell.pick("a")
        assertEquals("a", state.picked)
    }

    @Test
    fun `done returns apps to the grid and sources to Settings`() {
        shell.arrangeApps()
        shell.appsPage(1)
        shell.done()
        assertEquals(Shell(screen = Screen.Apps, page = 1), state)

        shell.arrangeSources()
        shell.done()
        assertEquals(Shell(screen = Screen.Settings), state)
    }

    @Test
    fun `the Home button clears everything`() {
        shell.arrangeApps()
        shell.pick("a")
        shell.show(Overlay.Voice)
        shell.home()
        assertEquals(Shell(), state)
    }
}
