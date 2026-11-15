package com.roufsyed.cabin.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.roufsyed.cabin.ui.components.pressable
import com.roufsyed.cabin.ui.icons.Icon
import com.roufsyed.cabin.ui.icons.SlateIcons
import com.roufsyed.cabin.ui.theme.Slate
import com.roufsyed.cabin.ui.theme.Txt

/**
 * One Settings tile. With [value] null it leads somewhere and shows a chevron; otherwise it shows the
 * value on the right, in the accent when [on].
 *
 * With [options] the value is one of a few. The tile then has a down chevron beside its value, and a
 * tap drops the options as a list over the tile's column, the one at [chosen] marked. [onPick] is
 * told which was tapped, and [onClick] is not used.
 */
@Immutable
data class SettingsTile(
    val title: String,
    val subtitle: String,
    val description: String,
    val value: String? = null,
    val on: Boolean = false,
    val options: List<String> = emptyList(),
    val chosen: Int = -1,
    val onPick: (Int) -> Unit = {},
    val onClick: () -> Unit = {},
)

private val TileShape = RoundedCornerShape(16.dp)

/** Settings: two columns of 112-high tiles, 13 apart, in the 1036 x 488 content area. */
@Composable
fun SettingsGrid(tiles: List<SettingsTile>) {
    // Which tile's list of options is down, if any.
    var open by remember { mutableStateOf<Int?>(null) }
    val listed = open?.let { tiles.getOrNull(it) }?.takeIf { it.options.isNotEmpty() }
    Box(Modifier.size(1036.dp, 488.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            for ((row, pair) in tiles.chunked(2).withIndex()) {
                Row(Modifier.fillMaxWidth().height(112.dp), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    for ((column, tile) in pair.withIndex()) {
                        val index = row * 2 + column
                        Tile(
                            tile = tile,
                            onClick = if (tile.options.isEmpty()) tile.onClick else ({ open = index }),
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (listed != null) {
            val close = { open = null }
            // Back, or a tap anywhere beside the list, closes it and changes nothing.
            BackHandler(onBack = close)
            Box(Modifier.fillMaxSize().pressable("Close the list", onClick = close))
            OptionList(
                tile = listed,
                onPick = { option ->
                    listed.onPick(option)
                    close()
                },
                modifier = Modifier.align(if (open?.rem(2) == 1) Alignment.TopEnd else Alignment.TopStart),
            )
        }
    }
}

@Composable
private fun Tile(tile: SettingsTile, onClick: () -> Unit, modifier: Modifier) {
    val leads = tile.value == null
    val drops = tile.options.isNotEmpty()
    Row(
        modifier = modifier
            .pressable(tile.description, onClick = onClick)
            .background(Slate.Surface, TileShape)
            .border(1.dp, Slate.Hairline, TileShape)
            // The design's 28 px (24 on a chevron's side), plus the 1 px border the content sits inside.
            .padding(start = 29.dp, end = if (leads || drops) 25.dp else 29.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Txt(tile.title, 30, weight = Slate.SemiBold, ellipsis = true)
            Txt(tile.subtitle, 24, color = Slate.Muted, ellipsis = true)
        }
        if (leads) {
            Icon(SlateIcons.ChevronRight, 28.dp, Slate.Muted, 1.4f)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt(
                    tile.value.orEmpty(), 28,
                    weight = Slate.SemiBold, color = if (tile.on) Slate.Accent else Slate.Muted, lineHeight = 1f,
                )
                if (drops) Icon(SlateIcons.ChevronDown, 28.dp, Slate.Muted, 1.4f)
            }
        }
    }
}

/**
 * A tile's options, dropped as a list over the whole of the tile's column: it is as wide as a tile
 * and as high as the grid, so that its rows, which share that height, are well over the 96 the
 * design asks of anything touched. The one in use is in the accent, with a dot in its ring.
 */
@Composable
private fun OptionList(tile: SettingsTile, onPick: (Int) -> Unit, modifier: Modifier) {
    Column(
        modifier
            .size(511.5.dp, 488.dp)
            .background(Slate.Raised, TileShape)
            .border(1.dp, Slate.Outline, TileShape)
            .padding(1.dp),
    ) {
        for ((index, option) in tile.options.withIndex()) {
            val chosen = index == tile.chosen
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Slate.Outline))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pressable("${tile.title}: $option", onClick = { onPick(index) })
                    .semantics { selected = chosen }
                    .padding(start = 28.dp, end = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt(
                    option, 30,
                    modifier = Modifier.weight(1f),
                    weight = Slate.SemiBold, color = if (chosen) Slate.Accent else Slate.Text, ellipsis = true,
                )
                Icon(
                    if (chosen) SlateIcons.RingChosen else SlateIcons.Ring, 34.dp,
                    if (chosen) Slate.Accent else Slate.Muted, 1.3f,
                )
            }
        }
    }
}
