package com.evofactory.homeboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text

/**
 * Settings as a page rather than a dialog, the shape a desktop browser uses: the categories down the
 * left, a search field across the top, and the chosen category's settings as cards on the right.
 *
 * Every row still comes from [AllSettings] — this file decides only how a row looks and where it sits.
 * A category is a [SettingSection] (with its icon), and the cards inside a category are the rows'
 * [SettingDef.group]s, so a new setting stays one entry in the schema.
 *
 * The focus model is the part that matters on a remote: the remote lands on the category rail, Right
 * or OK enters the page at its first row, Up from the first row comes back to the rail, and Back closes
 * the page from anywhere. Sub-dialogs (cameras, calendars, favourites) open over the page as before.
 */
@Composable
fun SettingsPage(
    prefs: LauncherPrefs,
    actions: SettingsActions,
    revision: Int,
    onSettingsChanged: () -> Unit,
    onDismiss: () -> Unit,
    onOpenAccessibility: () -> Unit,
) {
    var section by remember { mutableStateOf(SettingSection.Home) }
    var showingAbout by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    // Bumped by every write, so the rows read their values again
    var writes by remember { mutableIntStateOf(0) }
    var pendingConsent by remember { mutableStateOf<(() -> Unit)?>(null) }
    // Bumped by every category pick: the first row cannot be focused until the page is laid out
    var focusRowRequest by remember { mutableIntStateOf(0) }
    val version = revision + writes
    val rows = remember { ListFocus() }
    val railRequests = remember { mutableMapOf<SettingSection, FocusRequester>() }
    val railAbout = remember { FocusRequester() }
    val search = remember { FocusRequester() }
    val contentFirst = remember { FocusRequester() }

    fun rail(section_: SettingSection) = railRequests.getOrPut(section_) { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Back && event.type == KeyEventType.KeyUp) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Rail(
                section = section,
                about = showingAbout,
                aboutFocus = railAbout,
                onAbout = {
                    showingAbout = true
                    focusRowRequest++
                },
                onPick = { picked ->
                    showingAbout = false
                    if (picked == section) {
                        // Picking the open category again still enters it
                        focusRowRequest++
                    } else {
                        section = picked
                        query = ""
                        rows.reset()
                        focusRowRequest++
                    }
                },
                rail = ::rail,
                onDismiss = onDismiss,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 28.dp, end = 36.dp, top = 18.dp, bottom = 18.dp),
            ) {
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    focus = search,
                    railFocus = rail(section),
                    contentFocus = contentFirst,
                    onDone = { runCatching { contentFirst.requestFocus() } },
                )
                Spacer(Modifier.height(14.dp))
                if (showingAbout) {
                    AboutPage(
                        prefs = prefs,
                        first = contentFirst,
                        toRail = railAbout,
                        toSearch = search,
                    )
                } else if (query.isBlank()) {
                    SectionPage(
                        section = section,
                        prefs = prefs,
                        version = version,
                        rows = rows,
                        first = contentFirst,
                        onOpenAccessibility = onOpenAccessibility,
                        toRail = rail(section),
                        toSearch = search,
                        onWrite = { writes++; onSettingsChanged() },
                        onAction = actions,
                        requestConsent = { option ->
                            if (prefs.homeOverrideAccepted) option() else pendingConsent = option
                        },
                    )
                } else {
                    SearchResults(
                        query = query,
                        prefs = prefs,
                        version = version,
                        rows = rows,
                        // The first hit is what Down from the field lands on, as on a category page
                        first = contentFirst,
                        toRail = rail(section),
                        toSearch = search,
                        onWrite = { writes++; onSettingsChanged() },
                        onAction = actions,
                        requestConsent = { option ->
                            if (prefs.homeOverrideAccepted) option() else pendingConsent = option
                        },
                    )
                }
            }
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
    }
    // Opens on the rail of the category the page starts at
    LaunchedEffect(Unit) { runCatching { rail(section).requestFocus() } }
    // A pick, by OK or by Right, lands on the page's first row — never on the search field. The row
    // does not exist until the new list has been composed and laid out, and one frame is not always
    // enough, so this keeps asking for a few frames instead of losing the remote to the rail
    LaunchedEffect(focusRowRequest) {
        if (focusRowRequest == 0) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        runCatching { contentFirst.requestFocus() }
    }
}

