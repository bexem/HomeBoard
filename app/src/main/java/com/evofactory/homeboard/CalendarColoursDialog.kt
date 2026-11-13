package com.evofactory.homeboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * The colour of each chosen calendar, one row per calendar in priority order. Left and right move
 * through the palette; the choice is stored per entity, so reordering the calendars doesn't change
 * their colours.
 *
 * Home Assistant keeps no colour for a calendar entity over its API, so this is the launcher's own
 * choice; a calendar nobody changes takes the palette colour for its position, which is what Home
 * Assistant's own calendar card does.
 */
@Composable
fun CalendarColoursDialog(
    server: HaServer,
    chosen: List<String>,
    colours: List<String>,
    onChange: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(colours) }
    // Friendly names, read from Home Assistant: calendars aren't followed by the panel
    var names by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(server) {
        if (!server.isSet || chosen.isEmpty()) return@LaunchedEffect
        runCatching {
            haSession(server) {
                getStates().filter { it.domain == "calendar" }.associate { it.id to it.name }
            }
        }.onSuccess { names = it }
    }
    val rows = remember { ListFocus() }
    val done = remember { FocusRequester() }

    fun step(id: String, delta: Int) {
        picked = cycleCalendarColour(picked, id, delta)
        onChange(picked)
    }

    fun choose(id: String, hex: String) {
        picked = picked.filterNot { it.substringBefore('=') == id } + "$id=$hex"
        onChange(picked)
    }

    // OK lists the palette, named, with the current colour ticked: left and right still cycle
    var menuFor by remember { mutableStateOf<String?>(null) }

    TopDialog(
        title = "Calendar colours",
        onDismiss = onDismiss,
        subtitle = if (chosen.isEmpty()) {
            "No calendars chosen yet: pick some under Calendars first."
        } else {
            "Left and right change a calendar's colour. Home Assistant keeps no colour for a calendar, so this is the launcher's."
        },
        buttons = listOf(DialogButton("Done", primary = true, focus = done, onClick = onDismiss)),
        contentFocus = { rows.currentOrNull(chosen) ?: done },
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
            items(chosen, key = { it }) { id ->
                val hex = calendarColour(picked, id) ?: CalendarPalette.getOrNull(chosen.indexOf(id))?.hex
                val name = CalendarPalette.firstOrNull { it.hex.equals(hex, ignoreCase = true) }?.name
                    ?: CalendarPalette.getOrNull(chosen.indexOf(id))?.name
                    ?: "Green"
                ListItem(
                    selected = false,
                    onClick = { menuFor = id },
                    headlineContent = { Text(names[id] ?: id) },
                    supportingContent = { Text("Colour: $name") },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(colourOf(hex), CircleShape),
                        )
                    },
                    modifier = rows.row(id).onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) {
                            false
                        } else {
                            when (event.key) {
                                Key.DirectionLeft -> { step(id, -1); true }
                                Key.DirectionRight -> { step(id, 1); true }
                                else -> false
                            }
                        }
                    },
                )
            }
        }
        LaunchedEffect(chosen) { runCatching { (rows.currentOrNull(chosen) ?: done).requestFocus() } }
        menuFor?.let { id ->
            val current = calendarColour(picked, id)
            ContextMenu(
                title = names[id] ?: id,
                actions = CalendarPalette.map { entry ->
                    MenuAction(if (entry.hex.equals(current, true)) "${entry.name} ✓" else entry.name) {
                        choose(id, entry.hex)
                        menuFor = null
                    }
                },
                onDismiss = { menuFor = null },
            )
        }
    }
}

/** "#RRGGBB" from the palette, or the palette's first colour when nothing is chosen */
fun colourOf(hex: String?): Color {
    val text = hex?.removePrefix("#") ?: return Color(0xFF7CB342)
    val value = runCatching { text.toLong(16) }.getOrNull() ?: return Color(0xFF7CB342)
    return Color(0xFF000000 or value)
}
