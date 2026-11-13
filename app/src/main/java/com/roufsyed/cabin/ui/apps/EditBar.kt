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
) {
    val dim = if (hasPick) 1f else 0.35f
    Row(
        modifier = Modifier.size(1036.dp, 96.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Txt(title, 38, weight = Slate.Light, lineHeight = 1f)
            Txt("Tap one, then move or hide it", 24, color = Slate.Muted, lineHeight = 1f)
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
