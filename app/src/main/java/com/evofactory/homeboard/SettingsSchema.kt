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
    Assistant("Assistant"),
    Calendar("Calendar"),
    Cameras("Cameras"),
    HomeAssistant("Home Assistant"),
    Frigate("Frigate"),
    Advanced("Advanced"),
}

/**
 * What a row does; the settings page maps each of these to a lambda it was handed by the caller. A
 * door that opens another screen on a particular part of it carries that part, so the row lands where
 * it says instead of on whatever tab the screen happens to start at.
 */
sealed interface SettingAction {
    data object OpenCameras : SettingAction

    /** Which cameras the screensaver shows */
    data object OpenScreensaverCameras : SettingAction

    /** What the TV does when it is left alone: the screensaver's own status screen */
    data object OpenScreensaverStatus : SettingAction
    data object OpenFavourites : SettingAction
    data object OpenCalendars : SettingAction
    data object OpenBackgroundColour : SettingAction
    data object PickBackgroundImage : SettingAction
    data object OpenHaCameras : SettingAction
    data object OpenCalendarColours : SettingAction

    /** The Home Assistant dialog, opened on one of its tabs */
    data class OpenAssistantTab(val tab: HaDialogTab) : SettingAction

    /** The labels the launcher shows for the chosen entities */
    data object OpenEntityNames : SettingAction

    /** The address, typed in a dialog of its own */
    data object OpenHaAddress : SettingAction

    /** The one dialog: address and login, opened on the connection step */
    data object OpenHaLogin : SettingAction

    /** The home helper's explanation and its two buttons */
    data object OpenHomeHelper : SettingAction

    /** Ends the saved login */
    data object LogOutHomeAssistant : SettingAction

    data object OpenFrigate : SettingAction

    /** The notifications overlay, which says how to grant notification access when it is off */
    data object OpenNotificationAccess : SettingAction

    /** What notification access means once it is granted: the status screen, not the list again */
    data object OpenNotificationGrant : SettingAction

    /** Services with no entity of their own, chosen from the server's own list */
    data object OpenHaServices : SettingAction
    data object OpenPing : SettingAction
    data object OpenSystemSettings : SettingAction
    data object ExportSettings : SettingAction
    data object ImportSettings : SettingAction
    /** Sets the Alerts tab's dot and the top bar's count to nothing, without opening the tab */
    data object MarkAlertsReviewed : SettingAction

    data object RestartApp : SettingAction
}

sealed interface SettingKind {
    /** An on/off row */
    data object Toggle : SettingKind

    /** A number with − and + buttons, between [SettingDef.range]'s bounds */
    data object Stepper : SettingKind

    /** One of a few named values, chosen from a menu */
    data object Choice : SettingKind

    /** Opens something else (another dialog, a system screen) */
    data object Action : SettingKind

    /** An ordered list of ids, edited in its own dialog; the row opens that dialog */
    data object List : SettingKind
}

data class SettingDef(
    val key: String,
    val section: SettingSection,
    val title: String,
    val description: String = "",
    val kind: SettingKind,
    /** The card this row sits in on the settings page, e.g. "Apps" */
    val group: String = "",
    /** Null when the row always applies; otherwise whether it means anything right now */
    val showIf: ((LauncherPrefs) -> Boolean)? = null,
    /** Toggle */
    val boolean: KMutableProperty1<LauncherPrefs, Boolean>? = null,
    /** Stepper */
    val number: KMutableProperty1<LauncherPrefs, Int>? = null,
    val range: IntRange = 0..0,
    /** Choice: the stored value and the ones it can be, in menu order */
    val text: KMutableProperty1<LauncherPrefs, String>? = null,
    val choices: List<Pair<String, String>> = emptyList(),
    /** Action */
    val action: SettingAction? = null,
    /** The list a LIST row edits: also the values the export file carries */
    val strings: KMutableProperty1<LauncherPrefs, List<String>>? = null,
    /** Turning this on first shows the accessibility explanation */
    val requiresConsent: Boolean = false,
)

