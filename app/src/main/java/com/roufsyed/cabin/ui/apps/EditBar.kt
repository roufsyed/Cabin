package com.roufsyed.cabin.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.roufsyed.cabin.ui.components.pressable
import com.roufsyed.cabin.ui.icons.Icon
import com.roufsyed.cabin.ui.icons.SlateIcons
import com.roufsyed.cabin.ui.theme.Slate
import com.roufsyed.cabin.ui.theme.Txt

private val ButtonShape = RoundedCornerShape(20.dp)

/**
 * The bar that replaces the strip while arranging: a title, then Move earlier, Move later, Hide or
 * Show, and Done. The first three are dimmed until a tile has been tapped.
 */
@Composable
fun EditBar(
    title: String,
    hasPick: Boolean,
    pickHidden: Boolean,
    onEarlier: () -> Unit,
    onLater: () -> Unit,
    onFlip: () -> Unit,
    onDone: () -> Unit,
    hint: String = "Tap one, then move or hide it",
    /** An extra action on the tapped tile, shown before Done. */
    extraLabel: String? = null,
    extraDescription: String = "",
    onExtra: () -> Unit = {},
) {
    val dim = if (hasPick) 1f else 0.35f
    Row(
        modifier = Modifier.size(1036.dp, 96.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(start = 4.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Txt(title, 38, weight = Slate.Light, lineHeight = 1f)
            Txt(hint, 24, color = Slate.Muted, lineHeight = 1f, ellipsis = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(96.dp)
                    .alpha(dim)
                    .pressable("Move earlier", onClick = onEarlier)
                    .border(1.dp, Slate.Outline, ButtonShape),
                contentAlignment = Alignment.Center,
            ) { Icon(SlateIcons.ChevronLeft, 40.dp, Slate.Text, 1.5f) }
            Box(
                Modifier
                    .size(96.dp)
                    .alpha(dim)
                    .pressable("Move later", onClick = onLater)
                    .border(1.dp, Slate.Outline, ButtonShape),
                contentAlignment = Alignment.Center,
            ) { Icon(SlateIcons.ChevronRight, 40.dp, Slate.Text, 1.5f) }
            Box(
                Modifier
                    .size(150.dp, 96.dp)
                    .alpha(dim)
                    .pressable("Hide or show", onClick = onFlip)
                    .border(1.dp, Slate.Outline, ButtonShape),
                contentAlignment = Alignment.Center,
            ) { Txt(if (hasPick && pickHidden) "Show" else "Hide", 26, lineHeight = 1f) }
            if (extraLabel != null) {
                Box(
                    Modifier
                        .size(96.dp)
                        .alpha(dim)
                        .pressable(extraDescription, onClick = onExtra)
                        .border(1.dp, Slate.Outline, ButtonShape),
                    contentAlignment = Alignment.Center,
                ) { Txt(extraLabel, 26, lineHeight = 1f) }
            }
            Box(
                Modifier
                    .size(150.dp, 96.dp)
                    .pressable("Done arranging", onClick = onDone)
                    .background(Slate.Text, ButtonShape),
                contentAlignment = Alignment.Center,
            ) { Txt("Done", 26, weight = Slate.SemiBold, color = Slate.Ground, lineHeight = 1f) }
        }
    }
}

/**
 * The bar shown while choosing which app a slot opens: what is being chosen, a way back to guessing
 * it from the app names, and Back.
 */
@Composable
fun PickBar(title: String, hint: String, onAutomatic: () -> Unit, onBack: () -> Unit) {
    Row(
        modifier = Modifier.size(1036.dp, 96.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(start = 4.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Txt(title, 38, weight = Slate.Light, lineHeight = 1f, ellipsis = true)
            Txt(hint, 24, color = Slate.Muted, lineHeight = 1f, ellipsis = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(150.dp, 96.dp)
                    .pressable("Choose automatically", onClick = onAutomatic)
                    .border(1.dp, Slate.Outline, ButtonShape),
                contentAlignment = Alignment.Center,
            ) { Txt("Auto", 26, lineHeight = 1f) }
            Box(
                Modifier
                    .size(150.dp, 96.dp)
                    .pressable("Back", onClick = onBack)
                    .background(Slate.Text, ButtonShape),
                contentAlignment = Alignment.Center,
            ) { Txt("Back", 26, weight = Slate.SemiBold, color = Slate.Ground, lineHeight = 1f) }
        }
    }
}

/** A title, a hint and one primary button, in the strip's place: used by Setup. */
@Composable
fun TitleBar(title: String, hint: String, action: String, actionDescription: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.size(1036.dp, 96.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(start = 4.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Txt(title, 38, weight = Slate.Light, lineHeight = 1f, ellipsis = true)
            Txt(hint, 24, color = Slate.Muted, lineHeight = 1f, ellipsis = true)
        }
        Box(
            Modifier
                .size(150.dp, 96.dp)
                .pressable(actionDescription, onClick = onAction)
                .background(Slate.Text, ButtonShape),
            contentAlignment = Alignment.Center,
        ) { Txt(action, 26, weight = Slate.SemiBold, color = Slate.Ground, lineHeight = 1f) }
    }
}
