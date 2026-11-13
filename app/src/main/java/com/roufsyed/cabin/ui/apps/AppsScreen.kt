package com.roufsyed.cabin.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.roufsyed.cabin.model.AppEntry
import com.roufsyed.cabin.model.AppTile
import com.roufsyed.cabin.model.Glyph
import com.roufsyed.cabin.ui.components.pressable
import com.roufsyed.cabin.ui.icons.Icon
import com.roufsyed.cabin.ui.icons.SlateIcon
import com.roufsyed.cabin.ui.icons.SlateIcons
import com.roufsyed.cabin.ui.theme.Slate
import com.roufsyed.cabin.ui.theme.Txt
import kotlin.math.roundToInt

private val TileShape = RoundedCornerShape(16.dp)

/**
 * One page of the Apps grid: 5 columns by 3 rows of 154-high tiles, 13 apart, in the 1036 x 488 content
 * area. While arranging, [picked] is outlined in the accent and hidden tiles are shown dimmed.
 */
@Composable
fun AppsGrid(tiles: List<AppTile>, picked: String?, onTap: (AppEntry) -> Unit) {
    Column(Modifier.size(1036.dp, 488.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        for (row in 0 until 3) {
            Row(Modifier.fillMaxWidth().height(154.dp), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                for (column in 0 until 5) {
                    val tile = tiles.getOrNull(row * 5 + column)
                    if (tile == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        GridTile(
                            label = tile.app.label,
                            dimmed = tile.hidden,
                            outlined = tile.app.id == picked,
                            onClick = { onTap(tile.app) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        ) { GlyphIcon(tile.app, 46.dp) }
                    }
                }
            }
        }
    }
}

/** A grid tile: an optional mark over a label. Also used, without a mark, for the sources. */
@Composable
fun GridTile(
    label: String,
    dimmed: Boolean,
    outlined: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    labelSize: Int = 24,
    description: String = label,
    mark: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .alpha(if (dimmed) 0.35f else 1f)
            .pressable(description, onClick = onClick)
            .background(Slate.Surface, TileShape)
            .border(1.dp, if (outlined) Slate.Accent else Slate.Hairline, TileShape),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        mark?.invoke()
        Txt(label, labelSize, modifier = Modifier.padding(horizontal = 12.dp), ellipsis = true)
    }
}

/** The mark for an app: its line icon, or a ring with its initial when the kind of app is unknown. */
@Composable
fun GlyphIcon(app: AppEntry, size: Dp, color: Color = Slate.Ink) {
    val icon = iconFor(app.glyph)
    if (icon != null) {
        Icon(icon, size, color, 1.3f)
    } else {
        Monogram(app.letter, size, color)
    }
}

/**
 * A ring with one letter in it, drawn to the design's numbers: on a 24-unit grid the ring has radius 9
 * and the letter is 12.5 units tall with its baseline at 16.3.
 */
@Composable
fun Monogram(letter: String, size: Dp, color: Color = Slate.Ink) {
    val unit = size.value / 24f
    // Never below 24 px, the smallest text the design allows, even on the tray's smaller marks.
    val fontSize = maxOf(24, (12.5f * unit).roundToInt())
    // A tight one-line box of height fontSize has its baseline 0.85 of the way down.
    val top = 16.3f * unit - fontSize * 0.85f
    Box(Modifier.size(size)) {
        Icon(SlateIcons.Ring, size, color, 1.3f)
        Txt(
            letter, fontSize,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = top.dp),
            color = color, lineHeight = 1f, singleLine = true,
        )
    }
}

private fun iconFor(glyph: Glyph): SlateIcon? = when (glyph) {
    Glyph.Browser -> SlateIcons.Browser
    Glyph.Clock -> SlateIcons.Clock
    Glyph.Files -> SlateIcons.Files
    Glyph.Gallery -> SlateIcons.Gallery
    Glyph.Maps -> SlateIcons.Maps
    Glyph.Music -> SlateIcons.Music
    Glyph.Phone -> SlateIcons.Handset
    Glyph.Link -> SlateIcons.Link
    Glyph.Radio -> SlateIcons.Radio
    Glyph.Settings -> SlateIcons.Settings
    Glyph.Sound -> SlateIcons.Sound
    Glyph.Video -> SlateIcons.Video
    Glyph.Voice -> SlateIcons.Mic
    Glyph.Weather -> SlateIcons.Weather
    Glyph.Letter -> null
}
