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
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roufsyed.cabin.ui.components.pressable
import com.roufsyed.cabin.ui.icons.Icon
import com.roufsyed.cabin.ui.icons.SlateIcons
import com.roufsyed.cabin.ui.theme.Slate
import com.roufsyed.cabin.ui.theme.Txt

/**
 * One Settings tile. With [value] null it leads somewhere and shows a chevron; otherwise it shows the
 * value on the right, in the accent when [on].
 */
@Immutable
data class SettingsTile(
    val title: String,
    val subtitle: String,
    val description: String,
    val value: String? = null,
    val on: Boolean = false,
    val onClick: () -> Unit,
)

private val TileShape = RoundedCornerShape(16.dp)

/** Settings: two columns of 112-high tiles, 13 apart, in the 1036 x 488 content area. */
@Composable
fun SettingsGrid(tiles: List<SettingsTile>) {
    Column(Modifier.size(1036.dp, 488.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        for (row in tiles.chunked(2)) {
            Row(Modifier.fillMaxWidth().height(112.dp), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                for (tile in row) Tile(tile, Modifier.weight(1f).fillMaxHeight())
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Tile(tile: SettingsTile, modifier: Modifier) {
    val chevron = tile.value == null
    Row(
        modifier = modifier
            .pressable(tile.description, onClick = tile.onClick)
            .background(Slate.Surface, TileShape)
            .border(1.dp, Slate.Hairline, TileShape)
            // The design's 28 px (24 on the chevron side), plus the 1 px border the content sits inside.
            .padding(start = 29.dp, end = if (chevron) 25.dp else 29.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Txt(tile.title, 30, weight = Slate.SemiBold, ellipsis = true)
            Txt(tile.subtitle, 24, color = Slate.Muted, ellipsis = true)
        }
        if (chevron) {
            Icon(SlateIcons.ChevronRight, 28.dp, Slate.Muted, 1.4f)
        } else {
            Txt(
                tile.value.orEmpty(), 28,
                weight = Slate.SemiBold, color = if (tile.on) Slate.Accent else Slate.Muted, lineHeight = 1f,
            )
        }
    }
}
