package com.roufsyed.cabin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.roufsyed.cabin.ui.theme.Slate

/**
 * The design's card: a filled, hairline-bordered rounded box. As in the design the 1 px border sits
 * inside the stated size, so [content] gets the box minus one pixel on every side.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    radius: Dp = 20.dp,
    fill: Color = Slate.Surface,
    border: Color = Slate.Hairline,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .background(fill, shape)
            .border(1.dp, border, shape)
            .padding(1.dp)
            .clip(RoundedCornerShape(radius - 1.dp)),
        content = content,
    )
}

/** The short 1 px rule the design puts between adjacent controls. */
@Composable
fun Rule(width: Dp, height: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(width, height).background(Slate.Hairline))
}
