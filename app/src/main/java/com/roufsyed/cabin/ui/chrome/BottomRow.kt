package com.roufsyed.cabin.ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roufsyed.cabin.ui.components.Panel
import com.roufsyed.cabin.ui.components.Rule
import com.roufsyed.cabin.ui.components.pressable
import com.roufsyed.cabin.ui.icons.Icon
import com.roufsyed.cabin.ui.icons.SlateIcons
import com.roufsyed.cabin.ui.model.BrightnessUi
import com.roufsyed.cabin.ui.model.SourceId
import com.roufsyed.cabin.ui.model.SourceUi
import com.roufsyed.cabin.ui.theme.Slate
import com.roufsyed.cabin.ui.theme.Txt

/** The bottom row, 1036 x 96, identical on every screen: brightness stepper, then the source row. */
@Composable
fun BottomRow(
    brightness: BrightnessUi,
    sources: List<SourceUi>,
    onBrightnessDown: () -> Unit,
    onBrightnessMode: () -> Unit,
    onBrightnessUp: () -> Unit,
    onSource: (SourceId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.size(1036.dp, 96.dp), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
        BrightnessStepper(brightness, onBrightnessDown, onBrightnessMode, onBrightnessUp)
        SourceRow(sources, onSource)
    }
}

@Composable
private fun BrightnessStepper(
    brightness: BrightnessUi,
    onDown: () -> Unit,
    onMode: () -> Unit,
    onUp: () -> Unit,
) {
    Panel(Modifier.size(328.dp, 96.dp)) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(96.dp).fillMaxHeight().pressable("Brightness down", onClick = onDown),
                contentAlignment = Alignment.Center,
            ) { Icon(SlateIcons.Minus, 40.dp, Slate.Ink, 1.5f) }
            Rule(1.dp, 44.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pressable("Night mode: auto, day or night", onClick = onMode),
                verticalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (brightness.night) {
                        Icon(SlateIcons.Moon, 32.dp, Slate.Accent, 1.4f)
                    } else {
                        Icon(SlateIcons.Sun, 32.dp, Slate.Ink, 1.4f)
                    }
                    Txt(brightness.level.toString(), 30, lineHeight = 1f, tabular = true)
                }
                Txt(brightness.mode.label, 24, color = Slate.Muted, lineHeight = 1f)
            }
            Rule(1.dp, 44.dp)
            Box(
                Modifier.width(96.dp).fillMaxHeight().pressable("Brightness up", onClick = onUp),
                contentAlignment = Alignment.Center,
            ) { Icon(SlateIcons.Plus, 40.dp, Slate.Ink, 1.5f) }
        }
    }
}

/**
 * Shares the row's 680 px between the visible sources the way the design does: each gets its label
 * width plus an equal share of what is left, and the last one takes the remainder.
 */
internal fun sourceWidths(sources: List<SourceId>, total: Int = 680): List<Int> {
    if (sources.isEmpty()) return emptyList()
    val slack = (total - sources.sumOf { it.baseWidth }) / sources.size.toFloat()
    var used = 0
    return sources.mapIndexed { index, source ->
        val width = if (index == sources.lastIndex) total - used else (source.baseWidth + slack).toInt()
        used += width
        width
    }
}

@Composable
private fun SourceRow(sources: List<SourceUi>, onSource: (SourceId) -> Unit) {
    Panel(Modifier.size(682.dp, 96.dp)) {
        val widths = sourceWidths(sources.map { it.id })
        Row(Modifier.fillMaxSize()) {
            sources.forEachIndexed { index, source ->
                Box(
                    modifier = Modifier
                        .width(widths[index].dp)
                        .fillMaxHeight()
                        .pressable("${source.id.label} source") { onSource(source.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (index > 0) {
                        Box(
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(top = 25.dp)
                                .size(1.dp, 44.dp)
                                .background(Slate.Hairline),
                        )
                    }
                    Txt(
                        source.id.label, 26,
                        weight = if (source.active) Slate.SemiBold else Slate.Medium,
                        color = if (source.active) Slate.Accent else Slate.Muted,
                        lineHeight = 1f, singleLine = true,
                    )
                }
            }
        }
    }
}
