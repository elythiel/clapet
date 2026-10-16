package xyz.martelle.clapet.notifications

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import xyz.martelle.clapet.notificationHub

/**
 * Bound by the system once the user grants notification access. Feeds the hub with the active
 * notifications and, through the same access, the media sessions.
 */
class ClapetNotificationListener : NotificationListenerService() {

    private var media: MediaWatcher? = null

    override fun onListenerConnected() {
        val hub = notificationHub
        hub.onConnected(this)
        refresh()
        media = MediaWatcher(this, ComponentName(this, ClapetNotificationListener::class.java), hub::showSession)
            .also { it.start() }
    }

    override fun onListenerDisconnected() {
        media?.stop()
        media = null
        notificationHub.onDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    /** The whole list each time: simpler than patching it, and a few dozen items at most. */
    private fun refresh() {
        val active = try {
            activeNotifications
        } catch (e: SecurityException) {
            null
        }
        notificationHub.publish(active?.toList().orEmpty())
    }
}
