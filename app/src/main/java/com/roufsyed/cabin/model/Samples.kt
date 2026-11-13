package com.roufsyed.cabin.model

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
    val nowPlaying = NowPlayingUi(
        caption = "Now playing",
        title = "Open Road",
        artist = "Aster Lane",
        fraction = 0.38f,
        position = "1:24",
        duration = "3:41",
        playing = true,
    )
    val volume = VolumeUi(level = 12, muted = false)
    val brightness = BrightnessUi(level = 8, night = false, mode = BrightnessMode.Auto)
    val sources = listOf(
        SourceUi(SourceId.Radio, active = false),
        SourceUi(SourceId.Bluetooth, active = true),
        SourceUi(SourceId.Usb, active = false),
        SourceUi(SourceId.Auto, active = false),
    )
}
