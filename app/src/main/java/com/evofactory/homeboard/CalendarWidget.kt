package com.evofactory.homeboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import java.time.LocalDate

/**
 * The Home tab's calendar, after the Calendar Pro card Ema uses on his dashboard: one block per
 * event, a coloured bar down its left side saying which calendar it comes from, the title, then the
 * time in smaller text on a line with a clock.
 *
 * It says nothing it cannot back up: no calendars chosen or a Home Assistant that can't answer leaves
 * the card out entirely, a connection still coming up says so, and an empty window says "Nothing
 * scheduled" unless the owner asked for silence ([CalendarConfig.hideEmpty]). The last events stay on
 * screen while the connection is down, dimmed.
 */
@Composable
fun CalendarCard(
    connection: HaConnection,
    config: CalendarConfig,
    onOpen: () -> Unit,
    upFocus: FocusRequester?,
    modifier: Modifier = Modifier,
    // True when the sensors card is hidden: the card takes the panel's height, so nothing is cut
    expanded: Boolean = false,
    focus: FocusRequester? = null,
) {
    if (config.entities.isEmpty() || connection.calendarsUnsupported) return
    val today = LocalDate.now()
    val events = mergeCalendarEvents(connection.calendarEvents, config, today)
    val connecting = connection.status == HaStatus.Connecting && !connection.calendarsLoaded
    val offline = connection.status == HaStatus.Offline
    if (events.isEmpty() && config.hideEmpty && !connecting && !offline) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .selectableTile(upFocus, onOpen)
            // Capped at three entries beside the sensors: a whole window of events must not squeeze
            // them out of the panel, and anything past the cap is cut, not scrolled
            .then(if (expanded) Modifier else Modifier.heightIn(max = CalendarCardHeight))
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            // Stale rather than gone: the events are the last ones read
            .alpha(if (offline) 0.6f else 1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            connecting && events.isEmpty() -> Detail("Connecting to Home Assistant…")
            events.isEmpty() -> Detail("Nothing scheduled in the next ${config.days} days")
            else -> events.forEach { event ->
                CalendarEntry(event = event, config = config, today = today)
            }
        }
    }
}

/** Three entries and their spacing: what the side panel can give up without losing the sensors */
private val CalendarCardHeight = 132.dp

/** One event: its calendar's colour, its title, and when it runs */
@Composable
private fun CalendarEntry(event: HaEvent, config: CalendarConfig, today: LocalDate) {
    val index = config.entities.indexOf(event.calendarId)
    val colour = colourOf(calendarColour(config.colours, event.calendarId) ?: CalendarPalette.getOrNull(index)?.hex())
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colour),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                event.summary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                MdiIcon("mdi:clock-outline", size = 13.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Detail(event.detailLine(today))
            }
        }
    }
}

/** A smaller, quieter line: the times, a heading, or a message with nothing to show */
@Composable
private fun Detail(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
