package com.roufsyed.cabin.nav

import com.roufsyed.cabin.apps.Names
import com.roufsyed.cabin.nav.MapsNotificationParser.MAPS_PACKAGE
import java.net.URLEncoder

/** A launcher shortcut a maps app publishes: the ones on a long press of its icon. */
data class PlaceShortcut(val id: String, val label: String)

/**
 * What is sent to a maps app, as plain text so it can be tested. Only documented, public forms are
 * used: Google Maps' own navigation address for Google Maps, and Android's general "geo" address,
 * which any maps app is expected to understand, for the rest.
 */
object NavigationIntents {

    /** The address that asks [packageName] to go to a spoken or typed [destination]. */
    fun destination(packageName: String, destination: String): String {
        val query = encode(destination.trim())
        return if (packageName == MAPS_PACKAGE) "google.navigation:q=$query" else "geo:0,0?q=$query"
    }

    /** Google Maps' address for a place saved in the account. Google documents it for Android Automotive. */
    fun savedPlace(place: SavedPlace): String =
        "google.navigation:place=" + if (place == SavedPlace.Home) "home" else "work"

    /**
     * The shortcut that leads to [place], if the app publishes one. Google Maps names its own
     * "manifest_home" and "manifest_work"; for other apps a shortcut counts when its id or its label
     * has the word home, or work or office, in it.
     */
    fun shortcutFor(place: SavedPlace, shortcuts: List<PlaceShortcut>): PlaceShortcut? {
        val exact = if (place == SavedPlace.Home) "manifest_home" else "manifest_work"
        shortcuts.firstOrNull { it.id == exact }?.let { return it }
        val words = if (place == SavedPlace.Home) setOf("home") else setOf("work", "office")
        return shortcuts.firstOrNull { shortcut ->
            Names.words(shortcut.id).any { it in words } || Names.words(shortcut.label).any { it in words }
        }
    }

    private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
}
