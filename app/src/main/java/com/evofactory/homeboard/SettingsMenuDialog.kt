package com.evofactory.homeboard

import android.content.Context
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Date

/** What the ACTION rows do. One hook per action; a new action adds one here and one enum value. */
class SettingsActions(
    val openFavourites: () -> Unit,
    val openCalendars: () -> Unit,
    val openBackgroundColour: () -> Unit,
    val pickBackgroundImage: () -> Unit,
    val openCalendarColours: () -> Unit,
    val openHaCameras: () -> Unit,
    val openCameras: () -> Unit,
    val openScreensaver: () -> Unit,
    val openEntityNames: () -> Unit,
    val openHaAddress: () -> Unit,
    val logOutHomeAssistant: () -> Unit,
    val openHaLogin: () -> Unit,
    val openHomeHelper: () -> Unit,
    // Null opens the dialog where it starts: the connection, or what it already holds
    val openHomeAssistant: (HaDialogTab?) -> Unit,
    val openFrigate: () -> Unit,
    // The notifications panel, which also says how to grant notification access
    val openNotificationAccess: () -> Unit,
    val openPing: () -> Unit,
    // Clears the Alerts tab's dot and the top bar's count
    val markAlertsReviewed: () -> Unit,
    val openSystemSettings: () -> Unit,
    val exportSettings: () -> Unit,
    val importSettings: () -> Unit,
    val restartApp: () -> Unit,
) {
    fun of(action: SettingAction): () -> Unit = when (action) {
        SettingAction.OpenFavourites -> openFavourites
        SettingAction.OpenCalendars -> openCalendars
        SettingAction.OpenBackgroundColour -> openBackgroundColour
        SettingAction.PickBackgroundImage -> pickBackgroundImage
        SettingAction.OpenCalendarColours -> openCalendarColours
        SettingAction.OpenHaCameras -> openHaCameras
        SettingAction.OpenCameras -> openCameras
        SettingAction.OpenScreensaver -> openScreensaver
        SettingAction.OpenEntityNames -> openEntityNames
        SettingAction.OpenHaAddress -> openHaAddress
        SettingAction.LogOutHomeAssistant -> logOutHomeAssistant
        SettingAction.OpenHaLogin -> openHaLogin
        SettingAction.OpenHomeHelper -> openHomeHelper
        SettingAction.OpenHomeAssistant -> { { openHomeAssistant(null) } }
        is SettingAction.OpenAssistantTab -> { { openHomeAssistant(action.tab) } }
        SettingAction.OpenFrigate -> openFrigate
        SettingAction.OpenNotificationAccess -> openNotificationAccess
        SettingAction.OpenPing -> openPing
        SettingAction.MarkAlertsReviewed -> markAlertsReviewed
        SettingAction.OpenSystemSettings -> openSystemSettings
        SettingAction.ExportSettings -> exportSettings
        SettingAction.ImportSettings -> importSettings
        SettingAction.RestartApp -> restartApp
    }
}

/** The value a row shows; the dialog re-reads it whenever a setting changes */
internal fun SettingDef.read(prefs: LauncherPrefs): Any = when (kind) {
    SettingKind.Toggle -> boolean?.get(prefs) ?: false
    SettingKind.Stepper -> number?.get(prefs) ?: 0
    SettingKind.Choice -> text?.get(prefs) ?: ""
    else -> Unit
}

@Composable
internal fun HelperStatusRow(
    prefs: LauncherPrefs,
    revision: Int,
    onOpenAccessibility: () -> Unit,
    onTurnHelperOff: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val replaceHome = revision.let { prefs.catchHomeKey || prefs.reopenAfterHome }
    val isDefaultHome = remember(context) { context.isDefaultHomeApp() }
    val state = rememberHomeOverrideState()
    val ok = state == HomeOverrideState.Running

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(10.dp).background(
                    if (ok) HelperRunningColor else HelperIdleColor,
                    CircleShape,
                ),
            )
            Text(
                when {
                    state == HomeOverrideState.Running -> "Home helper: running"
                    replaceHome -> "Home helper: not running"
                    else -> "Home helper: off"
                },
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Text(
            when {
                // Already the TV's home app: Home opens this launcher whatever the helper does
                isDefaultHome && !replaceHome ->
                    "This app is the TV's home app, so Home opens it without the helper. Nothing to do here."
                isDefaultHome && replaceHome ->
                    "Home already opens this launcher. The helper only matters on a TV whose own home screen can't be replaced, so these options are optional."
                state == HomeOverrideState.Running ->
                    "Watching only the Home button and which app's window opens. It reads nothing else on screen."
                state == HomeOverrideState.EnabledNotRunning ->
                    "Android isn't running it. After the app is force-stopped Android drops the permission: " +
                        "open Accessibility settings, turn the helper off and on, and it runs again."
                else ->
                    "Off. Turn it on in Accessibility settings to have Home open this launcher on a TV that keeps its own home screen."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state != HomeOverrideState.Running && !isDefaultHome) {
                Button(onClick = onOpenAccessibility) { Text("Open Accessibility settings") }
            }
            if (replaceHome && isDefaultHome) {
                OutlinedButton(onClick = onTurnHelperOff) { Text("Turn the helper off") }
            }
        }
    }
}

@Composable
internal fun LogSize() {
    val context = LocalContext.current
    var size by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            size = withContext(Dispatchers.IO) { AppLog.sizeBytes() }
            delay(LOG_SIZE_REFRESH_MS)
        }
    }
    Text(
        "Log size: ${size?.let { Formatter.formatShortFileSize(context, it) } ?: "…"}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The version and build number, and when it was built */
@Composable
internal fun BuildInfo() {
    val context = LocalContext.current
    val built = remember { Date(BuildConfig.BUILD_TIME) }
    Column(horizontalAlignment = Alignment.End) {
        listOf(
            "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
            DateFormat.getMediumDateFormat(context).format(built),
            DateFormat.getTimeFormat(context).format(built),
        ).forEach {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val LOG_SIZE_REFRESH_MS = 5_000L

internal val HelperRunningColor = Color(0xFF7BD88F)
internal val HelperIdleColor = Color(0xFF8E8C92)
