package com.roufsyed.cabin.media

import androidx.compose.runtime.Immutable
import java.util.Locale

/**
 * What one of the unit's players tells Android about itself, as it reached Cabin. These are the raw
 * facts; [PlayerRules.report] puts them into words for the Players page.
 */
data class PlayerFacts(
    val packageName: String,
    val appLabel: String,
    /** True for the one the Now playing panel shows and the transport buttons act on. */
    val shown: Boolean,
    /** Android's number for what the player is doing, or null when it has said nothing at all. */
    val state: Int?,
    /** Where the player said the track was. Negative when it does not say. */
    val positionMs: Long,
    val speed: Float,
    /** How long ago the player said where the track was, or null if it never has. */
    val toldAgoMs: Long?,
    /** What the player says it can be told to do: Android's set of action flags. */
    val actions: Long,
    /** The track's length exactly as given, or null when the player gives none. */
    val durationMs: Long?,
    /** The names of the track details the player gives, as Android has them. */
    val details: List<String>,
    val queueSize: Int,
    /** Whatever else the player sends along: buttons of its own, and values under names of its own. */
    val extras: List<String>,
)

/** A row of a player's page. [lines] is how many lines of text its value may take there. */
@Immutable
data class ReportLine(val label: String, val value: String, val lines: Int = 1)

/** One page of the Players screen: a player, and what it says of itself, in words. */
@Immutable
data class PlayerReport(
    val appLabel: String,
    val packageName: String,
    val shown: Boolean,
    val lines: List<ReportLine>,
)

/**
 * How a player's facts are put into words. Kept apart from Android so it can be tested; the numbers
 * are Android's own, from its `PlaybackState`.
 */
object PlayerRules {
    const val STATE_PAUSED = 2
    const val STATE_PLAYING = 3

    const val SEEK_TO = 1L shl 8
    const val FAST_FORWARD = 1L shl 6
    const val REWIND = 1L shl 3

    /** Play, pause, next and previous: what the transport buttons send. */
    const val TRANSPORT = (1L shl 2) or (1L shl 1) or (1L shl 5) or (1L shl 4)

    /** Every action Android has a name for, in the order they are listed on the page. */
    private val ACTIONS = listOf(
        (1L shl 2) to "play",
        (1L shl 1) to "pause",
        (1L shl 9) to "play or pause",
        (1L shl 0) to "stop",
        (1L shl 5) to "next",
        (1L shl 4) to "previous",
        SEEK_TO to "move within the track",
        FAST_FORWARD to "wind forward",
        REWIND to "wind back",
        (1L shl 12) to "pick from the queue",
        (1L shl 22) to "change speed",
        (1L shl 18) to "repeat",
        ((1L shl 19) or (1L shl 21)) to "shuffle",
        (1L shl 7) to "rate",
        ((1L shl 10) or (1L shl 11) or (1L shl 13)) to "play something named",
        ((1L shl 14) or (1L shl 15) or (1L shl 16) or (1L shl 17)) to "prepare",
        (1L shl 20) to "captions",
    )

    /** The start of every name Android gives a track detail, left off the page to save room. */
    private const val DETAIL_PREFIX = "android.media.metadata."

    /** How many lines of text the page has room for; the rows of [report] add up to it. */
    const val PAGE_LINES = 12

    fun report(facts: PlayerFacts): PlayerReport = PlayerReport(
        appLabel = facts.appLabel,
        packageName = facts.packageName,
        shown = facts.shown,
        lines = listOf(
            ReportLine("App", facts.packageName),
            ReportLine("Slider", slider(facts)),
            ReportLine("State", state(facts)),
            ReportLine("Length", length(facts.durationMs)),
            ReportLine("Can be told to", actions(facts.actions), lines = 3),
            ReportLine("Track details", details(facts.details), lines = 2),
            ReportLine("Queue", queue(facts.queueSize)),
            ReportLine("Also sends", facts.extras.joinToString().ifEmpty { "Nothing" }, lines = 2),
        ),
    )

    /**
     * The track details a player gives, by name: Android's own first, since those are the ones
     * Cabin reads and the row has only so much room, then whatever the player sends under names of
     * its own.
     */
    fun details(names: List<String>): String =
        names.sortedBy { !it.startsWith(DETAIL_PREFIX) }.joinToString(transform = ::detail).ifEmpty { "None" }

    /**
     * A track detail's name as the page gives it. One of Android's own loses the start they all
     * share and is put in small letters, "ALBUM_ART" as "album art", which takes half the room. One
     * under a name of the player's own is left exactly as it came.
     */
    fun detail(name: String): String =
        if (name.startsWith(DETAIL_PREFIX)) name.removePrefix(DETAIL_PREFIX).lowercase().replace('_', ' ') else name

    /** Whether Cabin's progress line is a slider for this player, and if not, which of the two things is missing. */
    fun slider(facts: PlayerFacts): String = when {
        (facts.durationMs ?: 0L) <= 0L -> "No: it gives no length for the track"
        !MediaRules.seekable(facts.durationMs ?: 0L, facts.actions) -> "No: it does not say the track can be moved"
        else -> "Yes"
    }

    /** "Playing, at 1:24, speed 1.0, said 3 s ago": what the player is doing and where its track is. */
    fun state(facts: PlayerFacts): String =
        if (facts.state == null) "Not given" else "${state(facts.state)}, ${position(facts)}"

    fun state(state: Int): String = when (state) {
        0 -> "None"
        1 -> "Stopped"
        STATE_PAUSED -> "Paused"
        STATE_PLAYING -> "Playing"
        4 -> "Winding forward"
        5 -> "Winding back"
        6 -> "Buffering"
        7 -> "Error"
        8 -> "Connecting"
        9 -> "Skipping back"
        10 -> "Skipping ahead"
        11 -> "Skipping to a queue item"
        else -> "Number $state"
    }

    /** "at 1:24, speed 1.0, said 3 s ago": where the track was, how fast it moves, and how old that is. */
    fun position(facts: PlayerFacts): String {
        if (facts.positionMs < 0L) return "position not given"
        val speed = String.format(Locale.US, "%.1f", facts.speed)
        val said = facts.toldAgoMs?.let { ", said ${(it.coerceAtLeast(0L) / 1000L)} s ago" }.orEmpty()
        return "at ${PlaybackProgress.formatTime(facts.positionMs)}, speed $speed$said"
    }

    fun length(durationMs: Long?): String = when {
        durationMs == null -> "Not given"
        durationMs <= 0L -> "Given as $durationMs"
        else -> PlaybackProgress.formatTime(durationMs)
    }

    /** "Play, pause, next, previous (0x36)". The number is the whole set as Android has it, names or not. */
    fun actions(actions: Long): String {
        if (actions == 0L) return "Nothing: it lists no actions"
        val names = ACTIONS.filter { actions and it.first != 0L }.joinToString { it.second }
        val words = names.ifEmpty { "nothing Android has a name for" }
        return words.replaceFirstChar { it.uppercase() } + " (0x" + java.lang.Long.toHexString(actions) + ")"
    }

    fun queue(size: Int): String = when {
        size <= 0 -> "None"
        size == 1 -> "1 item"
        else -> "$size items"
    }
}
