package com.evofactory.homeboard

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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

private const val LOG_SIZE_REFRESH_MS = 5_000L

internal val HelperRunningColor = Color(0xFF7BD88F)
