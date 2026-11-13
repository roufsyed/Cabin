package com.roufsyed.cabin.model

import com.roufsyed.cabin.media.NowPlaying
import com.roufsyed.cabin.media.PlaybackProgress
import com.roufsyed.cabin.media.QueueItem

/**
 * The values the design itself is drawn with. Used for the static Home, for the debug "demo" launch
 * that makes the app directly comparable with the design, and by tests.
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
    /** The app the sample track plays in. In the design this is the phone's Bluetooth audio. */
    const val PLAYING_PACKAGE = "design.music"

    val playing = NowPlaying(
        packageName = PLAYING_PACKAGE,
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
    val nowPlaying = nowPlayingUi(playing, access = true)
    val volume = VolumeUi(level = 12, muted = false)
    val brightness = BrightnessUi(level = 8, night = false, mode = BrightnessMode.Auto)
    val sources = listOf(
        SourceUi(SourceId.Radio, active = false),
        SourceUi(SourceId.Bluetooth, active = true),
        SourceUi(SourceId.Usb, active = false),
        SourceUi(SourceId.Auto, active = false),
    )
}
