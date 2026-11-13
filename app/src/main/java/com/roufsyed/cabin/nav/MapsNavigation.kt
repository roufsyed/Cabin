package com.roufsyed.cabin.nav

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Process
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.compose.ui.graphics.asImageBitmap
import com.roufsyed.cabin.BuildConfig
import com.roufsyed.cabin.model.NavUi
import com.roufsyed.cabin.nav.MapsNotificationParser.MAPS_PACKAGE
import com.roufsyed.cabin.notify.ListenerClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Navigation as told by Google Maps: its ongoing notification is read through Cabin's notification
 * listener, and destinations are sent to it with its public intents and its own launcher shortcuts.
 * Google's Navigation SDK is not used, and the route line itself is not available this way.
 */
class MapsNavigation(
    private val context: Context,
    private val scope: CoroutineScope,
) : NavigationSource, ListenerClient {

    private val current = MutableStateFlow<NavUi>(NavUi.Idle)
    override val state: StateFlow<NavUi> = current

    /** The key of the notification being shown, so that only its removal clears the panel. */
    private var shownKey: String? = null
    private var lastRaw: RawNavigation? = null
    private var glyph: ImageBitmap? = null
    private var glyphHash = 0
    private var glyphJob: Job? = null

    // ---- Reading the route

    override fun onConnected(service: NotificationListenerService) {
        val active = try {
            service.activeNotifications
        } catch (e: RuntimeException) {
            null
        }
        active?.forEach(::onPosted)
    }

    override fun onDisconnected() = clear()

    override fun onPosted(notification: StatusBarNotification) {
        if (notification.packageName != MAPS_PACKAGE || !notification.isOngoing) return
        val extras = notification.notification.extras
        val raw = RawNavigation(
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
        )
        if (raw == lastRaw && notification.key == shownKey) return
        val parsed = MapsNotificationParser.parse(raw)
        if (BuildConfig.DEBUG) Log.d(TAG, "maps notification $raw -> $parsed")
        if (parsed == null) {
            // Not a navigation notification, or one this parser cannot read: show no route.
            if (notification.key == shownKey) clear()
            return
        }
        shownKey = notification.key
        lastRaw = raw
        current.value = NavUi.Active(parsed.distance, parsed.road, parsed.arrive, parsed.remaining, glyph)
        readGlyph(notification.notification)
    }

    override fun onRemoved(notification: StatusBarNotification) {
        if (notification.key == shownKey) clear()
    }

    private fun clear() {
        shownKey = null
        lastRaw = null
        glyph = null
        glyphHash = 0
        glyphJob?.cancel()
        current.value = NavUi.Idle
    }

    /**
     * The manoeuvre arrow is only available as the notification's small picture. It is decoded off the
     * main thread and replaced only when its pixels actually change, so a notification that updates
     * every second with the same arrow costs nothing to draw.
     */
    private fun readGlyph(notification: Notification) {
        val icon = notification.getLargeIcon() ?: return
        glyphJob?.cancel()
        glyphJob = scope.launch(Dispatchers.Main.immediate) {
            val decoded = withContext(Dispatchers.Default) {
                try {
                    val drawable = icon.loadDrawable(context) ?: return@withContext null
                    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
                    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
                    val bitmap = createBitmap(width, height)
                    drawable.setBounds(0, 0, width, height)
                    drawable.draw(Canvas(bitmap))
                    val pixels = IntArray(width * height)
                    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                    trimmed(bitmap, pixels) to pixels.contentHashCode()
                } catch (e: RuntimeException) {
                    null
                }
            } ?: return@launch
            val (bitmap, hash) = decoded
            if (hash == glyphHash && glyph != null) return@launch
            glyphHash = hash
            glyph = bitmap.asImageBitmap()
            (current.value as? NavUi.Active)?.let { current.value = it.copy(glyph = glyph) }
        }
    }

    /**
     * The arrow sits in the middle of a larger transparent picture. Cutting the empty margin away lets
     * the panel draw the arrow itself at the size the design gives its own.
     */
    private fun trimmed(bitmap: Bitmap, pixels: IntArray): Bitmap {
        val width = bitmap.width
        var left = width
        var top = bitmap.height
        var right = -1
        var bottom = -1
        for (index in pixels.indices) {
            if (pixels[index] ushr 24 > 24) {
                val x = index % width
                val y = index / width
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        if (right < left || bottom < top) return bitmap
        return Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1)
    }

    // ---- Sending destinations

    override fun openMaps(): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(MAPS_PACKAGE) ?: return false
        return start(intent)
    }

    override fun navigateTo(destination: String): Boolean {
        val query = destination.trim()
        if (query.isEmpty()) return false
        return start(Intent(Intent.ACTION_VIEW, ("google.navigation:q=" + Uri.encode(query)).toUri()).setPackage(MAPS_PACKAGE))
    }

    /**
     * Home and Work are kept in the user's Maps account, not in Cabin. Maps offers them as launcher
     * shortcuts (the ones on a long press of its icon), which the unit's home app is allowed to start;
     * that is the first choice. Failing that, the "place" form of the navigation intent is tried, which
     * Google documents for Maps on Android Automotive.
     */
    override fun navigateTo(place: SavedPlace): Boolean {
        val shortcut = if (place == SavedPlace.Home) "manifest_home" else "manifest_work"
        val launcher = context.getSystemService(LauncherApps::class.java)
        try {
            if (launcher != null && launcher.hasShortcutHostPermission()) {
                launcher.startShortcut(MAPS_PACKAGE, shortcut, null, null, Process.myUserHandle())
                return true
            }
        } catch (e: RuntimeException) {
            // The shortcut is missing in this Maps build, or Cabin is not the home app: try the intent.
        }
        val name = if (place == SavedPlace.Home) "home" else "work"
        return start(Intent(Intent.ACTION_VIEW, "google.navigation:place=$name".toUri()).setPackage(MAPS_PACKAGE))
    }

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: RuntimeException) {
        false
    }

    private companion object {
        const val TAG = "CabinNav"
    }
}
