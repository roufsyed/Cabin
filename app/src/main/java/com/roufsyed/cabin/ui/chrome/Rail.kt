package com.roufsyed.cabin.ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roufsyed.cabin.ui.components.Panel
import com.roufsyed.cabin.ui.components.Rule
import com.roufsyed.cabin.ui.components.pressable
import com.roufsyed.cabin.ui.icons.Icon
import com.roufsyed.cabin.ui.icons.SlateIcon
import com.roufsyed.cabin.ui.icons.SlateIcons
import com.roufsyed.cabin.model.VolumeUi
import com.roufsyed.cabin.ui.theme.Slate
import com.roufsyed.cabin.ui.theme.Txt

/**
 * The right-hand rail, 132 x 720, identical on every screen: Phone, Link, Apps (or Home when not on
 * Home), then the volume rocker with mute. With [onRecents], holding Apps or Home shows the recent
 * apps; without it a hold is a tap.
 *
 * Back is not here. It is on the bar that stays over other apps (see [SideBar]), which is where it
 * is needed; Cabin's own screens each have their way back.
 */
@Composable
fun Rail(
    onHome: Boolean,
    volume: VolumeUi,
    onPhone: () -> Unit,
    onLink: () -> Unit,
    onAppsOrHome: () -> Unit,
    onRecents: (() -> Unit)?,
    onVolumeUp: () -> Unit,
    onMute: () -> Unit,
    onVolumeDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Panel(modifier.size(132.dp, 720.dp)) {
        Column(Modifier.fillMaxSize()) {
            RailButton("Phone", "Phone", SlateIcons.Handset, onPhone)
            RailButton("Phone link", "Link", SlateIcons.Link, onLink)
            if (onHome) {
                RailButton("All apps", "Apps", SlateIcons.AppsGrid, onAppsOrHome, onLongClick = onRecents)
            } else {
                RailButton("Home", "Home", SlateIcons.House, onAppsOrHome, onLongClick = onRecents)
            }
            Box(Modifier.size(130.dp, 310.dp)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Slate.Hairline))
                Column(
                    modifier = Modifier.fillMaxSize().padding(top = 1.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(130.dp, 104.dp).pressable("Volume up", onClick = onVolumeUp),
                        contentAlignment = Alignment.Center,
                    ) { Icon(SlateIcons.Plus, 44.dp, Slate.Ink, 1.5f) }
                    Rule(44.dp, 1.dp)
                    Row(
                        modifier = Modifier.size(130.dp, 99.dp).pressable("Mute or unmute", onClick = onMute),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (volume.muted) {
                            Icon(SlateIcons.SpeakerMuted, 32.dp, Slate.Accent, 1.4f)
                        } else {
                            Icon(SlateIcons.Speaker, 32.dp, Slate.Ink, 1.4f)
                        }
                        Txt(
                            volume.level.toString(), 30,
                            color = if (volume.muted) Slate.Disabled else Slate.Text,
                            lineHeight = 1f, tabular = true,
                        )
                    }
                    Rule(44.dp, 1.dp)
                    Box(
                        Modifier.size(130.dp, 104.dp).pressable("Volume down", onClick = onVolumeDown),
                        contentAlignment = Alignment.Center,
                    ) { Icon(SlateIcons.Minus, 44.dp, Slate.Ink, 1.5f) }
                }
            }
        }
    }
}

@Composable
private fun RailButton(
    description: String,
    label: String,
    icon: SlateIcon,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .size(130.dp, 136.dp)
            .pressable(description, onLongClick = onLongClick, onLongClickLabel = "Recent apps", onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, 44.dp, Slate.Ink, 1.3f)
        Txt(label, 24, color = Slate.Muted, lineHeight = 1f)
    }
}
