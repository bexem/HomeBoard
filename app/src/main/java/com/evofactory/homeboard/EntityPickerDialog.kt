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

/** One row of an [EntityPickerDialog]: what it is, what it reads as, and the line under that */
data class PickerRow(val id: String, val label: String, val detail: String)

/**
 * Choose from a list and put the chosen ones in order: calendars, Home Assistant cameras, pinned
 * apps. The caller owns the list ([chosen]) and hears every change through [onChange], so the order
 * here is the order the card reads.
 *
 * OK on a row opens its menu: [includeLabel], or [excludeLabel] and moving it along. Left and right
 * on a chosen row move it a step, so nothing depends on discovering the menu; both go through the
 * same move. The rows keep the caller's order while they are chosen and moved: only the numbering
 * changes, never the row under the remote.
 *
 * [noun] names one of them in the subtitle. With one chosen there is nothing to reorder, so it says
 * what OK does rather than promising left and right; [subtitle] takes over from two.
 */
@Composable
fun EntityPickerDialog(
    title: String,
    noun: String,
    rows: List<PickerRow>,
    chosen: List<String>,
    includeLabel: String,
    excludeLabel: String,
    /** A chosen thing's place in the list, 1-based: "Priority 2", "Position 3" */
    positionLabel: (Int) -> String,
    // Whether order means anything here: false hides the move entries, the position line and left/right
    movesMatter: Boolean = true,
    /** Nothing chosen, and the line for a list with nothing in it */
    emptyText: String,
    /** The subtitle from two chosen up: what the count means and what the keys do */
    subtitle: (count: Int) -> String,
    onChange: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    // The row whose menu is open: OK offers what can be done with it, rather than guessing
    var menuFor by remember { mutableStateOf<String?>(null) }
    val focus = remember { ListFocus() }
    val done = remember { FocusRequester() }
    val keys = remember(rows) { rows.map { it.id } }

    fun move(id: String, delta: Int) {
        val from = chosen.indexOf(id)
        val to = from + delta
        if (from < 0 || to !in chosen.indices) return
        onChange(chosen.toMutableList().also { it[from] = chosen[to]; it[to] = chosen[from] })
    }

    TopDialog(
        title = title,
        onDismiss = onDismiss,
        subtitle = when {
            chosen.size == 1 -> "One $noun. OK chooses more."
            // Nothing to choose is the list's own line, under it: don't say it twice
            chosen.isEmpty() -> emptyText.takeIf { rows.isNotEmpty() }
            else -> subtitle(chosen.size)
        },
        buttons = listOf(DialogButton("Done", primary = true, focus = done, onClick = onDismiss)),
        contentFocus = { focus.currentOrNull(keys) ?: done },
    ) {
        if (rows.isEmpty()) {
            Text(emptyText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
        }
        // Shorter than the dialog is tall, or the last row falls off its bottom edge
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
            items(rows, key = { it.id }) { row ->
                val isChosen = row.id in chosen
                val position = chosen.indexOf(row.id)
                ListItem(
                    selected = false,
                    onClick = { menuFor = row.id },
                    headlineContent = {
                        Text(row.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        // A chosen row is numbered; the rest say what they are, when the caller has a line
                        val line = if (position >= 0 && movesMatter) "${positionLabel(position + 1)} of ${chosen.size}" else row.detail
                        if (line.isNotEmpty()) Text(line)
                    },
                    leadingContent = {
                        Checkbox(
                            checked = isChosen,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(
                                checkedColor = MarkOn,
                                uncheckedColor = MarkOff,
                            ),
                        )
                    },
                    modifier = focus.row(row.id).onPreviewKeyEvent { event ->
                        // Left and right move a chosen row; anything else is the list's own. Where order
                        // means nothing they are left to the list, so focus still leaves sideways
                        if (!isChosen || !movesMatter || event.type != KeyEventType.KeyDown) {
                            false
                        } else {
                            when (event.key) {
                                Key.DirectionLeft -> { move(row.id, -1); true }
                                Key.DirectionRight -> { move(row.id, 1); true }
                                else -> false
                            }
                        }
                    },
                )
            }
        }
        LaunchedEffect(keys) { runCatching { (focus.currentOrNull(keys) ?: done).requestFocus() } }
        // What OK does with a row: include it, leave it out, or move it along the list
        menuFor?.let { id ->
            val isChosen = id in chosen
            val position = chosen.indexOf(id)
            ContextMenu(
                title = rows.firstOrNull { it.id == id }?.label,
                actions = buildList {
                    add(
                        MenuAction(if (isChosen) excludeLabel else includeLabel) {
                            onChange(if (isChosen) chosen - id else chosen + id)
                            menuFor = null
                        },
                    )
                    if (isChosen) {
                        if (position > 0) {
                            if (movesMatter) add(MenuAction("Move up (${positionLabel(position)})") { move(id, -1); menuFor = null })
                        }
                        if (position < chosen.lastIndex) {
                            add(MenuAction("Move down (${positionLabel(position + 2)})") { move(id, 1); menuFor = null })
                        }
                    }
                },
                onDismiss = { menuFor = null },
            )
        }
    }
}