private fun toggle(
    key: String,
    section: SettingSection,
    group: String = "",
    title: String,
    description: String,
    property: KMutableProperty1<LauncherPrefs, Boolean>,
    requiresConsent: Boolean = false,
    showIf: ((LauncherPrefs) -> Boolean)? = null,
) = SettingDef(key, section, title, description, SettingKind.Toggle, group = group, showIf = showIf, boolean = property, requiresConsent = requiresConsent)

private fun stepper(
    key: String,
    section: SettingSection,
    group: String = "",
    title: String,
    description: String,
    property: KMutableProperty1<LauncherPrefs, Int>,
    range: IntRange,
    showIf: ((LauncherPrefs) -> Boolean)? = null,
) = SettingDef(key, section, title, description, SettingKind.Stepper, group = group, showIf = showIf, number = property, range = range)

private fun list(
    key: String,
    section: SettingSection,
    group: String = "",
    title: String,
    description: String,
    property: KMutableProperty1<LauncherPrefs, List<String>>,
    action: SettingAction,
    showIf: ((LauncherPrefs) -> Boolean)? = null,
) = SettingDef(key, section, title, description, SettingKind.List, group = group, showIf = showIf, action = action, strings = property)

private fun choice(
    key: String,
    section: SettingSection,
    group: String = "",
    title: String,
    description: String,
    property: KMutableProperty1<LauncherPrefs, String>,
    choices: List<Pair<String, String>>,
    showIf: ((LauncherPrefs) -> Boolean)? = null,
) = SettingDef(key, section, title, description, SettingKind.Choice, group = group, showIf = showIf, text = property, choices = choices)

private fun action(
    key: String,
    section: SettingSection,
    group: String = "",
    title: String,
    description: String,
    action: SettingAction,
    showIf: ((LauncherPrefs) -> Boolean)? = null,
) = SettingDef(key, section, title, description, SettingKind.Action, group = group, showIf = showIf, action = action)

/**
 * Every setting the menu shows, in the order each section lists them. A section is a category in the
 * settings page's rail, and its rows are grouped into cards by [SettingDef.group]. One category per
 * thing the launcher reads: how it behaves (Launcher, Home), what Home Assistant shows (Assistant),
 * the cameras, and the two servers it talks to (Home Assistant, Frigate) — a server's own rows live
 * with it, not inside one "Services" bucket. Advanced holds the rarely touched ones.
 */
