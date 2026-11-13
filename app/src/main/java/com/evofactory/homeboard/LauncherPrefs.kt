package com.evofactory.homeboard

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Remembers the order chosen with "Move" and which apps are hidden, across restarts.
 * Stored in shared_prefs/launcher.xml in the app's private data on the TV.
 */
class LauncherPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    /** Package names of visible apps, in row order */
    var order: List<String>
        get() = readList("order")
        set(value) = writeList("order", value)

    /** Package names of hidden apps, in the order they were hidden */
    var hidden: List<String>
        get() = readList("hidden_list").ifEmpty {
            // Earlier versions stored an unordered set
            prefs.getStringSet("hidden", null)?.sorted() ?: emptyList()
        }
        set(value) = prefs.edit {
            putString("hidden_list", value.joinToString("\n"))
            remove("hidden")
        }

    /** Shows hidden from Continue watching, see [WatchItem.hideKey] */
    var hiddenWatching: List<String>
        get() = readList("hidden_watching")
        set(value) = writeList("hidden_watching", value)

    /** Last address entered in the Ping utility */
    var pingHost: String
        get() = prefs.getString("ping_host", null) ?: ""
        set(value) = prefs.edit { putString("ping_host", value) }

    /** The [NetworkKind] last connected, for which icon to cross out without a network */
    var lastNetworkKind: String
        get() = prefs.getString("last_network_kind", null) ?: ""
        set(value) = prefs.edit { putString("last_network_kind", value) }

    /** Cameras, in the order chosen; their addresses may include the camera's login */
    var cameras: List<Camera>
        get() = prefs.getString("cameras", null)?.let(::camerasFromJson)
            // Earlier versions had a single camera
            ?: prefs.getString("camera_url", null)?.takeIf { it.isNotEmpty() }?.let { url ->
                listOf(Camera(id = "camera1", name = "Camera", url = url, useTcp = prefs.getBoolean("camera_tcp", true)))
            }
            ?: emptyList()
        set(value) = prefs.edit {
            putString("cameras", camerasToJson(value))
            remove("camera_url")
            remove("camera_tcp")
        }

    /** Id of the camera shown on the home screen */
    var selectedCamera: String
        get() = prefs.getString("camera_selected", null) ?: ""
        set(value) = prefs.edit { putString("camera_selected", value) }

    /** Home Assistant address, e.g. http://192.168.1.20:8123; empty = not set up */
    var haUrl: String
        get() = prefs.getString("ha_url", null) ?: ""
        set(value) = prefs.edit { putString("ha_url", value) }

    /** Home Assistant long-lived access token, when logging in with one */
    var haToken: String
        get() = prefs.getString("ha_token", null) ?: ""
        set(value) = prefs.edit { putString("ha_token", value) }

    /** Home Assistant refresh token, when logging in with a username and password (never stored) */
    var haRefreshToken: String
        get() = prefs.getString("ha_refresh_token", null) ?: ""
        set(value) = prefs.edit { putString("ha_refresh_token", value) }

    /** Username of that login, to show it again in the settings */
    var haUsername: String
        get() = prefs.getString("ha_username", null) ?: ""
        set(value) = prefs.edit { putString("ha_username", value) }

    /**
     * Everything from Home Assistant on the Assistant tab, in its order there: weather, sensors,
     * scenes, scripts and buttons. The sensors and weather also show next to the camera.
     */
    val haEntities: List<String>
        get() = if (prefs.contains("ha_top_bar")) readList("ha_entities") else readList("ha_entities").filterNot(::isHaAction) + oldHaActions()

    /** The names the launcher shows for entities, "entity_id=Label", by id */
    var haEntityNames: List<String>
        get() = readList("ha_entity_names")
        set(value) = writeList("ha_entity_names", value)

    /**
     * What the Home tab's panel shows beside the camera: its own list, so the tab can hold the living
     * room while the Assistant tab holds the house. Absent from older setups, it falls back to what
     * that tab used to show: everything chosen that is not a scene or script.
     */
    val haHomeEntities: List<String>
        get() = if (prefs.contains("ha_home_entities")) readList("ha_home_entities") else haEntities.filterNot(::isHaAction)

    /** The scenes, scripts and buttons also in the top bar, at most [MAX_HA_ACTIONS] */
    val haTopBar: List<String>
        get() = if (prefs.contains("ha_top_bar")) readList("ha_top_bar") else oldHaActions().take(MAX_HA_ACTIONS)

    /** What the Assistant tab and the Home tab's panel show of Home Assistant */
    var haDisplay: HaDisplay
        get() = HaDisplay(
            dashboard = prefs.getString("ha_dashboard", null),
            bottomRow = prefs.getBoolean("ha_bottom_row", false),
            sectionColumns = prefs.getBoolean("ha_section_columns", false),
            homeDashboard = prefs.getString("ha_home_dashboard", null),
            homeColumns = prefs.getBoolean("ha_home_columns", true),
        )
        set(value) = prefs.edit {
            if (value.dashboard == null) remove("ha_dashboard") else putString("ha_dashboard", value.dashboard)
            putBoolean("ha_bottom_row", value.bottomRow)
            putBoolean("ha_section_columns", value.sectionColumns)
            if (value.homeDashboard == null) remove("ha_home_dashboard") else putString("ha_home_dashboard", value.homeDashboard)
            putBoolean("ha_home_columns", value.homeColumns)
            // The bottom row's first version, which only hid the alerts row
            remove("ha_alerts_row")
        }

    // Together: before the top bar was a choice of its own, the actions were in another list
    fun saveHaChoice(entities: List<String>, homeEntities: List<String>, topBar: List<String>) {
        writeList("ha_entities", entities)
        writeList("ha_home_entities", homeEntities.filter { it in entities })
        writeList("ha_top_bar", topBar)
        prefs.edit { remove("ha_actions") }
    }

    // What [haDisplay] holds, one key each, so the settings page can describe a choice without going
    // through the dialog. Null is "the chosen entities"; "" is Home Assistant's own default dashboard,
    // which is a dashboard like any other and keeps its own key.
    val haDashboard: String? get() = haDisplay.dashboard
    val haHomeDashboard: String? get() = haDisplay.homeDashboard

    // The chosen dashboard's own title, so a row can print "Config" instead of "lovelace-config".
    // Cosmetic and local: written when the dialog saves, never part of an export.
    var haDashboardLabel: String
        get() = prefs.getString("ha_dashboard_label", "").orEmpty()
        set(value) = prefs.edit { putString("ha_dashboard_label", value) }

    var haHomeDashboardLabel: String
        get() = prefs.getString("ha_home_dashboard_label", "").orEmpty()
        set(value) = prefs.edit { putString("ha_home_dashboard_label", value) }

    var haBottomRow: Boolean
        get() = haDisplay.bottomRow
        set(value) { haDisplay = haDisplay.copy(bottomRow = value) }

    var haSectionColumns: Boolean
        get() = haDisplay.sectionColumns
        set(value) { haDisplay = haDisplay.copy(sectionColumns = value) }

    var haHomeColumns: Boolean
        get() = haDisplay.homeColumns
        set(value) { haDisplay = haDisplay.copy(homeColumns = value) }

    // Earlier versions kept the top bar's actions in "ha_actions", and before that with the sensors
    private fun oldHaActions() = if (prefs.contains("ha_actions")) readList("ha_actions") else readList("ha_entities").filter(::isHaAction)

    /** Frigate's address and login; the password is kept, see [FrigateServer] */
    var frigate: FrigateServer
        get() = FrigateServer(
            prefs.getString("frigate_url", null) ?: "",
            prefs.getString("frigate_username", null) ?: "",
            prefs.getString("frigate_password", null) ?: "",
            prefs.getString("frigate_cert_pin", null) ?: "",
        )
        set(value) = prefs.edit {
            putString("frigate_url", value.url)
            putString("frigate_username", value.username)
            putString("frigate_password", value.password)
            putString("frigate_cert_pin", value.certPin)
        }

    // Earlier versions had one switch, which did what both of these do. Both off by default.
    private val replaceHomeBefore get() = prefs.getBoolean("replace_home", false)

    /** Catch the Home button so the TV's home screen never opens. May stop a held volume button from repeating. */
    var catchHomeKey: Boolean
        get() = prefs.getBoolean("catch_home_key", replaceHomeBefore)
        set(value) = prefs.edit { putBoolean("catch_home_key", value) }

    /** Reopen this launcher whenever the TV's own home screen opens */
    var reopenAfterHome: Boolean
        get() = prefs.getBoolean("reopen_after_home", replaceHomeBefore)
        set(value) = prefs.edit { putBoolean("reopen_after_home", value) }

    /** Either way of taking over from the TV's home screen is on (see HomeOverrideService) */
    val replaceHome: Boolean get() = catchHomeKey || reopenAfterHome

    fun stopReplacingHome() {
        catchHomeKey = false
        reopenAfterHome = false
    }

    /** The Cameras tab plays substreams live; otherwise it shows still pictures */
    var cameraGridLive: Boolean
        get() = prefs.getBoolean("camera_grid_live", true)
        set(value) = prefs.edit { putBoolean("camera_grid_live", value) }

    /**
     * For older TVs: the cameras stop while a clip plays, and the clip waits until their players have
     * released. Decoders open one at a time.
     * Otherwise videos play side by side, and open together.
     */
    var minimizeVideoStreams: Boolean
        get() = prefs.getBoolean("minimize_video_streams", false)
        set(value) = prefs.edit { putBoolean("minimize_video_streams", value) }

    /** How long the screensaver shows each camera, one of [SCREENSAVER_SECONDS] */
    var screensaverSeconds: Int
        get() = prefs.getInt("screensaver_seconds", SCREENSAVER_SECONDS_DEFAULT).takeIf { it in SCREENSAVER_SECONDS } ?: SCREENSAVER_SECONDS_DEFAULT
        set(value) = prefs.edit { putInt("screensaver_seconds", value) }

    /** Ids of the cameras the screensaver leaves out; new cameras show */
    var screensaverHiddenCameras: List<String>
        get() = readList("screensaver_hidden_cameras")
        set(value) = writeList("screensaver_hidden_cameras", value)

    /** The screensaver shows a camera's status while it connects; off, only once something fails */
    var screensaverShowProgress: Boolean
        get() = prefs.getBoolean("screensaver_show_progress", false)
        set(value) = prefs.edit { putBoolean("screensaver_show_progress", value) }

    /** How many hours of the app's log to keep, see [AppLog] */
    var logHoursKept: Int
        get() = prefs.getInt("log_hours_kept", LOG_HOURS_DEFAULT).coerceIn(LOG_HOURS_MIN, LOG_HOURS_MAX)
        set(value) = prefs.edit { putInt("log_hours_kept", value) }

    /**
     * The Home tab's bottom row shows Frigate's events above the apps. Off, the events stay on the
     * Cameras tab and the Alerts tab, and the Home tab is the apps.
     */
    var homeAlertsRow: Boolean
        get() = prefs.getBoolean("home_alerts_row", false)
        set(value) = prefs.edit { putBoolean("home_alerts_row", value) }

    /** The row under the apps: what the TV's own apps last played */
    var continueWatching: Boolean
        get() = prefs.getBoolean("continue_watching", true)
        set(value) = prefs.edit { putBoolean("continue_watching", value) }

    /** Left on the first app goes to the last, and right off the last goes to the first */
    var appRowWrap: Boolean
        get() = prefs.getBoolean("app_row_wrap", true)
        set(value) = prefs.edit { putBoolean("app_row_wrap", value) }

    /**
     * Where camera pictures come from: [CAMERAS_DIRECT] reads the cameras' own addresses, [CAMERAS_HOME_ASSISTANT]
     * has Home Assistant do it, so the TV needs no access to the cameras or to go2rtc.
     */
    var cameraSource: String
        get() = prefs.getString("camera_source", CAMERAS_DIRECT).takeIf { it in CAMERA_SOURCES } ?: CAMERAS_DIRECT
        set(value) = prefs.edit { putString("camera_source", value) }

    /**
     * Which camera the Home tab shows when the launcher starts: [HOME_CAMERA_FIRST] (the first in the
     * list, so the order in the camera dialog decides) or [HOME_CAMERA_LAST] (whichever the remote last
     * switched to). Channel up and down always switch at once, whichever this is.
     */
    var homeCameraStart: String
        get() = prefs.getString("home_camera_start", HOME_CAMERA_LAST).takeIf { it in HOME_CAMERA_STARTS } ?: HOME_CAMERA_LAST
        set(value) = prefs.edit { putString("home_camera_start", value) }

    /** Home Assistant cameras, as "entity|name", in display order */
    var haCameraEntities: List<String>
        get() = readList("ha_camera_entities")
        set(value) = writeList("ha_camera_entities", value)

    /**
     * What fills the Home tab beside the camera when the picture is narrower than its slot:
     * [CAMERA_SIDE_LEFT] leaves the gap, [CAMERA_SIDE_CENTRE] centres the picture,
     * [CAMERA_SIDE_WIDER] gives the gap to the widgets.
     */
    var cameraSide: String
        get() = prefs.getString("camera_side", CAMERA_SIDE_LEFT).takeIf { it in CAMERA_SIDES } ?: CAMERA_SIDE_LEFT
        set(value) = prefs.edit { putString("camera_side", value) }

    /**
     * What is behind everything: [BACKGROUND_NONE] keeps the launcher's own dark background,
     * [BACKGROUND_COLOUR] fills it with [backgroundColour], [BACKGROUND_IMAGE] draws [backgroundImage].
     */
    var backgroundKind: String
        get() = prefs.getString("background_kind", BACKGROUND_NONE).takeIf { it in BACKGROUND_KINDS } ?: BACKGROUND_NONE
        set(value) = prefs.edit { putString("background_kind", value) }

    /** The colour a [BACKGROUND_COLOUR] background fills with, as ARGB */
    var backgroundColour: Int
        get() = prefs.getInt("background_colour", 0xFF000000.toInt())
        set(value) = prefs.edit { putInt("background_colour", value) }

    /** Where the chosen image was copied to, inside the app's own folder */
    var backgroundImage: String
        get() = prefs.getString("background_image", "").orEmpty()
        set(value) = prefs.edit { putString("background_image", value) }

    /** How the image fills the screen: [BACKGROUND_FITS] */
    var backgroundFit: String
        get() = prefs.getString("background_fit", BACKGROUND_FILL).takeIf { it in BACKGROUND_FITS } ?: BACKGROUND_FILL
        set(value) = prefs.edit { putString("background_fit", value) }

    /** How dark the layer over the background is, in percent: what keeps text readable over any picture */
    var backgroundScrim: Int
        get() = prefs.getInt("background_scrim", BACKGROUND_SCRIM_DEFAULT).coerceIn(BACKGROUND_SCRIM)
        set(value) = prefs.edit { putInt("background_scrim", value) }

    /**
     * A new order once per session: the camera on the Home tab, the top bar's actions and the Home
     * Assistant cards on it. Rolled when the launcher starts after the TV has been off a while, and
     * again after [shuffleHours] of it being left on; never while the remote is in use.
     */
    var shuffleCamera: Boolean
        get() = prefs.getBoolean("shuffle_camera", false)
        set(value) = prefs.edit { putBoolean("shuffle_camera", value) }

    var shuffleActions: Boolean
        get() = prefs.getBoolean("shuffle_actions", false)
        set(value) = prefs.edit { putBoolean("shuffle_actions", value) }

    /** Each session, the camera and the widgets swap sides */
    var shuffleSides: Boolean
        get() = prefs.getBoolean("shuffle_sides", false)
        set(value) = prefs.edit { putBoolean("shuffle_sides", value) }

    var shuffleCards: Boolean
        get() = prefs.getBoolean("shuffle_cards", false)
        set(value) = prefs.edit { putBoolean("shuffle_cards", value) }

    /** Hours of the TV being on before the order is rolled again; 0 means only on a new session */
    var shuffleHours: Int
        get() = prefs.getInt("shuffle_hours", 0).coerceIn(SHUFFLE_HOURS)
        set(value) = prefs.edit { putInt("shuffle_hours", value) }

    /** The current order's seed, and when it was last rolled: what makes it per session, not per frame */
    var shuffleSeed: Int
        get() = prefs.getInt("shuffle_seed", 0)
        set(value) = prefs.edit { putInt("shuffle_seed", value) }

    var shuffleRolledAt: Long
        get() = prefs.getLong("shuffle_rolled_at", 0L)
        set(value) = prefs.edit { putLong("shuffle_rolled_at", value) }

    /**
     * Icons chosen for apps, as "package=file" entries: a picture copied into the app's own folder,
     * drawn instead of the app's banner or icon.
     */
    var appIcons: List<String>
        get() = readList("app_icons")
        set(value) = writeList("app_icons", value)

    /** A count of new Frigate events beside the clock, cleared when the Alerts tab is opened */
    var badge: Boolean
        get() = prefs.getBoolean("badge", false)
        set(value) = prefs.edit { putBoolean("badge", value) }

    /** A dot on the Alerts tab while there are alerts nobody has looked at */
    var alertsDot: Boolean
        get() = prefs.getBoolean("alerts_dot", true)
        set(value) = prefs.edit { putBoolean("alerts_dot", value) }

    /** Events that started before this are already seen; they stay out of the count */
    var badgeSeenAt: Double
        get() = Double.fromBits(prefs.getLong("badge_seen_at", 0L))
        set(value) = prefs.edit { putLong("badge_seen_at", value.toBits()) }

    var alertsTab: Boolean
        get() = prefs.getBoolean("alerts_tab", true)
        set(value) = prefs.edit { putBoolean("alerts_tab", value) }

    /** The remote's channel up and down keys switch to the next or previous camera */
    var channelKeys: Boolean
        get() = prefs.getBoolean("channel_keys", true)
        set(value) = prefs.edit { putBoolean("channel_keys", value) }

    /**
     * Camera sound: [CAMERA_SOUND_FULLSCREEN] keeps the tiles silent and plays a camera's sound in full
     * screen, [CAMERA_SOUND_MUTED] never plays it. A camera's own "Use audio track" still decides
     * whether its player gets the track at all.
     */
    var cameraSound: String
        get() = prefs.getString("camera_sound", CAMERA_SOUND_FULLSCREEN).takeIf { it in CAMERA_SOUNDS } ?: CAMERA_SOUND_FULLSCREEN
        set(value) = prefs.edit { putString("camera_sound", value) }

    /**
     * Home Assistant calendar entities on the Home tab, in display order. Empty means the widget is
     * not shown; a calendar added in Home Assistant appears in the picker with no app change.
     */
    var calendarEntities: List<String>
        get() = readList("calendar_entities")
        set(value) = writeList("calendar_entities", value)

    /**
     * Colours chosen per calendar, as "entityId=#RRGGBB". A calendar without an entry takes the
     * palette colour for its position, which is the order Home Assistant's own card uses.
     */
    var calendarColours: List<String>
        get() = readList("calendar_colours")
        set(value) = writeList("calendar_colours", value)

    /** The Home tab's sensors card; off leaves its space to the calendar */
    var homeSensors: Boolean
        get() = prefs.getBoolean("home_sensors", true)
        set(value) = prefs.edit { putBoolean("home_sensors", value) }

    /** How many days of events the widget covers */
    var calendarDays: Int
        get() = prefs.getInt("calendar_days", CALENDAR_DAYS_DEFAULT).coerceIn(CALENDAR_DAYS)
        set(value) = prefs.edit { putInt("calendar_days", value) }

    /** The most events the widget lists, however many fit the window */
    var calendarMaxEvents: Int
        get() = prefs.getInt("calendar_max_events", CALENDAR_MAX_EVENTS_DEFAULT).coerceIn(CALENDAR_MAX_EVENTS)
        set(value) = prefs.edit { putInt("calendar_max_events", value) }

    /** How often the widget asks Home Assistant for the events again */
    var calendarRefreshMinutes: Int
        get() = prefs.getInt("calendar_refresh_minutes", CALENDAR_REFRESH_DEFAULT).coerceIn(CALENDAR_REFRESH)
        set(value) = prefs.edit { putInt("calendar_refresh_minutes", value) }

    /** How the day headings read: [CALENDAR_TODAY_TOMORROW] or [CALENDAR_DATE] */
    var calendarGroupStyle: String
        get() = prefs.getString("calendar_group_style", CALENDAR_TODAY_TOMORROW).takeIf { it in CALENDAR_GROUP_STYLES } ?: CALENDAR_TODAY_TOMORROW
        set(value) = prefs.edit { putString("calendar_group_style", value) }

    /** All-day events listed alongside the timed ones */
    var calendarShowAllDay: Boolean
        get() = prefs.getBoolean("calendar_show_all_day", true)
        set(value) = prefs.edit { putBoolean("calendar_show_all_day", value) }

    /** How the card orders events: [CALENDAR_BY_TIME] or [CALENDAR_BY_PRIORITY] */
    var calendarGrouping: String
        get() = prefs.getString("calendar_grouping", CALENDAR_BY_TIME).takeIf { it in CALENDAR_GROUPINGS } ?: CALENDAR_BY_TIME
        set(value) = prefs.edit { putString("calendar_grouping", value) }

    /** Nothing scheduled: hide the card rather than say so */
    var calendarHideEmpty: Boolean
        get() = prefs.getBoolean("calendar_hide_empty", true)
        set(value) = prefs.edit { putBoolean("calendar_hide_empty", value) }

    /** The calendar card's settings as one value, read where they are all known at once */
    fun calendarConfig() = CalendarConfig(
        entities = calendarEntities,
        days = calendarDays,
        maxEvents = calendarMaxEvents,
        groupStyle = calendarGroupStyle,
        showAllDay = calendarShowAllDay,
        hideEmpty = calendarHideEmpty,
        colours = calendarColours,
        grouping = calendarGrouping,
    )

    /** Leaves out the apps that only have a phone launcher, no TV entry point */
    var hidePhoneApps: Boolean
        get() = prefs.getBoolean("hide_phone_apps", false)
        set(value) = prefs.edit { putBoolean("hide_phone_apps", value) }

    /** The tile at the end of the apps that opens everything, beside the favourites */
    var allAppsTile: Boolean
        get() = prefs.getBoolean("all_apps_tile", true)
        set(value) = prefs.edit { putBoolean("all_apps_tile", value) }

    /**
     * How many lines the "All apps" page shows at once: rows when it scrolls sideways, columns when
     * it scrolls up and down. One is a single row, four is the smallest tiles.
     */
    var allAppsLines: Int
        get() {
            // A page saved as one row before lines existed keeps that look
            if (!prefs.contains("all_apps_lines") && prefs.getString("all_apps_page", null) == "row") return 1
            return prefs.getInt("all_apps_lines", ALL_APPS_LINES_DEFAULT).coerceIn(ALL_APPS_LINES)
        }
        set(value) = prefs.edit { putInt("all_apps_lines", value) }

    /** Which way the "All apps" page scrolls: [ALL_APPS_HORIZONTAL] or [ALL_APPS_VERTICAL] */
    var allAppsScroll: String
        get() = prefs.getString("all_apps_scroll", ALL_APPS_HORIZONTAL).takeIf { it in ALL_APPS_SCROLLS } ?: ALL_APPS_HORIZONTAL
        set(value) = prefs.edit { putString("all_apps_scroll", value) }

    /**
     * Package keys of the apps the row shows, in their order. Empty means every visible app, as
     * before favourites existed, so turning the feature on changes nothing until something is pinned.
     */
    var favouriteApps: List<String>
        get() = readList("favourite_apps")
        set(value) = writeList("favourite_apps", value)

    /** Show the memory left on the TV in the top bar */
    var showFreeMemory: Boolean
        get() = prefs.getBoolean("show_free_memory", false)
        set(value) = prefs.edit { putBoolean("show_free_memory", value) }

    /** The user accepted what the home override does, asked the first time they turn on either option */
    var homeOverrideAccepted: Boolean
        get() = prefs.getBoolean("home_override_accepted", false)
        set(value) = prefs.edit { putBoolean("home_override_accepted", value) }

    /** Set when the crash guard turned replaceHome off, so HomeBoard settings can say why */
    var replaceHomeAutoOff: Boolean
        get() = prefs.getBoolean("replace_home_auto_off", false)
        set(value) = prefs.edit { putBoolean("replace_home_auto_off", value) }

    /**
     * Crash guard: launches by the home override since the launcher last started properly, counted
     * from [overrideLaunchesSince]. Written to disk at once, since a crash follows a failed launch.
     */
    var overrideLaunches: Int
        get() = prefs.getInt("override_launches", 0)
        set(value) = prefs.edit(commit = true) { putInt("override_launches", value) }

    var overrideLaunchesSince: Long
        get() = prefs.getLong("override_launches_since", 0)
        set(value) = prefs.edit(commit = true) { putLong("override_launches_since", value) }

    /** The listener is only weakly held: keep a reference to it */
    fun addChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun removeChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    /** The apps found last time */
    var installedApps: List<InstalledApp>
        get() = runCatching { installedAppsFromJson(prefs.getString("installed_apps", null).orEmpty()) }.getOrDefault(emptyList())
        set(value) = prefs.edit { putString("installed_apps", installedAppsToJson(value)) }

    private fun readList(key: String): List<String> =
        prefs.getString(key, null)?.split('\n')?.filter { it.isNotEmpty() } ?: emptyList()

    private fun writeList(key: String, value: List<String>) = prefs.edit { putString(key, value.joinToString("\n")) }
}

