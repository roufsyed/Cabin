package com.roufsyed.cabin.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.roufsyed.cabin.model.ThemeId
import kotlin.math.abs

/** One complete set of Cabin's colours. The names are the design's token names. */
@Immutable
data class Palette(
    val ground: Color,
    val surface: Color,
    val raised: Color,
    val hairline: Color,
    val outline: Color,
    val accent: Color,
    val text: Color = Color(0xFFF2F2F0),
    val ink: Color = Color(0xFFECEBE7),
    val textSecondary: Color = Color(0xFFDAD9D5),
    val muted: Color = Color(0xFFA4A6A9),
    val disabled: Color = Color(0xFF6E737B),
    val danger: Color = Color(0xFFC8423B),
    val scrim: Color = Color(0x9E000000),
    val connected: Color = Color(0xFF5FD08A),
)

/**
 * The ten themes. Blue is the design itself, value for value.
 *
 * The others are built the way the design builds its own colours: the five dark surfaces keep the
 * design's lightness steps and carry a faint tint of the accent's hue, and the accent is about as
 * light as the design's blue, so it reads equally well as text. Only the hue changes; the text
 * colours, the layout and the rule that the accent marks the active state alone stay as they are.
 */
object Themes {
    private fun palette(ground: Long, surface: Long, raised: Long, hairline: Long, outline: Long, accent: Long) =
        Palette(Color(ground), Color(surface), Color(raised), Color(hairline), Color(outline), Color(accent))

    private val palettes: Map<ThemeId, Palette> = mapOf(
        ThemeId.Blue to palette(0xFF08090A, 0xFF111315, 0xFF15181B, 0xFF1F2226, 0xFF2A2E35, 0xFF9CC9F5),
        ThemeId.Teal to palette(0xFF080909, 0xFF101313, 0xFF141919, 0xFF1D2323, 0xFF263030, 0xFF7ED6D5),
        ThemeId.Emerald to palette(0xFF080908, 0xFF111312, 0xFF151916, 0xFF1E2320, 0xFF28302B, 0xFF84D9AD),
        ThemeId.Champagne to palette(0xFF090908, 0xFF131310, 0xFF191814, 0xFF23221D, 0xFF302E26, 0xFFE0D0A1),
        ThemeId.Amber to palette(0xFF0A0908, 0xFF141210, 0xFF1A1714, 0xFF25211D, 0xFF322D26, 0xFFF6B663),
        ThemeId.Copper to palette(0xFF0A0908, 0xFF151211, 0xFF1B1715, 0xFF26211E, 0xFF342C28, 0xFFE6A37D),
        ThemeId.Ruby to palette(0xFF0A0808, 0xFF151212, 0xFF1B1616, 0xFF262020, 0xFF352B2B, 0xFFF47A79),
        ThemeId.Rose to palette(0xFF0A0809, 0xFF151213, 0xFF1B1618, 0xFF262022, 0xFF342B2E, 0xFFEFA4C1),
        ThemeId.Amethyst to palette(0xFF09090A, 0xFF131215, 0xFF18171B, 0xFF222126, 0xFF2F2C34, 0xFFC0AAEC),
        ThemeId.Platinum to palette(0xFF090909, 0xFF121313, 0xFF171818, 0xFF212223, 0xFF2C2E2F, 0xFFDBDEE2),
    )

    private val dayPalettes: Map<ThemeId, Palette> = palettes.mapValues { day(it.value) }

    fun palette(id: ThemeId): Palette = palettes.getValue(id)

    fun palette(id: ThemeId, day: Boolean): Palette = if (day) dayPalettes.getValue(id) else palettes.getValue(id)

    private fun day(night: Palette): Palette {
        val accent = night.accent
        val high = maxOf(accent.red, accent.green, accent.blue)
        val low = minOf(accent.red, accent.green, accent.blue)
        val spread = high - low
        val lightness = (high + low) / 2f
        val saturation = if (spread == 0f) 0f else spread / (1f - abs(2f * lightness - 1f))
        val hue = when {
            spread == 0f -> 0f
            high == accent.red -> 60f * (((accent.green - accent.blue) / spread).mod(6f))
            high == accent.green -> 60f * ((accent.blue - accent.red) / spread + 2f)
            else -> 60f * ((accent.red - accent.green) / spread + 4f)
        }
        val tint = if (saturation >= 0.12f) 0.10f else 0.02f
        return night.copy(
            ground = Color.hsl(hue, tint, 0.055f),
            surface = Color.hsl(hue, tint, 0.13f),
            raised = Color.hsl(hue, tint, 0.16f),
            hairline = Color.hsl(hue, tint, 0.22f),
            outline = Color.hsl(hue, tint, 0.35f),
            muted = Color(0xFFB8BABD),
            disabled = Color(0xFF8A8F97),
        )
    }
}
