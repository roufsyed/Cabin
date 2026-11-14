package com.roufsyed.cabin.nav

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.compose.ui.graphics.asImageBitmap
import com.roufsyed.cabin.BuildConfig
import com.roufsyed.cabin.apps.AppsSource
import com.roufsyed.cabin.data.SettingsStore
import com.roufsyed.cabin.model.AppEntry
import com.roufsyed.cabin.model.NavUi
import com.roufsyed.cabin.model.Slot
import com.roufsyed.cabin.nav.MapsNotificationParser.MAPS_PACKAGE
import com.roufsyed.cabin.notify.ListenerClient
import com.roufsyed.cabin.sources.SourceRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Navigation through the unit's maps app. The route is read from Google Maps' ongoing notification,
 * through Cabin's notification listener: that is the one maps app whose route can be read, so with
 * any other the panel stays idle. Destinations go to whichever maps app is chosen in Setup, with
 * public intents and the app's own launcher shortcuts. Google's Navigation SDK is not used, and the
 * route line itself is not available this way.
 */
class MapsNavigation(
    private val context: Context,
    private val scope: CoroutineScope,
    private val apps: AppsSource,
    private val settings: SettingsStore,
    private val shortcuts: ShortcutHost = LauncherShortcuts(context),
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

    /** The maps app chosen in Setup, or the one guessed while none has been chosen. */
    private fun mapsApp(): AppEntry? = SourceRules.binding(Slot.Maps, settings.settings.value, apps.installed.value)

    override fun openMaps(): Boolean {
        // While a route is showing, the app to open is the one that is navigating.
        val navigating = if (current.value is NavUi.Active) {
            apps.installed.value.firstOrNull { SourceRules.packageOf(it.id) == MAPS_PACKAGE }
        } else {
            null
        }
        val app = navigating ?: mapsApp() ?: return false
        return apps.launch(app.id)
    }

    override fun navigateTo(destination: String): Boolean {
        if (destination.isBlank()) return false
        val app = mapsApp() ?: return false
        val packageName = SourceRules.packageOf(app.id)
        val address = NavigationIntents.destination(packageName, destination)
        // An app that does not take the address is at least opened, so the tap is never without effect.
        return start(Intent(Intent.ACTION_VIEW, address.toUri()).setPackage(packageName)) || apps.launch(app.id)
    }

    /**
     * Home and Work are kept in the user's account with the maps app, not in Cabin. Maps apps offer
     * them as launcher shortcuts (the ones on a long press of the icon), which the unit's home app is
     * allowed to start; that is the first choice. For Google Maps the "place" form of its navigation
     * intent is tried next. Failing both, the app is simply opened.
     */
    override fun navigateTo(place: SavedPlace): Boolean {
        val app = mapsApp() ?: return false
        // Looking up shortcuts asks the system, so it is kept off the main thread.
        scope.launch(Dispatchers.Default) {
            if (!startSavedPlace(SourceRules.packageOf(app.id), place)) apps.launch(app.id)
        }
        return true
    }

    private fun startSavedPlace(packageName: String, place: SavedPlace): Boolean {
        val offered = shortcuts.shortcuts(packageName)
        if (BuildConfig.DEBUG) Log.d(TAG, "$packageName shortcuts $offered")
        val shortcut = NavigationIntents.shortcutFor(place, offered)
        if (shortcut != null && shortcuts.start(packageName, shortcut.id)) return true
        if (packageName != MAPS_PACKAGE) return false
        return start(Intent(Intent.ACTION_VIEW, NavigationIntents.savedPlace(place).toUri()).setPackage(packageName))
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
