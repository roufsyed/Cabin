package com.roufsyed.cabin.system

import com.roufsyed.cabin.model.ClockUi
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.util.Locale

class ClockUiTest {

    @Test
    fun `the design's moment is formatted the design's way`() {
        val clock = clockUi(LocalDateTime.of(2026, 11, 12, 10, 10), is24Hour = true, locale = Locale.UK)
        assertEquals(ClockUi("Thursday", "10", "10", "12 November", "Thu 12 Nov"), clock)
        assertEquals("10:10", clock.time)
    }

    @Test
    fun `24 hour time keeps two digits`() {
        assertEquals("09:05", clockUi(LocalDateTime.of(2026, 1, 3, 9, 5), true, Locale.UK).time)
        assertEquals("00:00", clockUi(LocalDateTime.of(2026, 1, 3, 0, 0), true, Locale.UK).time)
        assertEquals("23:59", clockUi(LocalDateTime.of(2026, 1, 3, 23, 59), true, Locale.UK).time)
    }

    @Test
    fun `12 hour time has no leading zero and no zero hour`() {
        assertEquals("9:05", clockUi(LocalDateTime.of(2026, 1, 3, 9, 5), false, Locale.UK).time)
        assertEquals("12:00", clockUi(LocalDateTime.of(2026, 1, 3, 0, 0), false, Locale.UK).time)
        assertEquals("12:30", clockUi(LocalDateTime.of(2026, 1, 3, 12, 30), false, Locale.UK).time)
        assertEquals("11:59", clockUi(LocalDateTime.of(2026, 1, 3, 23, 59), false, Locale.UK).time)
    }
}
