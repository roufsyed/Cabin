package com.roufsyed.cabin.notify

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.format.DateFormat
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Which line icon a notification gets. Apps' own icons are logos, so they are not shown. */
enum class TrayKind { Call, Message, Media, Other }

@Immutable
data class TrayItem(
    val key: String,
    val kind: TrayKind,
    /** The posting app's initial, drawn in a ring when [kind] is [TrayKind.Other]. */
    val letter: String,
    val title: String,
    val text: String,
    val time: String,
)

/** The notifications shown in Cabin's tray: the newest few that the user can act on or dismiss. */
interface TraySource {
    val items: StateFlow<List<TrayItem>>

    /** Does what tapping the notification in Android's own shade would do. */
    fun open(key: String)

    fun clearAll()
}

/** How many rows fit between the tray's heading and its buttons on an 800-high screen. */
const val TRAY_ROWS = 6

/** A fixed set of notifications: for the demo launch and for tests. */
class FixedTray(initial: List<TrayItem> = emptyList()) : TraySource {
    private val state = MutableStateFlow(initial)
    override val items: StateFlow<List<TrayItem>> = state
    val opened = mutableListOf<String>()

    override fun open(key: String) {
        opened += key
        state.value = state.value.filterNot { it.key == key }
    }

    override fun clearAll() {
        state.value = emptyList()
    }
}

/**
 * The unit's notifications, as delivered to Cabin's notification listener. Only what a driver might
 * act on is kept: not Cabin's own, not ongoing ones (those are the music and navigation already shown
 * on Home), not group summaries. Nothing is fetched or polled; the list changes when Android says a
 * notification was posted or removed.
 */
class AndroidTray(private val context: Context) : TraySource, ListenerClient {

    private class Entry(val item: TrayItem, val postedAt: Long, val intent: PendingIntent?, val autoCancel: Boolean)

    private val entries = HashMap<String, Entry>()
    private val labels = HashMap<String, String>()
    private var service: NotificationListenerService? = null

    private val state = MutableStateFlow<List<TrayItem>>(emptyList())
    override val items: StateFlow<List<TrayItem>> = state

    override fun onConnected(service: NotificationListenerService) {
        this.service = service
        entries.clear()
        val active = try {
            service.activeNotifications
        } catch (e: RuntimeException) {
            null
        }
        active?.forEach(::accept)
        publish()
    }

    override fun onDisconnected() {
        service = null
        entries.clear()
        publish()
    }

    override fun onPosted(notification: StatusBarNotification) {
        val had = entries.containsKey(notification.key)
        if (accept(notification) || had) publish()
    }

    override fun onRemoved(notification: StatusBarNotification) {
        if (entries.remove(notification.key) != null) publish()
    }

    override fun open(key: String) {
        val entry = entries[key] ?: return
        try {
            entry.intent?.send()
        } catch (e: PendingIntent.CanceledException) {
            // The app withdrew it; the notification is stale and is removed below.
        }
        if (entry.autoCancel || entry.intent == null) {
            try {
                service?.cancelNotification(key)
            } catch (e: RuntimeException) {
                // The listener was unbound in the meantime.
            }
            entries.remove(key)
            publish()
        }
    }

    override fun clearAll() {
        try {
            service?.cancelAllNotifications()
        } catch (e: RuntimeException) {
            // As above.
        }
        entries.clear()
        publish()
    }

    /** Keeps [sbn] if it belongs in the tray, drops any earlier version of it if not. True if kept. */
    private fun accept(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_TEXT) ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
            ?.toString()?.trim().orEmpty()
        val wanted = sbn.packageName != context.packageName &&
            sbn.isClearable && !sbn.isOngoing &&
            notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 &&
            (title.isNotEmpty() || text.isNotEmpty())
        if (!wanted) {
            entries.remove(sbn.key)
            return false
        }
        val app = labelOf(sbn.packageName)
        entries[sbn.key] = Entry(
            item = TrayItem(
                key = sbn.key,
                kind = kindOf(notification.category),
                letter = app.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
                title = title.ifEmpty { app },
                // One line only: a long message is cut with an ellipsis when drawn.
                text = text.lineSequence().firstOrNull().orEmpty(),
                time = timeOf(sbn.postTime),
            ),
            postedAt = sbn.postTime,
            intent = notification.contentIntent,
            autoCancel = notification.flags and Notification.FLAG_AUTO_CANCEL != 0,
        )
        return true
    }

    private fun publish() {
        state.value = entries.values.sortedByDescending { it.postedAt }.take(TRAY_ROWS).map { it.item }
    }

    private fun timeOf(millis: Long): String {
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
    }

    private fun labelOf(packageName: String): String = labels.getOrPut(packageName) {
        try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    companion object {
        fun kindOf(category: String?): TrayKind = when (category) {
            Notification.CATEGORY_CALL, Notification.CATEGORY_MISSED_CALL -> TrayKind.Call
            Notification.CATEGORY_MESSAGE, Notification.CATEGORY_EMAIL, Notification.CATEGORY_SOCIAL -> TrayKind.Message
            Notification.CATEGORY_TRANSPORT -> TrayKind.Media
            else -> TrayKind.Other
        }
    }
}
