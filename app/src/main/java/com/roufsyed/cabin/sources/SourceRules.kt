package com.roufsyed.cabin.sources

import com.roufsyed.cabin.apps.Names
import com.roufsyed.cabin.data.CabinSettings
import com.roufsyed.cabin.model.AppEntry
import com.roufsyed.cabin.model.Glyph
import com.roufsyed.cabin.model.Slot
import com.roufsyed.cabin.model.SourceId
import com.roufsyed.cabin.nav.MapsNotificationParser

/**
 * Which of the unit's apps each slot opens, and which source is the active one.
 * Cabin does not play radio, Bluetooth audio or anything else itself: a source is a way to start, and
 * get to, the app that does.
 */
object SourceRules {

    /**
     * Apps that are the first choice for a slot whenever they are installed, by package. Google Maps
     * is the one maps app whose route Cabin can read, so it is preferred when it is there.
     */
    private val packages: Map<Slot, List<String>> = mapOf(Slot.Maps to listOf(MapsNotificationParser.MAPS_PACKAGE))

    /** Name fragments that identify the stock app for a slot, most specific first. */
    private val names: Map<Slot, List<String>> = mapOf(
        Slot.Radio to listOf("radio", "fm", "dab"),
        Slot.Bluetooth to listOf("bt music", "bluetooth music", "a2dp", "bluetooth", "bt"),
        Slot.Usb to listOf("usb", "music"),
        Slot.Auto to listOf("android auto", "headunit", "zlink", "tlink", "carlink", "easyconnect", "phone link", "phonelink"),
        Slot.Aux to listOf("aux", "av in", "avin", "line in"),
        Slot.Phone to listOf("phone", "dialer", "dialler", "bluetooth", "bt"),
        Slot.Link to listOf("phone link", "phonelink", "easyconnect", "zlink", "tlink", "carlink", "carplay", "mirror", "android auto"),
    )

    /** What kind of app a slot may fall back to when no name matches. */
    private val glyphs: Map<Slot, Glyph> = mapOf(
        Slot.Radio to Glyph.Radio,
        Slot.Usb to Glyph.Music,
        Slot.Phone to Glyph.Phone,
        Slot.Link to Glyph.Link,
        Slot.Maps to Glyph.Maps,
    )

    /**
     * The app a slot would open if the user has not chosen one: a preferred package, then the best
     * match by name, then by kind. Music is never guessed: left alone, play wakes whichever app played
     * last, which is Android's own choice and usually the right one.
     */
    fun guess(slot: Slot, apps: List<AppEntry>): AppEntry? {
        for (wanted in packages[slot].orEmpty()) {
            apps.firstOrNull { packageOf(it.id) == wanted }?.let { return it }
        }
        for (name in names[slot].orEmpty()) {
            // Of several matches the plainest name wins: "Music" over "BT Music", "Phone" over "Phone Link".
            val match = apps.filter { Names.matches(it.label, name) }.minByOrNull { Names.words(it.label).size }
            if (match != null) return match
        }
        val glyph = glyphs[slot] ?: return null
        return apps.firstOrNull { it.glyph == glyph }
    }

    /** Every slot's app: the user's choice where there is one and it is still installed, else the guess. */
    fun bindings(settings: CabinSettings, apps: List<AppEntry>): Map<Slot, AppEntry> {
        val byId = apps.associateBy { it.id }
        val result = LinkedHashMap<Slot, AppEntry>()
        for (slot in Slot.entries) {
            val app = settings.bindings[slot]?.let(byId::get) ?: guess(slot, apps)
            if (app != null) result[slot] = app
        }
        return result
    }

    /** One slot's app, by the same rule as [bindings]. */
    fun binding(slot: Slot, settings: CabinSettings, apps: List<AppEntry>): AppEntry? =
        settings.bindings[slot]?.let { id -> apps.firstOrNull { it.id == id } } ?: guess(slot, apps)

    /** The package part of a component id such as "com.example/.Main". */
    fun packageOf(appId: String): String = appId.substringBefore('/')

    /**
     * The source to highlight. While an app is actually playing, it is that app's source, or none if
     * the app belongs to no source. Otherwise it is the source tapped last. A paused session left
     * behind by some other app therefore does not steal the highlight.
     */
    fun active(
        playingPackage: String?,
        bindings: Map<Slot, AppEntry>,
        lastTapped: SourceId?,
        visible: List<SourceId>,
    ): SourceId? {
        if (playingPackage == null) return lastTapped?.takeIf { it in visible }
        val candidates = visible.filter { bindings[Slot.of(it)]?.let { app -> packageOf(app.id) } == playingPackage }
        // When one app serves two sources, prefer the one the user picked last.
        return candidates.firstOrNull { it == lastTapped } ?: candidates.firstOrNull()
    }

    fun visible(settings: CabinSettings): List<SourceId> = settings.sourceOrder.filter { it !in settings.sourceHidden }

    /** The source after [current] in the row, wrapping round: what the wheel's Mode key selects. */
    fun next(visible: List<SourceId>, current: SourceId?): SourceId? {
        if (visible.isEmpty()) return null
        return visible[(visible.indexOf(current) + 1) % visible.size]
    }
}
