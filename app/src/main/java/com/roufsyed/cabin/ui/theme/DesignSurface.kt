package com.roufsyed.cabin.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

const val DESIGN_WIDTH = 1280
const val DESIGN_HEIGHT = 800

/**
 * Lays [content] out on the design's 1280 x 800 canvas, whatever density the unit reports.
 *
 * Inside this surface 1.dp and 1.sp both equal one design pixel, so every number can be taken from the
 * design as written. On a 1280 x 800 panel the scale is exactly 1; on any other window the canvas is
 * scaled uniformly and centred.
 */
@Composable
fun DesignSurface(content: @Composable () -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate.Ground),
        contentAlignment = Alignment.Center,
    ) {
        val scale = minOf(
            constraints.maxWidth / DESIGN_WIDTH.toFloat(),
            constraints.maxHeight / DESIGN_HEIGHT.toFloat(),
        )
        val density = remember(scale) { Density(density = scale, fontScale = 1f) }
        CompositionLocalProvider(LocalDensity provides density) {
            Box(Modifier.requiredSize(DESIGN_WIDTH.dp, DESIGN_HEIGHT.dp)) { content() }
        }
    }
}
