package com.roufsyed.cabin.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roufsyed.cabin.ui.components.pressable
import com.roufsyed.cabin.ui.icons.Icon
import com.roufsyed.cabin.ui.icons.SlateIcon
import com.roufsyed.cabin.ui.icons.SlateIcons
import com.roufsyed.cabin.ui.theme.Slate
import com.roufsyed.cabin.ui.theme.Txt

/** What the six wheel keys do. The tiles on the screen and the real keys call the same things. */
class WheelActions(
    val volumeUp: () -> Unit,
    val volumeDown: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val voice: () -> Unit,
    val mode: () -> Unit,
)

private val TileShape = RoundedCornerShape(16.dp)
private val BackShape = RoundedCornerShape(20.dp)

/**
 * Steering wheel keys: what each of the six keys does, as tiles that do the same thing when tapped,
 * so a key can be tried from the screen. Fills the 1036 x 488 content area.
 */
@Composable
fun KeysScreen(actions: WheelActions, onBack: () -> Unit) {
    Column(Modifier.size(1036.dp, 488.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(96.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .size(200.dp, 96.dp)
                    .pressable("Back to settings", onClick = onBack)
                    .border(1.dp, Slate.Outline, BackShape),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(SlateIcons.ChevronLeft, 28.dp, Slate.Text, 1.5f)
                Txt("Settings", 26, lineHeight = 1f)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Txt("Steering wheel keys", 34, lineHeight = 1f)
                Txt("Tap a key here to try what it does", 24, color = Slate.Muted, lineHeight = 1f)
            }
        }
        Spacer(Modifier.height(13.dp))
        Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(Modifier.fillMaxWidth().height(183.dp), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                Key("Volume up", "One step louder", SlateIcons.Plus, actions.volumeUp, Modifier.weight(1f))
                Key("Next", "Next track or station", SlateIcons.Next, actions.next, Modifier.weight(1f))
                Key("Voice", "Starts listening", SlateIcons.Mic, actions.voice, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().height(183.dp), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                Key("Volume down", "One step quieter", SlateIcons.Minus, actions.volumeDown, Modifier.weight(1f))
                Key("Previous", "Previous track or station", SlateIcons.Previous, actions.previous, Modifier.weight(1f))
                Key("Mode", "Next source in the row", SlateIcons.Mode, actions.mode, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Key(name: String, does: String, icon: SlateIcon, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .pressable("Steering key: ${name.lowercase()}", onClick = onClick)
            .background(Slate.Surface, TileShape)
            .border(1.dp, Slate.Hairline, TileShape)
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, 40.dp, Slate.Ink, 1.4f)
        Txt(name, 28, weight = Slate.SemiBold, ellipsis = true)
        Txt(does, 24, color = Slate.Muted, ellipsis = true)
    }
}
