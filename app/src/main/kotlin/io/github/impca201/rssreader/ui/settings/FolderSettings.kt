package io.github.impca201.rssreader.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import io.github.impca201.rssreader.data.FolderIcon
import io.github.impca201.rssreader.ui.home.drawableRes
import io.github.impca201.rssreader.ui.theme.AppShapes
import io.github.impca201.rssreader.ui.theme.CollapsibleSection

/** Folders: create, rename, change the icon, delete. Feeds are put in folders under Feeds. */
@Composable
fun FoldersSection(
    folders: List<Folder>,
    feeds: List<Feed>,
    onAddFolder: (String, FolderIcon) -> Unit,
    onUpdateFolder: (Folder) -> Unit,
    onRemoveFolder: (Folder) -> Unit,
) {
    // "" while creating a folder, a folder's id while editing it, null when the dialog is closed.
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmRemove by rememberSaveable { mutableStateOf<String?>(null) }
    CollapsibleSection(
        key = "folders",
        title = stringResource(R.string.section_folders),
        summary = pluralStringResource(R.plurals.folders_count, folders.size, folders.size),
        icon = painterResource(R.drawable.ic_folder),
    ) {
        if (folders.isEmpty()) {
            Text(
                stringResource(R.string.folders_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        folders.forEach { folder ->
            FolderRow(
                folder = folder,
                feedCount = feeds.count { it.folderId == folder.id },
                onEdit = { editing = folder.id },
                onRemove = { confirmRemove = folder.id },
            )
        }
        FilledTonalButton(onClick = { editing = "" }, modifier = Modifier.fillMaxWidth()) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null)
            Text(stringResource(R.string.folder_new), modifier = Modifier.padding(start = 8.dp))
        }
    }

    editing?.let { id ->
        val folder = folders.firstOrNull { it.id == id }
        if (id.isEmpty() || folder != null) {
            FolderDialog(
                folder = folder,
                onSave = { title, icon ->
                    editing = null
                    if (folder == null) onAddFolder(title, icon) else onUpdateFolder(folder.copy(title = title, icon = icon))
                },
                onDismiss = { editing = null },
            )
        }
    }

    val removing = confirmRemove?.let { id -> folders.firstOrNull { it.id == id } }
    if (removing != null) {
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text(stringResource(R.string.folder_remove_title)) },
            text = { Text(stringResource(R.string.folder_remove_body, removing.title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = null
                    onRemoveFolder(removing)
                }) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun FolderRow(
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
        Icon(painterResource(folder.icon.drawableRes()), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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

/** New folder ([folder] null) or edit one: a title and an icon. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FolderDialog(
    folder: Folder?,
    onSave: (String, FolderIcon) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(folder?.title.orEmpty()) }
    var icon by rememberSaveable { mutableStateOf(folder?.icon ?: FolderIcon.Folder) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (folder == null) R.string.folder_new else R.string.folder_edit)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(MAX_TITLE_LENGTH) },
                    label = { Text(stringResource(R.string.folder_title)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.folder_icon), style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FolderIcon.entries.forEach { option ->
                        IconToggleButton(
                            checked = option == icon,
                            onCheckedChange = { icon = option },
                            shape = CircleShape,
                            colors =
                                IconButtonDefaults.iconToggleButtonColors(
                                    checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(painterResource(option.drawableRes()), contentDescription = stringResource(option.labelRes()))
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
    icon: FolderIcon?,
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
        if (icon != null) Icon(painterResource(icon.drawableRes()), contentDescription = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private const val MAX_TITLE_LENGTH = 40

@StringRes
private fun FolderIcon.labelRes(): Int =
    when (this) {
        FolderIcon.Folder -> R.string.folder_icon_folder
        FolderIcon.Star -> R.string.folder_icon_star
        FolderIcon.Heart -> R.string.folder_icon_heart
        FolderIcon.News -> R.string.folder_icon_news
        FolderIcon.Tech -> R.string.folder_icon_tech
        FolderIcon.Code -> R.string.folder_icon_code
        FolderIcon.Science -> R.string.folder_icon_science
        FolderIcon.Work -> R.string.folder_icon_work
        FolderIcon.Money -> R.string.folder_icon_money
        FolderIcon.Sports -> R.string.folder_icon_sports
        FolderIcon.Games -> R.string.folder_icon_games
        FolderIcon.Music -> R.string.folder_icon_music
        FolderIcon.Movies -> R.string.folder_icon_movies
        FolderIcon.Books -> R.string.folder_icon_books
        FolderIcon.Photos -> R.string.folder_icon_photos
        FolderIcon.Food -> R.string.folder_icon_food
        FolderIcon.Travel -> R.string.folder_icon_travel
        FolderIcon.Nature -> R.string.folder_icon_nature
        FolderIcon.Home -> R.string.folder_icon_home
    }
