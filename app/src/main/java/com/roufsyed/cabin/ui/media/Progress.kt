package com.roufsyed.cabin.ui.media

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.roufsyed.cabin.media.PlaybackProgress
import com.roufsyed.cabin.ui.theme.Slate
import com.roufsyed.cabin.ui.theme.Txt
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
                    // Wake just after the next whole second of the track, so the label changes on time.
                    val intoSecond = progress.positionAt(value) % 1000L
                    delay((1000L - intoSecond).coerceIn(50L, 1000L) + 20L)
                }
            }
        }
    }
    return now
}

/** The 3 px progress line with the position and length under it, [gap] apart. */
@Composable
fun ProgressBlock(progress: PlaybackProgress, gap: Dp, modifier: Modifier = Modifier) {
    val now = rememberPlaybackClock(progress)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        ProgressTrack(progress.fractionAt(now))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(29.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Txt(progress.positionLabel(now), 24, color = Slate.Muted, tabular = true)
            Txt(progress.durationLabel, 24, color = Slate.Muted, tabular = true)
        }
    }
}

/** The 3 px progress line: a hairline-coloured track with the played part in the accent. */
@Composable
fun ProgressTrack(fraction: Float, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(Slate.Hairline, shape),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(3.dp)
                .background(Slate.Accent, shape),
        )
    }
}