val AllSettings: List<SettingDef> = listOf(
    // --- Launcher: how Home behaves, and the accessibility helper it needs for that ---
    choice(
        "background_kind",
        SettingSection.Launcher,
        "Background",
        "What is behind every screen",
        "The launcher's own dark background, one colour, or a picture from this TV",
        LauncherPrefs::backgroundKind,
        BACKGROUND_KINDS.map {
            it to when (it) {
                BACKGROUND_COLOUR -> "A colour"
                BACKGROUND_IMAGE -> "A picture"
                else -> "None"
            }
        },
    ),
    action(
        "background_colour",
        SettingSection.Launcher,
        "Background",
        "Colour",
        "The colour the background fills with",
        SettingAction.OpenBackgroundColour,
        showIf = { it.backgroundKind == BACKGROUND_COLOUR },
    ),
    action(
        "background_image",
        SettingSection.Launcher,
        "Background",
        "Picture",
        "A file from this TV's storage, copied into the app so moving the original does not break it",
        SettingAction.PickBackgroundImage,
        showIf = { it.backgroundKind == BACKGROUND_IMAGE },
    ),
    choice(
        "background_fit",
        SettingSection.Launcher,
        "Background",
        "How it fills",
        "Fill crops to the edges, fit leaves bars, stretch distorts, tile repeats, centre keeps its size",
        LauncherPrefs::backgroundFit,
        listOf(
            BACKGROUND_FILL to "Fill",
            BACKGROUND_FIT to "Fit",
            BACKGROUND_STRETCH to "Stretch",
            BACKGROUND_TILE to "Tile",
            BACKGROUND_CENTRE to "Centre",
        ),
        showIf = { it.backgroundKind == BACKGROUND_IMAGE },
    ),
    stepper(
        "background_scrim",
        SettingSection.Launcher,
        "Background",
        "Darkness over it",
        "Black over the background so text stays readable; 0 is none",
        LauncherPrefs::backgroundScrim,
        range = BACKGROUND_SCRIM,
        showIf = { it.backgroundKind != BACKGROUND_NONE },
    ),
    toggle(
        "catch_home_key",
        SettingSection.Launcher,
        "Home button",
        "Catch the Home button",
        "Pressing Home opens this launcher instead of the TV's own home screen",
        LauncherPrefs::catchHomeKey,
        requiresConsent = true,
    ),
    action(
        "home_helper",
        SettingSection.Launcher,
        "Home button",
        "Home helper",
        "What makes the Home button open this launcher on a TV that keeps its own home screen",
        SettingAction.OpenHomeHelper,
    ),
    choice(
        "screensaver_seconds",
        SettingSection.Launcher,
        "Screensaver",
        "Time on each camera",
        "Counted from each camera's first picture, then the next one",
        LauncherPrefs::screensaverSecondsChoice,
        SCREENSAVER_SECONDS.map { it.toString() to formatSeconds(it) },
    ),
    list(
        "screensaver_cameras",
        SettingSection.Launcher,
        "Screensaver",
        "Cameras it shows",
        "Which cameras take a turn; the ones left out never appear",
        LauncherPrefs::screensaverHiddenCameras,
        SettingAction.OpenScreensaverCameras,
    ),
    toggle(
        "screensaver_progress",
        SettingSection.Launcher,
        "Screensaver",
        "Connection progress",
        "While a camera connects. Off: only a problem shows, e.g. a camera that doesn't answer",
        LauncherPrefs::screensaverShowProgress,
    ),
    action(
        "screensaver_status",
        SettingSection.Launcher,
        "Screensaver",
        "The TV's choice",
        "Whether this TV starts HomeBoard when it is left alone, and what to do if it doesn't",
        SettingAction.OpenScreensaverStatus,
    ),
    toggle(
        "reopen_after_home",
        SettingSection.Launcher,
        "Home button",
        "Reopen after Home",
        "Coming back to the TV shows this launcher again",
        LauncherPrefs::reopenAfterHome,
        requiresConsent = true,
    ),
    // --- Home tab ---
    toggle(
        "shuffle_camera",
        SettingSection.Home,
        "Shuffle",
        "Shuffle the camera",
        "The Home tab shows a different camera each session; the channel keys or a number button keep the one you pick until the TV's next session",
        LauncherPrefs::shuffleCamera,
    ),
    toggle(
        "shuffle_actions",
        SettingSection.Home,
        "Shuffle",
        "Shuffle the top bar",
        "The action buttons change order each session, so their places are not muscle memory",
        LauncherPrefs::shuffleActions,
    ),
    toggle(
        "shuffle_sides",
        SettingSection.Home,
        "Shuffle",
        "Swap the camera and widgets",
        "Each session the camera takes the left of the Home tab and the widgets the right, or the reverse",
        LauncherPrefs::shuffleSides,
    ),
    toggle(
        "shuffle_cards",
        SettingSection.Home,
        "Shuffle",
        "Shuffle the Assistant cards",
        "The room cards on the Assistant tab change order each session",
        LauncherPrefs::shuffleCards,
    ),
    stepper(
        "shuffle_hours",
        SettingSection.Home,
        "Shuffle",
        "New order after",
        "Hours of the TV being on before the order is rolled again; 0 rolls it once per session",
        LauncherPrefs::shuffleHours,
        range = SHUFFLE_HOURS,
    ),
    toggle(
        "home_alerts_row",
        SettingSection.Home,
        "Rows",
        "Alert strip",
        "The strip of Frigate events above the apps on this tab; Cameras and Alerts always show it, and the Assistant tab has its own switch",
        LauncherPrefs::homeAlertsRow,
    ),
    toggle(
        "continue_watching",
        SettingSection.Home,
        "Rows",
        "Continue watching",
        "The row of what the TV's apps last played, under the apps",
        LauncherPrefs::continueWatching,
    ),
    toggle(
        "app_row_centre",
        SettingSection.Home,
        "Rows",
        "Centre the row",
        "When the apps don't reach the screen's edge, the row sits in the middle instead of at the left",
        LauncherPrefs::appRowCentre,
    ),
    toggle(
        "app_row_wrap",
        SettingSection.Home,
        "Apps",
        "Wrap the app row",
        "Left on the first app goes to the last, and right off the last comes back",
        LauncherPrefs::appRowWrap,
    ),
    toggle(
        "hide_phone_apps",
        SettingSection.Home,
        "Apps",
        "Hide phone apps",
        "Leaves out apps with no TV entry point, in the row and in All apps",
        LauncherPrefs::hidePhoneApps,
    ),
    toggle(
        "all_apps_tile",
        SettingSection.Home,
        "Apps",
        "All apps tile",
        "A tile at the end of the row that opens every app",
        LauncherPrefs::allAppsTile,
    ),
    list(
        "favourite_apps",
        SettingSection.Home,
        "Apps",
        "Favourite apps",
        "The apps the row shows, in order; with none chosen the row shows every app",
        LauncherPrefs::favouriteApps,
        SettingAction.OpenFavourites,
    ),
    toggle(
        "home_sensors",
        SettingSection.Home,
        "Cards",
        "Sensors card",
        "The readings beside the calendar; off gives its space to the calendar",
        LauncherPrefs::homeSensors,
    ),
    stepper(
        "all_apps_lines",
        SettingSection.Home,
        "All apps page",
        "Lines",
        "Rows when the page scrolls sideways, columns when it scrolls up and down; more lines, smaller tiles",
        LauncherPrefs::allAppsLines,
        range = ALL_APPS_LINES,
    ),
    choice(
        "all_apps_scroll",
        SettingSection.Home,
        "All apps page",
        "Scroll direction",
        "Sideways, like the row, or up and down like a list",
        LauncherPrefs::allAppsScroll,
        listOf(ALL_APPS_HORIZONTAL to "Sideways", ALL_APPS_VERTICAL to "Up and down"),
    ),
    list(
        "calendar_entities",
        SettingSection.Calendar,
        "Calendars",
        "Which calendars",
        "The Home Assistant calendars the Home tab shows, highest priority first",
        LauncherPrefs::calendarEntities,
        SettingAction.OpenCalendars,
    ),
    list(
        "calendar_colours",
        SettingSection.Calendar,
        "Calendars",
        "Event colours",
        "The bar beside each event; Home Assistant keeps no colour, so these are ours",
        LauncherPrefs::calendarColours,
        SettingAction.OpenCalendarColours,
    ),
    stepper(
        "calendar_days",
        SettingSection.Calendar,
        "Window",
        "Days ahead",
        "How far the calendar looks",
        LauncherPrefs::calendarDays,
        range = CALENDAR_DAYS,
    ),
    stepper(
        "calendar_max_events",
        SettingSection.Calendar,
        "Window",
        "Events at most",
        "How many it may list; the card usually shows only what fits",
        LauncherPrefs::calendarMaxEvents,
        range = CALENDAR_MAX_EVENTS,
    ),
    stepper(
        "calendar_refresh_minutes",
        SettingSection.Calendar,
        "Window",
        "Refresh every",
        "Minutes between asking Home Assistant for the events again",
        LauncherPrefs::calendarRefreshMinutes,
        range = CALENDAR_REFRESH,
    ),
    choice(
        "calendar_grouping",
        SettingSection.Calendar,
        "Display",
        "Sort by",
        "By time reads like a diary; by calendar puts the first calendar's events first",
        LauncherPrefs::calendarGrouping,
        listOf(CALENDAR_BY_TIME to "By time", CALENDAR_BY_PRIORITY to "By calendar"),
    ),
    choice(
        "calendar_group_style",
        SettingSection.Calendar,
        "Display",
        "Day headings",
        "Today and Tomorrow, or the dates themselves",
        LauncherPrefs::calendarGroupStyle,
        listOf(CALENDAR_TODAY_TOMORROW to "Today / Tomorrow", CALENDAR_DATE to "Dates"),
    ),
    toggle(
        "calendar_show_all_day",
        SettingSection.Calendar,
        "Display",
        "All-day events",
        "Birthdays and the like, shown without a time at the top of their day",
        LauncherPrefs::calendarShowAllDay,
    ),
    toggle(
        "calendar_hide_empty",
        SettingSection.Calendar,
        "Display",
        "Hide when empty",
        "Nothing scheduled hides the card instead of saying so",
        LauncherPrefs::calendarHideEmpty,
    ),
    // --- Assistant: what the Assistant tab and the Home tab's panel show of Home Assistant ---
    action(
        "assistant_display",
        SettingSection.Assistant,
        "Assistant tab",
        "What it shows",
        "Your chosen entities, or one of Home Assistant's own dashboards",
        SettingAction.OpenAssistantTab(HaDialogTab.Dashboard),
    ),
    toggle(
        "ha_section_columns",
        SettingSection.Assistant,
        "Assistant tab",
        "Rooms side by side",
        "Each room's card in a column of its own; off, they fill the width one under another",
        LauncherPrefs::haSectionColumns,
    ),
    list(
        "ha_services",
        SettingSection.Assistant,
        "Devices and actions",
        "Custom services",
        "Services with no entity of their own, e.g. input_number.reload; each one becomes a button on the Assistant tab",
        LauncherPrefs::haServices,
        SettingAction.OpenHaServices,
    ),
    toggle(
        "ha_bottom_row",
        SettingSection.Assistant,
        "Assistant tab",
        "Alerts and apps below",
        "The alert strip, the app row and Continue Watching under the cards",
        LauncherPrefs::haBottomRow,
    ),
    action(
        "home_display",
        SettingSection.Assistant,
        "Home tab",
        "What it shows",
        "The chosen entities, or one of Home Assistant's dashboards, next to the camera",
        SettingAction.OpenAssistantTab(HaDialogTab.Home),
    ),
    toggle(
        "ha_home_columns",
        SettingSection.Assistant,
        "Home tab",
        "Rooms side by side",
        "The Home tab's cards in columns of their own; off, they stack",
        LauncherPrefs::haHomeColumns,
    ),
    action(
        "ha_home_entities",
        SettingSection.Assistant,
        "Home tab",
        "Devices beside the camera",
        "Its own list: what sits beside the camera need not be what the Assistant tab shows. The dialog can copy that list over",
        SettingAction.OpenAssistantTab(HaDialogTab.HomeDevices),
    ),
    action(
        "ha_entities",
        SettingSection.Assistant,
        "Assistant tab",
        "Devices on it",
        "What the Assistant tab shows, room by room; OK picks them, Order sets where they sit",
        SettingAction.OpenAssistantTab(HaDialogTab.Sensors),
    ),
    action(
        "ha_top_bar",
        SettingSection.Assistant,
        "Top bar and order",
        "Also in the top bar",
        "The scenes, scripts and buttons that also show in the top bar, at most three",
        SettingAction.OpenAssistantTab(HaDialogTab.Actions),
    ),
    action(
        "ha_names",
        SettingSection.Assistant,
        "Top bar and order",
        "Names on screen",
        "The labels this launcher shows, instead of Home Assistant's older friendly names; ids stay as they are",
        SettingAction.OpenEntityNames,
    ),
    action(
        "ha_order",
        SettingSection.Assistant,
        "Top bar and order",
        "Order",
        "The order the rooms, sensors and actions appear in",
        SettingAction.OpenAssistantTab(HaDialogTab.Order),
    ),
    action(
        "cameras",
        SettingSection.Cameras,
        "Cameras",
        "Camera list",
        "The addresses, their order and shape, and how they play in the grid",
        SettingAction.OpenCameras,
    ),
    toggle(
        "cameras_alerts_row",
        SettingSection.Cameras,
        "Grid",
        "Alert strip on this tab",
        "The strip of Frigate events under the grid; off gives the grid that room",
        LauncherPrefs::camerasAlertsRow,
    ),
    choice(
        "camera_source",
        SettingSection.Cameras,
        "Source",
        "Where the pictures come from",
        "The cameras' own addresses, or Home Assistant when the TV must not reach the cameras",
        LauncherPrefs::cameraSource,
        listOf(CAMERAS_DIRECT to "Their own addresses", CAMERAS_HOME_ASSISTANT to "Home Assistant"),
    ),
    choice(
        "camera_side",
        SettingSection.Home,
        "Camera beside the cards",
        "Space beside the camera",
        "When the picture is narrower than its space: leave the gap, centre it, or widen the widgets",
        LauncherPrefs::cameraSide,
        listOf(
            CAMERA_SIDE_LEFT to "Leave the gap",
            CAMERA_SIDE_CENTRE to "Centre the picture",
            CAMERA_SIDE_HALF to "Widgets: half again as wide",
            CAMERA_SIDE_DOUBLE to "Widgets: twice as wide",
            CAMERA_SIDE_WIDER to "Widgets: the rest of the width",
        ),
    ),
    choice(
        "home_camera_start",
        SettingSection.Home,
        "Camera beside the cards",
        "Camera on Home",
        "The first in the list, or the one the remote last switched to",
        LauncherPrefs::homeCameraStart,
        listOf(HOME_CAMERA_FIRST to "First in the list", HOME_CAMERA_LAST to "Last switched to"),
        // While the shuffle owns the camera this row would change nothing
        showIf = { !it.shuffleCamera },
    ),
    list(
        "ha_camera_entities",
        SettingSection.Cameras,
        "Source",
        "Home Assistant cameras",
        "Which camera entities to show when the pictures come from Home Assistant",
        LauncherPrefs::haCameraEntities,
        SettingAction.OpenHaCameras,
    ),
    toggle(
        "channel_keys",
        SettingSection.Cameras,
        "Keys and sound",
        "Channel keys switch cameras",
        "Channel up and down go to the next or previous camera on the Home and Cameras tabs; elsewhere they are left to the TV",
        LauncherPrefs::channelKeys,
    ),
    choice(
        "camera_sound",
        SettingSection.Cameras,
        "Keys and sound",
        "Camera sound",
        "Muted on the tiles and heard in full screen, or never heard",
        LauncherPrefs::cameraSound,
        listOf(CAMERA_SOUND_FULLSCREEN to "In full screen", CAMERA_SOUND_MUTED to "Muted everywhere"),
    ),
    toggle(
        "camera_grid_live",
        SettingSection.Cameras,
        "Grid",
        "Live pictures in the grid",
        "Off shows each camera's last picture, for TVs with few video decoders",
        LauncherPrefs::cameraGridLive,
    ),
    toggle(
        "minimize_video_streams",
        SettingSection.Cameras,
        "Grid",
        "One stream at a time",
        "For older TVs: the cameras stop while a clip plays, and decoders open one at a time",
        LauncherPrefs::minimizeVideoStreams,
    ),

    // --- Home Assistant: the connection, and nothing about how its tab looks (see Assistant) ---
    action(
        "ha_address",
        SettingSection.HomeAssistant,
        "Connection",
        "Address",
        "Where Home Assistant is, e.g. http://192.168.1.20:8123",
        SettingAction.OpenHaAddress,
    ),
    action(
        "ha_login",
        SettingSection.HomeAssistant,
        "Connection",
        "Log in",
        "The username and password, or a token; the TV keeps a refresh token, never the password",
        SettingAction.OpenHaLogin,
    ),
    action(
        "ha_logout",
        SettingSection.HomeAssistant,
        "Connection",
        "Log out",
        "Ends the login this TV has, in Home Assistant's own profile",
        SettingAction.LogOutHomeAssistant,
    ),

    // --- Frigate: the server and the tab of its events ---
    action(
        "frigate",
        SettingSection.Frigate,
        "Connection",
        "Server",
        "Address and login, and the certificate it answers with",
        SettingAction.OpenFrigate,
    ),
    toggle(
        "alerts_tab",
        SettingSection.Frigate,
        "Alerts",
        "Alerts tab",
        "The tab of Frigate events; without a Frigate server it stays away anyway",
        LauncherPrefs::alertsTab,
    ),
    toggle(
        "alerts_dot",
        SettingSection.Frigate,
        "Alerts",
        "Dot on the Alerts tab",
        "A dot while there are Frigate alerts you have not looked at; opening the tab clears it. Stands down while the badge counts those alerts",
        LauncherPrefs::alertsDot,
        showIf = { it.alertsTab },
    ),
    action(
        "mark_alerts_reviewed",
        SettingSection.Frigate,
        "Alerts",
        "Mark the alerts as reviewed",
        "Clears the dot and the count without opening the tab",
        SettingAction.MarkAlertsReviewed,
        showIf = { it.alertsTab && (it.alertsDot || it.badge) },
    ),

    // --- Advanced ---
    toggle(
        "badge",
        SettingSection.Advanced,
        "Top bar",
        "New events badge",
        "A count beside the clock: the next row says what it counts, and OK on it opens that",
        LauncherPrefs::badge,
        // A Frigate count needs the Alerts tab; the TV's notifications do not
        showIf = { it.alertsTab || it.badgeSource == BADGE_SOURCE_NOTIFICATIONS },
    ),
    choice(
        "badge_source",
        SettingSection.Advanced,
        "Top bar",
        "The count is",
        "Frigate alerts open the Alerts tab, and the tab's dot stands down; the TV's notifications open the panel, and the dot stays Frigate's",
        LauncherPrefs::badgeSource,
        listOf(BADGE_SOURCE_FRIGATE to "Frigate alerts", BADGE_SOURCE_NOTIFICATIONS to "The TV's notifications"),
        showIf = { it.badge },
    ),
    toggle(
        "show_free_memory",
        SettingSection.Advanced,
        "Top bar",
        "Free memory",
        "Show the memory left on the TV beside the clock",
        LauncherPrefs::showFreeMemory,
    ),
    action(
        "notifications_access",
        SettingSection.Advanced,
        "Notifications",
        "Access",
        "Android only shares the TV's notifications with an app that is allowed to read them",
        SettingAction.OpenNotificationGrant,
    ),
    action(
        "notifications",
        SettingSection.Advanced,
        "Notifications",
        "Notifications panel",
        "What Android is showing, listed here; it needs notification access, and the panel says how",
        SettingAction.OpenNotificationAccess,
    ),
    action(
        "ping",
        SettingSection.Advanced,
        "Network",
        "Ping",
        "Is it the camera, or the network?",
        SettingAction.OpenPing,
    ),
    stepper(
        "log_hours_kept",
        SettingSection.Advanced,
        "Logs",
        "Hours of log to keep",
        "Old log lines are deleted as they fall out of this window",
        LauncherPrefs::logHoursKept,
        LOG_HOURS_MIN..LOG_HOURS_MAX,
    ),
    action(
        "export_settings",
        SettingSection.Advanced,
        "Import and export",
        "Export",
        "Writes this TV's setup to a file, to move it or keep it",
        SettingAction.ExportSettings,
    ),
    action(
        "import_settings",
        SettingSection.Advanced,
        "Import and export",
        "Import",
        "Adds what a settings file holds; the file is not deleted",
        SettingAction.ImportSettings,
    ),
    action(
        "restart_app",
        SettingSection.Advanced,
        "App",
        "Restart the app",
        "Starts the launcher over in a new process, which frees stuck video decoders",
        SettingAction.RestartApp,
    ),
    action(
        "system_settings",
        SettingSection.Advanced,
        "App",
        "TV settings",
        "The TV's own settings, where the default home app and the screensaver live",
        SettingAction.OpenSystemSettings,
    ),
)

