package com.roufsyed.cabin.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationIntentsTest {

    private val google = MapsNotificationParser.MAPS_PACKAGE

    @Test
    fun `Google Maps is asked to navigate, with its own address`() {
        assertEquals("google.navigation:q=City%20Mall", NavigationIntents.destination(google, "City Mall"))
    }

    @Test
    fun `any other maps app gets Android's general map address`() {
        assertEquals("geo:0,0?q=City%20Mall", NavigationIntents.destination("org.example.maps", "City Mall"))
    }

    @Test
    fun `a spoken destination is trimmed and made safe to send`() {
        assertEquals(
            "geo:0,0?q=MG%20Road%2C%20Bengaluru%20%26%20more",
            NavigationIntents.destination("org.example.maps", "  MG Road, Bengaluru & more "),
        )
        assertEquals(
            "google.navigation:q=%E0%A4%A6%E0%A4%BF%E0%A4%B2%E0%A5%8D%E0%A4%B2%E0%A5%80",
            NavigationIntents.destination(google, "दिल्ली"),
        )
    }

    @Test
    fun `saved places use the form Google documents`() {
        assertEquals("google.navigation:place=home", NavigationIntents.savedPlace(SavedPlace.Home))
        assertEquals("google.navigation:place=work", NavigationIntents.savedPlace(SavedPlace.Work))
    }

    // The shortcuts Google Maps was seen to publish on the emulator.
    private val googleShortcuts = listOf(
        PlaceShortcut("manifest_work", "Work"),
        PlaceShortcut("manifest_home", "Home"),
    )

    @Test
    fun `Google Maps' own Home and Work shortcuts are found by their ids`() {
        assertEquals("manifest_home", NavigationIntents.shortcutFor(SavedPlace.Home, googleShortcuts)?.id)
        assertEquals("manifest_work", NavigationIntents.shortcutFor(SavedPlace.Work, googleShortcuts)?.id)
    }

    @Test
    fun `another app's shortcuts are found by the words in their id or label`() {
        val other = listOf(
            PlaceShortcut("search", "Search"),
            PlaceShortcut("s1", "Drive home"),
            PlaceShortcut("nav_to_office", "Commute"),
        )
        assertEquals("s1", NavigationIntents.shortcutFor(SavedPlace.Home, other)?.id)
        assertEquals("nav_to_office", NavigationIntents.shortcutFor(SavedPlace.Work, other)?.id)
    }

    @Test
    fun `a word that only contains home or work is not a match`() {
        val misleading = listOf(PlaceShortcut("homepage", "Homepage"), PlaceShortcut("network", "Networks"))
        assertNull(NavigationIntents.shortcutFor(SavedPlace.Home, misleading))
        assertNull(NavigationIntents.shortcutFor(SavedPlace.Work, misleading))
        assertNull(NavigationIntents.shortcutFor(SavedPlace.Home, emptyList()))
    }
}
