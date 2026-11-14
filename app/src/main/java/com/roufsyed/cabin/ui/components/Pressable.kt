package com.roufsyed.cabin.ui.components

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * The design's only touch feedback: a pressed control drops to 55% opacity, and a control focused from
 * a key or knob gets a white outline. No ripple, no animation.
 */
private object SlateIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = Node(interactionSource)
    override fun equals(other: Any?) = other === this
    override fun hashCode() = 55

    private class Node(private val source: InteractionSource) : Modifier.Node(), DrawModifierNode {
        private var pressed = false
        private var focused = false
        private val dim = Paint().apply { alpha = 0.55f }

        override fun onAttach() {
            coroutineScope.launch {
                var presses = 0
                var focuses = 0
                source.interactions.collect { interaction ->
                    when (interaction) {
                        is PressInteraction.Press -> presses++
                        is PressInteraction.Release, is PressInteraction.Cancel -> presses--
                        is FocusInteraction.Focus -> focuses++
                        is FocusInteraction.Unfocus -> focuses--
                    }
                    val nowPressed = presses > 0
                    val nowFocused = focuses > 0
                    if (nowPressed != pressed || nowFocused != focused) {
                        pressed = nowPressed
                        focused = nowFocused
                        invalidateDraw()
                    }
                }
            }
        }

        override fun ContentDrawScope.draw() {
            if (pressed) {
                drawIntoCanvas { canvas ->
                    canvas.saveLayer(Rect(0f, 0f, size.width, size.height), dim)
                    drawContent()
                    canvas.restore()
                }
            } else {
                drawContent()
            }
            if (focused) {
                val w = 3.dp.toPx()
                drawRect(
                    color = Color.White,
                    topLeft = Offset(w / 2, w / 2),
                    size = Size(size.width - w, size.height - w),
                    style = Stroke(w),
                )
            }
        }
    }
}

/**
 * Makes an element a button. Put it before any background or border that should dim with the press.
 * [label] is what a screen reader announces and what tests look up. With [onLongClick], holding the
 * button does that instead, and letting go afterwards is not a tap.
 */
fun Modifier.pressable(
    label: String? = null,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier {
    val clickable = if (onLongClick == null) {
        clickable(
            interactionSource = null,
            indication = SlateIndication,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        combinedClickable(
            interactionSource = null,
            indication = SlateIndication,
            enabled = enabled,
            role = Role.Button,
            onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick,
            // No buzz: the screen changing is the answer, as everywhere else in Cabin.
            hapticFeedbackEnabled = false,
            onClick = onClick,
        )
    }
    return if (label == null) clickable else clickable.semantics { contentDescription = label }
}
