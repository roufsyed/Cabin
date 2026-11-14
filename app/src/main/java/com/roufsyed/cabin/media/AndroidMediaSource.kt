package com.roufsyed.cabin.media

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.service.media.MediaBrowserService
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

    /** The app chosen for music in Setup, if any. */
    private var musicPackage: String? = null

    /** The session last seen playing in this run. It stays on show after it stops, so play resumes it. */
    private var lastPlayed: Entry? = null

    /** The attempt, if one is under way, to start an app playing. */
    private var starter: Starter? = null

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
        starter?.cancel()
        update(emptyList())
        accessState.value = readAccess()
    }

    override fun setMusicApp(packageName: String?) {
        // Sessions are only ever touched on the main thread, and this decides which of them is shown.
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { setMusicApp(packageName) }
            return
        }
        if (musicPackage == packageName) return
        musicPackage = packageName
        publish()
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

    override fun playPause(): Boolean {
        val entry = primary()
        when {
            // Nothing to resume. With a music app chosen, starting it is the caller's job; without
            // one, a media key lets Android wake whichever app played last.
            entry == null -> if (musicPackage == null) mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) else return false
            entry.playback.isActive() -> entry.controller.transportControls.pause()
            else -> entry.controller.transportControls.play()
        }
        return true
    }

    /**
     * An app with a session is told to play. One that is not running is woken first, by holding a
     * connection to the media browser service that players offer for Android Auto: the player then
     * comes up with its session, and the session is told to prepare and play, which is how a player
     * is asked to carry on with what it played last. A player that offers no such service can only be
     * reached with a media key, which Android sends to the app that played last, so that is tried
     * when [wasLastPlayer]. Whichever way it was asked, if the app is not playing a few seconds later
     * the attempt counts as failed, so that pressing play is never without effect.
     */
    override fun start(packageName: String, wasLastPlayer: Boolean, onFailed: () -> Unit) {
        // A second press while the first is still being answered changes nothing.
        if (starter?.packageName == packageName) return
        starter?.cancel()
        val attempt = Starter(packageName, wasLastPlayer, onFailed)
        starter = attempt
        attempt.begin()
    }

    private fun browserService(packageName: String): ComponentName? = try {
        val intent = Intent(MediaBrowserService.SERVICE_INTERFACE).setPackage(packageName)
        context.packageManager.queryIntentServices(intent, 0).firstOrNull()?.serviceInfo
            ?.let { ComponentName(it.packageName, it.name) }
    } catch (e: RuntimeException) {
        null
    }

    private fun isPlaying(packageName: String): Boolean =
        entries.any { it.controller.packageName == packageName && it.playback.isActive() }

    /** One attempt to start an app playing. Everything in it runs on the main thread. */
    private inner class Starter(
        val packageName: String,
        private val wasLastPlayer: Boolean,
        private val onFailed: () -> Unit,
    ) : ServiceConnection {

        /** True while Cabin holds a connection to the player's service, which keeps the player awake. */
        private var bound = false

        /** True once the app has been told to play, by whatever means: from then on only [check] decides. */
        private var asked = false
        private var over = false
        private val noSession = Runnable { wakeFailed() }

        // Without notification access the app's session cannot be seen, so it is taken to have worked.
        private val check = Runnable { end(failed = accessState.value && !isPlaying(packageName)) }

        fun begin() {
            if (askSession()) return
            // Waking the player only helps if its session can then be seen, which takes notification access.
            val service = if (accessState.value) browserService(packageName) else null
            if (service == null || !bind(service)) {
                wakeFailed()
                return
            }
            main.postDelayed(noSession, SESSION_WAIT_MS)
        }

        /** Called whenever the list of sessions changes: the player that was woken may have arrived. */
        fun sessionsChanged() {
            if (!over && !asked) askSession()
        }

        /** Tells the app's session, if it has one, to carry on playing. */
        private fun askSession(): Boolean {
            val entry = entries.firstOrNull { it.controller.packageName == packageName } ?: return false
            asked = true
            main.removeCallbacks(noSession)
            val controls = entry.controller.transportControls
            // Prepare, then play: the order Android's own media card uses to resume a player.
            if (entry.playback?.state != PlaybackState.STATE_PAUSED) controls.prepare()
            controls.play()
            main.postDelayed(check, PLAY_WAIT_MS)
            return true
        }

        private fun bind(service: ComponentName): Boolean = try {
            bound = true
            context.bindService(Intent(MediaBrowserService.SERVICE_INTERFACE).setComponent(service), this, Context.BIND_AUTO_CREATE)
        } catch (e: RuntimeException) {
            // The service is not open to other apps.
            false
        }

        /**
         * The player could not be woken, or woke without a session. If it was the last app to play, a
         * media key still reaches it; otherwise it cannot be started from here.
         */
        private fun wakeFailed() {
            if (over || asked) return
            main.removeCallbacks(noSession)
            if (wasLastPlayer) {
                asked = true
                mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
                main.postDelayed(check, PLAY_WAIT_MS)
            } else {
                end(failed = true)
            }
        }

        // The connection itself is never used: holding it is what keeps the player running.
        override fun onServiceConnected(name: ComponentName, service: IBinder) = Unit

        override fun onServiceDisconnected(name: ComponentName) = Unit

        /** Drops the attempt without reporting anything. */
        fun cancel() {
            over = true
            release()
        }

        private fun end(failed: Boolean) {
            if (over) return
            over = true
            release()
            if (failed) onFailed()
        }

        private fun release() {
            main.removeCallbacks(noSession)
            main.removeCallbacks(check)
            if (bound) {
                bound = false
                try {
                    context.unbindService(this)
                } catch (e: RuntimeException) {
                    // Never connected, or already gone.
                }
            }
            if (starter === this) starter = null
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
        for (old in entries) {
            if (old !in kept) {
                old.stop()
                if (old === lastPlayed) lastPlayed = null
            }
        }
        entries = kept
        starter?.sessionsChanged()
        publish()
    }

    private fun remove(entry: Entry) {
        entry.stop()
        if (entry === lastPlayed) lastPlayed = null
        entries = entries - entry
        publish()
    }

    /** The session to show and control, by [MediaRules.shown]. Android lists them in its own priority order. */
    private fun primary(): Entry? {
        val index = MediaRules.shown(
            sessions = entries.map {
                SessionState(
                    packageName = it.controller.packageName,
                    active = it.playback.isActive(),
                    paused = it.playback?.state == PlaybackState.STATE_PAUSED,
                    titled = it.title.isNotEmpty(),
                )
            },
            lastPlayed = lastPlayed?.let(entries::indexOf) ?: -1,
            musicPackage = musicPackage,
        )
        val entry = entries.getOrNull(index)
        if (entry != null && entry.playback.isActive()) lastPlayed = entry
        return entry
    }

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

        /** How long a player that has been woken may take to come up with its session. */
        const val SESSION_WAIT_MS = 4_000L

        /** How long after being told to play an app has to actually be playing. */
        const val PLAY_WAIT_MS = 4_000L

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
