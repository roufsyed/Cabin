package com.roufsyed.cabin.apps

import com.roufsyed.cabin.model.Glyph

/**
 * Picks a line icon for an app from its name, for apps the system does not categorise (most of a head
 * unit's own apps). Anything unrecognised gets [Glyph.Letter]: a ring with the app's initial.
 */
object GlyphRules {
    private val rules: List<Pair<Glyph, List<String>>> = listOf(
        Glyph.Link to listOf("carplay", "android auto", "phone link", "phonelink", "zlink", "tlink", "carlink",
            "easyconnect", "mirror", "screen cast", "screencast"),
        Glyph.Radio to listOf("radio", "fm", "dab"),
        Glyph.Sound to listOf("sound", "equalizer", "equaliser", "eq", "dsp", "amplifier", "audio settings"),
        Glyph.Settings to listOf("settings", "setting"),
        Glyph.Maps to listOf("maps", "map", "navigation", "navi", "gps"),
        Glyph.Phone to listOf("phone", "dialer", "dialler", "bluetooth", "bt"),
        Glyph.Music to listOf("music", "spotify"),
        Glyph.Video to listOf("video", "youtube", "movies", "tv"),
        Glyph.Gallery to listOf("gallery", "photos", "pictures"),
        Glyph.Files to listOf("files", "file manager", "explorer", "downloads"),
        Glyph.Browser to listOf("browser", "chrome", "firefox", "internet"),
        Glyph.Clock to listOf("clock", "alarm"),
        Glyph.Weather to listOf("weather"),
        Glyph.Voice to listOf("voice", "assistant"),
    )

    fun fromLabel(label: String): Glyph {
        for ((glyph, keys) in rules) {
            if (keys.any { Names.matches(label, it) }) return glyph
        }
        return Glyph.Letter
    }
}
