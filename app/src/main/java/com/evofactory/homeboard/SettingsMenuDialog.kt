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

/**
 * The launcher's settings: one dialog, five sections, every setting rendered from [AllSettings].
 * A new setting is an entry in the schema; this file needs no change for it.
 *
 * [actions] supplies what the ACTION rows do (the other dialogs, the system screens, export/import).
 * [onSettingsChanged] tells the launcher that a value changed, so it can pick it up.
 */
@Composable
fun SettingsMenuDialog(
    actions: SettingsActions,
    onSettingsChanged: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { LauncherPrefs(context) }
    // Only sections that hold something: an empty tab is a dead end on a remote
    val sections = remember { SettingSection.entries.filter { settingsIn(it).isNotEmpty() } }
    var section by remember { mutableStateOf(SettingSection.Launcher) }
    val items = remember { ListFocus() }
    // Bumped on every write: the rows read the prefs, and this is what makes them re-read
    var revision by remember { mutableIntStateOf(0) }
    // A helper option turned on for the first time: explained once, then remembered
    var pendingConsent by remember { mutableStateOf<(() -> Unit)?>(null) }
    val shown = remember(section) { settingsIn(section) }
    // Only real settings: the helper block is information, and on a TV where this app is the home app
    // it holds no button at all — a key with nothing focusable would leave the dialog on the X, where
    // OK closes it.
    val keys = remember(shown) { shown.map { it.key } }

    TopDialog(
        title = "Settings",
        onDismiss = onDismiss,
        tabs = sections.map { entry ->
            DialogTab(entry.label, selected = section == entry) { section = entry }
        },
        contentFocus = { items.current(keys) },
    ) {
        LaunchedEffect(Unit) { runCatching { items.current(keys).requestFocus() }.isSuccess }

        if (section == SettingSection.Launcher) {
            HelperStatusRow(
                prefs = prefs,
                revision = revision,
                onOpenAccessibility = actions.openAccessibilitySettings,
                onTurnHelperOff = {
                    prefs.catchHomeKey = false
                    prefs.reopenAfterHome = false
                    revision++
                    onSettingsChanged()
                },
                // Not in `keys`: it is text with, at most, one button the d-pad finds by itself
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            MenuDivider()
        }

        shown.forEachIndexed { index, setting ->
            val first = index == 0
            val last = index == shown.lastIndex
            SettingRow(
                setting = setting,
                prefs = prefs,
                // Reading `revision` is what re-composes a row after its value changed
                value = revision.let { setting.read(prefs) },
                onWrite = {
                    revision++
                    onSettingsChanged()
                },
                requestConsent = { option ->
                    if (prefs.homeOverrideAccepted) option() else pendingConsent = option
                },
                onAction = actions,
                modifier = Modifier.padding(horizontal = 12.dp)
                    .then(items.row(setting.key))
                    .ringToTop(left = first, right = last),
            )
        }

        pendingConsent?.let { option ->
            HomeOverrideConsentDialog(
                onAccept = {
                    prefs.homeOverrideAccepted = true
                    pendingConsent = null
                    option()
                },
                onDecline = { pendingConsent = null },
            )
        }

        if (section == SettingSection.Advanced) {
            MenuDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LogSize()
                Spacer(Modifier.weight(1f))
                BuildInfo()
            }
        }
    }
}

/** What the ACTION rows do. One hook per action; a new action adds one here and one enum value. */
class SettingsActions(
    val openCameras: () -> Unit,
    val openHomeAssistant: () -> Unit,
    val openFrigate: () -> Unit,
    val openPing: () -> Unit,
    val openAccessibilitySettings: () -> Unit,
    val openSystemSettings: () -> Unit,
    val exportSettings: () -> Unit,
    val importSettings: () -> Unit,
    val restartApp: () -> Unit,
) {
    fun of(action: SettingAction): () -> Unit = when (action) {
        SettingAction.OpenCameras -> openCameras
        SettingAction.OpenHomeAssistant -> openHomeAssistant
        SettingAction.OpenFrigate -> openFrigate
        SettingAction.OpenPing -> openPing
        SettingAction.OpenAccessibilitySettings -> openAccessibilitySettings
        SettingAction.OpenSystemSettings -> openSystemSettings
        SettingAction.ExportSettings -> exportSettings
        SettingAction.ImportSettings -> importSettings
        SettingAction.RestartApp -> restartApp
    }
}

/** The value a row shows; the dialog re-reads it whenever a setting changes */
private fun SettingDef.read(prefs: LauncherPrefs): Any = when (kind) {
    SettingKind.Toggle -> boolean?.get(prefs) ?: false
    SettingKind.Stepper -> number?.get(prefs) ?: 0
    else -> Unit
}

@Composable
private fun SettingRow(
    setting: SettingDef,
    prefs: LauncherPrefs,
    value: Any,
    onWrite: () -> Unit,
    requestConsent: (() -> Unit) -> Unit,
    onAction: SettingsActions,
    modifier: Modifier,
) {
    when (setting.kind) {
        SettingKind.Toggle -> HomeOption(
            title = setting.title,
            description = setting.description,
            checked = value as? Boolean ?: false,
            onClick = {
                val property = setting.boolean ?: return@HomeOption
                val turnOn = !property.get(prefs)
                val apply = {
                    property.set(prefs, turnOn)
                    onWrite()
                }
                if (turnOn && setting.requiresConsent) requestConsent(apply) else apply()
            },
            modifier = modifier,
        )

        SettingKind.Stepper -> StepperRow(
            title = setting.title,
            description = setting.description,
            value = value as? Int ?: 0,
            range = setting.range,
            onChange = { wanted ->
                val property = setting.number ?: return@StepperRow
                property.set(prefs, wanted.coerceIn(setting.range.first, setting.range.last))
                onWrite()
            },
            modifier = modifier,
        )

        // Actions read as buttons: they go somewhere or do something, they don't hold a value
        SettingKind.Action, SettingKind.Info -> ActionRow(
            title = setting.title,
            description = setting.description,
            onClick = setting.action?.let(onAction::of),
            modifier = modifier,
        )
    }
}

@Composable
private fun ActionRow(title: String, description: String, onClick: (() -> Unit)?, modifier: Modifier) {
    if (onClick != null) {
        ListItem(
            selected = false,
            onClick = onClick,
            headlineContent = { Text(title) },
            supportingContent = { if (description.isNotEmpty()) Text(description) },
            modifier = modifier,
        )
        return
    }
    ListItem(
        selected = false,
        onClick = {},
        headlineContent = { Text(title) },
        supportingContent = { if (description.isNotEmpty()) Text(description) },
        modifier = modifier,
    )
}

@Composable
private fun StepperRow(
    title: String,
    description: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { onChange(value - 1) }, enabled = value > range.first) { Text("−") }
            Text("$value", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { onChange(value + 1) }, enabled = value < range.last) { Text("+") }
        }
        if (description.isNotEmpty()) {
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The accessibility helper's real state (D8). Android drops an accessibility grant when the app is
 * force-stopped, and nothing an app does can turn it back on; so this distinguishes "off in settings"
 * from "on but Android isn't running it", and only nags when the helper is actually needed.
 */
@Composable
private fun HelperStatusRow(
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
private fun LogSize() {
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
private fun BuildInfo() {
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

private val HelperRunningColor = Color(0xFF7BD88F)
private val HelperIdleColor = Color(0xFF8E8C92)
