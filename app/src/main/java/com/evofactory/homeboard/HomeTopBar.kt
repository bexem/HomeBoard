package com.evofactory.homeboard

import android.app.ActivityManager
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the screen above the apps shows. [color]: the remote's colour button that opens it, softened, for the open tab's underline. */
enum class HomeTab(val label: String, val color: Color) {
    /** The main camera and Home Assistant */
    Home("Home", Color(0xFFD9605A)),

    /** All the cameras' last pictures */
    Cameras("Cameras", Color(0xFF5FB36A)),

    /** The latest Frigate alerts */
    Alerts("Alerts", Color(0xFFD9B84A)),

    /** Every chosen Home Assistant sensor and action, in a card per room */
    Assistant("Assistant", Color(0xFF5A8FD9)),
}

/** The top bar: tabs on the left; free memory, action buttons, the settings cog and the time on the right. */
@Composable
fun HomeTopBar(
    tab: HomeTab,
    onTabChange: (HomeTab) -> Unit,
    secondTabFocus: FocusRequester,
    homeTabFocus: FocusRequester,
    alertsTabFocus: FocusRequester,
    assistantTabFocus: FocusRequester,
    onHomeTabSelected: (Boolean) -> Unit,
    showFreeMemory: Boolean,
    // Frigate events since the Alerts tab was last opened; 0 hides the badge
    newEvents: Int = 0,
    // OK on the badge: the alerts it counts are behind this
    onNewEvents: () -> Unit = {},
    // The count comes from notifications and every one it counts is ongoing: grey, like the other
    // buttons, because nothing there is news
    newEventsCalm: Boolean = false,
    // A dot on the Alerts tab while there are alerts nobody has looked at
    alertsDot: Boolean = false,
    // The tabs to show: Alerts can be hidden, and has no meaning without a Frigate server
    tabs: List<HomeTab> = HomeTab.entries,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    settings: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.height(SettingsButtonSize),
        horizontalArrangement = Arrangement.spacedBy(PanelItemSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { entry ->
            TabButton(
                entry.label,
                selected = entry == tab,
                underline = entry.color,
                dot = alertsDot && entry == HomeTab.Alerts,
                onSelect = { onTabChange(entry) },
                modifier = when (entry) {
                    HomeTab.Home -> Modifier.focusRequester(homeTabFocus).onFocusChanged { onHomeTabSelected(it.hasFocus) }
                    HomeTab.Cameras -> Modifier.focusRequester(secondTabFocus)
                    HomeTab.Alerts -> Modifier.focusRequester(alertsTabFocus)
                    HomeTab.Assistant -> Modifier.focusRequester(assistantTabFocus)
                },
            )
        }
        // Measured last, so the cog and the clock always keep their room
        Row(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(PanelItemSpacing, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showFreeMemory) FreeMemory()
            actions()
        }
        settings()
        Clock()
        if (newEvents > 0) NewEventsBadge(newEvents, newEventsCalm, onNewEvents)
    }
}

/**
 * A count of Frigate events since the Alerts tab was last opened: a red pill with its number, and the
 * way to them — OK opens the Alerts tab, which also clears the count.
 *
 * The TV has no notification panel of its own to open (the Shield's notifications live in an activity
 * of the system launcher that no other app may start), so the badge leads to the alerts it counts.
 */
