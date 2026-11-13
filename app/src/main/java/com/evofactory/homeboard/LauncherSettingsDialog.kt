package com.evofactory.homeboard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * For the top bar, only when Home is set to open this launcher and the helper Android needs for that
 * is not running. No warning when this app already is the TV's home app: then Home opens it anyway (D8).
 */
@Composable
fun HomeHelperWarning(replaceHome: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state = rememberHomeOverrideState()
    // The helper connects a moment after the launcher starts: no warning flash meanwhile
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(HELPER_START_GRACE_MS)
        settled = true
    }
    if (!replaceHome || !settled || state == HomeOverrideState.Running) return
    if (remember(context) { context.isDefaultHomeApp() }) return
    TopBarWarning(
        // Short: it shares the top bar with the action buttons. Settings has the details.
        if (state == HomeOverrideState.EnabledNotRunning) "Helper not running" else "Helper turned off",
        onClick = onClick,
        modifier = modifier,
    )
}

/** Says what the accessibility service does, before either option that needs it is turned on. Declining leaves it off. */
@Composable
fun HomeOverrideConsentDialog(onAccept: () -> Unit, onDecline: () -> Unit) {
    val serviceName = stringResource(R.string.home_override_label)
    val accept = remember { FocusRequester() }
    TopDialog(
        title = "Use the Accessibility service?",
        onDismiss = onDecline,
        buttons = listOf(DialogButton("Accept", primary = true, focus = accept, onClick = onAccept)),
    ) {
        Text(
            "To take over from the TV's home screen, this app uses Android's Accessibility service, “$serviceName”. " +
                "With these options on, it sees your remote's button presses and which app's window opens, " +
                "only to open this launcher when you press Home or the TV's home screen appears. " +
                "It reads nothing else on screen, and it doesn't save or send any of this anywhere.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp),
        )
        Text(
            "You'll also need to turn it on in the TV's Accessibility settings. You can turn it off at any time.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        LaunchedEffect(Unit) { accept.requestFocus() }
    }
}

/**
 * Starts the launcher over in a new process, which frees whatever it holds, e.g. stuck video decoders.
 * Killed rather than exited: exiting runs cleanup code, which a stuck thread could hold up.
 */
fun Context.restartApp() {
    startActivity(Intent.makeRestartActivityTask(ComponentName(this, MainActivity::class.java)))
    Process.killProcess(Process.myPid())
}

private const val HELPER_START_GRACE_MS = 3_000L
