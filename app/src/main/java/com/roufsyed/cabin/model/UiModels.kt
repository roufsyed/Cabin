package com.roufsyed.cabin.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import com.roufsyed.cabin.media.NowPlaying
import com.roufsyed.cabin.media.PlaybackProgress

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
data class StatusUi(val phone: Boolean, val wifi: Boolean, val gps: Boolean, val hotspot: Boolean = false) {
    val description: String
        get() = (if (phone) "Phone connected" else "Phone not connected") + ", " +
            (if (wifi) "Wi-Fi on" else "Wi-Fi off") + ", " +
            (if (hotspot) "Hotspot on" else "Hotspot off") + ", " +
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
    val progress: PlaybackProgress,
    val art: ImageBitmap? = null,
    /** True while Cabin cannot see what is playing because notification access has not been granted. */
    val needsAccess: Boolean = false,
) {
    val playing: Boolean get() = progress.playing
}

/** What the Now playing panel says for each state of the unit's media. */
fun nowPlayingUi(now: NowPlaying?, access: Boolean): NowPlayingUi = when {
    !access -> NowPlayingUi(
        caption = "Now playing",
        title = "No access yet",
        artist = "Tap to let Cabin see what is playing",
        progress = PlaybackProgress.None,
        needsAccess = true,
    )
    now == null -> NowPlayingUi("Now playing", "Nothing playing", "", PlaybackProgress.None)
    else -> NowPlayingUi(
        caption = "Now playing",
        title = now.title.ifBlank { now.appLabel },
        artist = now.artist,
        progress = now.progress,
        art = now.art,
    )
}

@Immutable
data class VolumeUi(val level: Int, val muted: Boolean)

enum class BrightnessMode(val label: String) { Auto("Auto"), Day("Day"), Night("Night") }

/** The colour themes, in the order they are offered. Blue is the design's own. */
enum class ThemeId(val label: String) {
    Blue("Blue"),
    Teal("Teal"),
    Emerald("Emerald"),
    Champagne("Champagne"),
    Amber("Amber"),
    Copper("Copper"),
    Ruby("Ruby"),
    Rose("Rose"),
    Amethyst("Amethyst"),
    Platinum("Platinum"),
}

@Immutable
data class BrightnessUi(val level: Int, val night: Boolean, val mode: BrightnessMode, val manual: Boolean = false) {
    val label: String get() = if (manual && mode == BrightnessMode.Auto) "Manual" else mode.label
}

/** An audio source in the bottom row. [baseWidth] is the design's label width, used to share the row out. */
enum class SourceId(val label: String, val baseWidth: Int) {
    Radio("Radio", 68),
    Bluetooth("Bluetooth", 118),
    Usb("USB", 50),
    Auto("Android Auto", 164),
    Aux("AUX", 52),
}

@Immutable
data class SourceUi(val id: SourceId, val active: Boolean, val label: String = id.label)

/** A source while arranging: [app] is the name of the app it opens, or null if none is set. */
@Immutable
data class SourceTile(val id: SourceId, val hidden: Boolean, val app: String?, val label: String = id.label)
