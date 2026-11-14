package com.roufsyed.cabin.ui.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp

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