/** Applies the saved order and drops hidden apps; apps not ordered yet (e.g. newly installed) go last. */
fun arrange(apps: List<InstalledApp>, order: List<String>, hidden: List<String>): List<InstalledApp> {
    val rank = order.withIndex().associate { (index, key) -> key to index }
    val hiddenKeys = hidden.toSet()
    // sortedBy is stable, so unranked apps keep their alphabetical order
    return apps.filter { it.key !in hiddenKeys }.sortedBy { rank[it.key] ?: Int.MAX_VALUE }
}

/** The two layouts the "All apps" page offers */
const val CALENDAR_BY_TIME = "time"
const val CALENDAR_BY_PRIORITY = "priority"
val CALENDAR_GROUPINGS = listOf(CALENDAR_BY_TIME, CALENDAR_BY_PRIORITY)
const val CALENDAR_TODAY_TOMORROW = "today_tomorrow"
const val CALENDAR_DATE = "date"
val CALENDAR_GROUP_STYLES = listOf(CALENDAR_TODAY_TOMORROW, CALENDAR_DATE)
const val CALENDAR_DAYS_DEFAULT = 3
val CALENDAR_DAYS = 1..14
const val CALENDAR_MAX_EVENTS_DEFAULT = 20
val CALENDAR_MAX_EVENTS = 1..50
const val CALENDAR_REFRESH_DEFAULT = 30
val CALENDAR_REFRESH = 5..120

