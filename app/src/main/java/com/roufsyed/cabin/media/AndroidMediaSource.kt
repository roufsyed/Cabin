package com.roufsyed.cabin.media

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale
import com.roufsyed.cabin.notify.CabinNotificationListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reads the unit's media sessions, which Android only shows to an app with notification access.
 *
 * Everything here runs on the main thread from session callbacks, and does no work between them: each
 * session's details are cached as they arrive, so publishing a change costs no calls to other apps.
 * The one heavy step, scaling cover art, is done on a background thread.
 */
class AndroidMediaSource(
    private val context: Context,
    private val scope: CoroutineScope,
) : MediaSource {

    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val listener = ComponentName(context, CabinNotificationListener::class.java)

    private val state = MutableStateFlow<NowPlaying?>(null)
    private val accessState = MutableStateFlow(readAccess())
    override val nowPlaying: StateFlow<NowPlaying?> = state
    override val access: StateFlow<Boolean> = accessState

    /** One per active session, newest details only. Never holds the full-size cover bitmap. */
    private inner class Entry(val controller: MediaController) : MediaController.Callback() {
        var title = ""
        var artist = ""
        var durationMs = 0L
        var art: ImageBitmap? = null
        var playback: PlaybackState? = null
        var queue: List<MediaSession.QueueItem> = emptyList()
        var queueTitle: String? = null
        private var artJob: Job? = null

        fun start() {
            controller.registerCallback(this, main)
            readMetadata(controller.metadata)
            playback = controller.playbackState
            queue = controller.queue.orEmpty()
            queueTitle = controller.queueTitle?.toString()
        }

        fun stop() {
            controller.unregisterCallback(this)
            artJob?.cancel()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            readMetadata(metadata)
            publish()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            playback = state
            publish()
        }

        override fun onQueueChanged(items: MutableList<MediaSession.QueueItem>?) {
            queue = items.orEmpty()
            publish()
        }

        override fun onQueueTitleChanged(title: CharSequence?) {
            queueTitle = title?.toString()
            publish()
        }

        override fun onSessionDestroyed() {
            remove(this)
        }

        private fun readMetadata(metadata: MediaMetadata?) {
            title = metadata.text(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, MediaMetadata.METADATA_KEY_TITLE)
            artist = metadata.text(
                MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
                MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
            )
            durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            val cover = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            artJob?.cancel()
            if (cover == null) {
                art = null
            } else {
                artJob = scope.launch(Dispatchers.Main.immediate) {
                    art = withContext(Dispatchers.Default) { scaleCover(cover) }
                    publish()
                }
            }
        }
    }

    private var entries: List<Entry> = emptyList()
    private var listening = false
    private val labels = HashMap<String, String>()

    private val onSessions = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        update(controllers.orEmpty())
    }

    init {
        if (accessState.value) connect()
    }

    /** Starts watching sessions. Safe to call again; does nothing without notification access. */
    fun connect() {
        accessState.value = readAccess()
        if (listening || !accessState.value) return
        try {
            sessions.addOnActiveSessionsChangedListener(onSessions, listener, main)
            listening = true
            update(sessions.getActiveSessions(listener))
        } catch (e: SecurityException) {
            accessState.value = false
        }
    }

    fun disconnect() {
        if (listening) {
            sessions.removeOnActiveSessionsChangedListener(onSessions)
            listening = false
        }
        update(emptyList())
        accessState.value = readAccess()
    }

    override fun refreshAccess() {
        val granted = readAccess()
        accessState.value = granted
        if (granted) connect() else if (listening) disconnect()
    }

    override fun hasSession(packageName: String): Boolean = entries.any { it.controller.packageName == packageName }

    override fun play(packageName: String): Boolean {
        val entry = entries.firstOrNull { it.controller.packageName == packageName } ?: return false
        entry.controller.transportControls.play()
        return true
    }

    override fun playPause() {
        val entry = primary()
        when {
            entry == null -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            entry.playback.isActive() -> entry.controller.transportControls.pause()
            else -> entry.controller.transportControls.play()
        }
    }

    override fun next() {
        primary()?.controller?.transportControls?.skipToNext() ?: mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    override fun previous() {
        primary()?.controller?.transportControls?.skipToPrevious() ?: mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    override fun playQueueItem(id: Long) {
        primary()?.controller?.transportControls?.skipToQueueItem(id)
    }

    /** With no session to talk to, a media key lets Android wake the app that played last. */
    private fun mediaKey(code: Int) {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun update(controllers: List<MediaController>) {
        val kept = ArrayList<Entry>(controllers.size)
        for (controller in controllers) {
            val existing = entries.firstOrNull { it.controller.sessionToken == controller.sessionToken }
            kept += existing ?: Entry(controller).also { it.start() }
        }
        for (old in entries) if (old !in kept) old.stop()
        entries = kept
        publish()
    }

    private fun remove(entry: Entry) {
        entry.stop()
        entries = entries - entry
        publish()
    }

    /** The session that is playing, or failing that the first one Android lists (its own priority order). */
    private fun primary(): Entry? =
        entries.firstOrNull { it.playback.isActive() } ?: entries.firstOrNull { it.title.isNotEmpty() } ?: entries.firstOrNull()

    private fun publish() {
        val entry = primary()
        if (entry == null) {
            state.value = null
            return
        }
        val playback = entry.playback
        val activeId = playback?.activeQueueItemId ?: MediaSession.QueueItem.UNKNOWN_ID.toLong()
        val currentIndex = entry.queue.indexOfFirst { it.queueId == activeId }
        val packageName = entry.controller.packageName
        state.value = NowPlaying(
            packageName = packageName,
            appLabel = labelOf(packageName),
            title = entry.title,
            artist = entry.artist,
            progress = PlaybackProgress(
                positionMs = playback?.position ?: 0L,
                durationMs = entry.durationMs,
                speed = playback?.playbackSpeed ?: 0f,
                playing = playback.isActive(),
                atElapsedMs = playback?.lastPositionUpdateTime ?: 0L,
            ),
            art = entry.art,
            queueTitle = entry.queueTitle?.takeIf { it.isNotBlank() } ?: "Up next",
            queue = queueWindow(entry.queue, currentIndex).map { item ->
                QueueItem(
                    id = item.queueId,
                    title = item.description.title?.toString().orEmpty(),
                    subtitle = item.description.subtitle?.toString().orEmpty(),
                    current = item.queueId == activeId,
                )
            },
        )
    }

    private fun labelOf(packageName: String): String = labels.getOrPut(packageName) {
        try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun readAccess(): Boolean = try {
        notifications.isNotificationListenerAccessGranted(listener)
    } catch (e: RuntimeException) {
        false
    }

    private companion object {
        /** The largest size cover art is drawn at is 150 design pixels; twice that covers any scale. */
        const val COVER_PX = 300

        fun PlaybackState?.isActive(): Boolean =
            this != null && (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING)

        fun MediaMetadata?.text(vararg keys: String): String {
            if (this == null) return ""
            for (key in keys) {
                val value = getText(key)?.toString()
                if (!value.isNullOrBlank()) return value
            }
            return ""
        }

        fun scaleCover(source: Bitmap): ImageBitmap {
            val side = minOf(source.width, source.height)
            val square = if (source.width == source.height) {
                source
            } else {
                Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
            }
            val scaled = if (side > COVER_PX) square.scale(COVER_PX, COVER_PX) else square
            // An own copy, so the app that sent the cover can recycle its bitmap without affecting ours.
            val owned = if (scaled === source) source.copy(Bitmap.Config.ARGB_8888, false) else scaled
            return owned.asImageBitmap()
        }
    }
}
