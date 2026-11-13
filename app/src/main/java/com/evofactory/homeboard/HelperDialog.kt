package com.evofactory.homeboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * The home helper: what it is, whether it runs, and the two things that can be done about it. It used to
 * be a block of four lines under the Advanced rows, which read as a warning even when it was off by
 * choice; the row in Launcher shows the state and this says the rest.
 */
@Composable
fun HomeHelperDialog(prefs: LauncherPrefs, onOpenAccessibility: () -> Unit, onTurnOff: () -> Unit, onDismiss: () -> Unit) {
    val state = rememberHomeOverrideState()
    val wanted = prefs.catchHomeKey || prefs.reopenAfterHome
    val isDefaultHome = remember { HomeBoardApp.instance.isDefaultHomeApp() }
    val running = state == HomeOverrideState.Running
    TopDialog(
        title = "Home helper",
        onDismiss = onDismiss,
        subtitle = "Android allows one accessibility service at a time, and it has to be switched on by hand.",
        buttons = buildList {
            // Already the TV's home app: there is nothing to switch on, and nothing to switch off
            if (!isDefaultHome && !running) add(DialogButton("Accessibility settings", onClick = { onOpenAccessibility(); onDismiss() }))
            if (!isDefaultHome && wanted) add(DialogButton("Turn it off", onClick = { onTurnOff(); onDismiss() }))
            add(DialogButton("Done", primary = true, onClick = onDismiss))
        },
        contentFocus = { null },
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(9.dp).background(
                        if (running) HelperRunningColor else HelperIdleColor,
                        CircleShape,
                    ),
                )
                Text(
                    when {
                        isDefaultHome -> "Not needed: this is the TV's home app"
                        running -> "Running"
                        wanted -> "Asked for, not running"
                        else -> "Off"
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Text(
                when {
                    isDefaultHome -> "This app is already the TV's home app, so Home opens it without the helper. Turning it on only matters on a TV whose own home screen cannot be replaced."
                    state == HomeOverrideState.EnabledNotRunning ->
                        "Android dropped the grant when the app was force-stopped. Open Accessibility settings, turn HomeBoard off and on, and it runs again."
                    wanted -> "Turn HomeBoard on in Accessibility settings: Home then opens this launcher instead of the TV's own home screen."
                    else -> "Off. Turn on \"Catch the Home button\" or \"Reopen after Home\" to use it."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "It reads the Home button and which app's window opens: nothing else on screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** A confirmation before something that cannot be undone from the TV */
@Composable
fun ConfirmDialog(title: String, detail: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    TopDialog(
        title = title,
        onDismiss = onDismiss,
        buttons = listOf(
            DialogButton("Cancel", onClick = onDismiss),
            DialogButton(confirm, primary = true, onClick = { onConfirm(); onDismiss() }),
        ),
        contentFocus = { null },
    ) {
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        )
    }
}
