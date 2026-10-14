package com.example.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

data class StoredNotification(
    val key: String,
    val packageName: String,
    val appName: String,
    val sender: String,
    val text: String,
    val postedAt: Long,
    /** The notification's own inline-reply action (WhatsApp, Telegram, Messages...), if any. */
    val replyAction: Notification.Action?
)

/** The most recent message notifications, kept in memory only (never written to disk). */
object NotificationStore {
    private const val MAX = 40
    private val items = ArrayList<StoredNotification>()

    @Synchronized fun put(n: StoredNotification) {
        items.removeAll { it.key == n.key }
        items.add(n)
        while (items.size > MAX) items.removeAt(0)
    }

    @Synchronized fun recent(limit: Int = 8, app: String? = null, sender: String? = null): List<StoredNotification> =
        items.asReversed()
            .filter { app.isNullOrBlank() || it.appName.contains(app, ignoreCase = true) }
            .filter { sender.isNullOrBlank() || it.sender.contains(sender, ignoreCase = true) }
            .take(limit)

    @Synchronized fun clear() = items.clear()
}

/**
 * Lets JARVIS read and answer your message notifications. Switched on by the user in
 * Settings > Notification access. Only message-type notifications are kept, only in memory.
 */
class JarvisNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn?.notification ?: return
        if (sbn.packageName == packageName) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.category != Notification.CATEGORY_MESSAGE && n.category != Notification.CATEGORY_EMAIL) return

        val extras = n.extras ?: return
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        if (sender.isBlank() && text.isBlank()) return

        val app = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)

        val reply = n.actions?.firstOrNull { a -> a.remoteInputs?.isNotEmpty() == true }
        NotificationStore.put(StoredNotification(sbn.key, sbn.packageName, app, sender, text, sbn.postTime, reply))
    }

    override fun onListenerDisconnected() { NotificationStore.clear() }
}
