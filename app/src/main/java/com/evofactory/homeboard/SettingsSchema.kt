package com.evofactory.homeboard

import kotlin.reflect.KMutableProperty1

/**
 * What the launcher's settings menu shows, as data.
 *
 * Every setting is one entry in [AllSettings]: its section (a tab in the dialog), its title and
 * description, and how to read and write it. The dialog renders whatever is in this list, so a new
 * setting is one entry here and nothing else — no UI code, no per-setting composable.
 *
 * Reading and writing go through a property reference on [LauncherPrefs], which keeps the storage in
 * one place and the types checked by the compiler:
 *
 * ```
 * toggle("alerts_row", SettingSection.Home, "Frigate events on the Home tab", "…", LauncherPrefs::homeAlertsRow)
 * ```
 *
 * A setting that needs to do something rather than hold a value is an ACTION: the enum value names
 * the action, and the dialog looks up the lambda it was given for it. A row that only reports state
 * (the accessibility helper, the build) is INFO.
 */
enum class SettingSection(val label: String) {
    Launcher("Launcher"),
    Home("Home"),
    Cameras("Cameras"),
    Services("Services"),
    Advanced("Advanced"),
}

/** What a row does; the dialog maps each of these to a lambda it was handed by the caller */
enum class SettingAction {
    OpenCameras,
    OpenHomeAssistant,
    OpenFrigate,
    OpenPing,
    OpenAccessibilitySettings,
    OpenSystemSettings,
    ExportSettings,
    ImportSettings,
    RestartApp,
}

sealed interface SettingKind {
    /** An on/off row */
    data object Toggle : SettingKind

    /** A number with − and + buttons, between [SettingDef.range]'s bounds */
    data object Stepper : SettingKind

    /** Opens something else (another dialog, a system screen) */
    data object Action : SettingKind

    /** Read-only: state the user can act on but not change from here */
    data object Info : SettingKind
}

data class SettingDef(
    val key: String,
    val section: SettingSection,
    val title: String,
    val description: String = "",
    val kind: SettingKind,
    /** Toggle */
    val boolean: KMutableProperty1<LauncherPrefs, Boolean>? = null,
    /** Stepper */
    val number: KMutableProperty1<LauncherPrefs, Int>? = null,
    val range: IntRange = 0..0,
    /** Action */
    val action: SettingAction? = null,
    /** Turning this on first shows the accessibility explanation */
    val requiresConsent: Boolean = false,
)

private fun toggle(
    key: String,
    section: SettingSection,
    title: String,
    description: String,
    property: KMutableProperty1<LauncherPrefs, Boolean>,
    requiresConsent: Boolean = false,
) = SettingDef(key, section, title, description, SettingKind.Toggle, boolean = property, requiresConsent = requiresConsent)

private fun stepper(
    key: String,
    section: SettingSection,
    title: String,
    description: String,
    property: KMutableProperty1<LauncherPrefs, Int>,
    range: IntRange,
) = SettingDef(key, section, title, description, SettingKind.Stepper, number = property, range = range)

private fun action(
    key: String,
    section: SettingSection,
    title: String,
    description: String,
    action: SettingAction,
) = SettingDef(key, section, title, description, SettingKind.Action, action = action)

/**
 * Every setting the menu shows, in the order each section lists them. The first five sections are
 * tabs. Home, Cameras and Services are groups the rest of the launcher reads too; Advanced holds the
 * rarely touched ones.
 */
