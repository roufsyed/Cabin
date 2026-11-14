package com.roufsyed.cabin.ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** The grip on the bar that tucks it away: a strip this high between the bar's two groups of buttons. */
const val SIDE_BAR_GRIP = 32

/** The handle left at the screen's edge while the bar is tucked away. */
const val SIDE_BAR_HANDLE_WIDTH = 28
const val SIDE_BAR_HANDLE_HEIGHT = 96

/** How many of the bar's buttons there are, and how many of them sit above the grip. */
fun sideBarButtons(split: Boolean): Int = if (split) 8 else 7
fun sideBarButtonsAboveGrip(split: Boolean): Int = if (split) 5 else 4

/**
 * The rail as it stays on screen while another app is open: its buttons at half the width, down the
 * screen's right edge from top to bottom. There is no room for words at that width, so the buttons
 * are icons alone. Home is always Home here, since this is only ever seen outside Cabin; held, it
 * shows the recent apps. Above it, where the unit can show two apps side by side, is Split.
 *
 * With [onTuck], a grip between the bar's two groups tucks the bar away, for when it lies over
 * something that is wanted in the app beneath; [SideBarHandle] is then all that is left of it.
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
) {
    Row(
        modifier
            .width(SIDE_BAR_WIDTH.dp)
            .fillMaxHeight()
            .background(Slate.Surface),
    ) {
        // The one edge that meets the app beside it.
        Box(Modifier.width(1.dp).fillMaxHeight().background(Slate.Hairline))
        Column(Modifier.fillMaxSize()) {
            BarButton("Phone", SlateIcons.Handset, 1.3f, onPhone)
            BarButton("Phone link", SlateIcons.Link, 1.3f, onLink)
            if (onSplit != null) BarButton("Split screen", SlateIcons.Split, 1.3f, onSplit)
            BarButton("Home", SlateIcons.House, 1.3f, onHome, onLongClick = onRecents, onLongClickLabel = "Recent apps")
            BarButton("Go back", SlateIcons.Back, 1.3f, onBack)
            if (onTuck == null) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Slate.Hairline))
            } else {
                // The grip: it points the way the bar goes, off the screen's edge.
                Column(Modifier.fillMaxWidth().height(SIDE_BAR_GRIP.dp).pressable("Tuck the side bar away", onClick = onTuck)) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Slate.Hairline))
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Icon(SlateIcons.ChevronRight, 24.dp, Slate.Muted, 1.5f)
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Slate.Hairline))
                }
            }
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
        }
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
 * What is left of the bar while it is tucked away: a tab against the screen's edge, where the bar's
 * grip was. A tap brings the bar back.
 */
@Composable
fun SideBarHandle(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
    Box(
        modifier
            .size(SIDE_BAR_HANDLE_WIDTH.dp, SIDE_BAR_HANDLE_HEIGHT.dp)
            .pressable("Bring the side bar back", onClick = onClick)
            .background(Slate.Surface, shape)
            .border(1.dp, Slate.Hairline, shape),
        contentAlignment = Alignment.Center,
    ) { Icon(SlateIcons.ChevronLeft, 24.dp, Slate.Ink, 1.5f) }
}
