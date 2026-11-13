package com.evofactory.homeboard

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * The names the launcher shows for the chosen entities.
 *
 * Home Assistant's friendly names follow whatever scheme a setup started with ("Living Room - Lights -
 * Main"), and changing them there would change them for everything. This renames them on screen only:
 * the entity ids are untouched, and so is Home Assistant.
 */
@Composable
fun EntityNamesDialog(
    prefs: LauncherPrefs,
    entities: List<String>,
    // What Home Assistant itself calls an entity: shown beside the launcher's own label when they differ
    haNameOf: (String) -> String?,
    onDismiss: () -> Unit,
) {
    var labels by remember { mutableStateOf(prefs.haEntityNames) }
    // The entity whose name is being typed
    var editing by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    val rows = remember { ListFocus() }
    val first = remember { FocusRequester() }
    val keyboard = remember { FocusRequester() }

    fun label(id: String): String = labels.firstOrNull { it.substringBefore('=') == id }?.substringAfter('=') ?: ""

    fun save(id: String, value: String) {
        val kept = labels.filterNot { it.substringBefore('=') == id }
        labels = if (value.isBlank()) kept else kept + "$id=$value"
        prefs.haEntityNames = labels
        EntityNames.load(prefs)
    }

    if (editing != null) {
        val id = editing!!
        TopDialog(
            title = "Name on screen",
            onDismiss = { editing = null },
            buttons = listOf(
                DialogButton("Cancel", onClick = { editing = null }),
                DialogButton("Save", primary = true) {
                    save(id, text.trim())
                    editing = null
                },
            ),
            contentFocus = { keyboard },
        ) {
            TvTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = "e.g. Stand",
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
                onImeAction = { save(id, text.trim()); editing = null },
                modifier = Modifier.fillMaxWidth().padding(12.dp).focusRequester(keyboard),
            )
            Text(
                id,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        return
    }

    TopDialog(
        title = "Names on screen",
        onDismiss = onDismiss,
        subtitle = "Only the launcher's own labels change: entity ids and Home Assistant stay as they are.",
        buttons = listOf(DialogButton("Done", primary = true, onClick = onDismiss)),
        contentFocus = { rows.currentOrNull(entities) ?: first },
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
            items(entities, key = { it }) { id ->
                val shown = label(id)
                val haName = haNameOf(id)
                ListItem(
                    selected = false,
                    onClick = { editing = id; text = shown },
                    headlineContent = { Text(shown.ifEmpty { haName ?: id.substringAfter('.') }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(
                            when {
                                shown.isEmpty() -> id
                                haName == null || haName == shown -> id
                                else -> "$id · Home Assistant: $haName"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier = rows.row(id).ringToTop().focusable()
                        .then(if (id == entities.firstOrNull()) Modifier.focusRequester(first) else Modifier),
                )
            }
        }
        LaunchedEffect(entities) { runCatching { (rows.currentOrNull(entities) ?: first).requestFocus() } }
    }
}
