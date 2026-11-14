package com.roufsyed.cabin.ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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

/**
 * The rail as it stays on screen while another app is open: its seven buttons at half the width,
 * down the screen's right edge from top to bottom. There is no room for words at that width, so the
 * buttons are icons alone. The third is always Home, since this is only ever seen outside Cabin.
 *
 * At 66 wide the buttons are narrower than the 96 the design asks of anything touched; they are 114
 * tall on the design's 800, which is what they are aimed at.
 */
@Composable
fun SideBar(
    volume: VolumeUi,
    onPhone: () -> Unit,
    onLink: () -> Unit,
    onHome: () -> Unit,
    onBack: () -> Unit,
    onVolumeUp: () -> Unit,
    onMute: () -> Unit,
    onVolumeDown: () -> Unit,
    modifier: Modifier = Modifier,
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
            BarButton("Home", SlateIcons.House, 1.3f, onHome)
            BarButton("Go back", SlateIcons.Back, 1.3f, onBack)
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
        }
    }
}

@Composable
private fun ColumnScope.BarButton(description: String, icon: SlateIcon, stroke: Float, onClick: () -> Unit) {
    Box(
        Modifier.weight(1f).fillMaxWidth().pressable(description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, 38.dp, Slate.Ink, stroke) }
}
