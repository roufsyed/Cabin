package com.roufsyed.cabin.media

/** What Cabin knows about one media session when deciding which one to show. */
data class SessionState(
    val packageName: String,
    /** Playing or buffering. */
    val active: Boolean,
    val paused: Boolean,
    val titled: Boolean,
)

/** Which of the unit's media sessions Cabin shows and controls. Kept apart from Android so it can be tested. */
object MediaRules {

    /**
     * The session the Now playing panel shows and the transport buttons act on: an index into
     * [sessions], or -1 for none. [sessions] is in Android's own order, most relevant first, and
     * [lastPlayed] is the index of the one that was last seen playing in this run, or -1.
     *
     * Something that is playing always wins. Beyond that it depends on whether a music app has been
     * chosen in Setup:
     *
     * - Not chosen: the first session with a title, else the first of any kind. Play goes to it, or,
     *   with no session at all, to whichever app played last.
     * - Chosen: only something that was really in use is shown, so that play has one obvious meaning.
     *   That is the session that played last, else a paused one with a title, else the music app's
     *   own. Idle sessions that other apps leave lying about are ignored, and with nothing to show,
     *   play starts the music app.
     */
    fun shown(sessions: List<SessionState>, lastPlayed: Int, musicPackage: String?): Int {
        val playing = sessions.indexOfFirst { it.active }
        if (playing >= 0) return playing
        if (musicPackage == null) {
            val titled = sessions.indexOfFirst { it.titled }
            return if (titled >= 0 || sessions.isEmpty()) titled else 0
        }
        if (lastPlayed in sessions.indices) return lastPlayed
        val paused = sessions.indexOfFirst { it.paused && it.titled }
        if (paused >= 0) return paused
        return sessions.indexOfFirst { it.packageName == musicPackage }
    }

    /**
     * Whether the playing track can be moved to another position from outside its player. It takes
     * both things a slider needs: the track's length, and the player saying that it takes being
     * moved, which is one of the [actions] a media session lists. Android's own media card goes by
     * the same two. A player that lists nothing is taken at its word, so that the slider is never
     * one that does nothing.
     */
    fun seekable(durationMs: Long, actions: Long): Boolean =
        durationMs > 0L && actions and PlayerRules.SEEK_TO != 0L

    /**
     * Where to read a track's picture when its player sent none: the first of the addresses the
     * player gave that points at something on this unit. An address on the internet is never
     * followed: Cabin does not go online.
     */
    fun coverAddress(addresses: List<String?>): String? = addresses.firstOrNull { address ->
        address != null && LOCAL_ADDRESSES.any { address.startsWith(it, ignoreCase = true) }
    }

    /** Another app's own store of pictures, or a picture built into an app. */
    private val LOCAL_ADDRESSES = listOf("content://", "android.resource://")
}
