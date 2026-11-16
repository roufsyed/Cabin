package com.roufsyed.cabin.gesture

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangedIgnoreConsumed
import androidx.compose.ui.unit.dp

/**
 * Lets a control that is dragged itself, as a slider is, keep the touch that landed on it. The
 * gesture layer sees every touch before the controls do and would read a finger drawn along a
 * slider as a swipe. A control that calls [take] as the finger lands has the touch to itself, with
 * no gesture read from it, until every finger is up again.
 */
class OwnTouch {
    var taken = false
        private set

    fun take() {
        taken = true
    }

    fun release() {
        taken = false
    }
}

/** The one the screen's gesture layer goes by. Outside a [GestureLayer], one that nothing reads. */
val LocalOwnTouch = staticCompositionLocalOf { OwnTouch() }

/**
 * Cabin's screens with their gestures: [content] fills the screen, every touch on it is read by
 * [cabinGestures] first, and a slider anywhere in it keeps its own touch through [LocalOwnTouch].
 */
@Composable
fun GestureLayer(onGesture: (Gesture) -> Unit, content: @Composable BoxScope.() -> Unit) {
    val ownTouch = remember { OwnTouch() }
    CompositionLocalProvider(LocalOwnTouch provides ownTouch) {
        Box(Modifier.fillMaxSize().cabinGestures(onGesture), content = content)
    }
}

/**
 * Watches every touch on Cabin's screens before the buttons do. Touches pass through untouched until
 * the recogniser claims one as a swipe or a two-finger drag; from then on they are consumed, which makes
 * any button that had been pressed let go without firing. A touch that a control has taken through
 * [LocalOwnTouch] is left alone altogether.
 */
@Composable
fun Modifier.cabinGestures(onGesture: (Gesture) -> Unit): Modifier {
    val handler = rememberUpdatedState(onGesture)
    val ownTouch = LocalOwnTouch.current
    return pointerInput(ownTouch) {
        // The design's distances, in design pixels, scaled to this screen.
        val recognizer = GestureRecognizer(
            slop = 12.dp.toPx(),
            swipeDistance = 70.dp.toPx(),
            volumeStep = 24.dp.toPx(),
        )
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                // A first finger landing starts afresh, whatever became of the touch before it. The
                // control under it is asked after this layer, so it takes this touch after this line.
                if (event.changes.none { it.previousPressed }) ownTouch.release()
                if (ownTouch.taken) {
                    // A control has this touch: it is no swipe, wherever the finger goes from here.
                    for (gesture in recognizer.cancel()) handler.value(gesture)
                    if (event.changes.none { it.pressed }) ownTouch.release()
                    continue
                }
                // Claimed before this event counts too: the lift that ends a swipe must not reach a button.
                var claimed = recognizer.claimed
                for (change in event.changes) {
                    val id = change.id.value
                    val time = change.uptimeMillis
                    val gestures = when {
                        change.changedToDownIgnoreConsumed() ->
                            recognizer.down(id, change.position.x, change.position.y, time)
                        change.changedToUpIgnoreConsumed() -> recognizer.up(id, time)
                        change.pressed && change.positionChangedIgnoreConsumed() ->
                            recognizer.move(id, change.position.x, change.position.y, time)
                        else -> emptyList()
                    }
                    claimed = claimed || recognizer.claimed
                    for (gesture in gestures) handler.value(gesture)
                }
                if (claimed) event.changes.forEach { it.consume() }
                if (event.changes.none { it.pressed }) {
                    // Every finger is up. If the system dropped a touch without reporting it, forget it.
                    for (gesture in recognizer.cancel()) handler.value(gesture)
                }
            }
        }
    }
}
