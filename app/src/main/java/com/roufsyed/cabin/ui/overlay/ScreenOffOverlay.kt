package com.roufsyed.cabin.ui.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.roufsyed.cabin.model.ScreenOffUi
import com.roufsyed.cabin.ui.components.pressable
import com.roufsyed.cabin.ui.home.StatusIcons
import com.roufsyed.cabin.ui.icons.Icon
import com.roufsyed.cabin.ui.icons.SlateIcons
import com.roufsyed.cabin.ui.theme.Slate
import com.roufsyed.cabin.ui.theme.Txt

/** The widest a line may be before it ends in an ellipsis: the screen, less a margin on each side. */
private val LineWidth = 1040.dp

/** How much of the text's strength a status mark that is off keeps. It is struck through as well. */
private const val OFF_MARK = 0.5f

/**
 * "Screen off": a black screen for night driving, showing what was chosen in Settings > Screen off.
 * Left as it is, that is the design's dim clock, date and "Tap to wake". The whole screen is one
 * button that wakes it. The activity also turns the backlight right down while this is showing.
 *
 * Every line is one grey. At night it is the design's dim one; by day it is a step lighter, so that
 * it can still be made out in daylight. Night is the brightness control's own idea of it, so the
 * text follows the car's headlights wherever Auto brightness does.
 */
@Composable
fun ScreenOffOverlay(ui: ScreenOffUi, onWake: () -> Unit) {
    val tone = if (ui.night) Slate.Disabled else Slate.Muted
    Column(
        modifier = Modifier
            .fillMaxSize()
            .pressable("Wake screen", onClick = onWake)
            .background(Color.Black),
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (ui.time != null) {
            Txt(ui.time, 168, weight = Slate.Light, color = tone, lineHeight = 1f, tabular = true, singleLine = true)
        }
        if (ui.date != null) Txt(ui.date, 28, color = tone)
        if (ui.status != null) StatusIcons(ui.status, on = tone, off = tone.copy(alpha = OFF_MARK))
        if (ui.turn != null) TurnLine(ui.turn, ui.turnGlyph, tone)
        if (ui.playing != null) Txt(ui.playing, 28, Modifier.widthIn(max = LineWidth), color = tone, ellipsis = true)
        if (ui.hint) Txt("Tap to wake", 24, color = tone)
    }
}

/** The next turn on one line: its arrow, then how far it is and onto which road. */
@Composable
private fun TurnLine(text: String, glyph: ImageBitmap?, tone: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (glyph != null) {
            // The maps app's own arrow for this turn, in the same grey as the text.
            Image(
                bitmap = glyph,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(tone),
            )
        } else {
            // The design's own arrow, where the maps app gave none: drawn about as heavy as the text.
            Icon(SlateIcons.TurnRight, 28.dp, tone, 1.25f)
        }
        Txt(text, 34, Modifier.widthIn(max = LineWidth - 56.dp), color = tone, ellipsis = true)
    }
}