/** The settings of one section, in order */
fun settingsIn(section: SettingSection): List<SettingDef> = AllSettings.filter { it.section == section }

/** Every setting's current value, by key: what the export file carries */
// Choices made through a door rather than a row: a door has no value to read, so these two travel
// under their own names and an import writes them back with the rest
private const val HA_DASHBOARD_KEY = "ha_dashboard_choice"
private const val HA_HOME_DASHBOARD_KEY = "ha_home_dashboard_choice"
private const val HA_HOME_ENTITIES_KEY = "ha_home_entities"
private const val HA_ENTITY_NAMES_KEY = "ha_entity_names"

fun currentPreferences(prefs: LauncherPrefs): Map<String, Any> = AllSettings.mapNotNull { setting ->
    setting.strings?.let { return@mapNotNull setting.key to it.get(prefs) as Any }
    when (setting.kind) {
        SettingKind.Toggle -> setting.boolean?.let { setting.key to it.get(prefs) as Any }
        SettingKind.Stepper -> setting.number?.let { setting.key to it.get(prefs) as Any }
        SettingKind.Choice -> setting.text?.let { setting.key to it.get(prefs) as Any }
        else -> null
    }
}.toMap() + listOfNotNull(
    prefs.haDashboard?.let { HA_DASHBOARD_KEY to it },
    prefs.haHomeDashboard?.let { HA_HOME_DASHBOARD_KEY to it },
).toMap() + mapOf(HA_HOME_ENTITIES_KEY to prefs.haHomeEntities)

