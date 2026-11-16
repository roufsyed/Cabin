package com.roufsyed.cabin.sources

import com.roufsyed.cabin.data.CabinSettings
import com.roufsyed.cabin.model.AppEntry
import com.roufsyed.cabin.model.Glyph
import com.roufsyed.cabin.model.Slot
import com.roufsyed.cabin.model.SourceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceRulesTest {

    private fun app(label: String, glyph: Glyph = Glyph.Letter) =
        AppEntry("com.${label.lowercase().filter { it.isLetterOrDigit() }}/.Main", label, glyph)

    /** Roughly what the emulator has installed, in the grid's alphabetical order. */
    private val apps = listOf(
        app("Calendar"), app("Chrome", Glyph.Browser), app("Gramophone", Glyph.Music), app("Maps", Glyph.Maps),
        app("Open Headunit"), app("Phone", Glyph.Phone), app("RadioDroid", Glyph.Radio), app("YT Music", Glyph.Music),
    )

    private fun label(slot: Slot, among: List<AppEntry> = apps) = SourceRules.guess(slot, among)?.label

    @Test
    fun `slots are guessed from app names`() {
        assertEquals("RadioDroid", label(Slot.Radio))
        assertEquals("YT Music", label(Slot.Usb))
        assertEquals("Open Headunit", label(Slot.Auto))
        assertEquals("Phone", label(Slot.Phone))
    }

    @Test
    fun `a name that merely contains a keyword is not a match`() {
        // "Gramophone" ends in "phone" but is not the phone app.
        assertEquals("Phone", label(Slot.Phone, listOf(app("Gramophone", Glyph.Music), app("Phone", Glyph.Phone))))
        assertNull(label(Slot.Phone, listOf(app("Gramophone", Glyph.Music))))
    }

    @Test
    fun `slots with nothing suitable stay empty`() {
        assertNull(label(Slot.Aux))
        assertNull(label(Slot.Bluetooth))
        assertNull(label(Slot.Link))
    }

    @Test
    fun `a head unit's own apps are found`() {
        val unit = listOf(app("AUX"), app("Bluetooth"), app("BT Music"), app("EasyConnection"), app("FM"), app("Music"))
        assertEquals("FM", label(Slot.Radio, unit))
        assertEquals("BT Music", label(Slot.Bluetooth, unit))
        assertEquals("Music", label(Slot.Usb, unit))
        assertEquals("AUX", label(Slot.Aux, unit))
        assertEquals("Bluetooth", label(Slot.Phone, unit))
        assertEquals("EasyConnection", label(Slot.Link, unit))
        assertEquals("EasyConnection", label(Slot.Auto, unit))
    }

    @Test
    fun `Google Maps is the maps app when it is installed, whatever else handles maps`() {
        val google = AppEntry("com.google.android.apps.maps/com.google.android.maps.MapsActivity", "Maps", Glyph.Maps)
        val among = listOf(app("Atlas", Glyph.Maps), google, app("Waze", Glyph.Maps))
        assertEquals(google, SourceRules.guess(Slot.Maps, among))
    }

    @Test
    fun `without Google Maps the first maps app is used, and with none the slot stays empty`() {
        assertEquals("Atlas", label(Slot.Maps, listOf(app("Calendar"), app("Atlas", Glyph.Maps), app("Waze", Glyph.Maps))))
        assertNull(label(Slot.Maps, listOf(app("Calendar"), app("Phone", Glyph.Phone))))
    }

    @Test
    fun `the music app is never guessed, only chosen`() {
        assertNull(label(Slot.Music))
        val gramophone = apps.first { it.label == "Gramophone" }
        val chosen = CabinSettings(bindings = mapOf(Slot.Music to gramophone.id))
        assertEquals(gramophone, SourceRules.bindings(chosen, apps)[Slot.Music])
        assertEquals(gramophone, SourceRules.binding(Slot.Music, chosen, apps))
        // Once the chosen app is uninstalled there is no music app again.
        assertNull(SourceRules.binding(Slot.Music, chosen, apps - gramophone))
    }

    @Test
    fun `one slot's app is the same as in the full set`() {
        val settings = CabinSettings(bindings = mapOf(Slot.Maps to apps.first { it.label == "Chrome" }.id))
        for (slot in Slot.entries) {
            assertEquals(SourceRules.bindings(settings, apps)[slot], SourceRules.binding(slot, settings, apps))
        }
    }

    @Test
    fun `with no name match a slot falls back to the kind of app`() {
        val only = listOf(app("Tuner", Glyph.Radio), app("Tunes", Glyph.Music))
        assertEquals("Tuner", label(Slot.Radio, only))
        assertEquals("Tunes", label(Slot.Usb, only))
    }

    @Test
    fun `the user's choice beats the guess while the app is installed`() {
        val gramophone = apps.first { it.label == "Gramophone" }
        val chosen = CabinSettings(bindings = mapOf(Slot.Usb to gramophone.id))
        assertEquals("Gramophone", SourceRules.bindings(chosen, apps)[Slot.Usb]?.label)

        val uninstalled = CabinSettings(bindings = mapOf(Slot.Usb to "com.gone/.Main"))
        assertEquals("YT Music", SourceRules.bindings(uninstalled, apps)[Slot.Usb]?.label)
    }

    private fun rowName(source: SourceId, settings: CabinSettings, among: List<AppEntry> = apps) =
        SourceRules.label(source, settings, SourceRules.bindings(settings, among))

    @Test
    fun `a source left to find its own app keeps its own name`() {
        val untouched = CabinSettings()
        assertEquals("RadioDroid", SourceRules.bindings(untouched, apps)[Slot.Radio]?.label)
        assertEquals("Radio", rowName(SourceId.Radio, untouched))
        assertEquals("Android Auto", rowName(SourceId.Auto, untouched))
        assertNull(SourceRules.bindings(untouched, apps)[Slot.Bluetooth])
        assertEquals("Bluetooth", rowName(SourceId.Bluetooth, untouched))
    }

    @Test
    fun `a source the user has chosen an app for is called after that app`() {
        val ytMusic = apps.first { it.label == "YT Music" }
        val gramophone = apps.first { it.label == "Gramophone" }
        val chosen = CabinSettings(bindings = mapOf(Slot.Radio to ytMusic.id, Slot.Usb to gramophone.id))
        assertEquals("YT Music", rowName(SourceId.Radio, chosen))
        assertEquals("Gramophone", rowName(SourceId.Usb, chosen))
        assertEquals("Bluetooth", rowName(SourceId.Bluetooth, chosen))
        assertEquals("Android Auto", rowName(SourceId.Auto, chosen))
        val radioDroid = apps.first { it.label == "RadioDroid" }
        assertEquals("RadioDroid", rowName(SourceId.Radio, CabinSettings(bindings = mapOf(Slot.Radio to radioDroid.id))))
    }

    @Test
    fun `a chosen app that is no longer installed gives the source its own name back`() {
        val gone = CabinSettings(bindings = mapOf(Slot.Usb to "com.gone/.Main"))
        assertEquals("YT Music", SourceRules.bindings(gone, apps)[Slot.Usb]?.label)
        assertEquals("USB", rowName(SourceId.Usb, gone))
    }

    @Test
    fun `an app chosen for the rail or for music renames no source`() {
        val gramophone = apps.first { it.label == "Gramophone" }
        val chosen = CabinSettings(bindings = mapOf(Slot.Music to gramophone.id, Slot.Phone to gramophone.id))
        for (source in SourceId.entries) assertEquals(source.label, rowName(source, chosen))
    }

    @Test
    fun `an app with a blank name does not blank the source`() {
        val nameless = AppEntry("com.nameless/.Main", "  ", Glyph.Letter)
        val chosen = CabinSettings(bindings = mapOf(Slot.Usb to nameless.id))
        assertEquals("USB", rowName(SourceId.Usb, chosen, apps + nameless))
    }

    private val visible = listOf(SourceId.Radio, SourceId.Bluetooth, SourceId.Usb, SourceId.Auto)
    private val bound = mapOf(
        Slot.Radio to app("RadioDroid"),
        Slot.Usb to app("Gramophone"),
        Slot.Bluetooth to app("Gramophone"),
    )

    @Test
    fun `the source whose app is playing is the active one`() {
        assertEquals(SourceId.Radio, SourceRules.active("com.radiodroid", bound, lastTapped = SourceId.Usb, visible = visible))
    }

    @Test
    fun `when one app serves two sources the one tapped last is active`() {
        assertEquals(SourceId.Usb, SourceRules.active("com.gramophone", bound, SourceId.Usb, visible))
        assertEquals(SourceId.Bluetooth, SourceRules.active("com.gramophone", bound, SourceId.Bluetooth, visible))
        assertEquals(SourceId.Bluetooth, SourceRules.active("com.gramophone", bound, null, visible))
    }

    @Test
    fun `an app that belongs to no source leaves none active while it plays`() {
        assertNull(SourceRules.active("com.spotify", bound, SourceId.Radio, visible))
    }

    @Test
    fun `with nothing playing the source tapped last stays active`() {
        assertEquals(SourceId.Radio, SourceRules.active(null, bound, SourceId.Radio, visible))
        assertNull(SourceRules.active(null, bound, null, visible))
    }

    @Test
    fun `a hidden source is never active`() {
        assertNull(SourceRules.active(null, bound, SourceId.Aux, visible))
        assertNull(SourceRules.active("com.radiodroid", bound, SourceId.Radio, visible - SourceId.Radio))
    }

    @Test
    fun `Mode steps through the visible sources and wraps`() {
        assertEquals(SourceId.Bluetooth, SourceRules.next(visible, SourceId.Radio))
        assertEquals(SourceId.Radio, SourceRules.next(visible, SourceId.Auto))
        assertEquals(SourceId.Radio, SourceRules.next(visible, null))
        assertNull(SourceRules.next(emptyList(), SourceId.Radio))
    }

    @Test
    fun `visible sources follow the saved order without the hidden ones`() {
        val settings = CabinSettings(
            sourceOrder = listOf(SourceId.Usb, SourceId.Aux, SourceId.Radio, SourceId.Bluetooth, SourceId.Auto),
            sourceHidden = setOf(SourceId.Aux, SourceId.Bluetooth),
        )
        assertEquals(listOf(SourceId.Usb, SourceId.Radio, SourceId.Auto), SourceRules.visible(settings))
    }
}
