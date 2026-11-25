package com.roufsyed.cabin.gesture

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangedIgnoreConsumed
import androidx.compose.ui.unit.dp

/**
 * Cabin's screens with their gestures: [content] fills the screen, and every touch on it is read by
 * [cabinGestures] first.
 */
@Composable
fun GestureLayer(onGesture: (Gesture) -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().cabinGestures(onGesture), content = content)
}

/**
 * Watches every touch on Cabin's screens before the buttons do. Touches pass through untouched until
 * the recogniser claims one as a swipe or a two-finger drag; from then on they are consumed, which makes
 * any button that had been pressed let go without firing.
 */
@Composable
fun Modifier.cabinGestures(onGesture: (Gesture) -> Unit): Modifier {
    val handler = rememberUpdatedState(onGesture)
    return pointerInput(Unit) {
        // The design's distances, in design pixels, scaled to this screen.
        val recognizer = GestureRecognizer(
            slop = 12.dp.toPx(),
            swipeDistance = 70.dp.toPx(),
            volumeStep = 24.dp.toPx(),
        )
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
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
