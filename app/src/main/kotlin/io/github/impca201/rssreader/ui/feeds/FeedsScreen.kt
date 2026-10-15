package io.github.impca201.rssreader.ui.feeds

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.impca201.rssreader.R
import io.github.impca201.rssreader.data.Feed
import io.github.impca201.rssreader.data.FeedProblem
import io.github.impca201.rssreader.data.Folder
import io.github.impca201.rssreader.ui.home.FeedIcon
import io.github.impca201.rssreader.ui.home.FeedIconSize
import io.github.impca201.rssreader.ui.home.problemText
import io.github.impca201.rssreader.ui.theme.AppShapes
import io.github.impca201.rssreader.ui.theme.SettingsScaffold

data class FeedsState(
    val feeds: List<Feed>,
    val folders: List<Folder>,
    val problems: Map<String, FeedProblem>,
)

class FeedsActions(
    val onBack: () -> Unit,
    val onAddFeed: () -> Unit,
    val onRemoveFeed: (Feed) -> Unit,
    val onMoveFeed: (Feed, String?) -> Unit,
    val onAddFolder: (title: String, icon: String) -> Unit,
    val onUpdateFolder: (Folder) -> Unit,
    val onRemoveFolder: (Folder) -> Unit,
    val onImportOpml: () -> Unit,
    val onExportOpml: () -> Unit,
)

/**
 * Everything about what is followed, on one page: each folder with its feeds, the feeds outside
 * folders, and OPML import and export.
 */
@Composable
fun FeedsScreen(
    state: FeedsState,
    snackbarHostState: SnackbarHostState,
    actions: FeedsActions,
) {
    // "" while creating a folder, a folder's id while editing it, null when the dialog is closed.
    var editingFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var removingFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var movingFeed by rememberSaveable { mutableStateOf<String?>(null) }
    var removingFeed by rememberSaveable { mutableStateOf<String?>(null) }
    val folderIds = state.folders.map { it.id }.toSet()
    val loose = state.feeds.filter { it.folderId == null || it.folderId !in folderIds }

    @Composable
    fun FeedLine(feed: Feed) {
        FeedRow(
            feed = feed,
            problem = state.problems[feed.url],
            // Moving to a folder only makes sense once there is one.
            onMove = if (state.folders.isNotEmpty()) ({ movingFeed = feed.url }) else null,
            onRemove = { removingFeed = feed.url },
        )
    }

    SettingsScaffold(
        title = stringResource(R.string.feeds_page_title),
        backLabel = stringResource(R.string.action_back),
        onBack = actions.onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) {
        item(key = "buttons") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        if (state.feeds.isEmpty()) {
            item(key = "empty") { Hint(stringResource(R.string.home_no_feeds_body)) }
        } else if (state.folders.isEmpty()) {
            item(key = "folders_hint") { Hint(stringResource(R.string.folders_explanation)) }
        }
        state.folders.forEach { folder ->
            val inFolder = state.feeds.filter { it.folderId == folder.id }
            item(key = "folder:" + folder.id) {
                FolderRow(
                    folder = folder,
                    feedCount = inFolder.size,
                    onEdit = { editingFolder = folder.id },
                    onRemove = { removingFolder = folder.id },
                )
            }
            items(inFolder, key = { "feed:" + it.url }) { feed ->
                Column(Modifier.padding(start = 24.dp)) { FeedLine(feed) }
            }
        }
        if (loose.isNotEmpty()) {
            if (state.folders.isNotEmpty()) {
                item(key = "loose") { Heading(stringResource(R.string.folder_none)) }
            }
            items(loose, key = { "feed:" + it.url }) { feed -> FeedLine(feed) }
        }
        item(key = "opml") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
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
    state.feeds.firstOrNull { it.url == movingFeed }?.let { feed ->
        MoveFeedDialog(
            feed = feed,
            folders = state.folders,
            onMove = { folderId ->
                movingFeed = null
                actions.onMoveFeed(feed, folderId)
            },
            onDismiss = { movingFeed = null },
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
private fun FeedRow(
    feed: Feed,
    problem: FeedProblem?,
    onMove: (() -> Unit)?,
    onRemove: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer, AppShapes.Item)
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row {
            if (onMove != null) {
                IconButton(onClick = onMove) {
                    Icon(painterResource(R.drawable.ic_folder), contentDescription = stringResource(R.string.folder_move_title, feed.title))
                }
            }
            IconButton(onClick = onRemove) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.feeds_remove, feed.title))
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .padding(start = 4.dp, top = 8.dp)
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
