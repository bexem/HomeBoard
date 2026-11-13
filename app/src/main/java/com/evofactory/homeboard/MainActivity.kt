package com.evofactory.homeboard

import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.roundToInt

// The panel next to the camera (Home Assistant or the other cameras) is at least this wide; it
// also gets whatever width the camera leaves
private val SidePanelWidth = 260.dp

// App tiles from this 0-based slot on have the side panel above them, the earlier ones the camera
private const val FIRST_SLOT_UNDER_PANEL = 3

class MainActivity : ComponentActivity() {
    private val prefs by lazy { LauncherPrefs(this) }
    // For the few places inside the settings lambdas that need a Context of their own
    private val appContext get() = this
    private var installed by mutableStateOf<List<InstalledApp>?>(null)
    private var order by mutableStateOf(emptyList<String>())
    private var hidden by mutableStateOf(emptyList<String>())
    private var showHidden by mutableStateOf(false)
    private var pingOpen by mutableStateOf(false)
    private var networkStatusOpen by mutableStateOf(false)
    private var camerasOpen by mutableStateOf(false)
    private var cameras by mutableStateOf(emptyList<Camera>())
    private var cameraGridLive by mutableStateOf(true)
    private var minimizeVideoStreams by mutableStateOf(false)
    private var selectedCamera by mutableStateOf("")
    // A camera picked with the remote outranks the shuffle, for the rest of the session
    private var cameraChosenManually by mutableStateOf(false)
    private var tab by mutableStateOf(HomeTab.Home)
    // Which tab of the Home Assistant dialog is open; null when it is closed
    private var haDialogTab by mutableStateOf<HaDialogTab?>(null)
    private var haServer by mutableStateOf(HaServer(""))
    // Everything on the Assistant tab in its order, and the actions also in the top bar
    private var haEntities by mutableStateOf(emptyList<String>())
    private var haHomeEntities by mutableStateOf(emptyList<String>())
    private var haTopBar by mutableStateOf(emptyList<String>())
    // The Home Assistant dashboard the Assistant tab shows instead, if any
    private var haDisplay by mutableStateOf(HaDisplay())
    // Services with no entity of their own, shown as their own card on the Assistant tab
    private var haServices by mutableStateOf<List<String>>(emptyList())
    private var frigate by mutableStateOf(FrigateServer())
    private var frigateOpen by mutableStateOf(false)
    private var settingsOpen by mutableStateOf(false)
    // Counts every settings write, so the page re-reads its values
    private var settingsRevision by mutableIntStateOf(0)
    private var backgroundColourOpen by mutableStateOf(false)
    // This session's order, rolled once: nothing moves again until the TV has been off, or the hours are up
    private var shuffleSeed by mutableIntStateOf(0)
    private var shuffleCamera by mutableStateOf(false)
    private var shuffleActions by mutableStateOf(false)
    private var shuffleCards by mutableStateOf(false)
    private var shuffleSides by mutableStateOf(false)
    private var badge by mutableStateOf(false)
    // The dot on the Alerts tab, and the count beside the clock, both from the same read
    private var alertsDot by mutableStateOf(true)
    // What the count beside the clock counts: Frigate's alerts, or the TV's own notifications
    private var badgeSource by mutableStateOf(BADGE_SOURCE_FRIGATE)
    private var notificationsOpen by mutableStateOf(false)
    // The grant's own status screen, which is what "Access" means once it is granted
    private var notificationGrantOpen by mutableStateOf(false)
    private var screensaverOpen by mutableStateOf(false)
    private var entityNamesOpen by mutableStateOf(false)
    private var haAddressOpen by mutableStateOf(false)
    private var haServicesOpen by mutableStateOf(false)
    private var haConnectFirst by mutableStateOf(false)
    private var homeHelperOpen by mutableStateOf(false)
    private var logoutConfirm by mutableStateOf(false)
    private var newEvents by mutableIntStateOf(0)
    // The app whose icon is being chosen, and a counter that makes the tiles read the overrides again
    private var iconFor by mutableStateOf<InstalledApp?>(null)
    private var iconVersion by mutableIntStateOf(0)
    private var backgroundVersion by mutableIntStateOf(0)
    // A picture from this TV's storage; copied in, so moving or deleting the original changes nothing
    private val iconPick = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val app = iconFor
        iconFor = null
        if (uri != null && app != null) {
            lifecycleScope.launch {
                val copied = withContext(Dispatchers.IO) {
                    runCatching {
                        val file = java.io.File(filesDir, "icons/${app.key}.img")
                        file.parentFile?.mkdirs()
                        contentResolver.openInputStream(uri)?.use { input ->
                            file.outputStream().use { output -> input.copyTo(output) }
                        }
                        file.path
                    }.onFailure { AppLog.w("Icons", "Can't copy the picture", it) }.getOrNull()
                }
                if (copied != null) {
                    val entries = prefs.appIcons.filterNot { it.substringBefore('=') == app.key } + "${app.key}=$copied"
                    prefs.appIcons = entries
                    appIconOverrides = entries.associate { it.substringBefore('=') to it.substringAfter('=') }
                    iconVersion++
                } else {
                    android.widget.Toast.makeText(this@MainActivity, "Couldn't read that file", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    private val backgroundPick = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                val copied = withContext(Dispatchers.IO) {
                    runCatching {
                        val target = java.io.File(filesDir, "background.img")
                        contentResolver.openInputStream(uri)?.use { input ->
                            target.outputStream().use { output -> input.copyTo(output) }
                        }
                        target.path
                    }.onFailure { AppLog.w("Background", "Can't copy the picture", it) }.getOrNull()
                }
                if (copied != null) {
                    prefs.backgroundImage = copied
                    prefs.backgroundKind = BACKGROUND_IMAGE
                    backgroundVersion++
                } else {
                    android.widget.Toast.makeText(this@MainActivity, "Couldn't read that file", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    private var favouritesOpen by mutableStateOf(false)
    private var allAppsOpen by mutableStateOf(false)
    // The tile to come back to once the page closes
    private var allAppsReturnKey by mutableStateOf<String?>(null)
    private var stuckPlayersOpen by mutableStateOf(false)
    private var replaceHome by mutableStateOf(false)
    private var catchHomeKey by mutableStateOf(false)
    private var reopenAfterHome by mutableStateOf(false)
    private var showFreeMemory by mutableStateOf(false)
    private var exportOpen by mutableStateOf(false)
    private var importOpen by mutableStateOf(false)
    private var watchNext by mutableStateOf(emptyList<WatchItem>())
    private var watchNextReadAt by mutableStateOf(0L)
    private var hiddenWatching by mutableStateOf(emptyList<String>())
    // The Home tab's own settings: read here, changed from the settings menu
    private var homeAlertsRow by mutableStateOf(false)
    private var continueWatching by mutableStateOf(true)
    private var appRowWrap by mutableStateOf(true)
    private var allAppsTile by mutableStateOf(true)
    private var allAppsLines by mutableStateOf(ALL_APPS_LINES_DEFAULT)
    private var allAppsVertical by mutableStateOf(false)
    private var favourites by mutableStateOf(emptyList<String>())
    private var hidePhoneApps by mutableStateOf(false)
    private var homeSensors by mutableStateOf(true)
    private var channelKeys by mutableStateOf(true)
    private var cameraSound by mutableStateOf(CAMERA_SOUND_FULLSCREEN)
    private var calendarsOpen by mutableStateOf(false)
    private var calendarColoursOpen by mutableStateOf(false)
    private var haCamerasOpen by mutableStateOf(false)
    private var cameraSource by mutableStateOf(CAMERAS_DIRECT)
    private var homeCameraStart by mutableStateOf(HOME_CAMERA_LAST)
    private var alertsTab by mutableStateOf(true)
    private var cameraSide by mutableStateOf(CAMERA_SIDE_LEFT)
    private var haCameras by mutableStateOf(emptyList<HaCamera>())
    private var calendarConfig by mutableStateOf(CalendarConfig())

    // Home override's crash guard: the launcher counts as started once it has stayed up a moment
    private val confirmStarted = Runnable {
        running = true
        if (prefs.overrideLaunches != 0) prefs.overrideLaunches = 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Last time's list: the tiles and the selection are there from the first frame
        installed = prefs.installedApps.ifEmpty { null }
        order = prefs.order
        hidden = prefs.hidden
        cameras = prefs.cameras
        cameraSource = prefs.cameraSource
        homeCameraStart = prefs.homeCameraStart
        alertsTab = prefs.alertsTab
        cameraSide = prefs.cameraSide
        haCameras = haCamerasFromPrefs(prefs.haCameraEntities)
        cameraGridLive = prefs.cameraGridLive
        minimizeVideoStreams = prefs.minimizeVideoStreams
        selectedCamera = prefs.selectedCamera
        // After the saved selection, or the override is overwritten
        applyHomeCameraStart()
        haServer = HaServer(prefs.haUrl, prefs.haToken, prefs.haRefreshToken)
        haEntities = prefs.haEntities
        haHomeEntities = prefs.haHomeEntities
        EntityNames.load(prefs)
        haTopBar = prefs.haTopBar
        haDisplay = prefs.haDisplay
        haServices = prefs.haServices
        frigate = prefs.frigate
        hiddenWatching = prefs.hiddenWatching
        homeAlertsRow = prefs.homeAlertsRow
        continueWatching = prefs.continueWatching
        appRowWrap = prefs.appRowWrap
        allAppsTile = prefs.allAppsTile
        allAppsLines = prefs.allAppsLines
        allAppsVertical = prefs.allAppsScroll == ALL_APPS_VERTICAL
        favourites = prefs.favouriteApps
        hidePhoneApps = prefs.hidePhoneApps
        homeSensors = prefs.homeSensors
        shuffleCamera = prefs.shuffleCamera
        shuffleActions = prefs.shuffleActions
        shuffleCards = prefs.shuffleCards
        shuffleSides = prefs.shuffleSides
        badge = prefs.badge
        alertsDot = prefs.alertsDot
        badgeSource = prefs.badgeSource
        appIconOverrides = prefs.appIcons.associate { it.substringBefore('=') to it.substringAfter('=') }
        shuffleSeed = Shuffle.rollIfDue(prefs)
        channelKeys = prefs.channelKeys
        cameraSound = prefs.cameraSound
        calendarConfig = prefs.calendarConfig()
        AppLog.i(
            "App",
            "Camera sound: $cameraSound, calendars: ${calendarConfig.entities.size}, " +
                "grouping: ${calendarConfig.grouping}, sensors: $homeSensors",
        )
        // Android stops asking by itself after it's been refused. Answering comes back to onResume.
        if (!canReadWatchNext()) requestPermissions(arrayOf(READ_TV_LISTINGS), 0)
        setContent {
            MaterialTheme(colorScheme = AppColorScheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Behind every screen: nothing, a colour, or a picture, under a darkness setting
                    LauncherBackgroundLayer(prefs)
                    // Phone-only apps are dropped at the source, so every view and menu agrees
                    val all = remember(installed, hidePhoneApps) {
                        installed?.filter { !hidePhoneApps || it.isTvApp }
                    }
                    val visible = remember(all, order, hidden) { all?.let { arrange(it, order, hidden) } }
                    val hiddenApps = remember(all, hidden) {
                        all?.let { apps -> hidden.mapNotNull { key -> apps.find { it.key == key } } }
                    }
                    // Alerts has nothing to show without a Frigate server, and can be hidden anyway
                    val shownTabs = remember(alertsTab, frigate) {
                        HomeTab.entries.filterNot { it == HomeTab.Alerts && (!alertsTab || frigate.url.isEmpty()) }
                    }
                    // Hiding Alerts while it is open leaves on Home, rather than on a tab nobody can see
                    LaunchedEffect(shownTabs) {
                        if (tab !in shownTabs) tab = shownTabs.firstOrNull() ?: HomeTab.Home
                    }
                    // Home Assistant cameras, when the pictures come from there: same shape, no address
                    val cams = remember(cameraSource, haCameras, cameras) {
                        if (cameraSource == CAMERAS_HOME_ASSISTANT) {
                            haCameras.map { ha ->
                                Camera(id = "ha:${ha.entityId}", name = ha.name, url = "", haEntity = ha.entityId)
                            }
                        } else {
                            cameras
                        }
                    }
                    // Left and right swap each session, as one of the shuffle options
                    val panelFirst = remember(shuffleSeed, shuffleSides) {
                        shuffleSides && Shuffle.order(listOf(0, 1), shuffleSeed, true, salt = 4).first() == 1
                    }
                    val sessionCams = remember(cams, shuffleSeed, shuffleCamera) {
                        Shuffle.order(cams, shuffleSeed, shuffleCamera, salt = 1)
                    }
                    // The shuffled pick is the session's default; choosing one with the remote ends it
                    val shuffled = if (shuffleCamera && !cameraChosenManually) {
                        sessionCams.firstOrNull()?.takeIf { cams.size > 1 }
                    } else {
                        null
                    }
                    val camera = shuffled ?: cams.find { it.id == selectedCamera } ?: cams.firstOrNull()
                    val mainCameraFocus = remember { FocusRequester() }
                    val haSensorsFocus = remember { FocusRequester() }
                    val networkFocus = remember { FocusRequester() }
                    val cogFocus = remember { FocusRequester() }
                    val secondTabFocus = remember { FocusRequester() }
                    val homeTabFocus = remember { FocusRequester() }
                    val alertsTabFocus = remember { FocusRequester() }
                    val assistantTabFocus = remember { FocusRequester() }
                    var topBarSelected by remember { mutableStateOf(false) }
                    var homeTabSelected by remember { mutableStateOf(false) }
                    var firstTileSelected by remember { mutableStateOf(false) }
                    var goToFirstTile by remember { mutableIntStateOf(0) }
                    // The alerts, apps and Continue Watching at the bottom: off on the Assistant tab
                    // unless chosen in the Home Assistant dialog. Off, they slide in while the
                    // selection is on them, reached with Down from the cards.
                    val bottomRow = tab != HomeTab.Assistant || haDisplay.bottomRow
                    var bottomRevealed by remember { mutableStateOf(false) }
                    val bottomShown = bottomRow || bottomRevealed
                    val bottomFraction by animateFloatAsState(if (bottomShown) 1f else 0f, label = "bottom rows")
                    // Back never leaves the launcher, it moves the selection: the top bar to Home,
                    // anything else to the first tile, and the first tile to the cog. Without the
                    // tiles, anything below the top bar goes to the Assistant tab.
                    BackHandler {
                        when {
                            // The settings page is a page: Back closes it before anything moves behind it
                            settingsOpen -> settingsOpen = false
                            topBarSelected && !homeTabSelected -> runCatching { homeTabFocus.requestFocus() }
                            !bottomShown -> if (!topBarSelected) runCatching { assistantTabFocus.requestFocus() }
                            firstTileSelected -> runCatching { cogFocus.requestFocus() }
                            else -> goToFirstTile++
                        }
                    }
                    // The top bar's, in their order on the Assistant tab, which has them all
                    val actions = remember(haEntities, haTopBar, shuffleSeed, shuffleActions) {
                        val chosen = haEntities.filter { it in haTopBar }.take(MAX_HA_ACTIONS)
                        Shuffle.order(chosen, shuffleSeed, shuffleActions, salt = 2)
                    }
                    val haConnection = rememberHaConnection(
                        haServer,
                        haEntities,
                        haDisplay.dashboards,
                        calendarEntities = calendarConfig.entities,
                        calendarDays = calendarConfig.days,
                        calendarRefreshMinutes = prefs.calendarRefreshMinutes,
                    )
                    val sideContent = haServer.isSet
                    // A dashboard in columns on the Home tab: the camera and the columns scroll sideways together
                    val homeStrip = sideContent && haDisplay.homeDashboard != null && haDisplay.homeColumns
                    // Picked in the Cameras tab, showing full screen
                    var fullScreenCamera by remember { mutableStateOf<Camera?>(null) }
                    val continueItems = remember(watchNext, watchNextReadAt, hiddenWatching, visible) {
                        watchRow(watchNext, watchNextReadAt, visible.orEmpty().map { it.key }, hiddenWatching)
                    }
                    val appRowFocus = remember { FocusRequester() }
                    val continueFocus = remember { FocusRequester() }
                    var continueActive by remember { mutableStateOf(false) }
                    val frigateFeed = rememberFrigateFeed(frigate, onCertificateAccepted = { saveFrigate(frigate.copy(certPin = it)) })
                    // The badge counts what arrived since the Alerts tab was last opened
                    // The badge counts Frigate only while that is what it counts; the dot always does
                    val frigateCounted = badge && badgeSource == BADGE_SOURCE_FRIGATE
                    LaunchedEffect(badge, badgeSource, alertsDot, frigate, frigateFeed, tab) {
                        // Either indicator shows that something arrived, so either needs the count
                        if ((!badge && !alertsDot) || !frigate.isSet) {
                            newEvents = 0
                            return@LaunchedEffect
                        }
                        if (tab == HomeTab.Alerts) {
                            // Looked at: what is there now is seen
                            prefs.badgeSeenAt = (System.currentTimeMillis() / 1000).toDouble()
                            newEvents = 0
                            return@LaunchedEffect
                        }
                        // First run: everything already there is old news, so start counting from now
                        if (prefs.badgeSeenAt == 0.0) prefs.badgeSeenAt = (System.currentTimeMillis() / 1000).toDouble()
                        while (true) {
                            val seen = prefs.badgeSeenAt
                            val client = frigateFeed?.client ?: break
                            val result = runCatching { client.alerts() }
                            result.onSuccess { alerts ->
                                newEvents = alerts.count { it.startTime > seen }
                                AppLog.i("Badge", "seen=$seen of ${alerts.size} alerts, ${newEvents} new")
                            }.onFailure {
                                AppLog.w("Badge", "Can't count the alerts", it)
                            }
                            delay(60_000L)
                        }
                    }
                    val alertsRowFocus = remember { FocusRequester() }
                    // From reaching the row until the remote is back on the tiles, wherever it goes meanwhile
                    var alertsRowShown by remember { mutableStateOf(false) }
                    // Each tab's own switch is its gate: the Cameras tab always shows the strip, the Assistant
                    // tab has "Alerts and apps below", and Home's row switch stays about Home
                    val alertsRowAllowed = tab == HomeTab.Cameras || tab == HomeTab.Assistant || homeAlertsRow
                    val alertsRowFeed = frigateFeed?.takeIf { tab != HomeTab.Alerts && bottomRow && alertsRowAllowed }
                    val hasAlertsRow = alertsRowFeed != null && alertsRowFeed.rowAlerts.isNotEmpty()
                    LaunchedEffect(hasAlertsRow) { if (!hasAlertsRow) alertsRowShown = false }
                    var tilesRowSelected by remember { mutableStateOf(false) }
                    var otherTileSelected by remember { mutableStateOf(false) }
                    var noAlertsSelected by remember { mutableStateOf(false) }
                    val showNoAlertsTile = frigateFeed?.noRecentAlerts == true && bottomRow && alertsRowAllowed
                    // Worked out before the tile is removed, when focus is still known to be on it
                    val lostNoAlertsTile = remember(showNoAlertsTile) { !showNoAlertsTile && noAlertsSelected }
                    val alertsExist = frigateFeed != null && frigateFeed.rowAlerts.isNotEmpty()
                    // Above the rows, the row of alerts is what shows below
                    var middleSelected by remember { mutableStateOf(false) }
                    LaunchedEffect(hasAlertsRow, topBarSelected, middleSelected) {
                        if (hasAlertsRow && (topBarSelected || middleSelected)) alertsRowShown = true
                    }
                    // When alerts arrive or the Cameras tab opens, the selection on the first tile goes to the row
                    var alertsRowDue by remember { mutableStateOf(false) }
                    LaunchedEffect(alertsExist) { if (alertsExist) alertsRowDue = true }
                    LaunchedEffect(tab) { if (tab == HomeTab.Cameras) alertsRowDue = true }
                    LaunchedEffect(showNoAlertsTile) {
                        if (lostNoAlertsTile && (!alertsExist || tab == HomeTab.Alerts)) goToFirstTile++
                    }
                    LaunchedEffect(alertsRowDue, hasAlertsRow) {
                        if (!alertsRowDue || !hasAlertsRow) return@LaunchedEffect
                        // Not from another tile or the row below: those are in use
                        if (!otherTileSelected && !continueActive && (tilesRowSelected || lostNoAlertsTile)) {
                            alertsRowShown = true
                            withFrameNanos { } // let the row lay out, so its first card is focusable
                            runCatching { alertsRowFocus.requestFocus() }
                        }
                        alertsRowDue = false
                    }
                    // Where up goes from the bottom rows, from the item on screen in this 0-based slot
                    fun aboveRows(slot: Int) = when {
                        // What's above moves sideways: the nearest card or the camera
                        tab == HomeTab.Home && homeStrip -> FocusRequester.Default
                        slot >= FIRST_SLOT_UNDER_PANEL && tab == HomeTab.Home && haServer.isSet -> haSensorsFocus
                        slot < FIRST_SLOT_UNDER_PANEL && tab == HomeTab.Home && camera != null -> mainCameraFocus
                        else -> FocusRequester.Default
                    }
                    // A number button from the Alerts or Assistant tab: the selection goes to the camera
                    var mainCameraDue by remember { mutableStateOf(false) }
                    LaunchedEffect(mainCameraDue, tab) {
                        if (!mainCameraDue || tab != HomeTab.Home) return@LaunchedEffect
                        withFrameNanos { } // let the Home tab lay out, so the camera is focusable
                        runCatching { mainCameraFocus.requestFocus() }
                        mainCameraDue = false
                    }
                    // A number button on the Cameras tab: the selection goes to that camera
                    var gridCameraDue by remember { mutableStateOf<String?>(null) }
                    // The Cameras tab's camera the selection was last on: channel up and down go on from it
                    var gridFocusedCamera by remember { mutableStateOf<String?>(null) }
                    // The colour buttons open the tabs, with the selection on the tab. A number button picks
                    // a camera: selected on the Cameras tab, as the Home tab's camera everywhere else. The
                    // dot button selects the cog.
                    fun onRemoteButton(event: KeyEvent): Boolean {
                        if (isSettingsButton(event)) {
                            if (event.type == KeyEventType.KeyDown) runCatching { cogFocus.requestFocus() }
                            return true
                        }
                        colorButtonTab(event, shownTabs)?.let { pressed ->
                            if (event.type == KeyEventType.KeyDown) {
                                tab = pressed
                                val tabFocus = when (pressed) {
                                    HomeTab.Home -> homeTabFocus
                                    HomeTab.Cameras -> secondTabFocus
                                    HomeTab.Alerts -> alertsTabFocus
                                    HomeTab.Assistant -> assistantTabFocus
                                }
                                runCatching { tabFocus.requestFocus() }
                            }
                            return true
                        }
                        // Channel up and down: the next or previous camera, on the Home tab wherever the
                        // selection is, and on the Cameras tab the selection moves to it. Off, those keys
                        // are left to the TV
                        if (channelKeys) channelStep(event)?.let { step ->
                            if (tab != HomeTab.Home && tab != HomeTab.Cameras) return false
                            if (event.type == KeyEventType.KeyDown) {
                                if (tab == HomeTab.Cameras) {
                                    steppedCamera(cams, gridFocusedCamera, step)?.let { gridCameraDue = it.id }
                                } else {
                                    steppedCamera(cams, camera?.id, step)?.let(::selectCamera)
                                }
                            }
                            return true
                        }
                        val index = cameraButtonIndex(event) ?: return false
                        val picked = cams.getOrNull(index)
                        if (event.type == KeyEventType.KeyDown && picked != null) {
                            if (tab == HomeTab.Cameras) {
                                gridCameraDue = picked.id
                            } else {
                                selectCamera(picked)
                                if (tab != HomeTab.Home) {
                                    tab = HomeTab.Home
                                    mainCameraDue = true
                                }
                            }
                        }
                        return true
                    }
                    Column(modifier = Modifier.fillMaxSize().onPreviewKeyEvent(::onRemoteButton)) {
                        HomeTopBar(
                            tab = tab,
                            onTabChange = { tab = it },
                            tabs = shownTabs,
                            secondTabFocus = secondTabFocus,
                            homeTabFocus = homeTabFocus,
                            alertsTabFocus = alertsTabFocus,
                            assistantTabFocus = assistantTabFocus,
                            onHomeTabSelected = { homeTabSelected = it },
                            showFreeMemory = showFreeMemory,
                            // The count is the chosen source: Frigate's unseen alerts, or the TV's list
                            newEvents = when {
                                !badge -> 0
                                badgeSource == BADGE_SOURCE_NOTIFICATIONS -> Notifications.entries.size
                                frigate.isSet && alertsTab -> newEvents
                                else -> 0
                            },
                            // The dot is Frigate's, and stands down while the badge already counts Frigate
                            alertsDot = alertsDot && !frigateCounted && frigate.isSet && alertsTab && newEvents > 0,
                            onNewEvents = {
                                if (badgeSource == BADGE_SOURCE_NOTIFICATIONS) {
                                    // The count is the notifications: OK opens what it counts
                                    notificationsOpen = true
                                } else {
                                    // The count is cleared the moment the tab opens, so the focus goes with it
                                    tab = HomeTab.Alerts
                                    runCatching { alertsTabFocus.requestFocus() }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = EdgePadding, top = TopPadding, end = EdgePadding)
                                .onFocusChanged { topBarSelected = it.hasFocus },
                            actions = {
                                // These get what the action buttons leave
                                StuckPlayersWarning(
                                    onClick = { stuckPlayersOpen = true },
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                HomeHelperWarning(
                                    replaceHome,
                                    onClick = { settingsOpen = true },
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (haServer.isSet) HaActionButtons(haConnection, actions)
                                NetworkButton(
                                    onClick = { networkStatusOpen = true },
                                    modifier = Modifier.focusRequester(networkFocus),
                                )
                            },
                        ) {
                            SettingsButton(
                                onOpenSettings = { settingsOpen = true },
                                modifier = Modifier.focusRequester(cogFocus),
                            )
                        }
                        if (tab == HomeTab.Cameras) {
                            CameraGrid(
                                cameras = cams,
                                ha = haServer,
                                // Like the main camera: a clip opened from the alerts row may need its decoder,
                                // and so may a camera shown full screen
                                playSubstreams = cameraGridLive &&
                                    !(minimizeVideoStreams && (frigateFeed?.playing != null || fullScreenCamera != null)),
                                minimizeStreams = minimizeVideoStreams,
                                spareDecoder = fullScreenCamera != null,
                                soundEnabled = cameraSound == CAMERA_SOUND_FULLSCREEN,
                                onSelect = { fullScreenCamera = it },
                                onSetUp = { camerasOpen = true },
                                selectCamera = gridCameraDue,
                                onCameraSelected = { gridCameraDue = null },
                                onCameraFocused = { gridFocusedCamera = it },
                                modifier = Modifier.weight(1f).fillMaxWidth().padding(top = EdgePadding).onFocusChanged { middleSelected = it.hasFocus },
                            )
                        } else if (tab == HomeTab.Alerts) {
                            FrigateAlertsGrid(
                                feed = frigateFeed,
                                onSetUp = { frigateOpen = true },
                                modifier = Modifier.weight(1f).fillMaxWidth().padding(top = EdgePadding),
                            )
                        } else if (tab == HomeTab.Assistant) {
                            HaAssistantTab(
                                connection = haConnection,
                                homeAssistantSet = haServer.isSet,
                                entityIds = haEntities,
                                dashboard = haDisplay.dashboard,
                                services = haServices,
                                tabFocus = assistantTabFocus,
                                onSetUp = { haDialogTab = HaDialogTab.Home },
                                sectionColumns = haDisplay.sectionColumns,
                                toScreenEdge = !bottomShown,
                                modifier = Modifier.weight(1f).fillMaxWidth().padding(top = EdgePadding).onFocusChanged { middleSelected = it.hasFocus },
                            )
                        } else {
                            // The camera, stopped before a clip opens, so the clip can have its decoder
                            @Composable
                            fun CameraBox(width: Dp) {
                                Box(modifier = Modifier.width(width).fillMaxHeight()) {
                                    if (camera != null && !(minimizeVideoStreams && frigateFeed?.playing != null)) {
                                        // Restarted when the panel appears or goes: the playing video
                                        // doesn't follow the camera's new size otherwise
                                        key(sideContent) {
                                            MainCamera(
                                                camera = camera,
                                                cameras = cams,
                                                ha = haServer,
                                                minimizeStreams = minimizeVideoStreams,
                                                soundEnabled = cameraSound == CAMERA_SOUND_FULLSCREEN,
                                                // Learned for a camera whose shape is Auto; a chosen shape is the
                                                // owner's, so the stream must not overwrite it
                                                onAspect = { reported ->
                                                    val known = cameras.find { it.id == camera.id }?.aspect ?: 0f
                                                    if (known <= 0f && reported > 0f) {
                                                        saveCameras(
                                                            cameras.map {
                                                                if (it.id == camera.id) it.copy(aspect = reported) else it
                                                            },
                                                        )
                                                    }
                                                },
                                                onSelect = ::selectCamera,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .focusRequester(mainCameraFocus)
                                                    .focusProperties { up = secondTabFocus },
                                            )
                                        }
                                    }
                                }
                            }
                            // Its own choice: the living room beside the camera, the house on the Assistant tab
                            val homeEntities = remember(haHomeEntities) { haHomeEntities.filterNot(::isHaAction) }
                            if (homeStrip) {
                                // A dashboard in columns: the camera and the columns in one row that
                                // scrolls sideways to the screen's right edge, the camera leaving on
                                // the left as Right brings in more columns
                                BoxWithConstraints(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .padding(top = EdgePadding)
                                        .onFocusChanged { middleSelected = it.hasFocus },
                                ) {
                                    // As large as when next to the panel
                                    val cameraWidth = minOf(maxHeight * 16f / 9f, maxWidth - EdgePadding * 3 - SidePanelWidth)
                                    HaAssistantTab(
                                        connection = haConnection,
                                        homeAssistantSet = haServer.isSet,
                                        entityIds = homeEntities,
                                        dashboard = haDisplay.homeDashboard,
                                        // The button next to the cog
                                        tabFocus = networkFocus,
                                        onSetUp = { haDialogTab = HaDialogTab.Home },
                                        sectionColumns = true,
                                        leading = LeadingItem(cameraWidth, cameraWidth * 9f / 16f) { CameraBox(cameraWidth) },
                                        columnsFocus = haSensorsFocus,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            } else {
                                // The camera, as large as fits above the app row, and Home Assistant
                                // next to it, which gets all the width the camera leaves
                                BoxWithConstraints(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .padding(start = EdgePadding, top = EdgePadding, end = EdgePadding)
                                        .onFocusChanged { middleSelected = it.hasFocus },
                                ) {
                                    // The slot is the camera's own shape, so a 4:3 picture leaves a gap by
                                    // design; what happens to that gap is a setting
                                    val cameraShape = camera?.aspect?.takeIf { it > 0f } ?: (16f / 9f)
                                    val roomForCamera = if (sideContent) maxWidth - SidePanelWidth - EdgePadding else maxWidth
                                    val cameraWidth = minOf(maxHeight * cameraShape, roomForCamera)
                                    // What the choice does, in one place: "wider widgets" gives the panel
                                    // the rest of the width; the other two keep it at its own width and
                                    // leave the space beside a narrow picture empty, or centre it there.
                                    // Before this, the panel always took the rest, so "wider" and "leave
                                    // the gap" were the same picture and the choice did nothing.
                                    val gapKept = cameraSide != CAMERA_SIDE_WIDER
                                    Row(horizontalArrangement = Arrangement.spacedBy(EdgePadding)) {
                                        // A camera and widgets can trade places each session
                                        val cameraSlot: @Composable () -> Unit = {
                                            when (cameraSide) {
                                                CAMERA_SIDE_CENTRE -> Box(
                                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                                    contentAlignment = Alignment.Center,
                                                ) { CameraBox(cameraWidth) }
                                                else -> CameraBox(cameraWidth)
                                            }
                                        }
                                        if (!panelFirst) cameraSlot()
                                        // "Leave the gap": the space between the picture and a fixed-width
                                        // panel. Centring already holds that space in its own weighted box
                                        if (sideContent && cameraSide == CAMERA_SIDE_LEFT) Spacer(Modifier.weight(1f))
                                        if (sideContent && haDisplay.homeDashboard != null) {
                                            // A dashboard chosen for the Home tab: its sections, card by card
                                            HaAssistantTab(
                                                connection = haConnection,
                                                homeAssistantSet = haServer.isSet,
                                                entityIds = homeEntities,
                                                dashboard = haDisplay.homeDashboard,
                                                // The button next to the cog
                                                tabFocus = networkFocus,
                                                onSetUp = { haDialogTab = HaDialogTab.Home },
                                                inset = 0.dp,
                                                modifier = Modifier
                                                    .then(if (gapKept) Modifier.width(SidePanelWidth) else Modifier.weight(1f))
                                                    .fillMaxHeight()
                                                    .focusRequester(haSensorsFocus),
                                            )
                                        } else if (sideContent) {
                                            Box(
                                                modifier = Modifier
                                                    .then(if (gapKept) Modifier.width(SidePanelWidth) else Modifier.weight(1f))
                                                    .fillMaxHeight(),
                                            ) {
                                                HomeAssistantPanel(
                                                    connection = haConnection,
                                                    entityIds = homeEntities,
                                                    calendar = calendarConfig,
                                                    showSensors = homeSensors,
                                                    cardSeed = shuffleSeed,
                                                    shuffleCards = shuffleCards,
                                                    sensorsFocus = haSensorsFocus,
                                                    // The button next to the cog
                                                    upFocus = networkFocus,
                                                    onLogIn = { haDialogTab = HaDialogTab.Home },
                                                    onOpen = {
                                                        tab = HomeTab.Assistant
                                                        runCatching { assistantTabFocus.requestFocus() }
                                                    },
                                                    modifier = Modifier.fillMaxSize(),
                                                )
                                            }
                                        }
                                        if (panelFirst) cameraSlot()
                                    }
                                }
                            }
                        }
                        // Hidden on the Assistant tab unless chosen or the selection is on them: its
                        // cards then take the whole height. They slide up from the screen's edge,
                        // placed below it while hidden, where only Down finds them.
                        Column(
                            Modifier
                                .onFocusChanged { if (!bottomRow) bottomRevealed = it.hasFocus }
                                .clipToBounds()
                                .layout { measurable, constraints ->
                                    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                                    layout(placeable.width, (placeable.height * bottomFraction).roundToInt()) { placeable.place(0, 0) }
                                },
                        ) {
                            // The tiles' grow room counts towards the margins above and below the row
                            Spacer(Modifier.height(EdgePadding - TileGrowRoom))
                            SlidingRows(
                                revealed = continueActive,
                                aboveRevealed = alertsRowShown && hasAlertsRow,
                                above = {
                                    if (alertsRowFeed != null) {
                                        FrigateAlertsRow(
                                            feed = alertsRowFeed,
                                            shown = alertsRowShown,
                                            firstCardFocus = alertsRowFocus,
                                            upFocus = ::aboveRows,
                                            downFocus = appRowFocus,
                                            onEnter = { alertsRowShown = true },
                                            onLeave = { goToFirstTile++ },
                                        )
                                    }
                                },
                                first = {
                                    AppRowSpace(Modifier.onFocusChanged { tilesRowSelected = it.hasFocus }) {
                                        if (visible != null && hiddenApps != null) AppRow(
                                            apps = visible,
                                            hiddenApps = hiddenApps,
                                            favourites = favourites,
                                            allAppsTile = allAppsTile,
                                            wrapRow = appRowWrap,
                                            restoreKey = if (allAppsOpen) null else allAppsReturnKey,
                                            onRestored = { allAppsReturnKey = null },
                                            onOpenAllApps = {
                                                allAppsReturnKey = ALL_APPS_TILE_KEY
                                                allAppsOpen = true
                                            },
                                            onPin = { app ->
                                                favourites = favourites + app.key
                                                prefs.favouriteApps = favourites
                                            },
                                            onUnpin = { app ->
                                                favourites = favourites - app.key
                                                prefs.favouriteApps = favourites
                                            },
                                            showHidden = showHidden,
                                            onShowHiddenChange = { showHidden = it },
                                            onSetIcon = { app ->
                                                iconFor = app
                                                iconPick.launch(arrayOf("image/*"))
                                            },
                                            onResetIcon = { app ->
                                                val entries = prefs.appIcons.filterNot { it.substringBefore('=') == app.key }
                                                prefs.appIcons = entries
                                                appIconOverrides = entries.associate { it.substringBefore('=') to it.substringAfter('=') }
                                                // The decoded copy is cached: drop it so the app's own art returns
                                                tileImageCacheInvalidate(app.key)
                                                iconVersion++
                                            },
                                            onHide = { app ->
                                                // Hidden apps leave the row order and queue up at the end
                                                saveOrder(visible.map { it.key } - app.key)
                                                saveHidden(hidden + app.key)
                                            },
                                            onUnhide = { app ->
                                                // Back at the end of the visible apps
                                                saveOrder(visible.map { it.key } + app.key)
                                                saveHidden(hidden - app.key)
                                            },
                                            onReorder = { list ->
                                                if (favourites.isEmpty()) {
                                                    saveOrder(list.map { it.key })
                                                } else {
                                                    // The row shows the favourites: arrange those
                                                    favourites = list.map { it.key }.filter { key -> key in favourites }
                                                    prefs.favouriteApps = favourites
                                                }
                                            },
                                            upFocus = { slot ->
                                                when {
                                                    hasAlertsRow -> alertsRowFocus
                                                    else -> aboveRows(slot)
                                                }
                                            },
                                            downFocus = if (continueWatching && continueItems.isNotEmpty()) continueFocus else FocusRequester.Cancel,
                                            rowFocus = appRowFocus,
                                            goToFirstTile = goToFirstTile,
                                            onFirstTileSelected = { firstTileSelected = it },
                                            onOtherTileSelected = { otherTileSelected = it },
                                            showNoAlertsTile = showNoAlertsTile,
                                            onNoAlertsSelected = { noAlertsSelected = it },
                                            onNoAlertsClick = {
                                                tab = HomeTab.Alerts
                                                runCatching { alertsTabFocus.requestFocus() }
                                            },
                                            modifier = Modifier.onFocusChanged { if (it.hasFocus) alertsRowShown = false },
                                        ) else AppRowPlaceholder()
                                    }
                                },
                                second = {
                                    // Off, the row is simply not there
                                    if (!continueWatching) return@SlidingRows
                                    ContinueWatchingRow(
                                        items = continueItems,
                                        apps = all.orEmpty(),
                                        firstCardFocus = continueFocus,
                                        upFocus = appRowFocus,
                                        onActiveChange = { continueActive = it },
                                        onHide = {
                                            hiddenWatching = hiddenWatching + it.hideKey
                                            prefs.hiddenWatching = hiddenWatching
                                        },
                                        onLeave = { goToFirstTile++ },
                                    )
                                },
                            )
                            Spacer(Modifier.height(EdgePadding - TileGrowRoom))
                        }
                    }
                    frigateFeed?.let { FrigateClipPlayer(it, minimizeVideoStreams) }
                    fullScreenCamera?.let {
                        FullScreenCamera(
                            it,
                            minimizeVideoStreams,
                            ha = haServer,
                            soundEnabled = cameraSound == CAMERA_SOUND_FULLSCREEN,
                            onCameraButton = { index -> cams.getOrNull(index)?.let { picked -> fullScreenCamera = picked } },
                            onChannel = { step -> steppedCamera(cams, it.id, step)?.let { picked -> fullScreenCamera = picked } },
                            onClose = { fullScreenCamera = null },
                        )
                    }
                    if (camerasOpen) {
                        CamerasDialog(
                            cameras = cams,
                            onChange = ::saveCameras,
                            playSubstreams = cameraGridLive,
                            onPlaySubstreamsChange = {
                                cameraGridLive = it
                                prefs.cameraGridLive = it
                            },
                            minimizeStreams = minimizeVideoStreams,
                            onMinimizeStreamsChange = {
                                minimizeVideoStreams = it
                                prefs.minimizeVideoStreams = it
                            },
                            onDismiss = { camerasOpen = false },
                        )
                    }
                    if (haDialogTab != null) {
                        HomeAssistantDialog(
                            initialServer = haServer,
                            initialUsername = remember { prefs.haUsername },
                            initialEntities = haEntities,
                            initialHomeEntities = haHomeEntities,
                            initialTopBar = haTopBar,
                            initialDisplay = haDisplay,
                            loginRejected = haConnection.status == HaStatus.TokenRejected,
                            // A row in the settings page says which part it is about
                            initialTab = haDialogTab ?: HaDialogTab.Home,
                            // The Home Assistant row is the connection, so it opens there
                            connectionFirst = haConnectFirst,
                            onSave = { server, username, entities, homeEntities, topBar, display, dashboardTitles ->
                                // A replaced username-and-password login is ended in Home Assistant
                                if (haServer.refreshToken != server.refreshToken) revokeHaLogin(haServer)
                                haServer = server
                                prefs.haUrl = server.url
                                prefs.haToken = server.token
                                prefs.haRefreshToken = server.refreshToken
                                prefs.haUsername = username
                                saveHaChoice(entities, homeEntities, topBar)
                                haDisplay = display
                                prefs.haDisplay = display
                                // What the rows print: the chosen dashboard's title, not its key
                                prefs.haDashboardLabel = dashboardTitles.first
                                prefs.haHomeDashboardLabel = dashboardTitles.second
                                // The page behind the dialog shows these choices on its rows
                                settingsRevision++
                            },
                            onLogOut = ::logOutOfHa,
                            onDiscardLogin = ::revokeHaLogin,
                            onDismiss = { haDialogTab = null; haConnectFirst = false },
                        )
                    }
                    if (logoutConfirm) {
                        ConfirmDialog(
                            title = "Log out of Home Assistant?",
                            detail = "The TV's login is ended in Home Assistant's own profile. You can log in again here at any time.",
                            confirm = "Log out",
                            onConfirm = { logOutOfHa() },
                            onDismiss = { logoutConfirm = false },
                        )
                    }
                    if (homeHelperOpen) {
                        HomeHelperDialog(
                            prefs = prefs,
                            onOpenAccessibility = { openAccessibilitySettings() },
                            onTurnOff = {
                                prefs.catchHomeKey = false
                                prefs.reopenAfterHome = false
                            },
                            onDismiss = { homeHelperOpen = false },
                        )
                    }
                    if (haAddressOpen) {
                        HaAddressDialog(
                            prefs = prefs,
                            // The running server, not just the stored one: the panel and Log in read this
                            onSaved = { address ->
                                if (haServer.refreshToken.isNotEmpty()) revokeHaLogin(haServer)
                                haServer = HaServer(address)
                                settingsRevision++
                            },
                            onDismiss = { haAddressOpen = false },
                        )
                    }
                    if (entityNamesOpen) {
                        EntityNamesDialog(
                            prefs = prefs,
                            entities = (haEntities + haHomeEntities).distinct(),
                            haNameOf = { haConnection.entities[it]?.fullName },
                            onDismiss = { entityNamesOpen = false },
                        )
                    }
                    if (screensaverOpen) {
                        ScreensaverDialog(prefs = prefs, cameras = cams, onDismiss = { screensaverOpen = false })
                    }
                    if (notificationsOpen) {
                        NotificationsDialog(onDismiss = { notificationsOpen = false })
                    }
                    if (haServicesOpen) {
                        HaServicesDialog(
                            server = haServer,
                            chosen = haServices,
                            onChange = {
                                haServices = it
                                prefs.haServices = it
                            },
                            onDismiss = { haServicesOpen = false },
                        )
                    }
                    if (notificationGrantOpen) {
                        NotificationAccessDialog(onDismiss = { notificationGrantOpen = false })
                    }
                    if (frigateOpen) {
                        FrigateDialog(
                            initial = frigate,
                            onSave = ::saveFrigate,
                            onDismiss = { frigateOpen = false },
                        )
                    }
                    if (haCamerasOpen) {
                        HaCamerasDialog(
                            server = haServer,
                            chosen = haCameras,
                            onChange = { picked ->
                                haCameras = picked
                                prefs.haCameraEntities = haCamerasToPrefs(picked)
                            },
                            onDismiss = { haCamerasOpen = false },
                        )
                    }
                    if (calendarsOpen) {
                        CalendarPickerDialog(
                            server = haServer,
                            chosen = calendarConfig.entities,
                            onChange = { chosen ->
                                // Saved at once: the card and the connection both read it back
                                prefs.calendarEntities = chosen
                                calendarConfig = prefs.calendarConfig()
                            },
                            onDismiss = { calendarsOpen = false },
                        )
                    }
                    if (calendarColoursOpen) {
                        CalendarColoursDialog(
                            server = haServer,
                            chosen = calendarConfig.entities,
                            colours = calendarConfig.colours,
                            onChange = { picked ->
                                prefs.calendarColours = picked
                                calendarConfig = prefs.calendarConfig()
                            },
                            onDismiss = { calendarColoursOpen = false },
                        )
                    }
                    if (favouritesOpen) {
                        FavouritesEditorDialog(
                            apps = all.orEmpty(),
                            favourites = favourites,
                            onChange = {
                                favourites = it
                                prefs.favouriteApps = it
                            },
                            onDismiss = { favouritesOpen = false },
                        )
                    }
                    if (allAppsOpen) {
                        AllAppsPage(
                            apps = all.orEmpty(),
                            hidden = hidden.toSet(),
                            onSetIcon = { app ->
                                iconFor = app
                                iconPick.launch(arrayOf("image/*"))
                            },
                            onResetIcon = { app ->
                                val entries = prefs.appIcons.filterNot { it.substringBefore('=') == app.key }
                                prefs.appIcons = entries
                                appIconOverrides = entries.associate { it.substringBefore('=') to it.substringAfter('=') }
                                tileImageCacheInvalidate(app.key)
                                iconVersion++
                            },
                            favourites = favourites,
                            onPin = { app ->
                                favourites = favourites + app.key
                                prefs.favouriteApps = favourites
                            },
                            onUnpin = { app ->
                                favourites = favourites - app.key
                                prefs.favouriteApps = favourites
                            },
                            showHidden = showHidden,
                            onShowHiddenChange = { showHidden = it },
                            onHide = { app ->
                                saveOrder(visible.orEmpty().map { it.key } - app.key)
                                saveHidden(hidden + app.key)
                            },
                            onUnhide = { app ->
                                saveOrder(visible.orEmpty().map { it.key } + app.key)
                                saveHidden(hidden - app.key)
                            },
                            lines = allAppsLines,
                            vertical = allAppsVertical,
                            onClose = { allAppsOpen = false },
                        )
                    }
                    if (backgroundColourOpen) {
                        ColourPickerDialog(
                            title = "Background colour",
                            subtitle = "The launcher keeps its dark surfaces; the colour sits behind them, under the darkness setting.",
                            palette = BackgroundColours,
                            current = prefs.backgroundColour,
                            onPick = {
                                prefs.backgroundColour = it
                                prefs.backgroundKind = BACKGROUND_COLOUR
                                backgroundVersion++
                            },
                            onDismiss = { backgroundColourOpen = false },
                        )
                    }
                    if (settingsOpen) {
                        SettingsPage(
                            prefs = prefs,
                            actions = SettingsActions(
                                openFavourites = { favouritesOpen = true },
                                openCalendars = { calendarsOpen = true },
                                openBackgroundColour = { backgroundColourOpen = true },
                                pickBackgroundImage = { backgroundPick.launch(arrayOf("image/*")) },
                                openCalendarColours = { calendarColoursOpen = true },
                                openHaCameras = { haCamerasOpen = true },
                                openCameras = { camerasOpen = true },
                                openScreensaver = { screensaverOpen = true },
                                openEntityNames = { entityNamesOpen = true },
                                openHaAddress = { haAddressOpen = true },
                                logOutHomeAssistant = { logoutConfirm = true },
                                // The connection step, not a part of the editor
                                openHaLogin = {
                                    haDialogTab = HaDialogTab.Home
                                    haConnectFirst = true
                                },
                                openHomeHelper = { homeHelperOpen = true },
                                openHomeAssistant = { tab -> haDialogTab = tab ?: HaDialogTab.Home },
                                openFrigate = { frigateOpen = true },
                                openNotificationAccess = { notificationsOpen = true },
                                // The panel when access is off (it says how to grant it), the status when it is on
                                openHaServices = { haServicesOpen = true },
                                openNotificationGrant = {
                                    if (Notifications.granted || Notifications.enabled(appContext)) {
                                        notificationGrantOpen = true
                                    } else {
                                        notificationsOpen = true
                                    }
                                },
                                markAlertsReviewed = {
                                    // Everything there now is looked at, so neither the dot nor the
                                    // count has anything to say
                                    prefs.badgeSeenAt = (System.currentTimeMillis() / 1000).toDouble()
                                    newEvents = 0
                                    settingsRevision++
                                },
                                openPing = { pingOpen = true },
                                openSystemSettings = { openSystemSettings() },
                                exportSettings = { exportOpen = true },
                                importSettings = { importOpen = true },
                                restartApp = { restartApp() },
                            ),
                            revision = settingsRevision,
                            onSettingsChanged = {
                                settingsRevision++
                                // The launcher keeps its own copies of the few settings it renders
                                catchHomeKey = prefs.catchHomeKey
                                reopenAfterHome = prefs.reopenAfterHome
                                replaceHome = prefs.replaceHome
                                showFreeMemory = prefs.showFreeMemory
                                cameraGridLive = prefs.cameraGridLive
                                minimizeVideoStreams = prefs.minimizeVideoStreams
                                homeAlertsRow = prefs.homeAlertsRow
                                continueWatching = prefs.continueWatching
                                appRowWrap = prefs.appRowWrap
                                allAppsTile = prefs.allAppsTile
                                allAppsLines = prefs.allAppsLines
                                allAppsVertical = prefs.allAppsScroll == ALL_APPS_VERTICAL
                                favourites = prefs.favouriteApps
                                hidePhoneApps = prefs.hidePhoneApps
                                homeSensors = prefs.homeSensors
                                // The session's own seed is rolled already; these say how to use it
                                // Switched on again, the shuffle gets the camera back for this session
                                if (prefs.shuffleCamera && !shuffleCamera) cameraChosenManually = false
                                if (prefs.shuffleCamera && !shuffleCamera) cameraChosenManually = false
            shuffleCamera = prefs.shuffleCamera
                                shuffleActions = prefs.shuffleActions
                                shuffleCards = prefs.shuffleCards
                                shuffleSides = prefs.shuffleSides
                                cameraSource = prefs.cameraSource
                                cameraSide = prefs.cameraSide
                                // Where Home starts is a selection, not a value the row can leave alone
                                applyHomeCameraStart()
                                channelKeys = prefs.channelKeys
                                cameraSound = prefs.cameraSound
                                calendarConfig = prefs.calendarConfig()
                                // The Assistant and Frigate rows the page now holds
                                EntityNames.load(prefs)
                                haDisplay = prefs.haDisplay
                                haServices = prefs.haServices
                                alertsTab = prefs.alertsTab
                                badge = prefs.badge
                                alertsDot = prefs.alertsDot
            badgeSource = prefs.badgeSource
                                badgeSource = prefs.badgeSource
                                homeOverrideChanged()
                            },
                            onDismiss = { settingsOpen = false },
                        )
                    }
                    if (stuckPlayersOpen) {
                        StuckPlayersDialog(onDismiss = { stuckPlayersOpen = false })
                    }
                    if (exportOpen) {
                        ExportDialog(
                            file = SettingsFile(
                                cameras = cams,
                                login = haServer.takeIf { it.isSet },
                                username = remember { prefs.haUsername },
                                sensors = haEntities.filterNot(::isHaAction).map { NamedEntity(it, haConnection.entities[it]?.name ?: it) },
                                actions = haEntities.filter(::isHaAction).map { NamedEntity(it, haConnection.entities[it]?.name ?: it) },
                                order = haEntities,
                                topBar = haTopBar,
                                frigate = frigate.takeIf { it.isSet },
                                appTiles = visible.orEmpty().map { NamedEntity(it.key, it.label) },
                                preferences = currentPreferences(prefs),
                            ),
                            onDismiss = { exportOpen = false },
                        )
                    }
                    if (importOpen) {
                        ImportDialog(onImport = ::applyImport, onDismiss = { importOpen = false })
                    }
                    if (networkStatusOpen) {
                        NetworkStatusDialog(
                            frigate = frigateFeed,
                            // It only connects with something to show
                            homeAssistant = haConnection.takeIf { haServer.isSet && haEntities.isNotEmpty() },
                            onOpenPing = { pingOpen = true },
                            onDismiss = { networkStatusOpen = false },
                        )
                    }
                    if (pingOpen) {
                        PingDialog(
                            initialHost = remember { prefs.pingHost },
                            onHostEntered = { prefs.pingHost = it },
                            onDismiss = { pingOpen = false },
                        )
                    }
                }
            }
        }
    }

    /** Ends the login; the address, username and chosen entities stay for logging in again */
    private fun logOutOfHa() {
        revokeHaLogin(haServer)
        haServer = HaServer(haServer.url)
        prefs.haToken = ""
        prefs.haRefreshToken = ""
    }

    /** Accessibility settings, or the TV's main settings screen on a TV that answers that one with a placeholder */
    private fun openAccessibilitySettings() {
        openSystemSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }

    private fun revokeHaLogin(server: HaServer) {
        lifecycleScope.launch { haRevoke(server) }
    }

    /** Adds what's in the file. A camera whose name is already taken gets "(imported)" after its name. */
    private fun applyImport(file: SettingsFile) {
        if (file.preferences.isNotEmpty()) {
            applyPreferences(prefs, file.preferences)
            // The launcher renders a few of these itself
            catchHomeKey = prefs.catchHomeKey
            reopenAfterHome = prefs.reopenAfterHome
            replaceHome = prefs.replaceHome
            showFreeMemory = prefs.showFreeMemory
            cameraGridLive = prefs.cameraGridLive
            minimizeVideoStreams = prefs.minimizeVideoStreams
            homeAlertsRow = prefs.homeAlertsRow
            continueWatching = prefs.continueWatching
            appRowWrap = prefs.appRowWrap
            allAppsTile = prefs.allAppsTile
            allAppsLines = prefs.allAppsLines
            allAppsVertical = prefs.allAppsScroll == ALL_APPS_VERTICAL
            favourites = prefs.favouriteApps
            hidePhoneApps = prefs.hidePhoneApps
            homeSensors = prefs.homeSensors
            shuffleCamera = prefs.shuffleCamera
            shuffleActions = prefs.shuffleActions
            shuffleCards = prefs.shuffleCards
            shuffleSides = prefs.shuffleSides
            channelKeys = prefs.channelKeys
            cameraSource = prefs.cameraSource
            cameraSide = prefs.cameraSide
            applyHomeCameraStart()
            haCameras = haCamerasFromPrefs(prefs.haCameraEntities)
            cameraSound = prefs.cameraSound
            calendarConfig = prefs.calendarConfig()
            haDisplay = prefs.haDisplay
            haServices = prefs.haServices
            alertsTab = prefs.alertsTab
            badge = prefs.badge
            alertsDot = prefs.alertsDot
            homeOverrideChanged()
        }
        // The same camera, by its exact addresses, only takes the imported name. Each camera here
        // matches one in the file; any more copies in the file aren't added.
        val sameUrls = { a: Camera, b: Camera -> a.url == b.url && a.substreamUrl == b.substreamUrl }
        val renamed = mutableMapOf<String, String>()
        val new = mutableListOf<Camera>()
        file.cameras.forEach { camera ->
            val same = cameras.filter { sameUrls(it, camera) }
            val free = same.firstOrNull { it.id !in renamed }
            when {
                free != null -> renamed[free.id] = camera.name
                same.isEmpty() && new.none { sameUrls(it, camera) } -> new += camera
            }
        }
        val names = cameras.filter { it.id !in renamed }.map { it.name }.toMutableSet()
        fun uniqueName(wanted: String): String {
            var name = wanted
            while (name in names) name += " (imported)"
            names += name
            return name
        }
        val merged = cameras.map { camera -> renamed[camera.id]?.let { camera.copy(name = uniqueName(it)) } ?: camera } +
            new.map { it.copy(id = UUID.randomUUID().toString(), name = uniqueName(it.name)) }
        if (merged != cameras) saveCameras(merged)
        file.login?.let { server ->
            // A replaced username-and-password login is ended in Home Assistant
            if (haServer.refreshToken != server.refreshToken) revokeHaLogin(haServer)
            haServer = server
            prefs.haUrl = server.url
            prefs.haToken = server.token
            prefs.haRefreshToken = server.refreshToken
            prefs.haUsername = file.username
        }
        // Files from earlier versions: the sensors, then the actions, the first ones in the top bar
        val fileActions = file.actions.map { it.id }
        val imported = file.order ?: (file.sensors.map { it.id } + fileActions)
        val entities = (haEntities + imported).distinct()
        saveHaChoice(
            entities,
            haHomeEntities.filter { it in entities },
            (haTopBar + (file.topBar ?: fileActions.take(MAX_HA_ACTIONS))).distinct().filter { it in entities }.take(MAX_HA_ACTIONS),
        )
        file.frigate?.let(::saveFrigate)
        // The row becomes the apps in the file that are on this TV; the others can be unhidden later
        val apps = installed.orEmpty().map { it.key }
        val shown = file.appTiles.map { it.id }.filter { it in apps }
        if (shown.isNotEmpty()) {
            saveOrder(shown)
            saveHidden((hidden + apps).distinct() - shown.toSet())
        }
        Toast.makeText(this, "Settings imported", Toast.LENGTH_SHORT).show()
    }

    private fun saveHaChoice(entities: List<String>, homeEntities: List<String>, topBar: List<String>) {
        haEntities = entities
        haTopBar = topBar
        prefs.saveHaChoice(entities, homeEntities, topBar)
        haHomeEntities = prefs.haHomeEntities
    }

    private fun saveFrigate(value: FrigateServer) {
        frigate = value
        prefs.frigate = value
    }

    /**
     * Where the Home tab's camera starts: "First in the list" is the camera dialog's order, whatever
     * the remote last switched to, and "Last switched to" is that saved choice. Run at start, and
     * again when the row is changed: re-reading the pref alone changes nothing on screen.
     */
    private fun applyHomeCameraStart() {
        homeCameraStart = prefs.homeCameraStart
        if (homeCameraStart != HOME_CAMERA_FIRST) {
            selectedCamera = prefs.selectedCamera
            return
        }
        val first = if (cameraSource == CAMERAS_HOME_ASSISTANT) {
            haCameras.firstOrNull()?.let { "ha:${it.entityId}" }
        } else {
            prefs.cameras.firstOrNull()?.id
        }
        if (first != null) selectedCamera = first
    }

    private fun selectCamera(camera: Camera) {
        // From here the session shows what was asked for, not what the shuffle rolled
        cameraChosenManually = true
        selectedCamera = camera.id
        prefs.selectedCamera = camera.id
    }

    private fun saveCameras(value: List<Camera>) {
        cameras = value
        prefs.cameras = value
        pruneCameraSnapshots(this, value.flatMap { listOf(it.url, it.substreamUrl) })
    }

    private fun saveOrder(value: List<String>) {
        order = value
        prefs.order = value
    }

    private fun saveHidden(value: List<String>) {
        hidden = value
        prefs.hidden = value
    }

    private fun homeOverrideChanged() {
        prefs.replaceHomeAutoOff = false
        updateReplaceHome()
    }

    private fun updateReplaceHome() {
        replaceHome = prefs.replaceHome
        catchHomeKey = prefs.catchHomeKey
        reopenAfterHome = prefs.reopenAfterHome
        showFreeMemory = prefs.showFreeMemory
    }

    override fun onResume() {
        super.onResume()
        // The home override may have switched itself off meanwhile
        updateReplaceHome()
        window.decorView.postDelayed(confirmStarted, 2_000)
        // Refresh in the background (apps may have been installed or removed meanwhile);
        // the current row stays on screen until the new list is ready
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) { loadInstalledApps(this@MainActivity) }
            if (apps != installed) prefs.installedApps = apps
            installed = apps
        }
        // Shows watched meanwhile
        if (canReadWatchNext()) {
            lifecycleScope.launch {
                watchNext = withContext(Dispatchers.IO) { loadWatchNext(this@MainActivity) }
                watchNextReadAt = System.currentTimeMillis()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        window.decorView.removeCallbacks(confirmStarted)
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
    }

    override fun onStop() {
        super.onStop()
        // Hidden apps are only revealed until you leave the launcher
        showHidden = false
    }

    companion object {
        /** Started properly and still alive; a crash takes the whole process, so this resets */
        @Volatile
        var running = false
            private set
    }
}
