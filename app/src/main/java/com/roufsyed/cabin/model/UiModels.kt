package com.roufsyed.cabin.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

/** What the screens draw. Plain immutable values, produced off the main thread by the view models. */

@Immutable
data class ClockUi(
    val weekday: String,
    val hour: String,
    val minute: String,
    val dateLong: String,
    val dateShort: String,
) {
    val time: String get() = "$hour:$minute"
}

@Immutable
data class StatusUi(val phone: Boolean, val wifi: Boolean, val gps: Boolean) {
    val description: String
        get() = (if (phone) "Phone connected" else "Phone not connected") + ", " +
            (if (wifi) "Wi-Fi on" else "Wi-Fi off") + ", " +
            (if (gps) "GPS ready" else "GPS off")
}

sealed interface NavUi {
    data object Idle : NavUi

    @Immutable
    data class Active(
        val distance: String,
        val road: String,
        val arrive: String,
        val remaining: String,
        /** The manoeuvre glyph supplied by the maps app, if any. */
        val glyph: ImageBitmap? = null,
    ) : NavUi
}

@Immutable
data class NowPlayingUi(
    val caption: String,
    val title: String,
    val artist: String,
    /** 0..1 of the track played. */
    val fraction: Float,
    val position: String,
    val duration: String,
    val playing: Boolean,
)

@Immutable
data class VolumeUi(val level: Int, val muted: Boolean)

enum class BrightnessMode(val label: String) { Auto("Auto"), Day("Day"), Night("Night") }

@Immutable
data class BrightnessUi(val level: Int, val night: Boolean, val mode: BrightnessMode)

/** An audio source in the bottom row. [baseWidth] is the design's label width, used to share the row out. */
enum class SourceId(val label: String, val baseWidth: Int) {
    Radio("Radio", 68),
    Bluetooth("Bluetooth", 118),
    Usb("USB", 50),
    Auto("Android Auto", 164),
    Aux("AUX", 52),
}

@Immutable
data class SourceUi(val id: SourceId, val active: Boolean)
