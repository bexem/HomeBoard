package com.evofactory.homeboard

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat

/** One notification the TV is showing, as the overlay lists it */
data class LauncherNotification(
    val key: String,
    val packageName: String,
    val app: String,
    val title: String,
    val text: String,
    val time: Long,
    /** A service's own notification, or one that cannot be dismissed */
    val ongoing: Boolean,
    val clearable: Boolean,
)

/**
 * The TV's notifications, read through [LauncherNotificationListener].
 *
 * Android hands an app other apps' notifications only once notification access is granted, so this is
 * empty until then and the overlay says how to grant it. [entries] is what the listener last reported,
 * newest first; [granted] is true while the system has the listener bound.
 */
object Notifications {
    var entries by mutableStateOf<List<LauncherNotification>>(emptyList())
        private set

    /** The system has the listener bound, which it only does while notification access is on */
    var granted by mutableStateOf(false)
        private set

    private var service: LauncherNotificationListener? = null

    fun connect(listener: LauncherNotificationListener, active: Array<StatusBarNotification>?) {
        service = listener
        granted = true
        refresh(active)
    }

    fun disconnect() {
        service = null
        granted = false
        entries = emptyList()
    }

    /** The whole list, read again: a notification was posted or removed */
    fun refresh(active: Array<StatusBarNotification>?) {
        entries = active.orEmpty().mapNotNull(::entry).sortedByDescending { it.time }
    }

    /** Dismisses every notification the TV lets an app dismiss */
    fun clearAll() {
        service?.cancelAllNotifications()
    }

    /** Opens what the notification leads to; false when it leads nowhere */
    fun open(entry: LauncherNotification): Boolean {
        val target = service?.activeNotifications?.firstOrNull { it.key == entry.key }
        if (target == null) {
            AppLog.w("Notifications", "No live notification for ${entry.packageName}: nothing to open")
            return false
        }
        val intent = target.notification?.contentIntent
        if (intent == null) {
            // A service's own notification often has none, and a media one lives in its session
            AppLog.i("Notifications", "${entry.packageName} has no content intent")
            return false
        }
        return runCatching { intent.send() }
            .onSuccess { AppLog.i("Notifications", "Opened ${entry.packageName}") }
            .onFailure { AppLog.w("Notifications", "Can't open ${entry.packageName}", it) }
            .isSuccess
    }

    /** Asks the system to bind the listener again, for a grant made while the app was running */
    fun requestRebind(context: Context) {
        if (granted) return
        runCatching {
            NotificationListenerService.requestRebind(
                ComponentName(context, LauncherNotificationListener::class.java),
            )
        }
    }

    /** Notification access is on for this app, from the application object the launcher already has */
    val allowed: Boolean
        get() = runCatching { enabled(HomeBoardApp.instance) }.getOrDefault(false)

    /** Notification access is on for this app: the system's answer, not the listener's */
    fun enabled(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    private fun entry(sbn: StatusBarNotification): LauncherNotification? {
        val notification = sbn.notification ?: return null
        // A summary heads a stack; the messages it stands for are listed on their own
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        val extras = notification.extras ?: Bundle.EMPTY
        // A transport notification has neither: the row then shows the app alone, not its package name
        val title = extras.text(Notification.EXTRA_TITLE_BIG)
            ?: extras.text(Notification.EXTRA_TITLE)
            ?: ""
        val text = extras.text(Notification.EXTRA_TEXT)
            ?: extras.text(Notification.EXTRA_BIG_TEXT)
            ?: extras.text(Notification.EXTRA_SUMMARY_TEXT)
            ?: ""
        return LauncherNotification(
            key = sbn.key,
            packageName = sbn.packageName,
            app = appLabel(sbn.packageName),
            title = title.toString(),
            text = text.toString(),
            time = sbn.postTime,
            ongoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0 || !sbn.isClearable,
            clearable = sbn.isClearable,
        )
    }
}

/** What one of the notification's extras holds, if it holds anything */
private fun Bundle.text(key: String): CharSequence? =
    runCatching { getCharSequence(key) }.getOrNull()?.takeIf { it.isNotBlank() }

/** The app's name, with the package name when Android has none to give */
private fun appLabel(packageName: String): String = appLabels.getOrPut(packageName) {
    val context = HomeBoardApp.instance
    runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)
}

// Short-lived: an overlay of ten notifications is ten label lookups per redraw
private val appLabels = mutableMapOf<String, String>()

/**
 * The listener itself, which only reports: everything drawn comes from [Notifications], so the
 * overlay does not depend on this class being alive while it is open.
 */
class LauncherNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        Notifications.connect(this, activeNotifications)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Notifications.disconnect()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Notifications.refresh(activeNotifications)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        Notifications.refresh(activeNotifications)
    }

    override fun onDestroy() {
        Notifications.disconnect()
        super.onDestroy()
    }
}
