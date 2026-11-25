package com.roufsyed.cabin.media

import android.app.ActivityOptions
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.service.media.MediaBrowserService
import android.view.KeyEvent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.net.toUri
import com.roufsyed.cabin.notify.CabinNotificationListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reads the unit's media sessions, which Android only shows to an app with notification access.
 *
 * Everything here runs on the main thread from session callbacks, and does no work between them: each
 * session's details are cached as they arrive, so publishing a change costs no calls to other apps.
 * The heavy steps, scaling a track's cover or reading it from its player, are done on background threads.
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

        // For the Players page only: the length exactly as it was given, if it was, the names of
        // the track details the player gives, and the names of whatever else its session carries.
        var durationGiven: Long? = null
        var details: List<String> = emptyList()
        var sessionExtras: List<String> = emptyList()
        var playback: PlaybackState? = null
        private var artJob: Job? = null

        fun start() {
            controller.registerCallback(this, main)
            readMetadata(controller.metadata)
            playback = controller.playbackState
            sessionExtras = try {
                named(controller.extras)
            } catch (e: RuntimeException) {
                // The session went away as it was being read.
                emptyList()
            }
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

        override fun onExtrasChanged(extras: Bundle?) {
            sessionExtras = named(extras)
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
            durationGiven = metadata?.takeIf { it.containsKey(MediaMetadata.METADATA_KEY_DURATION) }
                ?.getLong(MediaMetadata.METADATA_KEY_DURATION)
            durationMs = durationGiven ?: 0L
            details = detailsOf(metadata)
            val cover = COVER_KEYS.firstNotNullOfOrNull { metadata?.getBitmap(it) }
            val address = if (cover == null) {
                MediaRules.coverAddress(COVER_ADDRESS_KEYS.map { metadata?.getString(it) })
            } else {
                null
            }
            artJob?.cancel()
            artJob = null
            // Nothing on show, and nothing to fetch.
            if (cover == null && address == null && art == null) return
            artJob = scope.launch(Dispatchers.Main.immediate) {
                val fresh = if (cover != null) {
                    withContext(Dispatchers.Default) { Covers.scaled(cover) }
                } else {
                    // A player sends a new track's details first and its picture a moment later, and
                    // that starts this again. So the picture on show is left up until one follows or
                    // the wait is over, and only then is the address tried: no blink between tracks.
                    delay(COVER_WAIT_MS)
                    address?.let { withContext(Dispatchers.IO) { Covers.read(context.contentResolver, it.toUri()) } }
                }
                art = fresh
                publish()
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

    override fun openPlayer(): Boolean {
        val screen = primary()?.controller?.let(::ownScreen) ?: return false
        return try {
            screen.send(context, 0, null, null, null, null, ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle())
            true
        } catch (e: PendingIntent.CanceledException) {
            false
        } catch (e: RuntimeException) {
            false
        }
    }

    private fun ownScreen(controller: MediaController): PendingIntent? = try {
        controller.sessionActivity?.takeIf { Build.VERSION.SDK_INT < 31 || it.isActivity }
    } catch (e: RuntimeException) {
        null
    }

    /** Like everything else here it is for the main thread only. It asks no other app anything. */
    override fun players(): List<PlayerFacts> {
        val shown = primary()
        val now = SystemClock.elapsedRealtime()
        return entries.map { entry ->
            val playback = entry.playback
            val told = playback?.lastPositionUpdateTime ?: 0L
            val packageName = entry.controller.packageName
            PlayerFacts(
                packageName = packageName,
                appLabel = labelOf(packageName),
                shown = entry === shown,
                state = playback?.state,
                positionMs = playback?.position ?: -1L,
                speed = playback?.playbackSpeed ?: 0f,
                // Android reports zero for a player that has never said where its track is.
                toldAgoMs = if (told > 0L) now - told else null,
                actions = playback?.actions ?: 0L,
                durationMs = entry.durationGiven,
                details = entry.details,
                // A player's own buttons first: they say the most in the least room.
                extras = extrasOf(playback) + entry.sessionExtras,
                screen = ownScreen(entry.controller) != null,
            )
        }
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
        /** Where a player puts a track's picture: the album's cover first, then any other. */
        val COVER_KEYS = listOf(
            MediaMetadata.METADATA_KEY_ALBUM_ART,
            MediaMetadata.METADATA_KEY_ART,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON,
        )

        /** Where a player says a track's picture can be read instead, in the same order. */
        val COVER_ADDRESS_KEYS = listOf(
            MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
            MediaMetadata.METADATA_KEY_ART_URI,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
        )

        /**
         * How long a track's picture may take to follow its title before the old picture is taken
         * down. Gramophone's came 12 to 25 ms behind on the emulator; this allows for a slower unit.
         */
        const val COVER_WAIT_MS = 500L

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

        /** The start of the name of every track detail Android itself defines. */
        const val ANDROID_DETAIL = "android.media.metadata."

        /** How much of a value is put on the Players page. */
        const val VALUE_SHOWN = 24

        /**
         * The names of the details a player gives for its track. A detail under a name of the
         * player's own comes with its value, since that is where a player that does not use
         * Android's names would put, say, the track's length.
         */
        fun detailsOf(metadata: MediaMetadata?): List<String> {
            if (metadata == null) return emptyList()
            return try {
                metadata.keySet().sorted().map { key ->
                    if (key.startsWith(ANDROID_DETAIL)) return@map key
                    val text = metadata.getString(key)
                    val number = if (text == null) metadata.getLong(key) else 0L
                    when {
                        text != null -> "$key=${text.take(VALUE_SHOWN)}"
                        number != 0L -> "$key=$number"
                        else -> key
                    }
                }
            } catch (e: RuntimeException) {
                listOf("something Cabin cannot read")
            }
        }

        /**
         * The names in a bundle another app sent, each with its value where that is a plain one.
         * Such a bundle can hold things only its own app can read, hence the care.
         */
        fun named(bundle: Bundle?): List<String> {
            if (bundle == null) return emptyList()
            return try {
                bundle.keySet().sorted().map { key ->
                    @Suppress("DEPRECATION")
                    when (val value = bundle.get(key)) {
                        is String, is Number, is Boolean -> "$key=${value.toString().take(VALUE_SHOWN)}"
                        else -> key
                    }
                }
            } catch (e: RuntimeException) {
                listOf("something Cabin cannot read")
            }
        }

        /** A player's own buttons, and whatever it sends along with where its track is. */
        fun extrasOf(playback: PlaybackState?): List<String> {
            if (playback == null) return emptyList()
            val buttons = try {
                // By the name the player gives it for people, which says more than its own code for it.
                playback.customActions.map { "button ${it.name?.toString()?.takeIf(String::isNotBlank) ?: it.action}" }
            } catch (e: RuntimeException) {
                emptyList()
            }
            return buttons + named(playback.extras)
        }
    }
}
