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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.roufsyed.cabin.ui.components.Panel
import com.roufsyed.cabin.ui.components.Rule
import com.roufsyed.cabin.ui.components.pressable
import com.roufsyed.cabin.ui.icons.Icon
import com.roufsyed.cabin.ui.icons.SlateIcons
import com.roufsyed.cabin.model.BrightnessUi
import com.roufsyed.cabin.model.SourceId
import com.roufsyed.cabin.model.SourceUi
import com.roufsyed.cabin.ui.theme.Slate
import com.roufsyed.cabin.ui.theme.Txt
import kotlin.math.ceil

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
                Txt(brightness.label, 24, color = Slate.Muted, lineHeight = 1f)
            }
            Rule(1.dp, 44.dp)
            Box(
                Modifier.width(96.dp).fillMaxHeight().pressable("Brightness up", onClick = onUp),
                contentAlignment = Alignment.Center,
            ) { Icon(SlateIcons.Plus, 40.dp, Slate.Ink, 1.5f) }
        }
    }
}

internal const val SOURCE_NAME_MARGIN = 14

/**
 * Shares the row's 680 px between the visible sources the way the design does: each gets its label
 * width plus an equal share of what is left, and the last one takes the remainder.
 */
internal fun sourceWidths(names: List<Int>, total: Int = 680): List<Int> {
    if (names.isEmpty()) return emptyList()
    val fitted = fitNames(names, total - names.size * 2 * SOURCE_NAME_MARGIN)
    val slack = (total - fitted.sum()) / fitted.size.toFloat()
    var used = 0
    return fitted.mapIndexed { index, name ->
        val width = if (index == fitted.lastIndex) total - used else (name + slack).toInt()
        used += width
        width
    }
}

internal fun fitNames(names: List<Int>, room: Int): List<Int> {
    if (names.sum() <= room) return names
    var left = room.coerceAtLeast(0)
    var cap = 0
    val ascending = names.sorted()
    for ((index, width) in ascending.withIndex()) {
        val remaining = ascending.size - index
        if (width * remaining <= left) {
            left -= width
        } else {
            cap = left / remaining
            break
        }
    }
    return names.map { minOf(it, cap) }
}

@Composable
private fun nameWidths(sources: List<SourceUi>): List<Int> {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current.density
    val labels = sources.map { it.label }
    return remember(labels, measurer, density) {
        val style = Slate.style(26, weight = Slate.SemiBold, lineHeight = 1f)
        sources.map { source ->
            if (source.label == source.id.label) {
                source.id.baseWidth
            } else {
                ceil(measurer.measure(source.label, style, softWrap = false, maxLines = 1).size.width / density).toInt()
            }
        }
    }
}

@Composable
private fun SourceRow(sources: List<SourceUi>, onSource: (SourceId) -> Unit) {
    Panel(Modifier.size(682.dp, 96.dp)) {
        val widths = sourceWidths(nameWidths(sources))
        Row(Modifier.fillMaxSize()) {
            sources.forEachIndexed { index, source ->
                Box(
                    modifier = Modifier
                        .width(widths[index].dp)
                        .fillMaxHeight()
                        .pressable("${source.label} source") { onSource(source.id) },
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
                        source.label, 26,
                        modifier = Modifier.padding(horizontal = SOURCE_NAME_MARGIN.dp),
                        weight = if (source.active) Slate.SemiBold else Slate.Medium,
                        color = if (source.active) Slate.Accent else Slate.Muted,
                        lineHeight = 1f, ellipsis = true,
                    )
                }
            }
        }
    }
}
