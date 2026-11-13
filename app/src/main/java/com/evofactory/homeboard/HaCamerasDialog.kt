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
 * Which Home Assistant cameras the launcher shows when the pictures come from Home Assistant, and in
 * what order. The list is read when the dialog opens, so a camera added there needs no rebuild.
 *
 * The list is read from Home Assistant when the dialog opens, not kept in the app: a calendar added
 * there appears here with no rebuild. OK chooses; left and right on a chosen calendar move it along,
 * so the order here is the order the card reads.
 */
@Composable
fun HaCamerasDialog(
    server: HaServer,
    chosen: List<HaCamera>,
    onChange: (List<HaCamera>) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(chosen) }
    val pickedIds = remember(picked) { picked.map { it.entityId } }
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
                getStates().filter { it.domain == "camera" }.sortedBy { it.name.lowercase() }
            }
        } catch (e: HaException) {
            problem = e.message ?: "Home Assistant didn't answer"
            found = emptyList()
        }
    }

    val cameras = found.orEmpty()
    val keys = remember(cameras) { cameras.map { it.id } }

    fun save(list: List<HaCamera>) {
        picked = list
        onChange(list)
    }

    fun toggle(camera: HaEntity) {
        save(
            if (camera.id in pickedIds) {
                picked.filterNot { it.entityId == camera.id }
            } else {
                picked + HaCamera(camera.id, camera.name)
            },
        )
    }

    fun move(id: String, delta: Int) {
        val from = picked.indexOfFirst { it.entityId == id }
        val to = from + delta
        if (from < 0 || to !in picked.indices) return
        save(picked.toMutableList().also { it[from] = picked[to]; it[to] = picked[from] })
    }

    TopDialog(
        title = "Home Assistant cameras",
        onDismiss = onDismiss,
        subtitle = when {
            problem != null -> problem
            found == null -> "Reading the cameras from Home Assistant…"
            picked.isEmpty() -> "Nothing chosen: no camera shows until you pick one."
            picked.size == 1 -> "One camera. OK chooses more; left and right set the order."
            else -> "${picked.size} cameras. Left and right move one up or down."
        },
        buttons = listOf(DialogButton("Done", primary = true, focus = done, onClick = onDismiss)),
        contentFocus = { rows.currentOrNull(keys) ?: done },
    ) {
        when {
            found == null -> Text(
                "Reading the cameras from Home Assistant…",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp),
            )
            cameras.isEmpty() -> Text(
                if (problem != null) {
                    "No cameras to choose: $problem"
                } else {
                    "Home Assistant reports no cameras."
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp),
            )
        }
        // Shorter than the dialog is tall, or the last row falls off its bottom edge
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
            items(cameras, key = { it.id }) { camera ->
                val isPicked = camera.id in pickedIds
                val position = pickedIds.indexOf(camera.id)
                ListItem(
                    selected = false,
                    onClick = { toggle(camera) },
                    headlineContent = {
                        Text(camera.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text(if (position >= 0) "Camera ${position + 1} of ${picked.size}" else camera.id)
                    },
                    leadingContent = {
                        Checkbox(
                            checked = isPicked,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(
                                checkedColor = CameraPickedBox,
                                uncheckedColor = CameraEmptyBox,
                            ),
                        )
                    },
                    modifier = rows.row(camera.id).onPreviewKeyEvent { event ->
                        // Left and right move a chosen calendar; anything else is the list's own
                        if (!isPicked || event.type != KeyEventType.KeyDown) {
                            false
                        } else {
                            when (event.key) {
                                Key.DirectionLeft -> { move(camera.id, -1); true }
                                Key.DirectionRight -> { move(camera.id, 1); true }
                                else -> false
                            }
                        }
                    },
                )
            }
        }
        LaunchedEffect(keys) { runCatching { (rows.currentOrNull(keys) ?: done).requestFocus() } }
    }
}

private val CameraPickedBox = androidx.compose.ui.graphics.Color(0xFF5A595F)
private val CameraEmptyBox = androidx.compose.ui.graphics.Color(0xFF8E8C92)
