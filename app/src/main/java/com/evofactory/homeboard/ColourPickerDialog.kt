package com.evofactory.homeboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** A colour a picker offers, by the name its list shows (kept for the palettes that still name colours) */
data class ColourEntry(val name: String, val argb: Int)

/** The stored form: "#RRGGBB", which is how a calendar colour is written */
fun ColourEntry.hex(): String = "#%06X".format(argb and 0xFFFFFF)

/**
 * The launcher's one colour picker: a grid of hues across and shades down, greys on the left, the colour
 * in use as a dot beside the title, and the colours last picked underneath. Every place that chooses a
 * colour uses this dialog — the background, a calendar's bar — so there is nothing to drift.
 *
 * The remote moves with the arrows; OK picks and closes; Back or Done closes without picking.
 */
@Composable
fun ColourPickerDialog(
    title: String,
    subtitle: String,
    current: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val done = remember { FocusRequester() }
    val cells = remember { ColourGrid }
    // Where the selection starts: the cell nearest the colour in use, else the middle of the grid
    val startColumn = remember(current) {
        cells.indexOfFirst { it.argb == current }.takeIf { it >= 0 } ?: (cells.size / 2)
    }
    val focus = remember { ListFocus() }
    var focused by remember { mutableStateOf(startColumn) }
    var recent by remember { mutableStateOf(RecentColours.load()) }
    TopDialog(
        title = title,
        // A grid of thirteen columns needs more than the usual dialog width
        width = 620.dp,
        onDismiss = onDismiss,
        subtitle = subtitle,
        buttons = listOf(DialogButton("Done", primary = true, focus = done, onClick = onDismiss)),
        contentFocus = { focus.currentOrNull(cells.indices.map { "c$it" }) ?: done },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(20.dp).background(Color(current), CircleShape).border(1.dp, MaterialTheme.colorScheme.onSurface, CircleShape))
            Text("In use", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // One focusable cell per colour: the grid's shape is 13 columns wide, 8 deep
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (row in 0 until ColourRows) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (column in 0 until ColourColumns) {
                        val index = row * ColourColumns + column
                        val entry = cells[index]
                        ColourCell(
                            argb = entry.argb,
                            selected = entry.argb == current,
                            modifier = Modifier
                                .focusRequester(focus.requester("c$index"))
                                .onFocusChanged { if (it.isFocused) focused = index },
                            onPick = {
                                RecentColours.remember(entry.argb)
                                onPick(entry.argb)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            Text(
                "Recently used",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 10.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(start = 4.dp, top = 6.dp)) {
                recent.forEach { argb ->
                    ColourCell(
                        argb = argb,
                        selected = false,
                        modifier = Modifier,
                        onPick = {
                            RecentColours.remember(argb)
                            onPick(argb)
                            onDismiss()
                        },
                    )
                }
            }
        }
        // The remote starts on the colour in use: it cannot be focused until the grid is laid out
        LaunchedEffect(startColumn) {
            runCatching { focus.requester("c$startColumn").requestFocus() }
            focused = startColumn
        }
    }
}

/** One swatch: focused, it grows a light ring so the remote always knows where it is */
@Composable
private fun ColourCell(argb: Int, selected: Boolean, modifier: Modifier, onPick: () -> Unit) {
    val shape = RoundedCornerShape(4.dp)
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(ColourCellSize)
            .onFocusChanged { focused = it.isFocused }
            .border(
                3.dp,
                when {
                    focused -> MaterialTheme.colorScheme.onSurface
                    selected -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    else -> Color.Transparent
                },
                shape,
            )
            .background(Color(argb), shape)
            .clickable { onPick() },
    )
}

// The grid's shape: greys, then the hues, each with eight shades from light to dark
private const val ColourRows = 8
private const val ColourColumns = 13

private val ColourCellSize = 36.dp

/**
 * Every swatch, row by row: greys in the first column, then one column per hue (every 30°), each row a
 * shade from the palest at the top to the deepest at the bottom, the last near black.
 */
private val ColourGrid: List<ColourEntry> = buildList {
    for (row in 0 until ColourRows) {
        add(ColourEntry("Grey", greyFor(row)))
        for (column in 1 until ColourColumns) {
            add(ColourEntry("Hue", hueFor((column - 1) * 30f, row)))
        }
    }
}

private fun greyFor(row: Int): Int {
    // From near-white to near-black in eight steps, so the launcher's own dark greys are all here
    val value = (240 - row * 33).coerceIn(8, 255)
    return 0xFF000000.toInt() or (value shl 16) or (value shl 8) or value
}

/** A hue at one of the eight shades: pale at the top, deep at the bottom */
private fun hueFor(hue: Float, row: Int): Int {
    val value = (1f - row * 0.115f).coerceAtLeast(0.1f)
    val saturation = (0.30f + row * 0.09f).coerceAtMost(1f)
    val hsv = floatArrayOf(hue, saturation, value)
    return 0xFF000000.toInt() or android.graphics.Color.HSVToColor(hsv).and(0xFFFFFF)
}

/** The last few colours picked on this TV, newest first; a local convenience, never part of an export */
object RecentColours {
    private const val KEY = "recent_colours"
    private const val KEEP = 6

    fun load(): List<Int> = LauncherPrefs(HomeBoardApp.instance).recentColours.split(',')
        .mapNotNull { it.trim().toIntOrNull() }
        .take(KEEP)

    fun remember(argb: Int) {
        val prefs = LauncherPrefs(HomeBoardApp.instance)
        val next = (listOf(argb) + prefs.recentColours.split(',').mapNotNull { it.trim().toIntOrNull() })
            .distinct()
            .take(KEEP)
        prefs.recentColours = next.joinToString(",")
    }
}
