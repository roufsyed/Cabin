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
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.roufsyed.cabin.notify.TRAY_ROWS
import com.roufsyed.cabin.notify.TrayItem
import com.roufsyed.cabin.notify.TrayKind
import com.roufsyed.cabin.ui.apps.Monogram
import com.roufsyed.cabin.ui.components.pressable
import com.roufsyed.cabin.ui.icons.Icon
import com.roufsyed.cabin.ui.icons.SlateIcon
import com.roufsyed.cabin.ui.icons.SlateIcons
import com.roufsyed.cabin.ui.theme.Slate
import com.roufsyed.cabin.ui.theme.Txt

/** How a control in the tray is drawn. */
enum class ControlLook {
    /** Something done in one tap. */
    Plain,

    /** A switch that is on: the one place the accent is used here. */
    On,

    /** A switch that is off. */
    Off,

    /** Not to be had on this unit, or not until Cabin has been allowed something. */
    Unavailable,
}

/**
 * One of the tray's controls: a switch of the unit's, or something done in one tap. [description]
 * is what a screen reader says, so it carries the state that the colour shows. With [onClick] null
 * the control is not a button at all.
 */
@Immutable
class TrayControl(
    val label: String,
    val description: String,
    val icon: SlateIcon,
    val look: ControlLook = ControlLook.Plain,
    val onClick: (() -> Unit)?,
)

/** The controls are laid out five across, each 96 high, with a 1 px line under every row. See [TRAY_HEADROOM]. */
const val TRAY_CONTROLS_ACROSS = 5
const val TRAY_CONTROL_HEIGHT = 96

/**
 * How much taller the first row of controls is, at its top. A swipe from the very edge of the screen
 * brings Android's own bar back for a moment, 24 high on the emulator, over the top of the tray. The
 * first row's buttons reach up under it, but their icons start below it.
 */
const val TRAY_HEADROOM = 16

/**
 * The notification tray: the screen dims and a 1200-wide sheet hangs from the top edge, with the
 * [controls] in rows at its top, then a heading, one row per notification and a row of buttons. A
 * tap on the dimmed screen closes it.
 *
 * The controls are first, against the screen's edge, so that each is always in the same place
 * however many notifications there are. The design's tray has none; they were asked for later.
 *
 * [needsAccess] replaces the list with one row that leads to Android's notification-access switch.
 */
@Composable
fun TrayOverlay(
    controls: List<TrayControl>,
    items: List<TrayItem>,
    needsAccess: Boolean,
    onOpen: (TrayItem) -> Unit,
    onAllowAccess: () -> Unit,
    onClearAll: () -> Unit,
    onScreenOff: () -> Unit,
    onClose: () -> Unit,
    scrim: Modifier = Modifier,
    sheet: Modifier = Modifier,
) {
    Box(Modifier.fillMaxSize()) {
        Box(scrim.fillMaxSize().pressable("Close notifications", onClick = onClose).background(Slate.Scrim))
        val shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
        Column(
            modifier = sheet
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
            for ((line, row) in controls.chunked(TRAY_CONTROLS_ACROSS).withIndex()) {
                val headroom = if (line == 0) TRAY_HEADROOM else 0
                Row(Modifier.fillMaxWidth().height((TRAY_CONTROL_HEIGHT + headroom).dp)) {
                    row.forEachIndexed { index, control ->
                        if (index > 0) Divider()
                        ControlButton(control, headroom, Modifier.weight(1f))
                    }
                    // A last row that is not full keeps its buttons the width of the others.
                    repeat(TRAY_CONTROLS_ACROSS - row.size) {
                        Divider()
                        Box(Modifier.weight(1f))
                    }
                }
                Line()
            }
            Box(Modifier.fillMaxWidth().height(76.dp).padding(start = 36.dp, top = 30.dp, end = 36.dp)) {
                Txt("Notifications", 24, color = Slate.Muted)
            }
            when {
                needsAccess -> TrayMessage("Allow notification access to see them here", onAllowAccess)
                items.isEmpty() -> TrayMessage("No notifications", null)
                // No more rows than there is room for under the controls.
                else -> for (item in items.take(TRAY_ROWS)) TrayRow(item) { onOpen(item) }
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

/**
 * A control: its icon over its name, as on the rail. A switch that is on is in the accent; one that
 * is off is a step dimmer than something that is simply tapped, so the two are not mistaken. With
 * [headroom] the button is that much taller at its top and its icon and name keep their place in
 * the 96 below.
 */
@Composable
private fun ControlButton(control: TrayControl, headroom: Int, modifier: Modifier) {
    val on = control.look == ControlLook.On
    val mark = when (control.look) {
        ControlLook.Plain -> Slate.Ink
        ControlLook.On -> Slate.Accent
        ControlLook.Off -> Slate.Muted
        ControlLook.Unavailable -> Slate.Disabled
    }
    val name = when (control.look) {
        ControlLook.On -> Slate.Accent
        ControlLook.Unavailable -> Slate.Disabled
        else -> Slate.Muted
    }
    val button = if (control.onClick == null) {
        modifier.fillMaxHeight().semantics { contentDescription = control.description }
    } else {
        modifier.fillMaxHeight().pressable(control.description, onClick = control.onClick)
    }
    Column(
        modifier = button.padding(top = headroom.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(control.icon, 40.dp, mark, 1.3f)
        Txt(
            control.label, 24,
            weight = if (on) Slate.SemiBold else Slate.Medium, color = name, lineHeight = 1f, singleLine = true,
        )
    }
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
