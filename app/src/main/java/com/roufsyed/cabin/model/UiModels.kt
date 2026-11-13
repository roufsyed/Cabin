package com.roufsyed.cabin.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import com.roufsyed.cabin.media.NowPlaying
import com.roufsyed.cabin.media.PlaybackProgress
import com.roufsyed.cabin.media.QueueItem

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
    val progress: PlaybackProgress,
    /** The playing app's name, shown on the Music screen. */
    val source: String = "",
    val art: ImageBitmap? = null,
    val queueTitle: String = "Up next",
    val queue: List<QueueItem> = emptyList(),
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
        source = now.appLabel,
        art = now.art,
        queueTitle = now.queueTitle,
        queue = now.queue,
    )
}

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

/** A source while arranging: [app] is the name of the app it opens, or null if none is set. */
@Immutable
data class SourceTile(val id: SourceId, val hidden: Boolean, val app: String?)
