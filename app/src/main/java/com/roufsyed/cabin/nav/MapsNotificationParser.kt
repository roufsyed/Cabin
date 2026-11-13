package com.roufsyed.cabin.nav

/** The three lines of text Google Maps puts in its ongoing navigation notification. */
data class RawNavigation(val title: String?, val text: String?, val subText: String?)

/** What the Navigation panel shows, as text ready to draw. */
data class ParsedNavigation(
    /** Distance to the next manoeuvre, e.g. "350 m". */
    val distance: String,
    /** The road or direction of the next manoeuvre, e.g. "Ring Road". */
    val road: String,
    /** "Arrive 10:48", or blank if the arrival time could not be read. */
    val arrive: String,
    /** "38 min · 17 km", or as much of it as could be read. */
    val remaining: String,
)

/**
 * Reads Google Maps' navigation notification. Nothing about that notification is a documented
 * interface: it is read the way a person reads it, and it can change with any Maps update. So this
 * parser is deliberately forgiving about spacing, separators and the order of the parts, and when it
 * cannot find the next manoeuvre it returns null, which the panel shows as "no route".
 *
 * As seen in Maps 11.129 (see the tests for real samples):
 *  - title: the distance to the next manoeuvre, "200 ft" or "0.2 mi", with a non-breaking space
 *  - text: the road, "toward N De Anza Blvd" or "I-280 N  / San Francisco"
 *  - subText: "14 min · 8.3 mi · 3:26 PM PST ETA"
 * Older builds put the distance and the road together in the title, "200 m - Ring Road".
 */
object MapsNotificationParser {
    const val MAPS_PACKAGE = "com.google.android.apps.maps"

    private val spaces = Regex("[\\s\\u00A0\\u202F\\u2007]+")
    private val separator = Regex("\\s+[·•∙‧|\\-–—]+\\s+")
    private val clock = Regex("(\\d{1,2}[:.]\\d{2})(\\s?[AaPp]\\.?\\s?[Mm]\\.?)?")
    private val distanceLike = Regex("^[\\d.,]+\\s?\\p{L}{1,6}\\.?$")
    private val lead = Regex("^(towards?|onto|on)\\s+", RegexOption.IGNORE_CASE)
    private val durationWord = Regex("\\b(min|mins|minutes?|h|hr|hrs|hours?|d|days?|sec|secs|s)\\b", RegexOption.IGNORE_CASE)

    fun parse(raw: RawNavigation): ParsedNavigation? {
        val title = clean(raw.title)
        val text = clean(raw.text)

        var distance = title
        var road = text
        if (!distanceLike.matches(distance)) {
            // The older layout: "200 m - Ring Road" in the title.
            val parts = title.split(separator, limit = 2)
            if (parts.size == 2 && distanceLike.matches(parts[0])) {
                distance = parts[0]
                if (road.isEmpty()) road = parts[1]
            } else {
                return null
            }
        }
        // "5 min" has the shape of a distance but is a time: such a title is some other notification.
        if (durationWord.containsMatchIn(distance)) return null
        road = road.replace(lead, "").replace(Regex("\\s*/\\s*"), " / ").trim()
        if (road.isEmpty()) return null

        // "14 min · 8.3 mi · 3:26 PM PST ETA": the part with a clock time is the arrival; of the other
        // two, Maps puts the duration first. Units are checked as well, in case that order changes.
        val parts = clean(raw.subText).split(separator).filter { it.isNotEmpty() }
        val arrive = parts.firstNotNullOfOrNull { clock.find(it) }?.let { "Arrive " + clean(it.value) }.orEmpty()
        val rest = parts.filter { clock.find(it) == null }
        val duration: String
        val distanceLeft: String
        when {
            rest.isEmpty() -> {
                duration = ""
                distanceLeft = ""
            }
            rest.size == 1 -> {
                val isDistance = distanceLike.matches(rest[0]) && !durationWord.containsMatchIn(rest[0])
                duration = if (isDistance) "" else rest[0]
                distanceLeft = if (isDistance) rest[0] else ""
            }
            durationWord.containsMatchIn(rest[1]) && !durationWord.containsMatchIn(rest[0]) -> {
                duration = rest[1]
                distanceLeft = rest[0]
            }
            else -> {
                duration = rest[0]
                distanceLeft = rest[1]
            }
        }
        return ParsedNavigation(
            distance = distance,
            road = road,
            arrive = arrive,
            remaining = listOf(duration, distanceLeft).filter { it.isNotEmpty() }.joinToString(" · "),
        )
    }

    /** One ordinary space wherever Maps used a non-breaking or doubled one. */
    private fun clean(value: String?): String = value.orEmpty().replace(spaces, " ").trim()
}
