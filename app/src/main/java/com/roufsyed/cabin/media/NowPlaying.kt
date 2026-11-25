package com.roufsyed.cabin.media

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import java.util.Locale

/** Where a track is, and how to work out where it will be a moment later without asking again. */
@Immutable
data class PlaybackProgress(
    val positionMs: Long,
    /** Zero or less when the length is unknown, as for radio and other live streams. */
    val durationMs: Long,
    val speed: Float,
    val playing: Boolean,
    /** The elapsed-realtime clock reading at which [positionMs] was true. */
    val atElapsedMs: Long,
    /** True when there is no track at all: the line is empty and no times are shown. */
    val empty: Boolean = false,
) {
    /** True when there is something on show whose length is not known, playing or not. */
    val live: Boolean get() = !empty && durationMs <= 0L

    /**
     * True while something of no known length is actually playing. Only that is drawn as a live
     * stream, with a full line and the word under it, as the design draws radio. A player that
     * gives no length and is not playing is no stream, only a player sitting idle with its name
     * up, and gets an empty line with nothing under it.
     */
    val onAir: Boolean get() = live && playing

    /** The position at [nowElapsedMs], advanced by the time gone by while playing. */
    fun positionAt(nowElapsedMs: Long): Long {
        val moved = if (playing) ((nowElapsedMs - atElapsedMs) * speed).toLong() else 0L
        val position = (positionMs + moved).coerceAtLeast(0L)
        return if (live) position else position.coerceAtMost(durationMs)
    }

    /** The played part of the track, 0..1. A stream that is playing reads as full. */
    fun fractionAt(nowElapsedMs: Long): Float = when {
        empty -> 0f
        live -> if (onAir) 1f else 0f
        else -> (positionAt(nowElapsedMs).toFloat() / durationMs).coerceIn(0f, 1f)
    }

    fun positionLabel(nowElapsedMs: Long): String = when {
        empty -> ""
        live -> if (onAir) "Live" else ""
        else -> formatTime(positionAt(nowElapsedMs))
    }

    val durationLabel: String get() = if (empty || live) "" else formatTime(durationMs)

    companion object {
        /** Nothing is playing. */
        val None = PlaybackProgress(0L, 0L, 0f, playing = false, atElapsedMs = 0L, empty = true)

        /** "1:24", "12:05", "1:02:03". */
        fun formatTime(ms: Long): String {
            val total = (ms.coerceAtLeast(0L) / 1000L).toInt()
            val hours = total / 3600
            val minutes = (total % 3600) / 60
            val seconds = total % 60
            return if (hours > 0) {
                String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
            }
        }
    }
}

/** What is playing, as published by whichever app holds the active media session. */
@Immutable
data class NowPlaying(
    val packageName: String,
    /** The playing app's name. Text only: third-party logos are never shown. */
    val appLabel: String,
    val title: String,
    val artist: String,
    val progress: PlaybackProgress,
    /** Cover art, already scaled down to the size it is drawn at. */
    val art: ImageBitmap? = null,
) {
    val playing: Boolean get() = progress.playing
}
