package com.roufsyed.cabin.apps

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitScreenTest {

    @Test
    fun `from Android 12L an app is opened beside another, before that Android is asked to split`() {
        assertEquals(SplitWay.Adjacent, SplitScreen.way(sdk = 33, multiWindow = true, splitScreen = true))
        assertEquals(SplitWay.Adjacent, SplitScreen.way(sdk = 32, multiWindow = true, splitScreen = true))
        assertEquals(SplitWay.Toggle, SplitScreen.way(sdk = 31, multiWindow = true, splitScreen = true))
        assertEquals(SplitWay.Toggle, SplitScreen.way(sdk = 29, multiWindow = true, splitScreen = true))
    }

    @Test
    fun `a firmware with either switch off has no split screen, whatever its Android`() {
        assertEquals(SplitWay.None, SplitScreen.way(sdk = 33, multiWindow = false, splitScreen = true))
        assertEquals(SplitWay.None, SplitScreen.way(sdk = 33, multiWindow = true, splitScreen = false))
        assertEquals(SplitWay.None, SplitScreen.way(sdk = 29, multiWindow = true, splitScreen = false))
    }

    @Test
    fun `asking Android to split takes the side bar's service, opening beside does not`() {
        assertTrue(SplitScreen.ready(SplitWay.Adjacent, serviceOn = false))
        assertTrue(SplitScreen.ready(SplitWay.Toggle, serviceOn = true))
        assertFalse(SplitScreen.ready(SplitWay.Toggle, serviceOn = false))
        assertFalse(SplitScreen.ready(SplitWay.None, serviceOn = true))
    }

    private class Unit(
        var split: Boolean = false,
        val docks: Boolean = true,
        val settles: Boolean = true,
        val leftShows: Boolean = true,
    ) {
        val did = mutableListOf<String>()
        val waits = mutableListOf<Long>()
        private var full = false

        fun steps() = ToggleSteps(
            split = { split },
            settled = { split && !full },
            toggle = {
                did += "toggle"
                if (split) {
                    split = false
                } else if (docks) {
                    split = true
                    full = !settles
                }
                true
            },
            within = { wait, condition ->
                waits += wait
                condition()
            },
            leftInFront = { leftShows },
            startLeft = { did.add("left") },
            startRight = { did.add("right") },
        )
    }

    @Test
    fun `a pair is the left app, the split, then the right app`() = runTest {
        val unit = Unit()
        assertTrue(SplitScreen.toggledPair(unit.steps()))
        assertEquals(listOf("left", "toggle", "right"), unit.did)
        assertTrue(unit.split)
    }

    @Test
    fun `the left app is given longer to open than Android is to split`() = runTest {
        val unit = Unit()
        SplitScreen.toggledPair(unit.steps())
        assertEquals(
            listOf(SplitScreen.OPEN_WAIT_MS, SplitScreen.SHOW_WAIT_MS, SplitScreen.SETTLE_WAIT_MS, SplitScreen.HOLD_MS),
            unit.waits,
        )
        assertTrue(SplitScreen.OPEN_WAIT_MS > SplitScreen.SETTLE_WAIT_MS)
    }

    @Test
    fun `an older split is ended first, and the left app brought forward again`() = runTest {
        val unit = Unit(split = true)
        assertTrue(SplitScreen.toggledPair(unit.steps()))
        assertEquals(listOf("left", "toggle", "left", "toggle", "right"), unit.did)
        assertTrue(unit.split)
        assertTrue(SplitScreen.REST_MS in unit.waits)
    }

    @Test
    fun `a unit that docks the app and never halves the screen is put back as it was`() = runTest {
        val unit = Unit(settles = false)
        assertFalse(SplitScreen.toggledPair(unit.steps()))
        assertEquals(listOf("left", "toggle", "toggle"), unit.did)
        assertFalse(unit.split)
    }

    @Test
    fun `a unit that does nothing when asked is asked once, and the right app is not opened`() = runTest {
        val unit = Unit(docks = false)
        assertFalse(SplitScreen.toggledPair(unit.steps()))
        assertEquals(listOf("left", "toggle"), unit.did)
    }

    @Test
    fun `a left app that never shows is not split, and the right one is not opened`() = runTest {
        val unit = Unit(leftShows = false)
        assertFalse(SplitScreen.toggledPair(unit.steps()))
        assertEquals(listOf("left"), unit.did)
    }

    @Test
    fun `Split over an app docks it and then offers a second, and pressed again ends the split`() = runTest {
        val unit = Unit()
        var choosers = 0
        assertTrue(SplitScreen.toggledBeside(unit.steps()) { choosers++; true })
        assertEquals(listOf("toggle"), unit.did)
        assertEquals(1, choosers)

        assertTrue(SplitScreen.toggledBeside(unit.steps()) { choosers++; true })
        assertEquals(listOf("toggle", "toggle"), unit.did)
        assertFalse(unit.split)
        assertEquals(1, choosers)
    }

    @Test
    fun `Split over an app that the unit will not halve offers nothing`() = runTest {
        val unit = Unit(settles = false)
        var choosers = 0
        assertFalse(SplitScreen.toggledBeside(unit.steps()) { choosers++; true })
        assertEquals(listOf("toggle", "toggle"), unit.did)
        assertEquals(0, choosers)
    }
}
