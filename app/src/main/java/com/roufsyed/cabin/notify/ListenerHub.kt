package com.roufsyed.cabin.notify

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Something that wants to know what the notification listener sees. All calls arrive on the main thread. */
interface ListenerClient {
    fun onConnected(service: NotificationListenerService) = Unit
    fun onDisconnected() = Unit
    fun onPosted(notification: StatusBarNotification) = Unit
    fun onRemoved(notification: StatusBarNotification) = Unit
}

/** Passes the listener service's events on to whoever registered: media, navigation, the tray. */
class ListenerHub(private val clients: List<ListenerClient> = emptyList()) {
    fun connected(service: NotificationListenerService) = clients.forEach { it.onConnected(service) }
    fun disconnected() = clients.forEach { it.onDisconnected() }
    fun posted(notification: StatusBarNotification) = clients.forEach { it.onPosted(notification) }
    fun removed(notification: StatusBarNotification) = clients.forEach { it.onRemoved(notification) }
}
