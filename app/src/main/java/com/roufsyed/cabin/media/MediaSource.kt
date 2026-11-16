package com.roufsyed.cabin.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Whatever is playing on the unit, and the controls for it. Cabin never plays anything itself. */
interface MediaSource {
    /** Null when nothing holds a media session. */
    val nowPlaying: StateFlow<NowPlaying?>

    /** Whether Cabin has been given notification access, which is what lets it see media sessions. */
    val access: StateFlow<Boolean>

    /** Packages that currently hold a media session, so a source can be started without opening its app. */
    fun hasSession(packageName: String): Boolean

    /**
     * Pauses what is playing, or resumes what is on show. False when a music app has been chosen and
     * there is nothing to resume: the caller then starts that app with [start].
     */
    fun playPause(): Boolean
    fun next()
    fun previous()
    fun playQueueItem(id: Long)

    /**
     * Moves the track on show to [positionMs]. Only worth asking while its progress says it is
     * seekable: a player is free to take no notice.
     */
    fun seekTo(positionMs: Long)

    /**
     * What each of the unit's players tells Android about itself just now, in Android's own order:
     * for the Players page, which is how a unit's own players are looked into.
     */
    fun players(): List<PlayerFacts>

    /** Starts playback in [packageName]'s session. False if it has none. */
    fun play(packageName: String): Boolean

    /**
     * Starts playback in an app whether or not it holds a session. [wasLastPlayer] says the app was the
     * last one seen playing, which makes a media key worth trying. [onFailed] is called if playback
     * cannot be started from outside the app, at once or a few seconds later; the app should then be
     * opened.
     */
    fun start(packageName: String, wasLastPlayer: Boolean, onFailed: () -> Unit)

    /** The app chosen for music in Setup, or null. It decides what is shown while nothing is playing. */
    fun setMusicApp(packageName: String?)

    fun refreshAccess()
}

/** A fixed track with working buttons: for the demo launch and for tests. */
class FixedMedia(
    initial: NowPlaying?,
    hasAccess: Boolean = true,
    /** Packages that [start] can set playing without their app being opened. */
    private val startable: Set<String> = emptySet(),
    /** The clock a moved track is timed from. It only matters for a track that is really advancing. */
    private val elapsed: () -> Long = { 0L },
) : MediaSource {
    private val state = MutableStateFlow(initial)
    override val nowPlaying: StateFlow<NowPlaying?> = state
    override val access: StateFlow<Boolean> = MutableStateFlow(hasAccess)
    val calls = mutableListOf<String>()

    @Volatile
    var musicApp: String? = null
        private set

    /** Replaces what is playing, as a player does when it moves to another track. */
    fun set(now: NowPlaying?) {
        state.value = now
    }

    override fun hasSession(packageName: String): Boolean = state.value?.packageName == packageName

    override fun playPause(): Boolean {
        calls += "playPause"
        val now = state.value ?: return musicApp == null
        state.value = now.copy(progress = now.progress.copy(playing = !now.progress.playing))
        return true
    }

    override fun next() {
        calls += "next"
        step(1)
    }

    override fun previous() {
        calls += "previous"
        step(-1)
    }

    override fun playQueueItem(id: Long) {
        calls += "queue:$id"
        val now = state.value ?: return
        val item = now.queue.firstOrNull { it.id == id } ?: return
        state.value = now.copy(
            title = item.title, artist = item.subtitle,
            queue = now.queue.map { it.copy(current = it.id == id) },
        )
    }

    override fun seekTo(positionMs: Long) {
        calls += "seek:$positionMs"
        val now = state.value ?: return
        // A player that cannot be moved takes no notice, as a real one would not.
        if (!now.progress.seekable) return
        state.value = now.copy(
            progress = now.progress.copy(
                positionMs = positionMs.coerceIn(0L, now.progress.durationMs),
                atElapsedMs = elapsed(),
            ),
        )
    }

    override fun players(): List<PlayerFacts> {
        val now = state.value ?: return emptyList()
        val progress = now.progress
        return listOf(
            PlayerFacts(
                packageName = now.packageName,
                appLabel = now.appLabel,
                shown = true,
                state = if (progress.playing) PlayerRules.STATE_PLAYING else PlayerRules.STATE_PAUSED,
                positionMs = progress.positionMs,
                speed = progress.speed,
                toldAgoMs = null,
                actions = PlayerRules.TRANSPORT or (if (progress.seekable) PlayerRules.SEEK_TO else 0L),
                durationMs = progress.durationMs.takeIf { !progress.live },
                details = listOfNotNull("TITLE", "ARTIST", "DURATION".takeIf { !progress.live }, "ALBUM_ART".takeIf { now.art != null })
                    .map { "android.media.metadata.$it" },
                queueSize = now.queue.size,
                extras = emptyList(),
            ),
        )
    }

    override fun play(packageName: String): Boolean {
        calls += "play:$packageName"
        return hasSession(packageName)
    }

    override fun start(packageName: String, wasLastPlayer: Boolean, onFailed: () -> Unit) {
        calls += "start:$packageName" + if (wasLastPlayer) ":last" else ""
        if (packageName !in startable) onFailed()
    }

    override fun setMusicApp(packageName: String?) {
        musicApp = packageName
    }

    override fun refreshAccess() = Unit

    private fun step(delta: Int) {
        val now = state.value ?: return
        if (now.queue.isEmpty()) return
        val index = now.queue.indexOfFirst { it.current }
        val next = now.queue[(index + delta + now.queue.size) % now.queue.size]
        playQueueItem(next.id)
    }
}
