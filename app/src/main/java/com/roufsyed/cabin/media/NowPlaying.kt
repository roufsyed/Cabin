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
    val live: Boolean get() = !empty && durationMs <= 0L

    /** The position at [nowElapsedMs], advanced by the time gone by while playing. */
    fun positionAt(nowElapsedMs: Long): Long {
        val moved = if (playing) ((nowElapsedMs - atElapsedMs) * speed).toLong() else 0L
        val position = (positionMs + moved).coerceAtLeast(0L)
        return if (live) position else position.coerceAtMost(durationMs)
    }

    /** The played part of the track, 0..1. A live stream reads as full, as the design draws radio. */
    fun fractionAt(nowElapsedMs: Long): Float = when {
        empty -> 0f
        live -> 1f
        else -> (positionAt(nowElapsedMs).toFloat() / durationMs).coerceIn(0f, 1f)
    }

    fun positionLabel(nowElapsedMs: Long): String = when {
        empty -> ""
        live -> "Live"
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

@Immutable
data class QueueItem(
    val id: Long,
    val title: String,
    val subtitle: String,
    val current: Boolean,
    /** Blank when the app does not say how long the item is, which is usual. */
    val duration: String = "",
)

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
    val queueTitle: String = "Up next",
    /** The current item and the few after it: as many as the Music screen has rows for. */
    val queue: List<QueueItem> = emptyList(),
) {
    val playing: Boolean get() = progress.playing
}

/** Picks the rows the Music screen shows: the current item and the next ones, [rows] in all. */
fun <T> queueWindow(items: List<T>, currentIndex: Int, rows: Int = 4): List<T> {
    if (items.size <= rows) return items
    val start = currentIndex.coerceIn(0, items.size - rows)
    return items.subList(start, start + rows)
}
