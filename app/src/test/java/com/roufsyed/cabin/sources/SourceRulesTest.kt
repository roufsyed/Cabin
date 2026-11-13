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
