package com.roufsyed.cabin.ui.overlay

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.roufsyed.cabin.notify.TrayItem
import com.roufsyed.cabin.notify.TrayKind
import com.roufsyed.cabin.ui.apps.Monogram
import com.roufsyed.cabin.ui.components.pressable
import com.roufsyed.cabin.ui.icons.Icon
import com.roufsyed.cabin.ui.icons.SlateIcons
import com.roufsyed.cabin.ui.theme.Slate
import com.roufsyed.cabin.ui.theme.Txt

/**
 * The notification tray: the screen dims and a 1200-wide sheet hangs from the top edge, with a
 * heading, one row per notification and a row of buttons. A tap on the dimmed screen closes it.
 *
 * [needsAccess] replaces the list with one row that leads to Android's notification-access switch.
 */
@Composable
fun TrayOverlay(
    items: List<TrayItem>,
    needsAccess: Boolean,
    onOpen: (TrayItem) -> Unit,
    onAllowAccess: () -> Unit,
    onClearAll: () -> Unit,
    onScreenOff: () -> Unit,
    onClose: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().pressable("Close notifications", onClick = onClose).background(Slate.Scrim))
        val shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
        Column(
            modifier = Modifier
                .offset(x = 40.dp)
                .width(1200.dp)
                .pointerInput(Unit) { detectTapGestures { } }
                .clip(shape)
                .background(Slate.Raised)
                .drawWithContent {
                    drawContent()
                    // A 1 px outline on the left, right and bottom; the top edge is the screen's edge.
                    val half = 0.5.dp.toPx()
                    val radius = 24.dp.toPx() - half
                    val outline = Path().apply {
                        moveTo(half, 0f)
                        lineTo(half, size.height - half - radius)
                        arcTo(Rect(half, size.height - half - 2 * radius, half + 2 * radius, size.height - half), 180f, -90f, false)
                        lineTo(size.width - half - radius, size.height - half)
                        arcTo(
                            Rect(size.width - half - 2 * radius, size.height - half - 2 * radius, size.width - half, size.height - half),
                            90f, -90f, false,
                        )
                        lineTo(size.width - half, 0f)
                    }
                    drawPath(outline, Slate.Outline, style = Stroke(1.dp.toPx()))
                }
                // The outline's pixel on the left, right and bottom.
                .padding(start = 1.dp, end = 1.dp, bottom = 1.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(76.dp).padding(start = 36.dp, top = 30.dp, end = 36.dp)) {
                Txt("Notifications", 24, color = Slate.Muted)
            }
            when {
                needsAccess -> TrayMessage("Allow notification access to see them here", onAllowAccess)
                items.isEmpty() -> TrayMessage("No notifications", null)
                else -> for (item in items) TrayRow(item) { onOpen(item) }
            }
            Line()
            Row(Modifier.fillMaxWidth().height(96.dp)) {
                if (items.isNotEmpty() && !needsAccess) {
                    FooterButton("Clear all", "Clear all notifications", Modifier.width(398.dp), onClearAll)
                    Divider()
                }
                FooterButton("Screen off", "Screen off", Modifier.width(398.dp), onScreenOff)
                Divider()
                FooterButton("Close", "Close notification tray", Modifier.weight(1f), onClose, strong = true)
            }
        }
    }
}

@Composable
private fun Line() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Slate.Outline))
}

@Composable
private fun Divider() {
    Box(Modifier.width(1.dp).fillMaxHeight().background(Slate.Outline))
}

@Composable
private fun TrayRow(item: TrayItem, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().height(104.dp).pressable("${item.title}. ${item.text}", onClick = onClick)) {
        Line()
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (item.kind) {
                TrayKind.Call -> Icon(SlateIcons.Handset, 40.dp, Slate.Ink, 1.3f)
                TrayKind.Message -> Icon(SlateIcons.Message, 40.dp, Slate.Ink, 1.3f)
                TrayKind.Media -> Icon(SlateIcons.Music, 40.dp, Slate.Ink, 1.3f)
                TrayKind.Other -> Monogram(item.letter, 40.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Txt(item.title, 28, weight = Slate.SemiBold, ellipsis = true)
                if (item.text.isNotEmpty()) Txt(item.text, 24, color = Slate.Muted, ellipsis = true)
            }
            Txt(item.time, 24, color = Slate.Muted, lineHeight = 1f, tabular = true, singleLine = true)
        }
    }
}

@Composable
private fun TrayMessage(text: String, onClick: (() -> Unit)?) {
    val base = Modifier.fillMaxWidth().height(104.dp)
    Column(if (onClick == null) base else base.pressable(text, onClick = onClick)) {
        Line()
        Box(Modifier.fillMaxSize().padding(horizontal = 36.dp), contentAlignment = Alignment.CenterStart) {
            Txt(text, 28, color = Slate.Muted, ellipsis = true)
        }
    }
}

@Composable
private fun FooterButton(label: String, description: String, modifier: Modifier, onClick: () -> Unit, strong: Boolean = false) {
    Box(modifier.fillMaxHeight().pressable(description, onClick = onClick), contentAlignment = Alignment.Center) {
        Txt(label, 26, weight = if (strong) Slate.SemiBold else Slate.Medium, lineHeight = 1f)
    }
}
