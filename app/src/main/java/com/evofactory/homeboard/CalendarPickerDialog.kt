package com.evofactory.homeboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Checkbox
import androidx.tv.material3.CheckboxDefaults
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * Which Home Assistant calendars the Home tab shows, and in what order.
 *
 * The list is read from Home Assistant when the dialog opens, not kept in the app: a calendar added
 * there appears here with no rebuild. OK chooses; left and right on a chosen calendar move it along,
 * so the order here is the order the card reads.
 */
@Composable
fun CalendarPickerDialog(
    server: HaServer,
    chosen: List<String>,
    onChange: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(chosen) }
    // The calendar whose menu is open: OK offers what can be done with it, rather than guessing
    var menuFor by remember { mutableStateOf<String?>(null) }
    // Every calendar the server reports, by id: what can be picked
    var found by remember { mutableStateOf<List<HaEntity>?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    val rows = remember { ListFocus() }
    val done = remember { FocusRequester() }

    LaunchedEffect(server) {
        if (!server.isSet) {
            found = emptyList()
            return@LaunchedEffect
        }
        try {
            found = haSession(server) {
                getStates().filter { it.domain == "calendar" }.sortedBy { it.name.lowercase() }
            }
        } catch (e: HaException) {
            problem = e.message ?: "Home Assistant didn't answer"
            found = emptyList()
        }
    }

    val calendars = found.orEmpty()
    // The server's order throughout: choosing must not move the row out from under the remote.
    // Chosen ones are numbered instead, and left and right reorder them.
    val keys = remember(calendars) { calendars.map { it.id } }

    fun save(list: List<String>) {
        picked = list
        onChange(list)
    }

    fun move(id: String, delta: Int) {
        val from = picked.indexOf(id)
        val to = from + delta
        if (from < 0 || to !in picked.indices) return
        save(picked.toMutableList().also { it[from] = picked[to]; it[to] = picked[from] })
    }

    TopDialog(
        title = "Calendars",
        onDismiss = onDismiss,
        subtitle = when {
            problem != null -> problem
            found == null -> "Reading the calendars from Home Assistant…"
            picked.isEmpty() -> "Nothing chosen: the Home tab shows no calendar card."
            picked.size == 1 -> "One calendar. OK chooses more; left and right set priority."
            else -> "${picked.size} calendars, highest priority first."
        },
        buttons = listOf(DialogButton("Done", primary = true, focus = done, onClick = onDismiss)),
        contentFocus = { rows.currentOrNull(keys) ?: done },
    ) {
        when {
            found == null -> Text(
                "Reading the calendars from Home Assistant…",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp),
            )
            calendars.isEmpty() -> Text(
                if (problem != null) {
                    "No calendars to choose: $problem"
                } else {
                    "Home Assistant reports no calendars. Add one there, or turn on its local calendar."
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp),
            )
        }
        // Shorter than the dialog is tall, or the last row falls off its bottom edge
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
            items(calendars, key = { it.id }) { calendar ->
                val isPicked = calendar.id in picked
                val position = picked.indexOf(calendar.id)
                ListItem(
                    selected = false,
                    onClick = { menuFor = calendar.id },
                    headlineContent = {
                        Text(calendar.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text(if (position >= 0) "Priority ${position + 1} of ${picked.size}" else calendar.id)
                    },
                    leadingContent = {
                        Checkbox(
                            checked = isPicked,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(
                                checkedColor = CalendarPickedBox,
                                uncheckedColor = CalendarEmptyBox,
                            ),
                        )
                    },
                    modifier = rows.row(calendar.id).onPreviewKeyEvent { event ->
                        // Left and right move a chosen calendar; anything else is the list's own
                        if (!isPicked || event.type != KeyEventType.KeyDown) {
                            false
                        } else {
                            when (event.key) {
                                Key.DirectionLeft -> { move(calendar.id, -1); true }
                                Key.DirectionRight -> { move(calendar.id, 1); true }
                                else -> false
                            }
                        }
                    },
                )
            }
        }
        LaunchedEffect(keys) { runCatching { (rows.currentOrNull(keys) ?: done).requestFocus() } }
        // What OK does with a calendar: include it, leave it out, or move it in the priority list
        menuFor?.let { id ->
            val isPicked = id in picked
            val position = picked.indexOf(id)
            ContextMenu(
                title = calendars.firstOrNull { it.id == id }?.name,
                actions = buildList {
                    add(
                        MenuAction(if (isPicked) "Leave it out" else "Include it") {
                            save(if (isPicked) picked - id else picked + id)
                            menuFor = null
                        },
                    )
                    if (isPicked) {
                        if (position > 0) add(MenuAction("Move up (Priority $position now)") { move(id, -1); menuFor = null })
                        if (position < picked.lastIndex) {
                            add(MenuAction("Move down (Priority ${position + 2} now)") { move(id, 1); menuFor = null })
                        }
                    }
                },
                onDismiss = { menuFor = null },
            )
        }
    }
}

private val CalendarPickedBox = androidx.compose.ui.graphics.Color(0xFF5A595F)
private val CalendarEmptyBox = androidx.compose.ui.graphics.Color(0xFF8E8C92)
