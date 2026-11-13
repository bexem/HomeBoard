package com.evofactory.homeboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** A colour a picker offers, by the name its list shows */
data class ColourEntry(val name: String, val argb: Int)

/** The stored form: "#RRGGBB", which is how a calendar colour is written */
fun ColourEntry.hex(): String = "#%06X".format(argb and 0xFFFFFF)

/**
 * One palette, as a list of rows: a dot in the colour, its name, and "In use" on the one chosen.
 * Choosing closes the dialog; [onPick] gets the colour's ARGB. Used for the background and the
 * calendar colours alike, so the two lists can't drift apart.
 */
@Composable
fun ColourPickerDialog(
    title: String,
    subtitle: String,
    palette: List<ColourEntry>,
    current: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val rows = remember { ListFocus() }
    val done = remember { FocusRequester() }
    val list = rememberLazyListState()
    val keys = remember(palette) { palette.map { it.name } }
    TopDialog(
        title = title,
        onDismiss = onDismiss,
        subtitle = subtitle,
        buttons = listOf(DialogButton("Done", primary = true, focus = done, onClick = onDismiss)),
        contentFocus = { rows.currentOrNull(keys) ?: done },
    ) {
        LazyColumn(state = list, modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
            items(palette, key = { it.name }) { entry ->
                ListItem(
                    selected = false,
                    onClick = { onPick(entry.argb); onDismiss() },
                    headlineContent = { Text(entry.name) },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(Color(entry.argb), CircleShape)
                                .border(
                                    1.dp,
                                    if (entry.argb == current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                    CircleShape,
                                ),
                        )
                    },
                    supportingContent = {
                        if (entry.argb == current) Text("In use") else null
                    },
                    modifier = rows.row(entry.name),
                )
            }
        }
        // The selection starts on the colour in use, so "In use" is on screen where it matters; the list
        // has to reach that row before it exists to take the focus
        LaunchedEffect(Unit) {
            val index = palette.indexOfFirst { it.argb == current }
            if (index > 0) list.scrollToItem(index)
            withFrameNanos { }
            val inUse = palette.getOrNull(index)?.name
            runCatching { (inUse?.let(rows::requester) ?: rows.currentOrNull(keys) ?: done).requestFocus() }
        }
    }
}