@Composable
private fun NewEventsBadge(count: Int, calm: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.padding(start = 10.dp).fillMaxHeight(),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        // Stays its size when the remote is on it, so the bar doesn't shift
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        // Red says something arrived; grey says these are only the services' own, the same grey as
        // the buttons beside it, because nothing there is news
        colors = if (calm) {
            ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.onSurface,
                focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
            )
        } else {
            ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFFC62828),
                contentColor = Color.White,
                focusedContainerColor = Color.White,
                focusedContentColor = Color(0xFFC62828),
            )
        },
    ) {
        Box(Modifier.fillMaxHeight().padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(
                if (count > 99) "99+" else "$count",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

/**
 * A tab: the arrows only move the selection, OK opens it. The open tab is underlined, in [underline]
 * if given, else the text's colour; the one the remote is on gets a light background. A [secondary]
 * tab, in a row under others, is lighter and dimmer.
 */
@Composable
fun TabButton(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    underline: Color = Color.Unspecified,
    secondary: Boolean = false,
    /** A small dot at the tab's top end: something arrived that nobody has looked at */
    dot: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onSelect,
        modifier = modifier.fillMaxHeight(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        // Stays in place, so the underline doesn't move
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = when {
                secondary -> if (selected) colors.onSurfaceVariant else FaintText
                selected -> colors.onSurface
                else -> colors.onSurfaceVariant
            },
            focusedContainerColor = colors.onSurface,
            focusedContentColor = colors.inverseOnSurface,
        ),
    ) {
        // Else the text's color: it reverses with the tab when the remote is on it
        val lineColor = when {
            !selected -> Color.Transparent
            underline.isSpecified -> underline
            else -> LocalContentColor.current
        }
        // The text alone is centred, in line with the buttons on the right; the line hangs just below
        // it, drawn outside the layout so it doesn't push the text up
        Box(Modifier.fillMaxHeight().padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            // Inside the text's box, at its top end, so a tab does not grow for it
            if (dot) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 9.dp)
                        .size(7.dp)
                        // The tab's own content colour, which reverses when the remote is on it
                        .background(LocalContentColor.current, CircleShape),
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.titleMedium.let { if (secondary) it.copy(fontWeight = FontWeight.Normal) else it },
                // One line, always: a long dashboard name wrapped out of the bar and over the clock
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.drawBehind {
                    val thickness = TabUnderline.toPx()
                    drawRoundRect(
                        lineColor,
                        topLeft = Offset(0f, size.height + TabUnderlineGap.toPx()),
                        size = Size(size.width, thickness),
                        cornerRadius = CornerRadius(thickness / 2),
                    )
                },
            )
        }
    }
}

private val TabUnderline = 4.dp

// Between the text and the line
private val TabUnderlineGap = 2.dp

/** Something needs attention: a red pill, OK says more. [text] is short, it shares the bar with the action buttons. */
@Composable
fun TopBarWarning(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = ErrorColor.copy(alpha = 0.2f),
            contentColor = ErrorColor,
            focusedContainerColor = ErrorColor,
            focusedContentColor = Color.Black,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxHeight().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The text's color: a red dot would vanish on the red fill when selected
            Box(Modifier.size(10.dp).background(LocalContentColor.current, CircleShape))
            Text(
                text,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Memory left on the TV, updated every second; in red when it's running out. Display only. */
@Composable
private fun FreeMemory() {
    val context = LocalContext.current
    val free by produceState(0L) {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo()
        while (true) {
            activityManager.getMemoryInfo(info)
            value = info.availMem
            delay(MEMORY_CHECK_INTERVAL_MS)
        }
    }
    val megabytes = free / (1024 * 1024)
    val color = if (megabytes < LOW_MEMORY_MB) ErrorColor else FaintText
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (megabytes >= 1024) "%.1f GB".format(megabytes / 1024f) else "$megabytes MB",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
        )
        Text("free", style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

private const val MEMORY_CHECK_INTERVAL_MS = 1_000L
private const val LOW_MEMORY_MB = 300

/** The current time, dimmed, and under it the date in the TV's language; display only, the remote never lands on it */
@Composable
private fun Clock() {
    val context = LocalContext.current
    val timeFormat = DateFormat.getTimeFormat(context)
    val dateFormat = SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMd"), Locale.getDefault())
    val now by produceState(Date()) {
        while (true) {
            value = Date()
            // Wake up just after the next minute starts
            delay(60_000 - System.currentTimeMillis() % 60_000 + 50)
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(timeFormat.format(now), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(dateFormat.format(now), style = MaterialTheme.typography.labelMedium, color = FaintText, maxLines = 1)
    }
}
