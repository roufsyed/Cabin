package com.roufsyed.cabin.system

import com.roufsyed.cabin.model.StatusUi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the tray's switches and its Clear memory go by, apart from Android. */
class SwitchesAndMemoryTest {

    @Test
    fun `Android's hotspot states read as on, off, or not settled yet`() {
        assertEquals(true, HotspotRules.on(13))
        assertEquals(false, HotspotRules.on(11))
        // Failed to start: it is off.
        assertEquals(false, HotspotRules.on(14))
        // Going down, coming up, and a number Android has not been seen to send.
        assertNull(HotspotRules.on(10))
        assertNull(HotspotRules.on(12))
        assertNull(HotspotRules.on(-1))
    }

    @Test
    fun `closing other apps is allowed up to the security patches of May 2023, and never on Android 14`() {
        // Android 13 as first released, and its last level before the change.
        assertTrue(MemoryRules.allowed(sdk = 33, patch = "2022-08-05"))
        assertTrue(MemoryRules.allowed(sdk = 33, patch = "2023-05-05"))
        // From the June 2023 level on, Android 11 to 13 ignore the request.
        assertFalse(MemoryRules.allowed(sdk = 33, patch = "2023-06-01"))
        assertFalse(MemoryRules.allowed(sdk = 33, patch = "2023-06-05"))
        assertFalse(MemoryRules.allowed(sdk = 33, patch = "2024-03-01"))
        assertFalse(MemoryRules.allowed(sdk = 30, patch = "2023-08-01"))
        // Android 10 had stopped getting patches by then.
        assertTrue(MemoryRules.allowed(sdk = 29, patch = "2023-02-05"))
        // Android 14 and later, whatever the level.
        assertFalse(MemoryRules.allowed(sdk = 34, patch = "2023-01-01"))
        assertFalse(MemoryRules.allowed(sdk = 35, patch = ""))
        // A unit that reports no level, or nonsense, is given the benefit of the doubt.
        assertTrue(MemoryRules.allowed(sdk = 33, patch = ""))
        assertTrue(MemoryRules.allowed(sdk = 33, patch = "unknown"))
    }

    @Test
    fun `free memory is put in gigabytes with one decimal, and in megabytes below one`() {
        val mb = 1024L * 1024L
        assertEquals("2.4 GB free", MemoryRules.label(2_576_980_378L))
        assertEquals("1.0 GB free", MemoryRules.label(1024 * mb))
        assertEquals("11.5 GB free", MemoryRules.label(11_776 * mb))
        assertEquals("850 MB free", MemoryRules.label(850 * mb + 300_000))
        assertEquals("0 MB free", MemoryRules.label(0))
        assertEquals("0 MB free", MemoryRules.label(-5))
    }

    @Test
    fun `the stand-in memory closes nothing and remembers what it was told to keep`() = runTest {
        val memory = FixedMemory(free = 3L * 1024 * 1024 * 1024)
        assertEquals("3.0 GB free", MemoryRules.label(memory.clear(setOf("org.example.music"))))
        assertEquals(listOf(setOf("org.example.music")), memory.kept)
    }

    @Test
    fun `switches that are allowed change the marks at once`() {
        val status = FixedStatus(StatusUi(phone = false, wifi = true, gps = true), bluetooth = true)
        val switches = FixedSwitches(status)

        assertTrue(switches.setBluetooth(false))
        assertFalse(status.bluetoothOn.value)
        assertTrue(switches.setWifi(false))
        assertFalse(status.status.value.wifi)
        assertTrue(switches.setWifi(true))
        assertTrue(status.status.value.wifi)
        assertEquals(listOf("bluetooth off", "wifi off", "wifi on"), switches.asked)
    }

    @Test
    fun `switches that are refused change nothing and say so`() {
        val status = FixedStatus(StatusUi(phone = false, wifi = true, gps = true), bluetooth = true)
        val switches = FixedSwitches(status, allowed = false)

        assertFalse(switches.setBluetooth(false))
        assertFalse(switches.setWifi(false))
        assertTrue(status.bluetoothOn.value)
        assertTrue(status.status.value.wifi)
        assertEquals(listOf("bluetooth off", "wifi off"), switches.asked)
    }

    @Test
    fun `the hotspot is unknown until Android has said`() {
        assertNull(FixedStatus(StatusUi(phone = false, wifi = false, gps = false), hotspot = null).hotspotOn.value)
    }
}
