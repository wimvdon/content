package be.wimvandonck.reader.ui.feeds

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import be.wimvandonck.reader.R
import be.wimvandonck.reader.data.Feed
import be.wimvandonck.reader.data.FeedArrangement
import be.wimvandonck.reader.data.FeedArrangement.Row as ArrangedRow
import be.wimvandonck.reader.data.FeedProblem
import be.wimvandonck.reader.data.Folder
import be.wimvandonck.reader.data.Library
import be.wimvandonck.reader.ui.home.FeedIcon
import be.wimvandonck.reader.ui.home.FeedIconSize
import be.wimvandonck.reader.ui.home.problemText
import be.wimvandonck.reader.ui.home.rememberFolderIconPainter
import be.wimvandonck.reader.ui.theme.AppShapes
import kotlinx.coroutines.launch

data class FeedsState(
    val feeds: List<Feed>,
    val folders: List<Folder>,
    val problems: Map<String, FeedProblem>,
    /** Instapaper's folders and tags, when signed in. */
    val instapaper: InstapaperManageState? = null,
)

class FeedsActions(
    val onBack: () -> Unit,
    val onAddFeed: () -> Unit,
    val onRemoveFeed: (Feed) -> Unit,
    val onEditFeed: (feed: Feed, title: String, folderId: String?) -> Unit,
    val onArrange: (List<ArrangedRow>) -> Unit,
    val onAddFolder: (title: String, icon: String) -> Unit,
    val onUpdateFolder: (Folder) -> Unit,
    val onRemoveFolder: (Folder) -> Unit,
    val onImportOpml: () -> Unit,
    val onExportOpml: () -> Unit,
    val instapaper: InstapaperManageActions? = null,
)

