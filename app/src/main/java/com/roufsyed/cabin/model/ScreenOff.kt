package com.roufsyed.cabin.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * What the screen-off screen may show. Each line has its own switch in Settings > Screen off. The
 * defaults are the design's, the time and the date; with every switch off the screen is plain black.
 */
data class ScreenOffShows(
    val clock: Boolean = true,
    val date: Boolean = true,
    /** The phone, Wi-Fi and GPS marks. */
    val status: Boolean = false,
    /** The next turn, while the maps app is guiding. */
    val turn: Boolean = false,
    /** Title and artist, while something is playing. */
    val playing: Boolean = false,
) {
    /** True when every line is switched off. */
    val nothing: Boolean get() = !(clock || date || playing || turn || status)
}

/** What the screen-off screen draws at this moment. A line that is null is left out. */
@Immutable
data class ScreenOffUi(
    val time: String?,
    /** "Thursday 12 November". */
    val date: String?,
    val status: StatusUi?,
    /** "350 m · Ring Road", with the maps app's own arrow for the turn if it gave one. */
    val turn: String?,
    val turnGlyph: ImageBitmap?,
    /** "Open Road · Aster Lane". */
    val playing: String?,
    /** Whether "Tap to wake" is shown: always, unless everything was switched off. */
    val hint: Boolean,
    /**
     * Whether it is night as the brightness control sees it: the headlights are on, or, on a unit that
     * does not report them, the sun is down. The text is then at its dimmest.
     */
    val night: Boolean,
)

/**
 * The screen-off screen for what is chosen in [shows] and what the unit is doing. The track is shown
 * only while it plays and the turn only while a route is being followed: neither leaves a stale line
 * on a screen meant to be dark.
 */
fun screenOffUi(
    shows: ScreenOffShows,
    clock: ClockUi,
    status: StatusUi,
    nav: NavUi,
    nowPlaying: NowPlayingUi,
    night: Boolean,
): ScreenOffUi {
    val route = (nav as? NavUi.Active)?.takeIf { shows.turn }
    val turn = route?.let { joined(it.distance, it.road) }
    return ScreenOffUi(
        time = clock.time.takeIf { shows.clock },
        date = "${clock.weekday} ${clock.dateLong}".takeIf { shows.date },
        status = status.takeIf { shows.status },
        turn = turn,
        turnGlyph = route?.glyph.takeIf { turn != null },
        playing = joined(nowPlaying.title, nowPlaying.artist).takeIf { shows.playing && nowPlaying.playing },
        hint = !shows.nothing,
        night = night,
    )
}

/** The parts that are not blank, with the design's dot between them; null if all are blank. */
private fun joined(vararg parts: String): String? =
    parts.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null }
