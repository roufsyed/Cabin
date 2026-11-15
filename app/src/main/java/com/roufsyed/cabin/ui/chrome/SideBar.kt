package com.roufsyed.cabin.ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.roufsyed.cabin.model.VolumeUi
import com.roufsyed.cabin.ui.components.pressable
import com.roufsyed.cabin.ui.icons.Icon
import com.roufsyed.cabin.ui.icons.SlateIcon
import com.roufsyed.cabin.ui.icons.SlateIcons
import com.roufsyed.cabin.ui.theme.Slate
import com.roufsyed.cabin.ui.theme.Txt

/** How wide the bar over other apps is, in design pixels: half the rail's 132. */
const val SIDE_BAR_WIDTH = 66

/**
 * The grip on the bar that tucks it away: a strip this high at the foot of the bar, under the volume
 * buttons. With the bar's one rule it takes 32 of the height, which leaves eight buttons 96 each.
 */
const val SIDE_BAR_GRIP = 31

/** The handle left at the screen's edge while the bar is tucked away, at the foot of the edge where the grip was. */
const val SIDE_BAR_HANDLE_WIDTH = 28
const val SIDE_BAR_HANDLE_HEIGHT = 96

/**
 * The rail as it stays on screen while another app is open: its buttons at half the width, down the
 * screen's right edge from top to bottom. There is no room for words at that width, so the buttons
 * are icons alone. Home is always Home here, since this is only ever seen outside Cabin; held, it
 * shows the recent apps. Above it, where the unit can show two apps side by side, is Split.
 *
 * With [onTuck], a grip under the volume buttons tucks the bar away, for when it lies over
 * something that is wanted in the app beneath; [SideBarHandle] is then all that is left of it.
 * [onTouch] is told when a finger comes down on the bar and when the last one leaves it, which is
 * what the wait before the bar tucks itself away is counted from.
 *
 * At 66 wide the buttons are narrower than the 96 the design asks of anything touched. With the grip
 * they are 96 tall on the design's 800 where there is a Split button, and 110 where there is not;
 * without the grip, 100 and 114.
 */
@Composable
fun SideBar(
    volume: VolumeUi,
    onPhone: () -> Unit,
    onLink: () -> Unit,
    /** Null where the unit has no split screen: the button is then left out. */
    onSplit: (() -> Unit)?,
    onHome: () -> Unit,
    onRecents: () -> Unit,
    onBack: () -> Unit,
    onVolumeUp: () -> Unit,
    onMute: () -> Unit,
    onVolumeDown: () -> Unit,
    modifier: Modifier = Modifier,
    /** Null where the handle is switched off in Settings: the bar then cannot be tucked away. */
    onTuck: (() -> Unit)? = null,
    /** True where the bar is down the screen's left edge. The design, and the default, is the right. */
    onLeft: Boolean = false,
    onTouch: ((held: Boolean) -> Unit)? = null,
) {
    val touched by rememberUpdatedState(onTouch)
    val watched = if (onTouch == null) Modifier else Modifier.pointerInput(Unit) {
        var held = false
        try {
            awaitPointerEventScope {
                while (true) {
                    // Seen on its way down to the buttons, and nothing is taken from them.
                    val now = awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed }
                    if (now != held) {
                        held = now
                        touched?.invoke(now)
                    }
                }
            }
        } finally {
            // The bar has gone from under the finger.
            if (held) touched?.invoke(false)
        }
    }
    Row(
        modifier
            .width(SIDE_BAR_WIDTH.dp)
            .fillMaxHeight()
            .then(watched)
            .background(Slate.Surface),
    ) {
        // The one edge that meets the app beside it has a hairline.
        if (!onLeft) Box(Modifier.width(1.dp).fillMaxHeight().background(Slate.Hairline))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            BarButton("Phone", SlateIcons.Handset, 1.3f, onPhone)
            BarButton("Phone link", SlateIcons.Link, 1.3f, onLink)
            if (onSplit != null) BarButton("Split screen", SlateIcons.Split, 1.3f, onSplit)
            BarButton("Home", SlateIcons.House, 1.3f, onHome, onLongClick = onRecents, onLongClickLabel = "Recent apps")
            BarButton("Go back", SlateIcons.Back, 1.3f, onBack)
            // The bar's one rule, between what goes somewhere and the volume.
            Box(Modifier.fillMaxWidth().height(1.dp).background(Slate.Hairline))
            BarButton("Volume up", SlateIcons.Plus, 1.5f, onVolumeUp)
            // The level sits under the speaker here; side by side they are wider than the bar.
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().pressable("Mute or unmute", onClick = onMute),
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (volume.muted) {
                    Icon(SlateIcons.SpeakerMuted, 30.dp, Slate.Accent, 1.4f)
                } else {
                    Icon(SlateIcons.Speaker, 30.dp, Slate.Ink, 1.4f)
                }
                Txt(
                    volume.level.toString(), 26,
                    color = if (volume.muted) Slate.Disabled else Slate.Text,
                    lineHeight = 1f, tabular = true,
                )
            }
            BarButton("Volume down", SlateIcons.Minus, 1.5f, onVolumeDown)
            if (onTuck != null) {
                // The grip, at the foot of the bar: it points the way the bar goes, off the screen's edge.
                Column(Modifier.fillMaxWidth().height(SIDE_BAR_GRIP.dp).pressable("Tuck the side bar away", onClick = onTuck)) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Slate.Hairline))
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Icon(if (onLeft) SlateIcons.ChevronLeft else SlateIcons.ChevronRight, 24.dp, Slate.Muted, 1.5f)
                    }
                }
            }
        }
        if (onLeft) Box(Modifier.width(1.dp).fillMaxHeight().background(Slate.Hairline))
    }
}

@Composable
private fun ColumnScope.BarButton(
    description: String,
    icon: SlateIcon,
    stroke: Float,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    Box(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .pressable(description, onLongClick = onLongClick, onLongClickLabel = onLongClickLabel, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, 38.dp, Slate.Ink, stroke) }
}

/**
 * What is left of the bar while it is tucked away: a tab in the screen's bottom corner, where the
 * bar's grip was. A tap brings the bar back. With [onLeft] it is the tab for the left edge.
 */
@Composable
fun SideBarHandle(onClick: () -> Unit, modifier: Modifier = Modifier, onLeft: Boolean = false) {
    // Rounded at its one corner that is not against an edge of the screen, with a chevron pointing
    // the way the bar comes.
    val shape = if (onLeft) RoundedCornerShape(topEnd = 12.dp) else RoundedCornerShape(topStart = 12.dp)
    Box(
        modifier
            .size(SIDE_BAR_HANDLE_WIDTH.dp, SIDE_BAR_HANDLE_HEIGHT.dp)
            .pressable("Bring the side bar back", onClick = onClick)
            .background(Slate.Surface, shape)
            .border(1.dp, Slate.Hairline, shape),
        contentAlignment = Alignment.Center,
    ) { Icon(if (onLeft) SlateIcons.ChevronRight else SlateIcons.ChevronLeft, 24.dp, Slate.Ink, 1.5f) }
}
