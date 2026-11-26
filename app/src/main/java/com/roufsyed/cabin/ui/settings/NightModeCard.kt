package com.roufsyed.cabin.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.roufsyed.cabin.ui.components.Panel
import com.roufsyed.cabin.ui.components.Rule
import com.roufsyed.cabin.ui.components.pressable
import com.roufsyed.cabin.ui.icons.Icon
import com.roufsyed.cabin.ui.icons.SlateIcons
import com.roufsyed.cabin.ui.theme.Slate
import com.roufsyed.cabin.ui.theme.Txt

@Immutable
data class NightHour(val title: String, val description: String, val value: String, val onStep: (Int) -> Unit)

@Composable
fun NightModeCard(
    manual: Boolean,
    autoSummary: String,
    autoDetail: String,
    hours: List<NightHour>,
    onPick: (Boolean) -> Unit,
) {
    Box(Modifier.size(1036.dp, 488.dp)) {
        Panel(Modifier.fillMaxWidth(), radius = 16.dp) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Column(Modifier.weight(1f)) {
                    Option("Auto", autoSummary, "Auto night mode", picked = !manual) { onPick(false) }
                    Txt(
                        autoDetail, 24,
                        modifier = Modifier.padding(start = 28.dp, end = 28.dp, bottom = 24.dp),
                        color = Slate.Muted,
                    )
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(Slate.Hairline))
                Column(Modifier.weight(1f)) {
                    Option("Manual", "At the hours you set", "Manual night mode", picked = manual) { onPick(true) }
                    if (manual) for (hour in hours) HourRow(hour)
                }
            }
        }
    }
}

@Composable
private fun Option(title: String, subtitle: String, description: String, picked: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .pressable(description, onClick = onClick)
            .semantics { selected = picked }
            .padding(start = 28.dp, end = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Txt(title, 30, weight = Slate.SemiBold, color = if (picked) Slate.Accent else Slate.Text, ellipsis = true)
            Txt(subtitle, 24, color = Slate.Muted, ellipsis = true)
        }
        Icon(
            if (picked) SlateIcons.RingChosen else SlateIcons.Ring, 34.dp,
            if (picked) Slate.Accent else Slate.Muted, 1.3f,
        )
    }
}

@Composable
private fun HourRow(hour: NightHour) {
    Row(
        modifier = Modifier.fillMaxWidth().height(112.dp).padding(start = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Txt(hour.title, 30, weight = Slate.SemiBold, ellipsis = true)
            Txt("Starts at", 24, color = Slate.Muted, ellipsis = true)
        }
        Box(
            Modifier.width(96.dp).fillMaxHeight().pressable("${hour.description}: earlier") { hour.onStep(-1) },
            contentAlignment = Alignment.Center,
        ) { Icon(SlateIcons.Minus, 40.dp, Slate.Ink, 1.5f) }
        Rule(1.dp, 44.dp)
        Box(
            Modifier.width(136.dp).semantics { contentDescription = "${hour.description}: ${hour.value}" },
            contentAlignment = Alignment.Center,
        ) { Txt(hour.value, 28, weight = Slate.SemiBold, color = Slate.Accent, lineHeight = 1f, tabular = true) }
        Rule(1.dp, 44.dp)
        Box(
            Modifier.width(96.dp).fillMaxHeight().pressable("${hour.description}: later") { hour.onStep(1) },
            contentAlignment = Alignment.Center,
        ) { Icon(SlateIcons.Plus, 40.dp, Slate.Ink, 1.5f) }
    }
}