/** The categories, with their icons, and the way out at the bottom */
@Composable
private fun Rail(
    section: SettingSection,
    about: Boolean,
    aboutFocus: FocusRequester,
    onAbout: () -> Unit,
    onPick: (SettingSection) -> Unit,
    rail: (SettingSection) -> FocusRequester,
    onDismiss: () -> Unit,
) {
    val closeFocus = remember { FocusRequester() }
    Column(
        modifier = Modifier
            .width(248.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp, bottom = 12.dp),
        ) {
            MdiIcon("mdi:cog", size = 24.dp)
            Spacer(Modifier.width(12.dp))
            Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        SettingSection.entries.forEachIndexed { index, entry ->
            RailItem(
                label = entry.label,
                icon = sectionIcon(entry),
                // Frigate's own mark: a shield was standing in for a product that has a logo
                logo = if (entry == SettingSection.Frigate) R.drawable.frigate_logo else null,
                selected = !about && entry == section,
                onClick = { onPick(entry) },
                modifier = Modifier
                    .focusRequester(rail(entry))
                    // Up from the first entry stays there rather than leaving the rail
                    .then(
                        if (index == 0) {
                            Modifier.focusProperties { up = rail(entry) }
                        } else {
                            Modifier
                        },
                    ),
                // Right enters the page too, as a two-pane layout should
                onRight = { onPick(entry) },
            )
        }
        Spacer(Modifier.weight(1f))
        RailItem(
            label = "About",
            icon = "mdi:information-outline",
            selected = about,
            onClick = onAbout,
            modifier = Modifier
                .focusRequester(aboutFocus)
                // Geometry alone sends Up to the search field from here; this points it at Advanced
                .focusProperties {
                    up = rail(SettingSection.entries.last())
                    down = closeFocus
                },
        )
        RailItem(
            label = "Close settings",
            icon = "mdi:close",
            selected = false,
            onClick = onDismiss,
            modifier = Modifier
                .focusRequester(closeFocus)
                // The last entry: Down keeps the selection here instead of losing it
                .focusProperties {
                    up = aboutFocus
                    down = closeFocus
                },
        )
    }
}

/** One category: icon, name, and the highlight of the page you are on */
@Composable
private fun RailItem(
    label: String,
    icon: String,
    // A brand mark from the app's own drawables, drawn instead of the icon font's glyph
    logo: Int? = null,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRight: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .background(
                when {
                    focused -> MaterialTheme.colorScheme.inverseSurface
                    selected -> MaterialTheme.colorScheme.surfaceVariant
                    else -> Color.Transparent
                },
                RoundedCornerShape(10.dp),
            )
            .onPreviewKeyEvent { event ->
                when {
                    event.key in PageOkKeys && event.type == KeyEventType.KeyUp -> { onClick(); true }
                    event.key == Key.DirectionRight && onRight != null -> { onRight(); true }
                    else -> false
                }
            }
            .focusable()
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (logo != null) {
            Icon(
                painter = painterResource(logo),
                contentDescription = null,
                // Tinted like every other rail icon: it is Frigate's own mark, not a badge
                tint = if (focused) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
        } else {
            MdiIcon(icon, size = 20.dp)
        }
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = if (focused) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The category's settings: one lazy item per row, not one per card.
 *
 * A card was a single lazy item holding all its rows, so a group taller than the window could not
 * scroll: focus moved to a row below the fold and the page had no reason to move, which is what "the
 * cursor disappears if you go down too much" was. Each row is its own item now, and the card is drawn
 * per row (rounded at its ends), so the list always scrolls the focused row into view.
 */
@Composable
private fun SectionPage(
    section: SettingSection,
    prefs: LauncherPrefs,
    version: Int,
    rows: ListFocus,
    first: FocusRequester,
    onOpenAccessibility: () -> Unit,
    toRail: FocusRequester,
    toSearch: FocusRequester,
    onWrite: () -> Unit,
    onAction: SettingsActions,
    requestConsent: (() -> Unit) -> Unit,
) {
    // A row that means nothing right now is not shown: a colour when no colour is used, and so on
    val settings = settingsIn(section).filter { it.showIf?.invoke(prefs) ?: true }
    val groups = remember(section, settings.size) { settings.groupBy { it.group } }
    val firstKey = settings.firstOrNull()?.key
    // The row the page ends with on screen, not in the schema: cards are drawn in the order their
    // first row appears, so the schema's last row can sit in a card halfway up (Favourite apps did,
    // and its pinned Down cancelled the only way to the cards below it)
    val lastKey = groups.values.lastOrNull()?.lastOrNull()?.key
    val cardColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    // One scroll state per category: a new page starts at its top, where its first row is
    key(section) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberLazyListState(),
    ) {
        groups.forEach { (group, groupSettings) ->
            item(key = "heading $section $group") {
                Text(
                    group,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 8.dp),
                )
            }
            items(groupSettings.size, key = { "row ${groupSettings[it].key}" }) { index ->
                val setting = groupSettings[index]
                val isFirst = setting.key == firstKey
                val shape = RoundedCornerShape(
                    topStart = if (index == 0) 12.dp else 0.dp,
                    topEnd = if (index == 0) 12.dp else 0.dp,
                    bottomStart = if (index == groupSettings.lastIndex) 12.dp else 0.dp,
                    bottomEnd = if (index == groupSettings.lastIndex) 12.dp else 0.dp,
                )
                Column(modifier = Modifier.fillMaxWidth().background(cardColor, shape)) {
                    SettingPageRow(
                        setting = setting,
                        prefs = prefs,
                        value = version.let { setting.read(prefs) },
                        onWrite = onWrite,
                        onAction = onAction,
                        requestConsent = requestConsent,
                        modifier = Modifier
                            .focusRequester(if (setting.key == firstKey) first else rows.requester(setting.key))
                            // Left always returns to the rail; up from the first row reaches the search
                            .focusProperties {
                                // Only Left and Up leave a row: Right and Down would find nothing and
                                // drop the remote somewhere invisible
                                left = toRail
                                right = FocusRequester.Cancel
                                if (isFirst) up = toSearch
                                if (setting.key == lastKey) down = FocusRequester.Cancel
                            },
                    )
                    if (index != groupSettings.lastIndex) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp)
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                        )
                    }
                }
            }
        }
        if (section == SettingSection.Advanced) {
            item(key = "about") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LogSize()
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        item(key = "bottom room") { Spacer(Modifier.height(12.dp)) }
    }
    }
}

