package com.roufsyed.cabin.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.roufsyed.cabin.model.ThemeId
import com.roufsyed.cabin.ui.components.pressable
import com.roufsyed.cabin.ui.icons.Icon
import com.roufsyed.cabin.ui.icons.SlateIcons
import com.roufsyed.cabin.ui.theme.Palette
import com.roufsyed.cabin.ui.theme.Themes
import com.roufsyed.cabin.ui.theme.Txt

private val TileShape = RoundedCornerShape(16.dp)

/**
 * The themes, as two rows of five tiles the size of the Apps grid's, in the 1036 x 488 content area.
 * Each tile is drawn in its own theme's colours, so it shows what it would give: its surface, its
 * outline, its text and its accent. The one in use has a dot in its ring and an accent outline.
 */
@Composable
fun ThemeGrid(current: ThemeId, onPick: (ThemeId) -> Unit) {
    Column(Modifier.size(1036.dp, 488.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        for (row in ThemeId.entries.chunked(5)) {
            Row(Modifier.fillMaxWidth().height(154.dp), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                for (id in row) {
                    ThemeTile(
                        id = id,
                        palette = Themes.palette(id),
                        chosen = id == current,
                        onClick = { onPick(id) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeTile(id: ThemeId, palette: Palette, chosen: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .pressable("${id.label} theme", onClick = onClick)
            .semantics { selected = chosen }
            .background(palette.surface, TileShape)
            .border(1.dp, if (chosen) palette.accent else palette.hairline, TileShape),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(if (chosen) SlateIcons.RingChosen else SlateIcons.Ring, 46.dp, palette.accent, 1.3f)
        Txt(id.label, 24, modifier = Modifier.padding(horizontal = 12.dp), color = palette.text, ellipsis = true)
    }
}
