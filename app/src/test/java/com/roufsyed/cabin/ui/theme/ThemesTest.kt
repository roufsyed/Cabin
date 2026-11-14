package com.roufsyed.cabin.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.roufsyed.cabin.model.ThemeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ThemesTest {

    private val all = ThemeId.entries.map { it to Themes.palette(it) }

    /** How clearly [a] stands out from [b], 1 to 21, as accessibility guidelines measure it. */
    private fun contrast(a: Color, b: Color): Float {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }

    @Test
    fun `there are ten themes and Blue is the first`() {
        assertEquals(10, ThemeId.entries.size)
        assertEquals(ThemeId.Blue, ThemeId.entries.first())
        assertEquals(10, ThemeId.entries.map { it.label }.toSet().size)
    }

    @Test
    fun `Blue is the design's own colours, value for value`() {
        assertEquals(
            Palette(
                ground = Color(0xFF08090A),
                surface = Color(0xFF111315),
                raised = Color(0xFF15181B),
                hairline = Color(0xFF1F2226),
                outline = Color(0xFF2A2E35),
                accent = Color(0xFF9CC9F5),
                text = Color(0xFFF2F2F0),
                ink = Color(0xFFECEBE7),
                textSecondary = Color(0xFFDAD9D5),
                muted = Color(0xFFA4A6A9),
                disabled = Color(0xFF6E737B),
                danger = Color(0xFFC8423B),
                scrim = Color(0x9E000000),
            ),
            Themes.palette(ThemeId.Blue),
        )
    }

    @Test
    fun `every theme's accent reads clearly as text on its own surfaces`() {
        for ((id, palette) in all) {
            for (behind in listOf(palette.ground, palette.surface, palette.raised)) {
                val ratio = contrast(palette.accent, behind)
                assertTrue("$id accent on $behind is only $ratio", ratio >= 6.5f)
            }
        }
    }

    @Test
    fun `every theme keeps the design's text as readable as it is on Blue`() {
        for ((id, palette) in all) {
            assertTrue("$id text", contrast(palette.text, palette.surface) >= 15f)
            assertTrue("$id muted text", contrast(palette.muted, palette.raised) >= 7f)
            // The text colours themselves are the design's in every theme.
            assertEquals(Themes.palette(ThemeId.Blue).text, palette.text)
            assertEquals(Themes.palette(ThemeId.Blue).muted, palette.muted)
        }
    }

    @Test
    fun `every theme is dark, with its surfaces stepping up in the design's order`() {
        for ((id, palette) in all) {
            val steps = listOf(palette.ground, palette.surface, palette.raised, palette.hairline, palette.outline)
                .map { it.luminance() }
            assertEquals("$id surfaces out of order", steps.sorted(), steps)
            assertTrue("$id ground is not near black", steps.first() < 0.004f)
            assertTrue("$id outline is too light", steps.last() < 0.035f)
        }
    }

    @Test
    fun `the surfaces of every theme are about as light as the design's`() {
        val design = Themes.palette(ThemeId.Blue)
        for ((id, palette) in all) {
            assertTrue("$id surface", abs(palette.surface.luminance() - design.surface.luminance()) < 0.002f)
            assertTrue("$id outline", abs(palette.outline.luminance() - design.outline.luminance()) < 0.004f)
        }
    }

    @Test
    fun `no two themes share an accent`() {
        val accents = all.map { it.second.accent }
        for (i in accents.indices) {
            for (j in i + 1 until accents.size) {
                val a = accents[i]
                val b = accents[j]
                val distance = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)
                assertTrue("${all[i].first} and ${all[j].first} look alike", distance > 0.12f)
            }
        }
    }
}
