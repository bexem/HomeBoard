package com.evofactory.homeboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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

/** The background's colour: a short list of sensible ones, dark first so a slip stays readable */
@Composable
fun BackgroundColourDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val rows = remember { ListFocus() }
    val done = remember { FocusRequester() }
    val keys = remember { BackgroundColours.map { it.first } }
    TopDialog(
        title = "Background colour",
        onDismiss = onDismiss,
        subtitle = "The launcher keeps its dark surfaces; the colour sits behind them, under the darkness setting.",
        buttons = listOf(DialogButton("Done", primary = true, focus = done, onClick = onDismiss)),
        contentFocus = { rows.currentOrNull(keys) ?: done },
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
            items(BackgroundColours, key = { it.first }) { (name, value) ->
                var focused by remember { mutableStateOf(false) }
                ListItem(
                    selected = false,
                    onClick = { onPick(value); onDismiss() },
                    headlineContent = { Text(name) },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(Color(value), CircleShape)
                                .border(
                                    1.dp,
                                    if (value == current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                    CircleShape,
                                ),
                        )
                    },
                    supportingContent = {
                        if (value == current) Text("In use") else null
                    },
                    modifier = rows.row(name).onFocusChanged { focused = it.isFocused },
                )
            }
        }
        LaunchedEffect(Unit) { runCatching { (rows.currentOrNull(keys) ?: done).requestFocus() } }
    }
}
