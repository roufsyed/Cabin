package com.roufsyed.cabin.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.roufsyed.cabin.ui.icons.Icon
import com.roufsyed.cabin.ui.icons.SlateIcon
import com.roufsyed.cabin.ui.icons.SlateIcons
import com.roufsyed.cabin.ui.theme.Slate
import com.roufsyed.cabin.ui.theme.Txt

/** The large volume readout shown while two fingers are dragging. */
@Composable
fun VolumeHud(level: Int) = LevelHud(SlateIcons.Speaker, "Volume", level)

/** The same readout for brightness, for when the two-finger drag has been set to change that instead. */
@Composable
fun BrightnessHud(level: Int, night: Boolean) =
    LevelHud(if (night) SlateIcons.Moon else SlateIcons.Sun, "Brightness", level)

/**
 * A 280 x 112 pill at the top centre showing one level. It is only a readout and takes no touches.
 */
@Composable
private fun LevelHud(icon: SlateIcon, name: String, level: Int) {
    val shape = RoundedCornerShape(56.dp)
    Row(
        modifier = Modifier
            .offset(500.dp, 40.dp)
            .size(280.dp, 112.dp)
            .background(Slate.Raised, shape)
            .border(1.dp, Slate.Outline, shape)
            .semantics { contentDescription = "$name $level" },
        horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, 48.dp, Slate.Accent, 1.3f)
        Txt(level.toString(), 64, weight = Slate.Light, lineHeight = 1f, tabular = true, singleLine = true)
    }
}