/**
 * A settings row: what it is on the left, its control on the right — a switch, a count, the chosen
 * value, or a door to another screen. Left and right change a count; OK works everything else.
 */
@Composable
private fun SettingPageRow(
    setting: SettingDef,
    prefs: LauncherPrefs,
    value: Any,
    onWrite: () -> Unit,
    onAction: SettingsActions,
    requestConsent: (() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    var choiceOpen by remember { mutableStateOf(false) }
    val number = value as? Int ?: 0
    val step = { delta: Int ->
        setting.number?.let {
            it.set(prefs, (number + delta).coerceIn(setting.range.first, setting.range.last))
            onWrite()
        }
    }
    val open = when (setting.kind) {
        SettingKind.Choice -> { { choiceOpen = true } }
        SettingKind.Action, SettingKind.List -> { { setting.action?.let(onAction::of)?.invoke() } }
        else -> null
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .background(
                if (focused) MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.10f) else Color.Transparent,
                RoundedCornerShape(10.dp),
            )
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (setting.kind) {
                    SettingKind.Stepper -> when (event.key) {
                        Key.DirectionLeft -> { step(-1); true }
                        Key.DirectionRight -> { step(1); true }
                        else -> false
                    }
                    else -> false
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.key in PageOkKeys && event.type == KeyEventType.KeyUp) open?.invoke() ?: onToggle(setting, prefs, value, onWrite, requestConsent)
                event.key in PageOkKeys
            }
            .focusable()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(setting.title, style = MaterialTheme.typography.titleSmall)
            if (setting.description.isNotEmpty()) {
                Text(
                    setting.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(18.dp))
        when (setting.kind) {
            SettingKind.Toggle -> PageSwitch(value as? Boolean ?: false)
            SettingKind.Stepper -> Row(verticalAlignment = Alignment.CenterVertically) {
                PageStep("−", enabled = number > setting.range.first) { step(-1) }
                Text(
                    "$number",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(44.dp),
                )
                PageStep("+", enabled = number < setting.range.last) { step(1) }
            }
            SettingKind.Choice -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    setting.choices.firstOrNull { it.first == value }?.second ?: value.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                MdiIcon("mdi:chevron-right", size = 20.dp)
            }
            SettingKind.Action, SettingKind.List -> {
                val summary = rowSummary(setting, prefs)
                if (summary.isNotEmpty()) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                MdiIcon("mdi:chevron-right", size = 20.dp)
            }
        }
    }
    if (choiceOpen) {
        ContextMenu(
            title = setting.title,
            actions = setting.choices.map { (choice, name) ->
                MenuAction(if (choice == value) "$name ✓" else name) {
                    setting.text?.set(prefs, choice)
                    onWrite()
                    choiceOpen = false
                }
            },
            onDismiss = { choiceOpen = false },
        )
    }
}

