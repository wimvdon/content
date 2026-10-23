package be.wimvandonck.content.ui.feeds

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import be.wimvandonck.content.R
import be.wimvandonck.content.data.Feed
import be.wimvandonck.content.data.Folder
import be.wimvandonck.content.data.IconCatalog
import be.wimvandonck.content.rssReaderApp
import be.wimvandonck.content.ui.home.rememberFolderIconPainter
import androidx.compose.foundation.selection.selectableGroup
import be.wimvandonck.content.ui.home.FeedIcon
import be.wimvandonck.content.ui.home.FeedIconSize

/**
 * New folder ([folder] null) or edit one: a title, and an icon from the whole Material Symbols
 * set, searchable by English name or topic.
 */
@Composable
internal fun FolderDialog(
    folder: Folder?,
    onSave: (title: String, icon: String) -> Unit,
    onDismiss: () -> Unit,
    /** Shown as Remove when editing an existing folder. */
    onRemove: (() -> Unit)? = null,
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
        dismissButton = { RemoveAndCancel(onRemove, onDismiss) },
    )
}

private val ICON_GRID_HEIGHT = 240.dp

/**
 * Edit a feed: its name (blank brings back the feed's own title), its folder, or remove it.
 * Shows why its last download failed, if it did.
 */
@Composable
internal fun FeedDialog(
    feed: Feed,
    folders: List<Folder>,
    problem: String?,
    onSave: (title: String, folderId: String?) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(feed.title) }
    var folderId by rememberSaveable { mutableStateOf(feed.folderId?.takeIf { id -> folders.any { it.id == id } }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { FeedIcon(feed, FeedIconSize.Large) },
        title = { Text(stringResource(R.string.feed_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(MAX_FEED_TITLE_LENGTH) },
                    label = { Text(stringResource(R.string.feed_name)) },
                    supportingText = { Text(stringResource(R.string.feed_name_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    feed.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (problem != null) {
                    Text(problem, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                if (folders.isNotEmpty()) {
                    Text(stringResource(R.string.feed_folder), style = MaterialTheme.typography.titleSmall)
                    LazyColumn(Modifier.heightIn(max = 240.dp).selectableGroup()) {
                        item(key = "none") {
                            FolderChoice(stringResource(R.string.folder_none), null, selected = folderId == null) { folderId = null }
                        }
                        items(folders, key = { it.id }) { folder ->
                            FolderChoice(folder.title, folder.icon, selected = folderId == folder.id) { folderId = folder.id }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, folderId) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { RemoveAndCancel(onRemove, onDismiss) },
    )
}

/** Remove (in the error colour, when there is something to remove) and Cancel. */
@Composable
private fun RemoveAndCancel(
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    Row {
        if (onRemove != null) {
            TextButton(onClick = onRemove) {
                Text(stringResource(R.string.action_remove), color = MaterialTheme.colorScheme.error)
            }
        }
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
    }
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
private const val MAX_FEED_TITLE_LENGTH = 80