/**
 * Everything about what is followed, on one page: each folder with its feeds, the feeds outside
 * folders, and OPML import and export. Drag the handle to reorder feeds and folders or to move a
 * feed into another folder; tap a row to rename or remove it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedsScreen(
    state: FeedsState,
    snackbarHostState: SnackbarHostState,
    actions: FeedsActions,
) {
    // "" while creating a folder, a folder's id while editing it, null when the dialog is closed.
    var editingFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var removingFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var editingFeed by rememberSaveable { mutableStateOf<String?>(null) }
    var removingFeed by rememberSaveable { mutableStateOf<String?>(null) }

    val library = remember(state.feeds, state.folders) { Library(feeds = state.feeds, folders = state.folders) }
    val stored = remember(library) { FeedArrangement.rows(library) }
    // While dragging: the rows as they are being rearranged, the dragged row and how far it moved.
    var dragRows by remember { mutableStateOf<List<ArrangedRow>?>(null) }
    var draggedKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rows = dragRows ?: stored
    // Feeds below a folder header are indented; those under "No folder" are not.
    val indented =
        remember(rows) {
            var inFolder = false
            rows.mapNotNullTo(HashSet()) { row ->
                when (row) {
                    is ArrangedRow.FolderHeader -> inFolder = true
                    ArrangedRow.Unfiled -> inFolder = false
                    is ArrangedRow.FeedItem -> if (inFolder) return@mapNotNullTo row.key
                }
                null
            }
        }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val edge = with(LocalDensity.current) { 64.dp.toPx() }

    fun startDrag(row: ArrangedRow) {
        // A folder moves on its own among the other folders; their feeds hide meanwhile.
        dragRows = if (row is ArrangedRow.FolderHeader) FeedArrangement.rows(library, foldersOnly = true) else stored
        draggedKey = row.key
        dragOffset = 0f
    }

    fun drag(delta: Float) {
        val current = dragRows ?: return
        val key = draggedKey ?: return
        dragOffset += delta
        val items = listState.layoutInfo.visibleItemsInfo
        val dragged = items.firstOrNull { it.key == key } ?: return
        val center = dragged.offset + dragOffset + dragged.size / 2
        val target = items.firstOrNull { it.key != key && center.toInt() in it.offset until it.offset + it.size }
        val from = current.indexOfFirst { it.key == key }
        val to = target?.let { info -> current.indexOfFirst { it.key == info.key } } ?: -1
        if (from >= 0 && to >= 0) {
            val moved = FeedArrangement.move(current, from, to)
            if (moved !== current) {
                dragRows = moved
                // The row now sits where the target was; keep it under the finger.
                dragOffset -= (target!!.offset - dragged.offset)
            }
        }
        // Near the top or bottom edge, scroll so rows further away can be reached.
        val viewport = listState.layoutInfo
        val top = dragged.offset + dragOffset
        val scrollBy =
            when {
                top < viewport.viewportStartOffset + edge -> -20f
                top + dragged.size > viewport.viewportEndOffset - edge -> 20f
                else -> 0f
            }
        if (scrollBy != 0f) {
            scope.launch {
                val scrolled = listState.scrollBy(scrollBy)
                dragOffset += scrolled
            }
        }
    }

    fun endDrag() {
        val final = dragRows
        if (final != null && final != stored && final != FeedArrangement.rows(library, foldersOnly = true)) actions.onArrange(final)
        dragRows = null
        draggedKey = null
        dragOffset = 0f
    }

    /** Moves a row one place up or down, for screen readers (they can't drag). */
    fun step(
        row: ArrangedRow,
        by: Int,
    ): Boolean {
        val list = if (row is ArrangedRow.FolderHeader) FeedArrangement.rows(library, foldersOnly = true) else stored
        val from = list.indexOfFirst { it.key == row.key }
        val moved = FeedArrangement.move(list, from, from + by)
        if (moved === list) return false
        actions.onArrange(moved)
        return true
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.feeds_page_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "buttons") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    FilledTonalButton(onClick = actions.onAddFeed, modifier = Modifier.weight(1f)) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                        Text(stringResource(R.string.feeds_add), modifier = Modifier.padding(start = 8.dp))
                    }
                    FilledTonalButton(onClick = { editingFolder = "" }, modifier = Modifier.weight(1f)) {
                        Icon(painterResource(R.drawable.ic_folder), contentDescription = null)
                        Text(stringResource(R.string.folder_new), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            item(key = "hint") {
                Hint(
                    stringResource(
                        when {
                            state.feeds.isEmpty() -> R.string.home_no_feeds_body
                            state.folders.isEmpty() -> R.string.folders_explanation
                            else -> R.string.feeds_drag_hint
                        },
                    ),
                )
            }
            items(rows, key = { it.key }) { row ->
                val dragging = row.key == draggedKey
                val rowModifier =
                    Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                        .then(if (dragging) Modifier else Modifier.animateItem())
                val handle =
                    // Restarted when the stored arrangement changes, so a drag never starts from an old one.
                    Modifier.pointerInput(row.key, stored) {
                        detectDragGestures(
                            onDragStart = { startDrag(row) },
                            onDrag = { change, amount ->
                                change.consume()
                                drag(amount.y)
                            },
                            onDragEnd = { endDrag() },
                            onDragCancel = { endDrag() },
                        )
                    }
                val moveUp = stringResource(R.string.action_move_up)
                val moveDown = stringResource(R.string.action_move_down)
                val a11y =
                    Modifier.semantics {
                        customActions =
                            listOf(
                                CustomAccessibilityAction(moveUp) { step(row, -1) },
                                CustomAccessibilityAction(moveDown) { step(row, 1) },
                            )
                    }
                when (row) {
                    is ArrangedRow.FolderHeader -> {
                        FolderLine(
                            folder = row.folder,
                            feedCount = state.feeds.count { it.folderId == row.folder.id },
                            lifted = dragging,
                            handleModifier = handle,
                            onClick = { editingFolder = row.folder.id },
                            modifier = rowModifier.then(a11y),
                        )
                    }

                    is ArrangedRow.FeedItem -> {
                        FeedLine(
                            feed = row.feed,
                            problem = state.problems[row.feed.url],
                            inFolder = row.key in indented,
                            lifted = dragging,
                            handleModifier = handle,
                            onClick = { editingFeed = row.feed.url },
                            modifier = rowModifier.then(a11y),
                        )
                    }

                    ArrangedRow.Unfiled -> {
                        Column(rowModifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Heading(stringResource(R.string.folder_none))
                            if (rows.last() is ArrangedRow.Unfiled && state.feeds.isNotEmpty()) {
                                // Nothing outside folders: say so, and where to drop a feed to change that.
                                Text(
                                    stringResource(R.string.folder_none_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, AppShapes.Item)
                                            .padding(16.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (state.instapaper != null && actions.instapaper != null) {
                item(key = "instapaper") { InstapaperManageSection(state.instapaper, actions.instapaper) }
            }
            item(key = "opml") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 24.dp)) {
                    Heading(stringResource(R.string.feeds_opml_title))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = actions.onImportOpml, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.feeds_import))
                        }
                        OutlinedButton(onClick = actions.onExportOpml, enabled = state.feeds.isNotEmpty(), modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.feeds_export))
                        }
                    }
                    Hint(stringResource(R.string.feeds_opml_hint))
                }
            }
        }
    }

    editingFolder?.let { id ->
        val folder = state.folders.firstOrNull { it.id == id }
        if (id.isEmpty() || folder != null) {
            FolderDialog(
                folder = folder,
                onSave = { title, icon ->
                    editingFolder = null
                    if (folder == null) actions.onAddFolder(title, icon) else actions.onUpdateFolder(folder.copy(title = title, icon = icon))
                },
                onDismiss = { editingFolder = null },
                onRemove =
                    folder?.let {
                        {
                            editingFolder = null
                            removingFolder = it.id
                        }
                    },
            )
        }
    }
    state.folders.firstOrNull { it.id == removingFolder }?.let { folder ->
        ConfirmRemove(
            title = stringResource(R.string.folder_remove_title),
            body = stringResource(R.string.folder_remove_body, folder.title),
            onConfirm = {
                removingFolder = null
                actions.onRemoveFolder(folder)
            },
            onDismiss = { removingFolder = null },
        )
    }
    state.feeds.firstOrNull { it.url == editingFeed }?.let { feed ->
        FeedDialog(
            feed = feed,
            folders = state.folders,
            problem = state.problems[feed.url]?.let { problemText(it) },
            onSave = { title, folderId ->
                editingFeed = null
                actions.onEditFeed(feed, title, folderId)
            },
            onRemove = {
                editingFeed = null
                removingFeed = feed.url
            },
            onDismiss = { editingFeed = null },
        )
    }
    state.feeds.firstOrNull { it.url == removingFeed }?.let { feed ->
        ConfirmRemove(
            title = stringResource(R.string.feeds_remove_title),
            body = stringResource(R.string.feeds_remove_body, feed.title),
            onConfirm = {
                removingFeed = null
                actions.onRemoveFeed(feed)
            },
            onDismiss = { removingFeed = null },
        )
    }
}

