package com.evofactory.homeboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Which apps the row shows, in what order. Nothing pinned means every app, as before; the first
 * pinned app becomes the first tile.
 *
 * [EntityPickerDialog] does the pinning and the ordering: OK pins and unpins, left and right move a
 * pinned app along the row, so the order here is the order on the Home tab. The row's own Move mode
 * reorders the same list afterwards, so there is one arrangement to keep, wherever it was changed.
 */
@Composable
fun FavouritesEditorDialog(
    apps: List<InstalledApp>,
    favourites: List<String>,
    onChange: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var pinned by remember { mutableStateOf(favourites) }

    // Pinned first, in their order, then the rest as the launcher lists them
    val rows = remember(apps, pinned) {
        val byKey = apps.associateBy { it.key }
        (pinned.mapNotNull { byKey[it] } + apps.filter { it.key !in pinned })
            .map { PickerRow(it.key, it.label, it.key) }
    }

    EntityPickerDialog(
        title = "Favourite apps",
        noun = "pinned app",
        rows = rows,
        chosen = pinned,
        includeLabel = "Pin it",
        excludeLabel = "Unpin it",
        positionLabel = { "Position $it" },
        emptyText = if (apps.isEmpty()) {
            "No apps to choose from yet"
        } else {
            "Nothing pinned: the row shows every app, in the launcher's own order."
        },
        subtitle = { "$it pinned, top to bottom as the row shows them. OK pins; left and right move." },
        onChange = { pinned = it; onChange(it) },
        onDismiss = onDismiss,
    )
}
