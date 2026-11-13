package com.evofactory.homeboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Which Home Assistant calendars the Home tab shows, and in what order.
 *
 * The list is read from Home Assistant when the dialog opens, not kept in the app: a calendar added
 * there appears here with no rebuild. [EntityPickerDialog] does the choosing and the ordering, so the
 * order here is the order the card reads.
 */
@Composable
fun CalendarPickerDialog(
    server: HaServer,
    chosen: List<String>,
    onChange: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(chosen) }
    // Every calendar the server reports, by id: what can be picked
    var found by remember { mutableStateOf<List<HaEntity>?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }

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

    // The server's order throughout: choosing must not move the row out from under the remote
    val rows = remember(found) { found.orEmpty().map { PickerRow(it.id, it.name, it.id) } }

    EntityPickerDialog(
        title = "Calendars",
        noun = "calendar",
        rows = rows,
        chosen = picked,
        includeLabel = "Include it",
        excludeLabel = "Leave it out",
        positionLabel = { "Priority $it" },
        emptyText = when {
            found == null -> "Reading the calendars from Home Assistant…"
            problem != null -> "No calendars to choose: $problem"
            rows.isEmpty() -> "Home Assistant reports no calendars. Add one there, or turn on its local calendar."
            else -> "Nothing chosen: the Home tab shows no calendar card."
        },
        subtitle = { "$it calendars, highest priority first." },
        onChange = { picked = it; onChange(it) },
        onDismiss = onDismiss,
    )
}
