package com.roufsyed.cabin.nav

import com.roufsyed.cabin.model.NavUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A place saved in the maps app itself, so Cabin never has to know the address. */
enum class SavedPlace { Home, Work }

/**
 * The one interface the rest of Cabin knows about navigation. Behind it sits whatever can tell where
 * the route is going; today that is Google Maps' notification, which may stop working with any Maps
 * update. When it does, [state] simply stays [NavUi.Idle] and nothing else in Cabin changes.
 */
interface NavigationSource {
    val state: StateFlow<NavUi>

    /** Brings the maps app to the front. False when the unit has no maps app Cabin knows of. */
    fun openMaps(): Boolean

    /** Starts turn-by-turn navigation to a spoken or typed destination. False as for [openMaps]. */
    fun navigateTo(destination: String): Boolean

    /** Starts navigation to the Home or Work place saved in the maps app. False as for [openMaps]. */
    fun navigateTo(place: SavedPlace): Boolean
}

/** A fixed route with recorded requests: for the demo launch and for tests. */
class FixedNavigation(initial: NavUi, private val hasMapsApp: Boolean = true) : NavigationSource {
    private val current = MutableStateFlow(initial)
    override val state: StateFlow<NavUi> = current
    val calls = mutableListOf<String>()

    fun set(value: NavUi) {
        current.value = value
    }

    override fun openMaps(): Boolean {
        calls += "open"
        return hasMapsApp
    }

    override fun navigateTo(destination: String): Boolean {
        calls += "to:$destination"
        return hasMapsApp
    }

    override fun navigateTo(place: SavedPlace): Boolean {
        calls += "saved:$place"
        return hasMapsApp
    }
}

/**
 * The recent destinations after [place] has been used: it goes to the front, an earlier mention of it
 * is dropped, and only as many are kept as the "Where to?" dialog has room for.
 */
fun recentPlaces(current: List<String>, place: String, keep: Int = 2): List<String> {
    val name = place.trim()
    if (name.isEmpty()) return current.take(keep)
    // A place said again keeps the spelling it was first saved with.
    val known = current.firstOrNull { it.equals(name, ignoreCase = true) } ?: name
    return (listOf(known) + current.filterNot { it.equals(name, ignoreCase = true) }).take(keep)
}