@Composable
private fun FolderLine(
    folder: Folder,
    feedCount: Int,
    lifted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    handleModifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = AppShapes.Item,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = if (lifted) 8.dp else 0.dp,
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(rememberFolderIconPainter(folder.icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Column(Modifier.weight(1f)) {
                Text(folder.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    pluralStringResource(R.plurals.feeds_count, feedCount, feedCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClick) {
                Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.folder_edit))
            }
            DragHandle(handleModifier)
        }
    }
}

@Composable
private fun FeedLine(
    feed: Feed,
    problem: FeedProblem?,
    inFolder: Boolean,
    lifted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    handleModifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = AppShapes.Item,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = if (lifted) 8.dp else 0.dp,
        modifier = modifier.fillMaxWidth().padding(start = if (inFolder) 24.dp else 0.dp),
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FeedIcon(feed, FeedIconSize.Large)
            Column(Modifier.weight(1f)) {
                Text(feed.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = problem?.let { problemText(it) } ?: feed.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onClick) {
                Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.feed_edit))
            }
            DragHandle(handleModifier)
        }
    }
}

/** The grip to drag a row by; screen readers use the row's Move up / Move down actions instead. */
@Composable
private fun DragHandle(modifier: Modifier) {
    Box(
        modifier.size(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_drag_handle), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Heading(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            modifier
                .padding(start = 4.dp)
                .semantics { heading() },
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ConfirmRemove(
    title: String,
    body: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_remove)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
