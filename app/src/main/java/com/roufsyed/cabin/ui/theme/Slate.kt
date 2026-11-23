package com.roufsyed.cabin.ui.theme

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.roufsyed.cabin.R
import com.roufsyed.cabin.model.ThemeId

/**
 * Colour tokens of the Slate design. The accent is used for the active state only.
 *
 * Each token reads the theme in use, [palette]. Everything on screen takes its colours from here, so
 * setting another palette recolours the whole of Cabin on the next frame.
 */
object Slate {
    /** The colours in use. The design's own until the saved theme has been read. */
    var palette: Palette by mutableStateOf(Themes.palette(ThemeId.Blue))

    val Ground: Color get() = palette.ground
    val Surface: Color get() = palette.surface
    val Raised: Color get() = palette.raised
    val Hairline: Color get() = palette.hairline
    val Outline: Color get() = palette.outline
    val Text: Color get() = palette.text
    val Ink: Color get() = palette.ink
    val TextSecondary: Color get() = palette.textSecondary
    val Muted: Color get() = palette.muted
    val Disabled: Color get() = palette.disabled
    val Accent: Color get() = palette.accent
    val Danger: Color get() = palette.danger
    val Connected: Color get() = palette.connected
    val NightMuted = Color(0xFFA4A6A9)
    val NightDisabled = Color(0xFF6E737B)
    val Scrim: Color get() = palette.scrim

    val Figtree = FontFamily(
        Font(R.font.figtree_light, FontWeight.Light),
        Font(R.font.figtree_medium, FontWeight.Medium),
        Font(R.font.figtree_semibold, FontWeight.SemiBold),
    )

    val Light = FontWeight.Light
    val Medium = FontWeight.Medium
    val SemiBold = FontWeight.SemiBold

    /** Figtree's own line height (ascent 0.95 + descent 0.25), as a multiple of the font size. */
    private const val NATURAL_LINE_HEIGHT = 1.2f

    private val looseLines = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

    /**
     * For line heights below the font's own. Without the tight mode Compose pads the first and last
     * line back out to the font's height, so a single line could never be shorter than 1.2 em.
     */
    private val tightLines = LineHeightStyle(
        LineHeightStyle.Alignment.Center,
        LineHeightStyle.Trim.Both,
        LineHeightStyle.Mode.Tight,
    )

    /**
     * A text style in design pixels. [lineHeight] is the CSS unitless multiplier; the extra or missing
     * leading is split evenly above and below the glyphs, which is how the design's line boxes work.
     */
    fun style(
        size: Int,
        weight: FontWeight = Medium,
        color: Color = Text,
        lineHeight: Float = 1.2f,
        tabular: Boolean = false,
        letterSpacingEm: Float = 0f,
        align: TextAlign = TextAlign.Unspecified,
    ) = TextStyle(
        color = color,
        fontSize = size.sp,
        fontWeight = weight,
        fontFamily = Figtree,
        fontFeatureSettings = if (tabular) "tnum" else null,
        letterSpacing = if (letterSpacingEm == 0f) 0.sp else letterSpacingEm.em,
        lineHeight = (size * lineHeight).sp,
        textAlign = align,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = if (lineHeight < NATURAL_LINE_HEIGHT) tightLines else looseLines,
        // Fractional glyph positions, as the design's renderer uses. With the default, every glyph
        // advance is rounded to a whole pixel and a line of text drifts a pixel or two off the design.
        textMotion = TextMotion.Animated,
    )
}

/** Text set in Figtree at a design-pixel [size]. */
@Composable
fun Txt(
    text: String,
    size: Int,
    modifier: Modifier = Modifier,
    weight: FontWeight = Slate.Medium,
    color: Color = Slate.Text,
    lineHeight: Float = 1.2f,
    tabular: Boolean = false,
    letterSpacingEm: Float = 0f,
    align: TextAlign = TextAlign.Unspecified,
    singleLine: Boolean = false,
    ellipsis: Boolean = false,
    /** With a limit, text that does not fit ends in an ellipsis on the last line. */
    maxLines: Int = Int.MAX_VALUE,
) {
    val style = remember(size, weight, color, lineHeight, tabular, letterSpacingEm, align) {
        Slate.style(size, weight, color, lineHeight, tabular, letterSpacingEm, align)
    }
    // Android puts half of the letter spacing before each glyph, the design puts all of it after.
    // Shifting by half the spacing puts the first glyph back where the design has it.
    val placed = if (letterSpacingEm == 0f) modifier else modifier.offset(x = (-letterSpacingEm * size / 2f).dp)
    BasicText(
        text = text,
        modifier = placed,
        style = style,
        maxLines = if (singleLine || ellipsis) 1 else maxLines,
        softWrap = !(singleLine || ellipsis),
        overflow = if (ellipsis || maxLines != Int.MAX_VALUE) TextOverflow.Ellipsis else TextOverflow.Visible,
    )
}
