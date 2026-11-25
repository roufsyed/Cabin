package com.roufsyed.cabin.system

import com.roufsyed.cabin.apps.SplitWay
import com.roufsyed.cabin.model.Samples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitRulesTest {

    private val old = Samples.unit.copy(
        release = "10", sdk = 29, patch = "2020-09-05", build = "QP1A.190711.020", maker = "Maker", model = "Unit 7",
        board = "board7", hardware = "soc7", memoryBytes = 3_900_000_000L, split = SplitWay.Toggle,
    )

    private fun line(facts: UnitFacts, label: String) = UnitRules.report(facts).single { it.label == label }.value

    @Test
    fun `the page has a row for each thing, and fits`() {
        val report = UnitRules.report(old)
        assertEquals(listOf("Android", "Build", "Device", "Screen", "Memory", "Split screen", "Accessibility"), report.map { it.label })
        assertTrue(report.sumOf { it.lines } <= UnitRules.PAGE_LINES)
    }

    @Test
    fun `Android is given as it really is, with the level apps go by`() {
        assertEquals("10 (API 29), security patch 2020-09-05", line(old, "Android"))
        assertEquals("13 (API 33), no security patch level given", line(Samples.unit.copy(patch = ""), "Android"))
        assertEquals("QP1A.190711.020", line(old, "Build"))
        assertEquals("Maker Unit 7, board board7, hardware soc7", line(old, "Device"))
        assertEquals("Design Unit", line(Samples.unit, "Device"))
        assertEquals("1280 x 800 at 160 dpi", line(old, "Screen"))
    }

    @Test
    fun `memory is in gigabytes, and says when Android counts the unit as short of it`() {
        assertEquals("3.6 GB", line(old, "Memory"))
        assertEquals("4.0 GB", line(Samples.unit, "Memory"))
        assertEquals("0.9 GB, which Android counts as a low-memory device", line(old.copy(memoryBytes = 1_000_000_000L, lowMemory = true), "Memory"))
    }

    @Test
    fun `split screen says which way Cabin goes about it and what the firmware's switches read`() {
        assertEquals(
            "Android's own: Cabin opens one app beside another (multi-window on, split screen on)",
            line(Samples.unit, "Split screen"),
        )
        assertEquals(
            "Android's older kind: Cabin asks for it through Accessibility (multi-window on, split screen on)",
            line(old, "Split screen"),
        )
        assertEquals(
            "None that Cabin can use (multi-window on, split screen off)",
            line(old.copy(split = SplitWay.None, splitScreen = false), "Split screen"),
        )
        assertEquals(
            "Android's own: Cabin opens one app beside another (multi-window not said, split screen not said)",
            line(Samples.unit.copy(multiWindow = null, splitScreen = null), "Split screen"),
        )
    }

    @Test
    fun `accessibility says whether Cabin is allowed and what Android then offers it`() {
        assertEquals("Off: Cabin is not allowed under Accessibility just now", line(old, "Accessibility"))
        assertEquals("On. This Android does not say what it offers", line(old.copy(serviceOn = true), "Accessibility"))
        assertEquals(
            "On. Android offers back, recent apps, split screen, screenshot, power menu",
            line(old.copy(serviceOn = true, offered = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9)), "Accessibility"),
        )
        assertEquals(
            "On. Android offers back, recent apps, power menu; not split screen, screenshot",
            line(old.copy(serviceOn = true, offered = listOf(1, 2, 3, 6)), "Accessibility"),
        )
    }
}
