package com.evofactory.homeboard

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/**
 * The TV's notifications, the panel Android TV keeps in its own launcher and offers no way to open
 * from another app: the same list, drawn here, because the listener has it.
 *
 * OK on a notification opens what it came from; "Clear all" dismisses the ones the TV lets an app
 * dismiss. With notification access off there is nothing to show, so the dialog says how to turn it on
 * instead of listing nothing.
 */
@Composable
fun NotificationsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    // The system's answer, or the listener's: whichever says yes, there is a list to draw
    val enabled = Notifications.granted || Notifications.enabled(context)
    // A grant made while the app was running leaves the listener unbound until it is asked again
    LaunchedEffect(Unit) { Notifications.requestRebind(context) }
    val entries = Notifications.entries
    val first = remember { FocusRequester() }
    // The row does not exist until the list is laid out, and one frame is not always enough
    LaunchedEffect(enabled, entries.size) {
        if (!enabled || entries.isEmpty()) return@LaunchedEffect
        first.requestFocusAfterLayout()
    }

    TopDialog(
        title = "Notifications",
        // Room for an app name, a title and two lines of text
        width = 720.dp,
        onDismiss = onDismiss,
        // Like the drawer this stands in for: Clear all is always offered with a list, and what the
        // TV refuses to dismiss (its ongoing services) is why some rows survive it
        buttons = if (enabled && entries.isNotEmpty()) {
            listOf(DialogButton("Clear all") { Notifications.clearAll() })
        } else {
            emptyList()
        },
        contentFocus = { first },
        hint = if (enabled && entries.isNotEmpty() && entries.none { it.clearable }) {
            "None of these can be dismissed: they belong to running services."
        } else {
            null
        },
    ) {
        when {
            !enabled -> NotificationAccessOff()
            entries.isEmpty() -> Text(
                "Nothing on the TV right now.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(entries, key = { it.key }) { entry ->
                    NotificationRow(
                        entry = entry,
                        modifier = Modifier
                            .then(if (entry == entries.first()) Modifier.focusRequester(first) else Modifier)
                            .ringToTop(),
                        onClick = { if (Notifications.open(entry)) onDismiss() },
                    )
                }
            }
        }
    }
}

/** One notification: the app it is from, its title, its text, and how long it has been there */
@Composable
private fun NotificationRow(entry: LauncherNotification, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
            focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    entry.app,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.title.isNotEmpty()) {
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (entry.text.isNotEmpty()) {
                    Text(
                        entry.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                relativeTime(entry.time),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** "5 min. ago", in the TV's language */
private fun relativeTime(time: Long): String = DateUtils.getRelativeTimeSpanString(
    time,
    System.currentTimeMillis(),
    DateUtils.MINUTE_IN_MILLIS,
).toString()

/**
 * Notification access off. Android TV has no settings screen for it on every box (the Shield's Settings
 * app has none), so the one sure way to grant it is the shell command, which the dialog gives.
 */
@Composable
private fun NotificationAccessOff() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
        Text(
            "HomeBoard can only list the TV's notifications once it is allowed to read them.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Android TV often has no screen for this. From a computer on the same network:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "adb shell cmd notification allow_listener\ncom.evofactory.homeboard/.LauncherNotificationListener",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            "The listener binds straight away, with no restart.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * What "Access" means once it is granted: the state, and the command that takes it back. The panel's
 * off state prints the allow counterpart, so the two screens read as one subject from both sides.
 */
@Composable
fun NotificationAccessDialog(onDismiss: () -> Unit) {
    // Read-only text: the X every dialog has is the whole top row, and where the remote starts
    val close = remember { FocusRequester() }
    TopDialog(
        title = "Notification access",
        width = 720.dp,
        onDismiss = onDismiss,
        closeFocus = close,
        openFocus = { close },
        contentFocus = { close },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
            Text(
                "Allowed: HomeBoard reads the TV's notifications and lists them under Notifications panel.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "To take it back, from a computer on the same network:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "adb shell cmd notification disallow_listener\ncom.evofactory.homeboard/.LauncherNotificationListener",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
