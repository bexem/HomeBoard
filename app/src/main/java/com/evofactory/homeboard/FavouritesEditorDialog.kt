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
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Checkbox
import androidx.tv.material3.CheckboxDefaults
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * Which apps the row shows, in what order. Nothing pinned means every app, as before; the first
 * pinned app becomes the first tile.
 *
 * OK pins and unpins. On a pinned row, left and right move it along the row, so the order here is the
 * order on the Home tab. The row's own Move mode reorders the same list afterwards, so there is one
 * arrangement to keep, wherever it was changed.
 */
@Composable
fun FavouritesEditorDialog(
    apps: List<InstalledApp>,
    favourites: List<String>,
    onChange: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var pinned by remember { mutableStateOf(favourites) }
    val rows = remember { ListFocus() }
    val done = remember { FocusRequester() }

    // Pinned first, in their order, then the rest as the launcher lists them
    val ordered = remember(apps, pinned) {
        val byKey = apps.associateBy { it.key }
        pinned.mapNotNull { byKey[it] } + apps.filter { it.key !in pinned }
    }
    val keys = remember(ordered) { ordered.map { it.key } }

    fun save(list: List<String>) {
        pinned = list
        onChange(list)
    }

    fun move(key: String, delta: Int) {
        val from = pinned.indexOf(key)
        val to = from + delta
        if (from < 0 || to !in pinned.indices) return
        save(pinned.toMutableList().also { it[from] = pinned[to]; it[to] = pinned[from] })
    }

    TopDialog(
        title = "Favourite apps",
        onDismiss = onDismiss,
        subtitle = when {
            pinned.isEmpty() -> "Nothing pinned: the row shows every app, in the launcher's own order."
            pinned.size == 1 -> "One pinned. OK pins more; left and right move a pinned app along the row."
            else -> "${pinned.size} pinned, top to bottom as the row shows them. OK pins; left and right move."
        },
        buttons = listOf(DialogButton("Done", primary = true, focus = done, onClick = onDismiss)),
        contentFocus = { rows.currentOrNull(keys) ?: done },
    ) {
        if (apps.isEmpty()) {
            Text("No apps to choose from yet", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
        }
        // Shorter than the dialog is tall, or the last row falls off its bottom edge
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
            items(ordered, key = { it.key }) { app ->
                val isPinned = app.key in pinned
                val position = pinned.indexOf(app.key)
                ListItem(
                    selected = false,
                    onClick = { save(if (isPinned) pinned - app.key else pinned + app.key) },
                    headlineContent = { Text(app.label) },
                    supportingContent = {
                        Text(
                            when {
                                position >= 0 -> "Pinned, ${position + 1} of ${pinned.size}"
                                else -> app.key
                            },
                        )
                    },
                    leadingContent = {
                        Checkbox(
                            checked = isPinned,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = MarkOn, uncheckedColor = MarkOff),
                        )
                    },
                    modifier = rows.row(app.key).onPreviewKeyEvent { event ->
                        // Left and right move a pinned app; anything else is the list's own
                        if (!isPinned || event.type != KeyEventType.KeyDown) {
                            false
                        } else {
                            when (event.key) {
                                Key.DirectionLeft -> { move(app.key, -1); true }
                                Key.DirectionRight -> { move(app.key, 1); true }
                                else -> false
                            }
                        }
                    },
                )
            }
        }
        LaunchedEffect(Unit) { runCatching { (rows.currentOrNull(keys) ?: done).requestFocus() } }
    }
}
