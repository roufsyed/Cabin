package com.roufsyed.cabin.model

import androidx.compose.runtime.Immutable

/**
 * What a tile shows above its label. Third-party logos are never used: an app gets a line icon when its
 * kind is recognisable and otherwise a ring with its initial.
 */
enum class Glyph {
    Browser, Clock, Files, Gallery, Maps, Music, Phone, Link, Radio, Settings, Sound, Video, Voice, Weather,
    Letter,
}

/** An installed, launchable app. [id] is its flattened component name. */
@Immutable
data class AppEntry(val id: String, val label: String, val glyph: Glyph) {
    val letter: String
        get() = label.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
}

/** A tile in the Apps grid. Hidden tiles are only shown, dimmed, while arranging. */
@Immutable
data class AppTile(val app: AppEntry, val hidden: Boolean)

/** A thing in Cabin that opens one of the unit's own apps. */
enum class Slot(val label: String) {
    Phone("Phone"),
    Link("Phone link"),
    Radio("Radio"),
    Bluetooth("Bluetooth"),
    Usb("USB"),
    Auto("Android Auto"),
    Aux("AUX"),

    /** The app the Navigation panel opens and sends destinations to. */
    Maps("Maps"),

    /** The app that is started when play is pressed and nothing is there to resume. */
    Music("Music");

    companion object {
        fun of(source: SourceId): Slot = when (source) {
            SourceId.Radio -> Radio
            SourceId.Bluetooth -> Bluetooth
            SourceId.Usb -> Usb
            SourceId.Auto -> Auto
            SourceId.Aux -> Aux
        }
    }
}
