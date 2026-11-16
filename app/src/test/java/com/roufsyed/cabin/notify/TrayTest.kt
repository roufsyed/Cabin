package com.roufsyed.cabin.notify

import android.app.Notification
import com.roufsyed.cabin.model.Samples
import com.roufsyed.cabin.model.StatusUi
import com.roufsyed.cabin.ui.overlay.TRAY_CONTROL_HEIGHT
import com.roufsyed.cabin.ui.overlay.TRAY_HEADROOM
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
    fun `the tray has room for four rows under its controls on an 800-high screen`() {
        // Two rows of controls, each with its line, and the first row's headroom, + 76 heading
        // + 4 x 104 rows + 97 buttons + 1 outline: the whole height and no more.
        val controls = TRAY_HEADROOM + 2 * (TRAY_CONTROL_HEIGHT + 1)
        assertEquals(4, TRAY_ROWS)
        assertEquals(800, controls + 76 + TRAY_ROWS * 104 + 97 + 1)
    }

    @Test
    fun `the status marks are described in words for a screen reader`() {
        assertEquals("Phone connected, Wi-Fi on, GPS ready", StatusUi(phone = true, wifi = true, gps = true).description)
        assertEquals("Phone not connected, Wi-Fi off, GPS off", StatusUi(phone = false, wifi = false, gps = false).description)
    }
}
