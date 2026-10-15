package io.github.impca201.rssreader.ui.feeds

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.impca201.rssreader.R
import io.github.impca201.rssreader.data.Feed
import io.github.impca201.rssreader.data.Folder
import io.github.impca201.rssreader.data.IconCatalog
import io.github.impca201.rssreader.rssReaderApp
import io.github.impca201.rssreader.ui.home.rememberFolderIconPainter
import io.github.impca201.rssreader.ui.theme.AppShapes

/** A folder's header on the Feeds and folders page: icon, title, number of feeds, edit and remove. */
@Composable
internal fun FolderRow(
    folder: Folder,
    feedCount: Int,
    onEdit: () -> Unit,
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
        Icon(rememberFolderIconPainter(folder.icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(folder.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                pluralStringResource(R.plurals.feeds_count, feedCount, feedCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row {
            IconButton(onClick = onEdit) {
                Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.folder_edit_named, folder.title))
            }
            IconButton(onClick = onRemove) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.feeds_remove, folder.title))
            }
        }
    }
}

/**
 * New folder ([folder] null) or edit one: a title, and an icon from the whole Material Symbols
 * set, searchable by English name or topic.
 */
@Composable
internal fun FolderDialog(
    folder: Folder?,
    onSave: (title: String, icon: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val catalog = LocalContext.current.rssReaderApp.iconCatalog
    var title by rememberSaveable { mutableStateOf(folder?.title.orEmpty()) }
    var icon by rememberSaveable { mutableStateOf(folder?.icon ?: IconCatalog.DEFAULT) }
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query) { catalog.search(query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (folder == null) R.string.folder_new else R.string.folder_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // The chosen icon, next to the title it goes with.
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(rememberFolderIconPainter(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(MAX_TITLE_LENGTH) },
                        label = { Text(stringResource(R.string.folder_title)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.folder_icon_search)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (results.isEmpty()) {
                    Text(
                        stringResource(R.string.folder_icon_none_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.height(ICON_GRID_HEIGHT),
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(48.dp),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(ICON_GRID_HEIGHT),
                    ) {
                        items(results, key = { it.name }) { option ->
                            IconToggleButton(
                                checked = option.name == icon,
                                onCheckedChange = { icon = option.name },
                                shape = CircleShape,
                                colors =
                                    IconButtonDefaults.iconToggleButtonColors(
                                        checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    ),
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(rememberFolderIconPainter(option.name), contentDescription = option.label)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title.trim(), icon) }, enabled = title.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private val ICON_GRID_HEIGHT = 240.dp

/** Picks the folder a feed is in, or none. */
@Composable
fun MoveFeedDialog(
    feed: Feed,
    folders: List<Folder>,
    onMove: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_move_title, feed.title)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item(key = "none") {
                    FolderChoice(stringResource(R.string.folder_none), null, selected = feed.folderId == null) { onMove(null) }
                }
                items(folders, key = { it.id }) { folder ->
                    FolderChoice(folder.title, folder.icon, selected = feed.folderId == folder.id) { onMove(folder.id) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun FolderChoice(
    label: String,
    icon: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        if (icon != null) Icon(rememberFolderIconPainter(icon), contentDescription = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private const val MAX_TITLE_LENGTH = 40
