package com.roufsyed.cabin.ui.theme

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

/** Colour tokens of the Slate design. The accent is used for the active state only. */
object Slate {
    val Ground = Color(0xFF08090A)
    val Surface = Color(0xFF111315)
    val Raised = Color(0xFF15181B)
    val Hairline = Color(0xFF1F2226)
    val Outline = Color(0xFF2A2E35)
    val Text = Color(0xFFF2F2F0)
    val Ink = Color(0xFFECEBE7)
    val TextSecondary = Color(0xFFDAD9D5)
    val Muted = Color(0xFFA4A6A9)
    val Disabled = Color(0xFF6E737B)
    val Accent = Color(0xFF9CC9F5)
    val Danger = Color(0xFFC8423B)
    val Scrim = Color(0x9E000000)

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
