package com.roufsyed.cabin.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

/**
 * A one-colour line icon, described with the same primitives and numbers as the design's SVGs.
 * The paths are built once, on first draw, and reused.
 */
class SlateIcon internal constructor(
    val viewWidth: Float,
    val viewHeight: Float,
    private val build: IconBuilder.() -> Unit,
) {
    internal val stroked: Path by lazy(LazyThreadSafetyMode.NONE) { parts.first }
    internal val filled: Path? by lazy(LazyThreadSafetyMode.NONE) { parts.second }

    private val parts: Pair<Path, Path?> by lazy(LazyThreadSafetyMode.NONE) {
        val builder = IconBuilder()
        builder.build()
        builder.stroke to builder.fill
    }
}

class IconBuilder internal constructor() {
    internal val stroke = Path()
    internal var fill: Path? = null

    /** An SVG path, stroked. */
    fun path(d: String) {
        stroke.addPath(PathParser().parsePathString(d).toPath())
    }

    fun rect(x: Float, y: Float, w: Float, h: Float, rx: Float = 0f) {
        stroke.addRoundRect(RoundRect(Rect(x, y, x + w, y + h), CornerRadius(rx, rx)))
    }

    fun circle(cx: Float, cy: Float, r: Float) {
        stroke.addOval(Rect(cx - r, cy - r, cx + r, cy + r))
    }

    /** A filled dot, for the few marks the design fills instead of strokes. */
    fun dot(cx: Float, cy: Float, r: Float) {
        val target = fill ?: Path().also { fill = it }
        target.addOval(Rect(cx - r, cy - r, cx + r, cy + r))
    }
}

internal fun icon(width: Float = 24f, height: Float = 24f, build: IconBuilder.() -> Unit) =
    SlateIcon(width, height, build)

/**
 * Draws [icon] at [width] x [height] design pixels. [stroke] is the SVG stroke-width, in the icon's
 * own grid units, exactly as written in the design.
 */
@Composable
fun Icon(
    icon: SlateIcon,
    width: Dp,
    color: Color,
    stroke: Float,
    modifier: Modifier = Modifier,
    height: Dp = width * (icon.viewHeight / icon.viewWidth),
) {
    Canvas(modifier.size(width, height)) {
        scale(size.width / icon.viewWidth, size.height / icon.viewHeight, pivot = Offset.Zero) {
            drawPath(
                path = icon.stroked,
                color = color,
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            icon.filled?.let { drawPath(it, color, style = Fill) }
        }
    }
}
