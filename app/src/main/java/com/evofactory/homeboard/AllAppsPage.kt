package com.evofactory.homeboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Every installed app, on demand: the row keeps the favourites, this keeps the rest within one press.
 *
 * A page rather than a tab: the four tabs are wired to the remote's colour buttons and to the key
 * handling in MainActivity, and a fifth would change both. Back closes it.
 *
 * The overlay swallows the colour buttons, otherwise they would switch the tab underneath while it is
 * open, and the page would be left over a tab nobody asked for.
 */
@Composable
fun AllAppsPage(
    apps: List<InstalledApp>,
    hidden: Set<String>,
    favourites: List<String>,
    onSetIcon: (InstalledApp) -> Unit = {},
    onResetIcon: (InstalledApp) -> Unit = {},
    onPin: (InstalledApp) -> Unit,
    onUnpin: (InstalledApp) -> Unit,
    showHidden: Boolean,
    onShowHiddenChange: (Boolean) -> Unit,
    onHide: (InstalledApp) -> Unit,
    onUnhide: (InstalledApp) -> Unit,
    lines: Int,
    vertical: Boolean,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    // Hidden apps stay out of the page, unless asked for, the same list the row uses
    val shown = remember(apps, hidden, showHidden) {
        if (showHidden) apps else apps.filter { it.key !in hidden }
    }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    var menuFor by remember { mutableStateOf<String?>(null) }

    fun focusFirst() {
        val key = shown.firstOrNull()?.key ?: return
        requesters[key]?.requestFocus()
    }

    LaunchedEffect(shown.map { it.key }) {
        // Opening, and after hiding a tile, the selection needs a home
        withFrameNanos { }
        runCatching { focusFirst() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onPreviewKeyEvent { event: KeyEvent ->
                when {
                    // Back closes; the keys the launcher uses for tabs are swallowed
                    event.key == Key.Back || event.key == Key.Escape -> {
                        if (event.type == KeyEventType.KeyUp) onClose()
                        true
                    }
                    event.key in TabColourKeys -> true
                    else -> false
                }
            },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                "All apps",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = EdgePadding, vertical = 12.dp),
            )
            // The weight hands the grid exactly what the header leaves, so every line always fits
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = EdgePadding),
            ) {
                val gaps = TileSpacing * (lines - 1)
                // A 16:9 cell, sized by the axis the lines run along
                val (cellWidth, cellHeight) = if (vertical) {
                    val w = (maxWidth - EdgePadding * 2 - gaps) / lines
                    w to w * 9f / 16f
                } else {
                    val h = (maxHeight - gaps) / lines
                    h * 16f / 9f to h
                }
                val tile: @Composable (InstalledApp) -> Unit = { app ->
                    AppPageTile(
                        app = app,
                        hidden = app.key in hidden,
                        onClick = {
                            context.launch(app)
                            onClose()
                        },
                        onLongClick = { menuFor = app.key },
                        modifier = Modifier
                            .width(cellWidth)
                            .height(cellHeight)
                            .focusRequester(requesters.getOrPut(app.key) { FocusRequester() }),
                        overlay = {
                            if (menuFor == app.key) {
                                ContextMenu(
                                    title = app.label,
                                    actions = pageMenuActions(
                                        app = app,
                                        pinned = app.key in favourites,
                                        onSetIcon = {
                                            menuFor = null
                                            onSetIcon(app)
                                        },
                                        onResetIcon = {
                                            menuFor = null
                                            onResetIcon(app)
                                        },
                                        onPin = {
                                            menuFor = null
                                            onPin(app)
                                        },
                                        onUnpin = {
                                            menuFor = null
                                            onUnpin(app)
                                        },
                                        hidden = app.key in hidden,
                                        onHide = {
                                            menuFor = null
                                            onHide(app)
                                        },
                                        onUnhide = {
                                            menuFor = null
                                            onUnhide(app)
                                        },
                                        showHidden = showHidden,
                                        hiddenCount = hidden.size,
                                        onShowHidden = { onShowHiddenChange(!showHidden) },
                                        onInfo = {
                                            menuFor = null
                                            context.openAppInfo(app)
                                        },
                                    ),
                                    onDismiss = { menuFor = null },
                                )
                            }
                        },
                    )
                }
                if (vertical) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(lines),
                        modifier = Modifier.fillMaxSize().focusRestorer(),
                        contentPadding = PaddingValues(horizontal = EdgePadding),
                        horizontalArrangement = Arrangement.spacedBy(TileSpacing),
                        verticalArrangement = Arrangement.spacedBy(TileSpacing),
                    ) {
                        items(shown, key = { it.key }) { app -> tile(app) }
                    }
                } else {
                    LazyHorizontalGrid(
                        rows = GridCells.Fixed(lines),
                        modifier = Modifier.fillMaxSize().focusRestorer(),
                        contentPadding = PaddingValues(horizontal = EdgePadding),
                        horizontalArrangement = Arrangement.spacedBy(TileSpacing),
                        verticalArrangement = Arrangement.spacedBy(TileSpacing),
                    ) {
                        items(shown, key = { it.key }) { app -> tile(app) }
                    }
                }
            }
        }
    }
}

/** The page's tile menu: hiding is the page's own business, plus the Info shortcut the row has */
private fun pageMenuActions(
    app: InstalledApp,
    pinned: Boolean,
    onSetIcon: () -> Unit,
    onResetIcon: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
    hidden: Boolean,
    onHide: () -> Unit,
    onUnhide: () -> Unit,
    showHidden: Boolean,
    hiddenCount: Int,
    onShowHidden: () -> Unit,
    onInfo: () -> Unit,
): List<MenuAction> = buildList {
    // Pinning from here is the same list the row keeps, so the two menus agree
    add(MenuAction(if (pinned) "Unpin from favourites" else "Pin to favourites", if (pinned) onUnpin else onPin))
    if (appIconOverrides.containsKey(app.key)) add(MenuAction("Reset icon", onResetIcon))
    add(MenuAction("Set icon", onSetIcon))
    add(MenuAction(if (hidden) "Unhide" else "Hide", if (hidden) onUnhide else onHide))
    add(MenuAction("Info", onInfo))
    add(showHiddenAction(showHidden, hiddenCount, onShowHidden))
}

/**
 * One app as art: its banner where the app has one, its icon otherwise. Sized by the caller's
 * constraints, so the same tile serves one row and four.
 */
@Composable
private fun AppPageTile(
    app: InstalledApp,
    hidden: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    overlay: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val image by produceState(initialValue = cachedTileImage(app), app.key) {
        if (value == null) value = withContext(Dispatchers.IO) { loadTileImage(context, app) }
    }
    Box(modifier = modifier) {
        Card(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.fillMaxSize(),
            shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
        ) {
            val content = Modifier.fillMaxSize().alpha(if (hidden) 0.35f else 1f)
            val tile = image
            when {
                tile == null -> Box(content.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Text(
                        app.label,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                tile.isBanner -> Image(
                    bitmap = tile.bitmap,
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    modifier = content,
                )
                else -> Box(content.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Image(bitmap = tile.bitmap, contentDescription = app.label, modifier = Modifier.size(56.dp))
                }
            }
        }
        overlay()
    }
}

/** The remote's colour buttons: Red, Green, Yellow, Blue, as Android numbers them */
private val TabColourKeys = setOf(
    Key(android.view.KeyEvent.KEYCODE_PROG_RED),
    Key(android.view.KeyEvent.KEYCODE_PROG_GREEN),
    Key(android.view.KeyEvent.KEYCODE_PROG_YELLOW),
    Key(android.view.KeyEvent.KEYCODE_PROG_BLUE),
)
