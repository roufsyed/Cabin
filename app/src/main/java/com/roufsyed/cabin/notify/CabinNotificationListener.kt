package com.roufsyed.cabin.notify

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.roufsyed.cabin.CabinApp

/**
 * The one door through which Cabin sees the rest of the unit: with notification access granted,
 * Android binds this service and lets Cabin read media sessions and notifications.
 * It holds no state; everything is passed straight to the [ListenerHub].
 */
class CabinNotificationListener : NotificationListenerService() {

    private val hub: ListenerHub get() = (application as CabinApp).container.listenerHub

    override fun onListenerConnected() {
        hub.connected(this)
    }

    override fun onListenerDisconnected() {
        hub.disconnected()
    }

    override fun onNotificationPosted(notification: StatusBarNotification) {
        hub.posted(notification)
    }

    override fun onNotificationRemoved(notification: StatusBarNotification) {
        hub.removed(notification)
    }
}
