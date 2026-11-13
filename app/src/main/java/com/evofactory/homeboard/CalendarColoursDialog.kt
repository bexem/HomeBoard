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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.toArgb
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
            "OK chooses a calendar's colour. Home Assistant keeps no colour for a calendar, so this is the launcher's."
        },
        buttons = listOf(DialogButton("Done", primary = true, focus = done, onClick = onDismiss)),
        contentFocus = { rows.currentOrNull(chosen) ?: done },
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
            items(chosen, key = { it }) { id ->
                val hex = calendarColour(picked, id) ?: CalendarPalette.getOrNull(chosen.indexOf(id))?.hex()
                val name = CalendarPalette.firstOrNull { it.hex().equals(hex, ignoreCase = true) }?.name
                    ?: CalendarPalette.getOrNull(chosen.indexOf(id))?.name
                    ?: "Green"
                ListItem(
                    selected = false,
                    onClick = { menuFor = id },
                    headlineContent = { Text(names[id] ?: id, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("Colour: $name", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(colourOf(hex), CircleShape),
                        )
                    },
                    // A door: the colour itself is chosen in the launcher's one colour picker
                    modifier = rows.row(id).ringToTop(),
                )
            }
        }
        LaunchedEffect(chosen) { runCatching { (rows.currentOrNull(chosen) ?: done).requestFocus() } }
        menuFor?.let { id ->
            // The launcher's one colour picker, the same one the background uses
            val hex = calendarColour(picked, id) ?: CalendarPalette.getOrNull(chosen.indexOf(id))?.hex()
            val current = colourOf(hex).toArgb()
            ColourPickerDialog(
                title = names[id] ?: id,
                subtitle = "The bar beside this calendar's events; Home Assistant keeps no colour for a calendar, so this is the launcher's",
                current = current,
                onPick = { argb ->
                    choose(id, ColourEntry("", argb).hex())
                    menuFor = null
                },
                onDismiss = { menuFor = null },
            )
        }
    }
}

