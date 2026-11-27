package com.roufsyed.cabin.ui.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.roufsyed.cabin.ui.theme.Slate

/** The playing track's own picture, as its player supplied it: a square with rounded corners. */
@Composable
fun CoverArt(art: ImageBitmap, side: Dp, corner: Dp, modifier: Modifier = Modifier) {
    Image(
        bitmap = art,
        contentDescription = "Cover art",
        modifier = modifier.size(side).clip(RoundedCornerShape(corner)),
        contentScale = ContentScale.Crop,
    )
}

@Composable
fun NoCover(side: Dp, corner: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(corner)
    val ground = Slate.Ground
    val groove = Slate.Outline
    val label = Slate.Raised
    val hole = Slate.Disabled
    Canvas(modifier.size(side).background(ground.copy(alpha = 0.5f), shape).border(1.dp, Slate.Hairline, shape)) {
        val unit = size.minDimension / RECORD_BOX
        fun ring(radius: Float, width: Float, colour: Color) =
            drawCircle(colour, radius * unit, style = Stroke(width * unit))
        ring(100f, 2f, groove)
        for (radius in listOf(84f, 68f, 52f)) ring(radius, 1.25f, groove)
        drawCircle(label, 34f * unit)
        ring(34f, 1.5f, groove)
        drawCircle(ground, 5f * unit)
        ring(5f, 2f, hole)
    }
}

private const val RECORD_BOX = 262f
