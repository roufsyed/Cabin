package com.roufsyed.cabin.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.roufsyed.cabin.ui.components.pressable
import com.roufsyed.cabin.ui.icons.Icon
import com.roufsyed.cabin.ui.icons.SlateIcons
import com.roufsyed.cabin.ui.theme.Slate
import com.roufsyed.cabin.ui.theme.Txt

/** What the "Where to?" dialog shows: whether it is still listening, and up to two recent places. */
@Immutable
data class VoiceUi(val listening: Boolean = true, val recents: List<String> = emptyList())

private val ButtonShape = RoundedCornerShape(20.dp)

/**
 * "Where to?": a dimmed screen under a 680 x 408 dialog with the microphone, what Cabin is doing, and
 * a row of the last destinations next to Cancel. Tapping outside the dialog cancels.
 */
@Composable
fun VoiceOverlay(ui: VoiceUi, onRetry: () -> Unit, onPick: (String) -> Unit, onCancel: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().pressable("Close", onClick = onCancel).background(Slate.Scrim))
        val shape = RoundedCornerShape(24.dp)
        Column(
            modifier = Modifier
                .offset(300.dp, 196.dp)
                .size(680.dp, 408.dp)
                // Taps on the dialog itself stop here instead of falling through to the scrim.
                .pointerInput(Unit) { detectTapGestures { } }
                .background(Slate.Raised, shape)
                .border(1.dp, Slate.Outline, shape)
                // The design's 32 / 28 / 28 padding, plus the 1 px border it sits inside.
                .padding(start = 29.dp, top = 33.dp, end = 29.dp, bottom = 29.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(96.dp)
                    .pressable("Listen again", onClick = onRetry)
                    .border(1.5.dp, Slate.Accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(SlateIcons.Mic, 48.dp, Slate.Text, 1.4f) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Txt(if (ui.listening) "Listening…" else "Didn’t catch that", 44, weight = Slate.Light, lineHeight = 1.1f)
                Txt(
                    when {
                        !ui.listening -> "Tap the microphone to try again"
                        ui.recents.isEmpty() -> "Say a destination"
                        else -> "Say a destination, or pick one"
                    },
                    24, color = Slate.Muted,
                )
            }
            Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (place in ui.recents.take(2)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pressable("Navigate to $place") { onPick(place) }
                            .border(1.dp, Slate.Outline, ButtonShape)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Txt(place, 26, lineHeight = 1f, ellipsis = true) }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pressable("Cancel", onClick = onCancel)
                        .background(Slate.Text, ButtonShape),
                    contentAlignment = Alignment.Center,
                ) { Txt("Cancel", 26, weight = Slate.SemiBold, color = Slate.Ground, lineHeight = 1f) }
            }
        }
    }
}
