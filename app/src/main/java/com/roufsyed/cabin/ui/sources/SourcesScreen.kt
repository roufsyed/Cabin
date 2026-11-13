package com.roufsyed.cabin.ui.sources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roufsyed.cabin.model.SourceId
import com.roufsyed.cabin.model.SourceTile
import com.roufsyed.cabin.ui.apps.GridTile

/**
 * Arranging the sources: the five of them as tiles in the Apps grid's first row. The tapped one is
 * outlined in the accent; hidden ones are dimmed.
 */
@Composable
fun SourcesGrid(tiles: List<SourceTile>, picked: SourceId?, onTap: (SourceId) -> Unit) {
    Box(Modifier.size(1036.dp, 488.dp)) {
        Row(Modifier.fillMaxWidth().height(154.dp), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            for (tile in tiles) {
                GridTile(
                    label = tile.id.label,
                    description = "Select ${tile.id.label}",
                    dimmed = tile.hidden,
                    outlined = tile.id == picked,
                    onClick = { onTap(tile.id) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    labelSize = 26,
                )
            }
        }
    }
}
