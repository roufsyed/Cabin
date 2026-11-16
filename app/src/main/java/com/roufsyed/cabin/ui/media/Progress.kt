package com.roufsyed.cabin.ui.media

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.roufsyed.cabin.gesture.LocalOwnTouch
import com.roufsyed.cabin.media.PlaybackProgress
import com.roufsyed.cabin.ui.theme.Slate
import com.roufsyed.cabin.ui.theme.Txt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

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

/**
 * The 3 px progress line with the position and length under it, [gap] apart.
 *
 * With [onSeek], and a track its player lets be moved, the line is a slider: it has a knob where the
 * track is, a finger put anywhere along it brings the knob there, and lifting the finger moves the
 * track. It takes no more room for that; only the finger's reach is larger than what is drawn.
 */
@Composable
fun ProgressBlock(
    progress: PlaybackProgress,
    gap: Dp,
    modifier: Modifier = Modifier,
    onSeek: ((Long) -> Unit)? = null,
) {
    val now = rememberPlaybackClock(progress)
    if (onSeek != null && progress.seekable) {
        // What a finger has done to the slider belongs to one track, and goes with it.
        key(progress.durationMs) { SeekBlock(progress, now, gap, modifier, onSeek) }
    } else {
        ProgressLayout(progress.fractionAt(now), progress.positionLabel(now), progress.durationLabel, gap, modifier)
    }
}

/** The knob's width: at rest, and under a finger. */
private val KNOB = 20.dp
private val KNOB_HELD = 28.dp

/**
 * How far above the line a finger still counts as on the slider. Below it the reach runs past the
 * times, to the 96 the design asks of anything touched: 40 above, the 3 of the line, and 53 below.
 */
private val REACH_ABOVE = 40.dp
private val REACH = 96.dp

/**
 * How far past either end of the line the reach runs: on the Music screen, to the edge of the card.
 * The knob sits on the very end of the line when a track starts, and a finger that goes for it
 * there lands beside the line as often as on it.
 */
private val REACH_BESIDE = 32.dp

/** The height of the line of times under the progress line. */
private val TIMES_HEIGHT = 29.dp

/** How long the knob waits where it was put for the player to say where the track now is. */
private const val SEEK_WAIT_MS = 2_000L

@Composable
private fun SeekBlock(progress: PlaybackProgress, now: Long, gap: Dp, modifier: Modifier, onSeek: (Long) -> Unit) {
    val ownTouch = LocalOwnTouch.current
    // Where the finger has put the track: while it is down, and after it lifts until the player has
    // followed, so that the knob does not spring back to the old place for a moment in between.
    var chosen by remember { mutableStateOf<Long?>(null) }
    var held by remember { mutableStateOf(false) }
    // What the player had last said when the finger lifted: its answer is whatever differs from it.
    var before by remember { mutableStateOf(progress) }
    // How many times the track has been moved. It is counted, rather than gone by whether a finger
    // is down, because a quick tap comes and goes between two frames.
    var moves by remember { mutableIntStateOf(0) }
    val latest by rememberUpdatedState(progress)
    val seek by rememberUpdatedState(onSeek)

    LaunchedEffect(moves) {
        if (moves == 0) return@LaunchedEffect
        // A player that takes no notice says nothing new; the knob then goes back by itself.
        withTimeoutOrNull(SEEK_WAIT_MS) { snapshotFlow { latest }.first { it != before } }
        // Unless a finger has come back in the meantime: then the knob is that finger's.
        if (!held) chosen = null
    }

    val position = chosen ?: progress.positionAt(now)
    val fraction = (position.toFloat() / progress.durationMs).coerceIn(0f, 1f)
    val label = PlaybackProgress.formatTime(position)
    val block = 3.dp + gap + TIMES_HEIGHT
    Box(modifier) {
        ProgressLayout(
            fraction = fraction,
            position = label,
            length = progress.durationLabel,
            gap = gap,
            knob = if (held) KNOB_HELD else KNOB,
            positionColor = if (held) Slate.Text else Slate.Muted,
        )
        Box(
            Modifier
                .matchParentSize()
                .reach(above = REACH_ABOVE, below = (REACH - REACH_ABOVE - block).coerceAtLeast(0.dp), beside = REACH_BESIDE)
                .semantics {
                    contentDescription = "Track position"
                    stateDescription = "$label of ${progress.durationLabel}"
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                    setProgress { wanted ->
                        seek(latest.positionFor(wanted))
                        true
                    }
                }
                .pointerInput(ownTouch) {
                    // The line is the middle of what is touched: the reach runs on past both its ends.
                    val beside = REACH_BESIDE.toPx()
                    fun along(x: Float): Float = (x - beside) / (size.width - 2f * beside)
                    awaitEachGesture {
                        // A touch the gesture layer has already claimed never gets here.
                        val down = awaitFirstDown()
                        ownTouch.take()
                        down.consume()
                        held = true
                        chosen = latest.positionFor(along(down.position.x))
                        var lifted = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            // The touch was taken away, or a second finger came down: two fingers are
                            // never a tap anywhere in Cabin, and here they move nothing either.
                            if (change == null || change.isConsumed) break
                            if (event.changes.any { it.id != down.id && it.pressed }) break
                            change.consume()
                            if (!change.pressed) {
                                lifted = true
                                break
                            }
                            chosen = latest.positionFor(along(change.position.x))
                        }
                        val target = chosen
                        if (lifted && target != null) {
                            before = latest
                            held = false
                            moves++
                            seek(target)
                        } else {
                            chosen = null
                            held = false
                        }
                    }
                },
        )
    }
}

/**
 * Lets what it is put on reach [above], [below] and to both sides [beside] the room it has, without
 * taking any more of it.
 */
private fun Modifier.reach(above: Dp, below: Dp, beside: Dp): Modifier = layout { measurable, constraints ->
    val top = above.roundToPx()
    val side = beside.roundToPx()
    val taller = top + below.roundToPx()
    val wider = 2 * side
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + wider, maxWidth = constraints.maxWidth + wider,
            minHeight = constraints.minHeight + taller, maxHeight = constraints.maxHeight + taller,
        ),
    )
    layout(placeable.width - wider, placeable.height - taller) { placeable.place(-side, -top) }
}

@Composable
private fun ProgressLayout(
    fraction: Float,
    position: String,
    length: String,
    gap: Dp,
    modifier: Modifier = Modifier,
    knob: Dp = 0.dp,
    positionColor: Color = Slate.Muted,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        ProgressTrack(fraction, knob = knob)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TIMES_HEIGHT),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Txt(position, 24, color = positionColor, tabular = true)
            Txt(length, 24, color = Slate.Muted, tabular = true)
        }
    }
}

/**
 * The 3 px progress line: a hairline-coloured track with the played part in the accent. With a
 * [knob] it has a round one of that width where the played part ends, drawn over the line and
 * taking no room.
 */
@Composable
fun ProgressTrack(fraction: Float, modifier: Modifier = Modifier, knob: Dp = 0.dp) {
    val shape = RoundedCornerShape(2.dp)
    val played = fraction.coerceIn(0f, 1f)
    val accent = Slate.Accent
    Box(
        modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(Slate.Hairline, shape)
            .drawWithContent {
                drawContent()
                if (knob > 0.dp) drawCircle(accent, knob.toPx() / 2f, Offset(size.width * played, size.height / 2f))
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth(played)
                .height(3.dp)
                .background(accent, shape),
        )
    }
}
