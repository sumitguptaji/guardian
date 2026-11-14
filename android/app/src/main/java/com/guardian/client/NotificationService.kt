package com.guardian.client

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Captures notifications from every app the moment they are posted - this is the
 * only way to see WhatsApp/Instagram/Signal message previews without rooting.
 *
 * IMPORTANT: there is no Android API to read notifications that arrived BEFORE
 * this listener was enabled. "Full notification history" therefore means
 * everything from the time the user turns this on in Settings onward. At enable
 * time we also grab whatever is currently in the shade.
 */
class NotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        // Capture notifications already showing when monitoring is switched on.
        runCatching { activeNotifications?.forEach(::save) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = save(sbn)

    private fun save(sbn: StatusBarNotification) {
        runCatching {
            val n = sbn.notification ?: return
            val extras = n.extras
            val title = extras.getCharSequence("android.title")?.toString()
            val text = extras.getCharSequence("android.text")?.toString()
                ?: extras.getCharSequence("android.bigText")?.toString()
            val pkg = sbn.packageName
            val label = runCatching {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(pkg, 0)
                ).toString()
            }.getOrNull()
            // Skip our own ongoing notification and empty system ones.
            if (pkg == packageName) return
            if (title.isNullOrBlank() && text.isNullOrBlank()) return
            Store(this).addNotif(sbn.postTime, pkg, label, title, text)
        }
    }
}