/**
 * Writes the values a settings file carries. Keys the schema doesn't know are ignored, so an older
 * build reading a newer file still applies what it understands.
 */
fun applyPreferences(prefs: LauncherPrefs, values: Map<String, Any?>) {
    values.forEach { (key, value) ->
        if (key == HA_ENTITY_NAMES_KEY) {
            val list = (value as? List<*>)?.mapNotNull { it as? String } ?: return@forEach
            prefs.haEntityNames = list
            EntityNames.load(prefs)
            return@forEach
        }
        if (key == HA_HOME_ENTITIES_KEY) {
            val list = (value as? List<*>)?.mapNotNull { it as? String } ?: return@forEach
            prefs.saveHaChoice(prefs.haEntities, list, prefs.haTopBar)
            return@forEach
        }
        if (key == HA_DASHBOARD_KEY || key == HA_HOME_DASHBOARD_KEY) {
            if (value is String) {
                prefs.haDisplay = if (key == HA_DASHBOARD_KEY) {
                    prefs.haDisplay.copy(dashboard = value)
                } else {
                    prefs.haDisplay.copy(homeDashboard = value)
                }
            }
            return@forEach
        }
        val setting = AllSettings.firstOrNull { it.key == key } ?: return@forEach
        setting.strings?.let { strings ->
            // A list of ids arrives as a JSON array
            val list = (value as? List<*>)?.mapNotNull { it as? String } ?: return@forEach
            strings.set(prefs, list)
            return@forEach
        }
        when (setting.kind) {
            SettingKind.Toggle -> if (value is Boolean) setting.boolean?.set(prefs, value)
            SettingKind.Stepper -> if (value is Number) {
                setting.number?.set(prefs, value.toInt().coerceIn(setting.range.first, setting.range.last))
            }
            SettingKind.Choice -> if (value is String && setting.choices.any { it.first == value }) {
                setting.text?.set(prefs, value)
            }
            else -> Unit
        }
    }
}
