package com.roufsyed.cabin.notify

import android.app.Notification
import com.roufsyed.cabin.model.Samples
import com.roufsyed.cabin.model.StatusUi
import org.junit.Assert.assertEquals
import org.junit.Test

class TrayTest {

    @Test
    fun `a notification's category picks its line icon`() {
        assertEquals(TrayKind.Call, AndroidTray.kindOf(Notification.CATEGORY_CALL))
        assertEquals(TrayKind.Call, AndroidTray.kindOf(Notification.CATEGORY_MISSED_CALL))
        assertEquals(TrayKind.Message, AndroidTray.kindOf(Notification.CATEGORY_MESSAGE))
        assertEquals(TrayKind.Message, AndroidTray.kindOf(Notification.CATEGORY_EMAIL))
        assertEquals(TrayKind.Message, AndroidTray.kindOf(Notification.CATEGORY_SOCIAL))
        assertEquals(TrayKind.Media, AndroidTray.kindOf(Notification.CATEGORY_TRANSPORT))
        assertEquals(TrayKind.Other, AndroidTray.kindOf(Notification.CATEGORY_REMINDER))
        assertEquals(TrayKind.Other, AndroidTray.kindOf(null))
    }

    @Test
    fun `opening a notification removes it, clearing removes all`() {
        val tray = FixedTray(Samples.notifications)
        tray.open("message")
        assertEquals(listOf("call", "usb"), tray.items.value.map { it.key })
        assertEquals(listOf("message"), tray.opened)
        tray.clearAll()
        assertEquals(emptyList<TrayItem>(), tray.items.value)
    }

    @Test
    fun `the tray has room for six rows on an 800-high screen`() {
        // 76 heading + 6 x 104 rows + 97 buttons = 797.
        assertEquals(6, TRAY_ROWS)
        assertEquals(797, 76 + TRAY_ROWS * 104 + 97)
    }

    @Test
    fun `the status marks are described in words for a screen reader`() {
        assertEquals("Phone connected, Wi-Fi on, GPS ready", StatusUi(phone = true, wifi = true, gps = true).description)
        assertEquals("Phone not connected, Wi-Fi off, GPS off", StatusUi(phone = false, wifi = false, gps = false).description)
    }
}
