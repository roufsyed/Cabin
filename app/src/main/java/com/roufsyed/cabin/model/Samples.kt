package com.roufsyed.cabin.model

import com.roufsyed.cabin.media.NowPlaying
import com.roufsyed.cabin.media.PlaybackProgress
import com.roufsyed.cabin.media.QueueItem
import com.roufsyed.cabin.notify.TrayItem
import com.roufsyed.cabin.notify.TrayKind

/**
 * The values the design itself is drawn with. They back the debug "design" launch, which makes the app
 * directly comparable with the design, and the tests. Volume (12) and brightness (8, Auto) are the
 * defaults of their in-memory stand-ins.
 */
object Samples {
    val clock = ClockUi(
        weekday = "Thursday",
        hour = "10",
        minute = "10",
        dateLong = "12 November",
        dateShort = "Thu 12 Nov",
    )

    val status = StatusUi(phone = true, wifi = true, gps = true)

    val nav = NavUi.Active(
        distance = "350 m",
        road = "Ring Road",
        arrive = "Arrive 10:48",
        remaining = "38 min · 17 km",
    )

    /** In the design this is the phone's Bluetooth audio; here it "plays" in the design's Music app. */
    val playing = NowPlaying(
        packageName = "design.music",
        appLabel = "Bluetooth audio",
        title = "Open Road",
        artist = "Aster Lane",
        // 1:24 of 3:41, held still so a screenshot does not depend on when it is taken.
        progress = PlaybackProgress(positionMs = 84_000, durationMs = 221_000, speed = 0f, playing = true, atElapsedMs = 0),
        queueTitle = "Up next",
        queue = listOf(
            QueueItem(1, "Open Road", "Aster Lane", current = true, duration = "3:41"),
            QueueItem(2, "Night Signals", "Vela North", current = false, duration = "4:05"),
            QueueItem(3, "Paper Lanterns", "June Harbor", current = false, duration = "3:12"),
            QueueItem(4, "Slow Tide", "Marlow Reed", current = false, duration = "2:58"),
        ),
    )

    /** The three notifications in the design's tray. */
    val notifications = listOf(
        TrayItem("call", TrayKind.Call, "P", "Missed call", "Meera · tap to call back", "10:02"),
        TrayItem("message", TrayKind.Message, "M", "Kabir", "Running ten minutes late", "9:58"),
        TrayItem("usb", TrayKind.Media, "M", "USB drive connected", "Tap to play from USB", "9:41"),
    )
}
