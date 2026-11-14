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

    @Test
    fun `swiping left goes from Home through the pages of apps and stops at the last`() {
        shell.swipeLeft(pages = 2)
        assertEquals(Shell(screen = Screen.Apps, page = 0), state)
        shell.swipeLeft(pages = 2)
        assertEquals(1, state.page)
        shell.swipeLeft(pages = 2)
        assertEquals(1, state.page)
    }

    @Test
    fun `swiping right goes back a page, then Home, and from any other screen straight Home`() {
        shell.appsPage(1)
        shell.swipeRight()
        assertEquals(Shell(screen = Screen.Apps, page = 0), state)
        shell.swipeRight()
        assertEquals(Shell(), state)
        shell.swipeRight()
        assertEquals(Shell(), state)

        shell.open(Screen.Music)
        shell.swipeRight()
        assertEquals(Shell(), state)
    }

    @Test
    fun `a swipe while arranging only changes page`() {
        shell.arrangeApps()
        shell.pick("a")
        shell.swipeLeft(pages = 3)
        assertEquals(Shell(screen = Screen.Apps, page = 1, edit = EditKind.Apps, picked = "a"), state)
        shell.swipeRight()
        shell.swipeRight()
        // Still arranging, on the first page: a stray swipe must not throw the work away.
        assertEquals(Shell(screen = Screen.Apps, page = 0, edit = EditKind.Apps, picked = "a"), state)

        shell.arrangeSources()
        shell.swipeRight()
        assertEquals(EditKind.Sources, state.edit)
    }

    @Test
    fun `swipes do nothing under an overlay`() {
        shell.show(Overlay.Tray)
        shell.swipeLeft(pages = 2)
        assertEquals(Shell(overlay = Overlay.Tray), state)
    }

    @Test
    fun `the app chooser returns to where it was opened from`() {
        shell.arrangeSources()
        shell.pick("Radio")
        shell.pickApp(com.roufsyed.cabin.model.Slot.Radio)
        assertEquals(Screen.Pick, state.screen)
        shell.pickDone()
        assertEquals(Shell(screen = Screen.Sources, edit = EditKind.Sources, picked = "Radio"), state)

        shell.home()
        shell.pickApp(com.roufsyed.cabin.model.Slot.Phone)
        shell.back()
        assertEquals(Shell(), state)
    }

    @Test
    fun `back from the keys screen, from Gestures and from Setup returns to Settings`() {
        for (screen in listOf(Screen.Keys, Screen.Gestures, Screen.Setup)) {
            shell.open(screen)
            shell.back()
            assertEquals(Shell(screen = Screen.Settings), state)
        }
    }

    @Test
    fun `choosing an app from Setup returns to Setup`() {
        shell.open(Screen.Setup)
        shell.pickApp(com.roufsyed.cabin.model.Slot.Link)
        shell.pickDone()
        assertEquals(Shell(screen = Screen.Setup), state)
    }

    @Test
    fun `Setup has two pages, turned by its button or a swipe`() {
        shell.open(Screen.Setup)
        shell.setupPage(1)
        assertEquals(Shell(screen = Screen.Setup, page = 1), state)
        shell.setupPage(7)
        assertEquals(1, state.page)
        shell.setupPage(0)
        assertEquals(0, state.page)

        shell.swipeLeft(pages = SETUP_PAGES)
        assertEquals(1, state.page)
        shell.swipeLeft(pages = SETUP_PAGES)
        assertEquals(1, state.page)
        shell.swipeRight()
        assertEquals(Shell(screen = Screen.Setup, page = 0), state)
        // On its first page a swipe right changes nothing: Setup is left with Done.
        shell.swipeRight()
        assertEquals(Shell(screen = Screen.Setup, page = 0), state)
    }

    @Test
    fun `turning Setup's page only works on Setup`() {
        shell.open(Screen.Settings)
        shell.setupPage(1)
        assertEquals(Shell(screen = Screen.Settings), state)
    }

    @Test
    fun `back on Setup's second page goes to its first, then to Settings`() {
        shell.open(Screen.Setup)
        shell.setupPage(1)
        shell.back()
        assertEquals(Shell(screen = Screen.Setup, page = 0), state)
        shell.back()
        assertEquals(Shell(screen = Screen.Settings), state)
    }

    @Test
    fun `choosing an app from Setup's second page returns to that page`() {
        shell.open(Screen.Setup)
        shell.setupPage(1)
        shell.pickApp(com.roufsyed.cabin.model.Slot.Maps)
        assertEquals(Screen.Pick, state.screen)
        // The chooser has pages of its own, which must not be mistaken for Setup's.
        shell.swipeLeft(pages = 3)
        shell.swipeLeft(pages = 3)
        shell.pickDone()
        assertEquals(Shell(screen = Screen.Setup, page = 1), state)

        shell.pickApp(com.roufsyed.cabin.model.Slot.Music)
        shell.back()
        assertEquals(Shell(screen = Screen.Setup, page = 1), state)
    }

    @Test
    fun `holding an app opens the split chooser, and leaving it returns to the same page of apps`() {
        shell.appsPage(1)
        shell.splitWith("maps")
        assertEquals(Shell(screen = Screen.Split, splitFirst = "maps", pickFromPage = 1), state)

        shell.splitDone()
        assertEquals(Shell(screen = Screen.Apps, page = 1), state)

        shell.splitWith("maps")
        shell.back()
        assertEquals(Shell(screen = Screen.Apps, page = 1), state)
    }

    @Test
    fun `the split chooser has pages of its own and a swipe never leaves it`() {
        shell.open(Screen.Apps)
        shell.splitWith("maps")
        shell.swipeLeft(pages = 2)
        assertEquals(1, state.page)
        shell.swipeLeft(pages = 2)
        assertEquals(1, state.page)
        shell.swipeRight()
        shell.swipeRight()
        assertEquals(Shell(screen = Screen.Split, splitFirst = "maps"), state)
    }

    @Test
    fun `the split chooser only opens from the plain Apps grid`() {
        shell.splitWith("maps")
        assertEquals(Shell(), state)

        shell.arrangeApps()
        shell.splitWith("maps")
        assertEquals(Shell(screen = Screen.Apps, edit = EditKind.Apps), state)

        shell.open(Screen.Apps)
        shell.show(Overlay.Tray)
        shell.splitWith("maps")
        assertEquals(Shell(screen = Screen.Apps, overlay = Overlay.Tray), state)

        // Leaving it when it is not open changes nothing.
        shell.closeOverlay()
        shell.splitDone()
        assertEquals(Shell(screen = Screen.Apps), state)
    }

    @Test
    fun `a swipe right leaves Gestures for Home like any other settings screen`() {
        shell.open(Screen.Gestures)
        shell.swipeRight()
        assertEquals(Shell(), state)
    }
}
