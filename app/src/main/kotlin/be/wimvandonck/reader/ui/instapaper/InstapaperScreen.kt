package be.wimvandonck.reader.ui.instapaper

import android.text.format.DateUtils
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.wimvandonck.reader.R
import be.wimvandonck.reader.instapaper.InstapaperBookmark
import be.wimvandonck.reader.instapaper.InstapaperFolder
import be.wimvandonck.reader.instapaper.InstapaperModel
import be.wimvandonck.reader.instapaper.InstapaperTag
import be.wimvandonck.reader.instapaper.InstapaperUiState
import be.wimvandonck.reader.instapaper.InstapaperView
import be.wimvandonck.reader.ui.theme.SettingsScaffold

private enum class Manage { None, Folders, Tags }

/** The user's Instapaper account: Unread, Archive, Liked, folders and tags, with the actions on each article. */
@Composable
fun InstapaperScreen(
    model: InstapaperModel,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(model) { model.start() }
    var manage by rememberSaveable { mutableStateOf(Manage.None) }
    var deleting by remember { mutableStateOf<InstapaperBookmark?>(null) }
    var addingTagTo by remember { mutableStateOf<InstapaperBookmark?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<InstapaperTag?>(null) }

    SettingsScaffold(
        title = stringResource(R.string.section_instapaper),
        backLabel = stringResource(R.string.action_back),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) {
        item(key = "views") { ViewChips(state, model::select) }
        item(key = "manage") {
            Row(Modifier.padding(horizontal = 8.dp)) {
                TextButton(onClick = { manage = if (manage == Manage.Folders) Manage.None else Manage.Folders }) {
                    Text(stringResource(R.string.instapaper_manage_folders))
                }
                TextButton(onClick = { manage = if (manage == Manage.Tags) Manage.None else Manage.Tags }) {
                    Text(stringResource(R.string.instapaper_manage_tags))
                }
            }
        }
        if (manage == Manage.Folders) {
            item(key = "folders-note") {
                Text(
                    stringResource(R.string.instapaper_delete_folder_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            itemsIndexed(state.folders, key = { _, folder -> "f${folder.id}" }) { index, folder ->
                ListItem(
                    headlineContent = { Text(folder.title) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { model.moveFolder(folder, -1) }, enabled = index > 0) {
                                Icon(painterResource(R.drawable.ic_arrow_upward), contentDescription = stringResource(R.string.action_move_up))
                            }
                            IconButton(onClick = { model.moveFolder(folder, 1) }, enabled = index < state.folders.lastIndex) {
                                Icon(painterResource(R.drawable.ic_arrow_downward), contentDescription = stringResource(R.string.action_move_down))
                            }
                            IconButton(onClick = { model.deleteFolder(folder) }) {
                                Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.instapaper_delete_folder))
                            }
                        }
                    },
                )
            }
            item(key = "folders-new") {
                TextButton(onClick = { newFolder = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.folder_new))
                }
            }
        }
        if (manage == Manage.Tags) {
            items(state.tags, key = { tag -> "t${tag.id}" }) { tag ->
                ListItem(
                    headlineContent = { Text(tag.name) },
                    trailingContent = {
                        IconButton(onClick = { renaming = tag }) {
                            Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.instapaper_rename_tag))
                        }
                    },
                )
            }
        }
        state.problem?.let { problem ->
            item(key = "problem") {
                Text(
                    stringResource(problem.messageRes()),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        items(state.items, key = { bookmark -> "b${bookmark.id}" }) { bookmark ->
            BookmarkRow(
                bookmark = bookmark,
                folders = state.folders,
                onOpen = onOpenLink,
                onArchive = { model.toggleArchive(bookmark) },
                onLike = { model.toggleLike(bookmark) },
                onMove = { folderId -> model.moveTo(bookmark, folderId) },
                onDelete = { deleting = bookmark },
                onAddTag = { addingTagTo = bookmark },
                onRemoveTag = { tag -> model.removeTag(bookmark, tag) },
            )
        }
        item(key = "footer") { Footer(state, onLoadMore = model::loadMore) }
    }

    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.instapaper_delete)) },
            text = { Text(stringResource(R.string.instapaper_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        model.delete(target)
                        deleting = null
                    },
                ) { Text(stringResource(R.string.instapaper_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    addingTagTo?.let { target ->
        TextInputDialog(
            title = R.string.instapaper_add_tag,
            label = R.string.instapaper_tag_name,
            initial = "",
            confirm = R.string.action_add,
            onConfirm = { name ->
                model.addTag(target, name)
                addingTagTo = null
            },
            onDismiss = { addingTagTo = null },
        )
    }
    if (newFolder) {
        TextInputDialog(
            title = R.string.folder_new,
            label = R.string.folder_title,
            initial = "",
            confirm = R.string.action_add,
            onConfirm = { title ->
                model.createFolder(title)
                newFolder = false
            },
            onDismiss = { newFolder = false },
        )
    }
    renaming?.let { tag ->
        TextInputDialog(
            title = R.string.instapaper_rename_tag,
            label = R.string.instapaper_tag_name,
            initial = tag.name,
            confirm = R.string.action_save,
            onConfirm = { name ->
                model.renameTag(tag, name)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
}

/** Unread, Archive and Liked, then the folders and tags, as one row of chips. */
@Composable
private fun ViewChips(
    state: InstapaperUiState,
    onSelect: (InstapaperView) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "home") { ListChip(state.view == InstapaperView.Home, stringResource(R.string.filter_unread), null) { onSelect(InstapaperView.Home) } }
        item(key = "archive") { ListChip(state.view == InstapaperView.Archive, stringResource(R.string.instapaper_archive_list), null) { onSelect(InstapaperView.Archive) } }
        item(key = "liked") { ListChip(state.view == InstapaperView.Liked, stringResource(R.string.instapaper_liked), null) { onSelect(InstapaperView.Liked) } }
        items(state.folders, key = { folder -> "f${folder.id}" }) { folder ->
            ListChip(state.view == InstapaperView.InFolder(folder.id), folder.title, R.drawable.ic_folder) { onSelect(InstapaperView.InFolder(folder.id)) }
        }
        items(state.tags, key = { tag -> "t${tag.id}" }) { tag ->
            ListChip(state.view == InstapaperView.WithTag(tag.name), tag.name, R.drawable.ic_label) { onSelect(InstapaperView.WithTag(tag.name)) }
        }
    }
}

@Composable
private fun ListChip(
    selected: Boolean,
    label: String,
    @DrawableRes icon: Int?,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = if (icon != null) {
            { Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else {
            null
        },
    )
}

@Composable
private fun BookmarkRow(
    bookmark: InstapaperBookmark,
    folders: List<InstapaperFolder>,
    onOpen: (String) -> Unit,
    onArchive: () -> Unit,
    onLike: () -> Unit,
    onMove: (Long?) -> Unit,
    onDelete: () -> Unit,
    onAddTag: () -> Unit,
    onRemoveTag: (InstapaperTag) -> Unit,
) {
    val link = bookmark.link
    val title = bookmark.title.ifEmpty { bookmark.url.orEmpty() }
    val meta =
        listOf(
            bookmark.host,
            if (bookmark.time > 0) {
                DateUtils.getRelativeTimeSpanString(bookmark.time * 1000, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
            } else {
                ""
            },
        ).filter { it.isNotEmpty() }.joinToString(" · ")
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = if (link != null) Modifier.clickable(role = Role.Button) { onOpen(link) } else Modifier,
        )
        if (meta.isNotEmpty()) {
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (bookmark.description.isNotBlank()) {
            Text(
                bookmark.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onLike) {
                Icon(
                    painterResource(R.drawable.ic_favorite),
                    contentDescription = stringResource(if (bookmark.liked) R.string.instapaper_unlike else R.string.instapaper_like),
                    tint = if (bookmark.liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onArchive) {
                Icon(
                    painterResource(if (bookmark.archived) R.drawable.ic_unarchive else R.drawable.ic_archive),
                    contentDescription = stringResource(if (bookmark.archived) R.string.instapaper_move_to_unread else R.string.instapaper_archive),
                )
            }
            if (folders.isNotEmpty()) {
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(painterResource(R.drawable.ic_folder), contentDescription = stringResource(R.string.instapaper_move_to))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.filter_unread)) },
                            onClick = {
                                menu = false
                                onMove(null)
                            },
                        )
                        folders.forEach { folder ->
                            DropdownMenuItem(
                                text = { Text(folder.title) },
                                onClick = {
                                    menu = false
                                    onMove(folder.id)
                                },
                            )
                        }
                    }
                }
            }
            if (link != null) {
                IconButton(onClick = { onOpen(link) }) {
                    Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = stringResource(R.string.action_open_in_browser))
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDelete) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.instapaper_delete))
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            bookmark.tags.forEach { tag ->
                InputChip(
                    selected = false,
                    onClick = { onRemoveTag(tag) },
                    label = { Text(tag.name) },
                    trailingIcon = {
                        Icon(
                            painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.instapaper_remove_tag),
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
            TextButton(onClick = onAddTag) { Text(stringResource(R.string.instapaper_add_tag)) }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun Footer(
    state: InstapaperUiState,
    onLoadMore: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        when {
            state.loading || state.loadingMore -> CircularProgressIndicator()
            state.items.isEmpty() && state.problem == null ->
                Text(stringResource(R.string.instapaper_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.canLoadMore -> FilledTonalButton(onClick = onLoadMore) { Text(stringResource(R.string.instapaper_load_more)) }
        }
    }
}

@Composable
private fun TextInputDialog(
    @StringRes title: Int,
    @StringRes label: Int,
    initial: String,
    @StringRes confirm: Int,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(stringResource(label)) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(stringResource(confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
