package com.roufsyed.cabin.apps

import com.roufsyed.cabin.model.Glyph
import org.junit.Assert.assertEquals
import org.junit.Test

class GlyphRulesTest {

    private fun check(expected: Glyph, vararg labels: String) {
        for (label in labels) assertEquals("label \"$label\"", expected, GlyphRules.fromLabel(label))
    }

    @Test
    fun `head unit apps are recognised by name`() {
        check(Glyph.Radio, "Radio", "FM Radio", "RadioDroid", "DAB")
        check(Glyph.Link, "ZLink", "Android Auto", "CarPlay", "Phone Link", "EasyConnection")
        check(Glyph.Sound, "Sound", "Equalizer", "DSP", "EQ")
        check(Glyph.Settings, "Settings", "Car Settings")
        check(Glyph.Phone, "Phone", "Bluetooth", "BT Phone")
        check(Glyph.Maps, "Maps", "Navigation", "Navi")
        check(Glyph.Music, "Music", "YouTube Music")
        check(Glyph.Video, "Video", "YouTube")
        check(Glyph.Files, "Files", "File Manager")
        check(Glyph.Gallery, "Gallery", "Photos")
        check(Glyph.Browser, "Browser", "Chrome")
        check(Glyph.Clock, "Clock")
        check(Glyph.Weather, "Weather")
        check(Glyph.Voice, "Voice", "Assistant")
    }

    @Test
    fun `anything else gets its initial`() {
        check(Glyph.Letter, "Dashcam", "Calendar", "Contacts", "Drive", "Gmail", "Play Store", "")
        // A keyword inside a longer word does not count: this is not a phone app.
        check(Glyph.Letter, "Gramophone", "Headphones")
    }

    @Test
    fun `short keys only match whole words`() {
        // "bt", "fm" and "eq" must not fire inside longer words.
        check(Glyph.Letter, "Subtitles", "Confirm", "Sequence")
    }
}