/** The home helper's one-line state, for its row */
private fun helperSummary(prefs: LauncherPrefs): String = when {
    !(prefs.catchHomeKey || prefs.reopenAfterHome) -> "Off"
    HomeBoardApp.instance.isDefaultHomeApp() -> "Not needed: this is the TV's home app"
    else -> "Asked for"
}

/** What sits behind a door: "6 cameras", "4 chosen", or nothing when there is nothing to say */
private fun rowSummary(setting: SettingDef, prefs: LauncherPrefs): String {
    if (setting.kind == SettingKind.List) {
        val count = setting.strings?.get(prefs)?.size ?: 0
        return if (count == 0) "None chosen" else "$count chosen"
    }
    return when (setting.key) {
        "cameras" -> if (prefs.cameras.isEmpty()) "None yet" else "${prefs.cameras.size} cameras"
        // A server's own door says whether it is set up, and where
        "home_assistant" -> prefs.haUrl.ifEmpty { "Not set up" }
        "frigate" -> prefs.frigate.url.ifEmpty { "Not set up" }
        "assistant_display", "home_display" -> {
            // Null is the chosen entities; "" is Home Assistant's default dashboard, "Overview"
            val dashboard = if (setting.key == "assistant_display") prefs.haDashboard else prefs.haHomeDashboard
            when {
                dashboard == null -> "My selection"
                dashboard.isEmpty() -> "Home Assistant's own"
                else -> dashboard
            }
        }
        "ha_entities" -> "${prefs.haEntities.count { !isHaAction(it) }} chosen"
        "ha_home_entities" -> {
            val count = prefs.haHomeEntities.count { !isHaAction(it) }
            if (count == 0) "Same as the tab" else "$count beside the camera"
        }
        "ha_top_bar" -> "${prefs.haTopBar.size} of $MAX_HA_ACTIONS"
        "ha_order" -> "${prefs.haEntities.size} on the tab"
        "notifications" -> "${Notifications.entries.size} showing"
        "notifications_access" -> if (Notifications.granted || Notifications.allowed) "Allowed" else "Not allowed"
        "ha_address" -> prefs.haUrl.ifEmpty { "No address" }
        "ha_login" -> if (prefs.haUrl.isEmpty()) "Set the address first" else if (prefs.haRefreshToken.isNotEmpty() || prefs.haToken.isNotEmpty()) "Signed in" else "Not signed in"
        "home_helper" -> helperSummary(prefs)
        else -> ""
    }
}

/** OK on a row that holds a value of its own: a switch flips, a door opens, the rest do nothing */
private fun onToggle(
    setting: SettingDef,
    prefs: LauncherPrefs,
    value: Any,
    onWrite: () -> Unit,
    requestConsent: (() -> Unit) -> Unit,
) {
    if (setting.kind != SettingKind.Toggle) return
    val property = setting.boolean ?: return
    val turnOn = !(value as? Boolean ?: false)
    val apply = {
        property.set(prefs, turnOn)
        onWrite()
    }
    if (turnOn && setting.requiresConsent) requestConsent(apply) else apply()
}

/** The switch a row shows: drawn, not focusable — the row around it owns the keys */
@Composable
private fun PageSwitch(checked: Boolean) {
    val track = if (checked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f) else Color.Transparent
    val rim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    Box(
        modifier = Modifier
            .width(52.dp)
            .height(28.dp)
            .background(track, CircleShape)
            .then(if (checked) Modifier else Modifier.border(1.dp, rim, CircleShape)),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 3.dp)
                .size(22.dp)
                .background(MaterialTheme.colorScheme.onSurface, CircleShape),
        )
    }
}

/** The − and + of a count: shapes only, the row takes the keys */
@Composable
private fun PageStep(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .border(
                1.dp,
                if (enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
        )
    }
}

/** "Search settings": typing filters every row, wherever it lives */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focus: FocusRequester,
    railFocus: FocusRequester,
    contentFocus: FocusRequester,
    onDone: () -> Unit,
) {
    TvTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = "Search settings",
        keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
        imeAction = ImeAction.Search,
        onImeAction = onDone,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .focusProperties {
                left = railFocus
                down = contentFocus
                // Nothing is above or to the right of the field: without these the focus can vanish
                up = focus
                right = focus
            },
    )
}

