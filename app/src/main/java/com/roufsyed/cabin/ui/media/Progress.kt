package com.roufsyed.cabin.ui.media

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.roufsyed.cabin.media.PlaybackProgress
import com.roufsyed.cabin.ui.theme.Slate
import kotlinx.coroutines.delay

/**
 * The current clock reading for working out a track's position. It moves once a second, and only while
 * the track is actually advancing and Cabin is on screen; a paused or live track costs nothing.
 */
@Composable
fun rememberPlaybackClock(progress: PlaybackProgress): Long {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val now by produceState(SystemClock.elapsedRealtime(), progress, lifecycle) {
        value = SystemClock.elapsedRealtime()
        if (progress.playing && progress.speed > 0f && !progress.live) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    value = SystemClock.elapsedRealtime()
                    // Wake just after the next whole second of the track, so the arc moves in even steps.
                    val intoSecond = progress.positionAt(value) % 1000L
                    delay((1000L - intoSecond).coerceIn(50L, 1000L) + 20L)
                }
            }
        }
    }
    return now
}

/**
 * The progress ring, [stroke] thick: an outline-coloured track with the played part in the accent,
 * from the top and clockwise. Where the player gives no length the ring is whole and in the accent,
 * and says nothing of progress.
 */
@Composable
fun ProgressRing(progress: PlaybackProgress, modifier: Modifier = Modifier, stroke: Dp = 5.dp) {
    val timed = progress.hasLength
    val played = progress.fractionAt(rememberPlaybackClock(progress))
    val accent = Slate.Accent
    val rest = Slate.Outline
    Canvas(modifier.semantics { if (timed) progressBarRangeInfo = ProgressBarRangeInfo(played, 0f..1f) }) {
        val width = stroke.toPx()
        val corner = Offset(width / 2f, width / 2f)
        val box = Size(size.width - width, size.height - width)
        if (timed) {
            drawArc(rest, 0f, 360f, useCenter = false, topLeft = corner, size = box, style = Stroke(width))
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * played,
                useCenter = false,
                topLeft = corner,
                size = box,
                style = Stroke(width, cap = StrokeCap.Round),
            )
        } else {
            drawArc(accent, 0f, 360f, useCenter = false, topLeft = corner, size = box, style = Stroke(width))
        }
    }
}
