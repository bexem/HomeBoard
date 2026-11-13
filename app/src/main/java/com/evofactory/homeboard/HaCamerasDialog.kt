package com.evofactory.homeboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Which Home Assistant cameras the launcher shows when the pictures come from Home Assistant, and in
 * what order. The list is read from Home Assistant when the dialog opens, not kept in the app: a
 * camera added there appears here with no rebuild. [EntityPickerDialog] does the choosing and the
 * ordering, so the order here is the order the card reads.
 */
@Composable
fun HaCamerasDialog(
    server: HaServer,
    chosen: List<HaCamera>,
    onChange: (List<HaCamera>) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(chosen) }
    // Every camera the server reports, by id: what can be picked
    var found by remember { mutableStateOf<List<HaEntity>?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(server) {
        if (!server.isSet) {
            found = emptyList()
            return@LaunchedEffect
        }
        try {
            found = haSession(server) {
                getStates().filter { it.domain == "camera" }.sortedBy { it.name.lowercase() }
            }
        } catch (e: HaException) {
            problem = e.message ?: "Home Assistant didn't answer"
            found = emptyList()
        }
    }

    val rows = remember(found) { found.orEmpty().map { PickerRow(it.id, it.name, it.id) } }
    // The name is stored with the id, so the launcher has it without reading the states again
    val names = remember(picked, rows) {
        picked.associate { it.entityId to it.name } + rows.associate { it.id to it.label }
    }

    EntityPickerDialog(
        title = "Home Assistant cameras",
        noun = "camera",
        rows = rows,
        chosen = remember(picked) { picked.map { it.entityId } },
        includeLabel = "Include it",
        excludeLabel = "Leave it out",
        positionLabel = { "Position $it" },
        emptyText = when {
            found == null -> "Reading the cameras from Home Assistant…"
            problem != null -> "No cameras to choose: $problem"
            rows.isEmpty() -> "Home Assistant reports no cameras."
            else -> "Nothing chosen: no camera shows until you pick one."
        },
        subtitle = { "$it cameras. Left and right move one up or down." },
        onChange = { ids ->
            val next = ids.map { HaCamera(it, names[it] ?: it) }
            picked = next
            onChange(next)
        },
        onDismiss = onDismiss,
    )
}