/** Every row whose title or description matches, with the category beside it */
@Composable
private fun SearchResults(
    query: String,
    prefs: LauncherPrefs,
    version: Int,
    rows: ListFocus,
    first: FocusRequester,
    toRail: FocusRequester,
    toSearch: FocusRequester,
    onWrite: () -> Unit,
    onAction: SettingsActions,
    requestConsent: (() -> Unit) -> Unit,
) {
    val found = remember(query, version) {
        AllSettings.filter { matchesQuery(it, query) }
            .filter { it.showIf?.invoke(prefs) ?: true }
    }
    if (found.isEmpty()) {
        Text(
            "Nothing matches \"$query\"",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        state = rememberPageState(),
    ) {
        items(found.size, key = { "hit ${found[it].key}" }) { index ->
            val setting = found[index]
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    setting.section.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(104.dp).padding(top = 16.dp),
                )
                SettingPageRow(
                    setting = setting,
                    prefs = prefs,
                    value = version.let { setting.read(prefs) },
                    onWrite = onWrite,
                    onAction = onAction,
                    requestConsent = requestConsent,
                    modifier = Modifier
                        .weight(1f)
                        // One requester per row, or two on the same node leave the first
                        // uninitialised: Down from the search field went nowhere
                        .then(
                            if (index == 0) {
                                Modifier.focusRequester(first)
                            } else {
                                Modifier.focusRequester(rows.requester("hit ${setting.key}"))
                            },
                        )
                        .focusProperties {
                            left = toRail
                            right = FocusRequester.Cancel
                            if (index == 0) up = toSearch
                            if (index == found.lastIndex) down = FocusRequester.Cancel
                        },
                )
            }
        }
    }
}

/** Matches on title and description, every word somewhere in the text */
internal fun matchesQuery(setting: SettingDef, query: String): Boolean {
    val text = (setting.title + " " + setting.description).lowercase()
    return query.trim().lowercase().split(' ').filter { it.isNotEmpty() }.all { it in text }
}

/**
 * About: the launcher's name, version and build, the device it runs on, and how much log it keeps.
 * The brand line is not a setting, so it has its own page rather than a row nobody edits.
 */
@Composable
private fun AboutPage(
    prefs: LauncherPrefs,
    first: FocusRequester,
    toRail: FocusRequester,
    toSearch: FocusRequester,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val built = remember { java.util.Date(BuildConfig.BUILD_TIME) }
    val dateFormat = remember {
        android.text.format.DateFormat.getMediumDateFormat(context)
    }
    val timeFormat = remember { android.text.format.DateFormat.getTimeFormat(context) }
    val device = remember { "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim() }
    val facts = listOf(
        "Version" to "${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
        "Built" to "${dateFormat.format(built)}, ${timeFormat.format(built)}",
        "Package" to context.packageName,
        "Device" to device,
        "Android" to android.os.Build.VERSION.RELEASE,
        "Cameras" to if (prefs.cameras.isEmpty()) "none set up" else "${prefs.cameras.size}",
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberLazyListState(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "about title") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(first)
                    .focusProperties {
                        left = toRail
                        right = FocusRequester.Cancel
                        up = toRail
                    }
                    .focusable()
                    .padding(start = 6.dp, top = 4.dp, bottom = 10.dp),
            ) {
                MdiIcon("mdi:television-classic", size = 34.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("HomeBoard", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "A launcher for the TV: the house, the cameras and the apps on one screen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item(key = "about facts") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
            ) {
                facts.forEachIndexed { index, (name, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(140.dp))
                        Text(
                            value,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (index != facts.lastIndex) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp)
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                        )
                    }
                }
            }
        }
        item(key = "about log") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LogSize()
            }
        }
        item(key = "about room") { Spacer(Modifier.height(12.dp)) }
    }
}

/** The icon a category shows in the rail */
internal fun sectionIcon(section: SettingSection): String = when (section) {
    SettingSection.Launcher -> "mdi:home-outline"
    SettingSection.Home -> "mdi:view-dashboard-outline"
    SettingSection.Assistant -> "mdi:assistant"
    SettingSection.Calendar -> "mdi:calendar-month-outline"
    SettingSection.Cameras -> "mdi:cctv"
    SettingSection.HomeAssistant -> "mdi:home-assistant"
    SettingSection.Frigate -> "mdi:shield-alert"
    SettingSection.Advanced -> "mdi:cog-outline"
}

private val PageOkKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/** One scroll state for a page: kept while the page is open, dropped when it closes */
@Composable
private fun rememberPageState() = androidx.compose.foundation.lazy.rememberLazyListState()