const val CAMERA_SIDE_LEFT = "left"
const val CAMERA_SIDE_CENTRE = "centre"
const val CAMERA_SIDE_WIDER = "wider"
val CAMERA_SIDES = listOf(CAMERA_SIDE_LEFT, CAMERA_SIDE_CENTRE, CAMERA_SIDE_WIDER)

val SHUFFLE_HOURS = 0..24
/** After this long away, coming back to the launcher counts as a new session */
const val SHUFFLE_SESSION_GAP_MS = 30 * 60_000L

const val BACKGROUND_NONE = "none"
const val BACKGROUND_COLOUR = "colour"
const val BACKGROUND_IMAGE = "image"
val BACKGROUND_KINDS = listOf(BACKGROUND_NONE, BACKGROUND_COLOUR, BACKGROUND_IMAGE)
const val BACKGROUND_FILL = "fill"
const val BACKGROUND_FIT = "fit"
const val BACKGROUND_STRETCH = "stretch"
const val BACKGROUND_TILE = "tile"
const val BACKGROUND_CENTRE = "centre"
val BACKGROUND_FITS = listOf(BACKGROUND_FILL, BACKGROUND_FIT, BACKGROUND_STRETCH, BACKGROUND_TILE, BACKGROUND_CENTRE)
const val BACKGROUND_SCRIM_DEFAULT = 65
val BACKGROUND_SCRIM = 0..95

const val HOME_CAMERA_FIRST = "first"
const val HOME_CAMERA_LAST = "last"
val HOME_CAMERA_STARTS = listOf(HOME_CAMERA_FIRST, HOME_CAMERA_LAST)

const val CAMERAS_DIRECT = "direct"
const val CAMERAS_HOME_ASSISTANT = "home_assistant"

val CAMERA_SOURCES = listOf(CAMERAS_DIRECT, CAMERAS_HOME_ASSISTANT)

const val CAMERA_SOUND_FULLSCREEN = "fullscreen"
const val CAMERA_SOUND_MUTED = "muted"
val CAMERA_SOUNDS = listOf(CAMERA_SOUND_FULLSCREEN, CAMERA_SOUND_MUTED)

const val ALL_APPS_HORIZONTAL = "horizontal"
const val ALL_APPS_VERTICAL = "vertical"
val ALL_APPS_SCROLLS = listOf(ALL_APPS_HORIZONTAL, ALL_APPS_VERTICAL)
const val ALL_APPS_LINES_DEFAULT = 2
val ALL_APPS_LINES = 1..6