val AllSettings: List<SettingDef> = listOf(
    // --- Launcher: how Home behaves, and the accessibility helper it needs for that ---
    toggle(
        "catch_home_key",
        SettingSection.Launcher,
        "Catch the Home button",
        "Opens at once. May stop the volume from changing continuously while you hold a volume button.",
        LauncherPrefs::catchHomeKey,
        requiresConsent = true,
    ),
    toggle(
        "reopen_after_home",
        SettingSection.Launcher,
        "Reopen after the TV's home screen",
        "Also works when leaving an app or after a restart. The TV's home screen shows first, for several seconds on some TVs.",
        LauncherPrefs::reopenAfterHome,
        requiresConsent = true,
    ),
    // --- Home tab ---
    action(
        "cameras",
        SettingSection.Cameras,
        "Cameras",
        "Addresses, what plays at once, and the screensaver.",
        SettingAction.OpenCameras,
    ),
    toggle(
        "camera_grid_live",
        SettingSection.Cameras,
        "Live pictures in the grid",
        "Off shows each camera's last picture instead, for TVs with few video decoders.",
        LauncherPrefs::cameraGridLive,
    ),
    toggle(
        "minimize_video_streams",
        SettingSection.Cameras,
        "Fewer streams at once",
        "For TVs that struggle with several live cameras together.",
        LauncherPrefs::minimizeVideoStreams,
    ),

    // --- Services the launcher talks to ---
    action(
        "home_assistant",
        SettingSection.Services,
        "Home Assistant",
        "Address, login, what to show, and the buttons in the top bar.",
        SettingAction.OpenHomeAssistant,
    ),
    action(
        "frigate",
        SettingSection.Services,
        "Frigate",
        "Address and login, for the alerts tab.",
        SettingAction.OpenFrigate,
    ),
    action(
        "ping",
        SettingSection.Services,
        "Ping",
        "Is it the camera, or the network?",
        SettingAction.OpenPing,
    ),

    // --- Advanced ---
    stepper(
        "log_hours_kept",
        SettingSection.Advanced,
        "Hours of log to keep",
        "Logs are on this TV only, with logins and tokens stripped out.",
        LauncherPrefs::logHoursKept,
        LOG_HOURS_MIN..LOG_HOURS_MAX,
    ),
    toggle(
        "show_free_memory",
        SettingSection.Advanced,
        "Show free memory",
        "In the top bar, next to the clock.",
        LauncherPrefs::showFreeMemory,
    ),
    action(
        "export_settings",
        SettingSection.Advanced,
        "Export settings",
        "Writes this TV's setup to a file, to move it to another TV or keep it.",
        SettingAction.ExportSettings,
    ),
    action(
        "import_settings",
        SettingSection.Advanced,
        "Import settings",
        "Adds what a settings file holds. Nothing already here is removed.",
        SettingAction.ImportSettings,
    ),
    action(
        "restart_app",
        SettingSection.Advanced,
        "Restart app",
        "Starts the launcher over in a new process, which frees whatever it holds, e.g. stuck video decoders.",
        SettingAction.RestartApp,
    ),
    action(
        "system_settings",
        SettingSection.Advanced,
        "TV settings",
        "The TV's own settings, where the default home app and the screensaver live.",
        SettingAction.OpenSystemSettings,
    ),
)

/** The settings of one section, in order */
fun settingsIn(section: SettingSection): List<SettingDef> = AllSettings.filter { it.section == section }

/** Every setting's current value, by key: what the export file carries */
fun currentPreferences(prefs: LauncherPrefs): Map<String, Any> = AllSettings.mapNotNull { setting ->
    when (setting.kind) {
        SettingKind.Toggle -> setting.boolean?.let { setting.key to it.get(prefs) as Any }
        SettingKind.Stepper -> setting.number?.let { setting.key to it.get(prefs) as Any }
        else -> null
    }
}.toMap()

/**
 * Writes the values a settings file carries. Keys the schema doesn't know are ignored, so an older
 * build reading a newer file still applies what it understands.
 */
fun applyPreferences(prefs: LauncherPrefs, values: Map<String, Any?>) {
    values.forEach { (key, value) ->
        val setting = AllSettings.firstOrNull { it.key == key } ?: return@forEach
        when (setting.kind) {
            SettingKind.Toggle -> if (value is Boolean) setting.boolean?.set(prefs, value)
            SettingKind.Stepper -> if (value is Number) {
                setting.number?.set(prefs, value.toInt().coerceIn(setting.range.first, setting.range.last))
            }
            else -> Unit
        }
    }
}
