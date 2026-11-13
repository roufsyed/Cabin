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

    fun playPause()
    fun next()
    fun previous()
    fun playQueueItem(id: Long)

    /** Starts playback in [packageName]'s session. False if it has none. */
    fun play(packageName: String): Boolean

    fun refreshAccess()
}

/** A fixed track with working buttons: for the demo launch and for tests. */
class FixedMedia(initial: NowPlaying?, hasAccess: Boolean = true) : MediaSource {
    private val state = MutableStateFlow(initial)
    override val nowPlaying: StateFlow<NowPlaying?> = state
    override val access: StateFlow<Boolean> = MutableStateFlow(hasAccess)
    val calls = mutableListOf<String>()

    override fun hasSession(packageName: String): Boolean = state.value?.packageName == packageName

    override fun playPause() {
        calls += "playPause"
        state.value = state.value?.let { it.copy(progress = it.progress.copy(playing = !it.progress.playing)) }
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

    override fun play(packageName: String): Boolean {
        calls += "play:$packageName"
        return hasSession(packageName)
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
