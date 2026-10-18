package be.wimvandonck.reader.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import be.wimvandonck.reader.R
import be.wimvandonck.reader.instapaper.InstapaperBookmark
import be.wimvandonck.reader.instapaper.InstapaperFolder
import be.wimvandonck.reader.instapaper.InstapaperTag

/** A saved article in the reader: its state and what its buttons do. Null for feed articles. */
class SavedArticle(
    val bookmark: InstapaperBookmark,
    /** Saved in the Instapaper account; false when saved on this phone (no folders). */
    val account: Boolean,
    val folders: List<InstapaperFolder>,
    /** All the account's tags, to add one with a tap. */
    val tags: List<InstapaperTag>,
    val onLike: () -> Unit,
    val onArchive: () -> Unit,
    /** Into a folder, or back to Saved when null. */
    val onMove: (Long?) -> Unit,
    /** Into a new folder with this name, made on the spot. */
    val onMoveToNewFolder: (String) -> Unit,
    val onAddTag: (String) -> Unit,
    val onRemoveTag: (InstapaperTag) -> Unit,
    val onDelete: () -> Unit,
)

/** Which of the saved article's dialogs is open. */
enum class SavedDialog { Move, Tags, Delete }

/** Like (a filled heart once liked) and Archive, in the reader's top bar instead of Save. */
@Composable
fun SavedArticleButtons(saved: SavedArticle) {
    val b = saved.bookmark
    IconButton(onClick = saved.onLike) {
        Icon(
            painterResource(if (b.liked) R.drawable.ic_favorite_filled else R.drawable.ic_favorite),
            contentDescription = stringResource(if (b.liked) R.string.instapaper_unlike else R.string.instapaper_like),
            tint = if (b.liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    IconButton(onClick = saved.onArchive) {
        Icon(
            painterResource(if (b.archived) R.drawable.ic_unarchive else R.drawable.ic_archive),
            contentDescription = stringResource(if (b.archived) R.string.instapaper_move_to_unread else R.string.instapaper_archive),
        )
    }
}

/**
 * Move to folder (with an Instapaper account), Tags and Delete for good, in the reader's ⋮ menu
 * instead of Mark as unread.
 */
@Composable
fun SavedArticleMenuItems(
    withFolders: Boolean,
    onOpen: (SavedDialog) -> Unit,
) {
    if (withFolders) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.instapaper_move_to)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_folder), contentDescription = null) },
            onClick = { onOpen(SavedDialog.Move) },
        )
    }
    DropdownMenuItem(
        text = { Text(stringResource(R.string.instapaper_tags)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_label), contentDescription = null) },
        onClick = { onOpen(SavedDialog.Tags) },
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.instapaper_delete)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
        onClick = { onOpen(SavedDialog.Delete) },
    )
}

@Composable
fun SavedArticleDialog(
    saved: SavedArticle,
    dialog: SavedDialog,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        SavedDialog.Move -> MoveDialog(saved, onDismiss)
        SavedDialog.Tags -> TagsDialog(saved, onDismiss)
        SavedDialog.Delete ->
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.instapaper_delete)) },
                text = { Text(stringResource(if (saved.account) R.string.instapaper_delete_body else R.string.saved_delete_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        onDismiss()
                        saved.onDelete()
                    }) { Text(stringResource(R.string.instapaper_delete)) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
            )
    }
}

/** Saved or one of the folders, like choosing a feed's folder. */
@Composable
private fun MoveDialog(
    saved: SavedArticle,
    onDismiss: () -> Unit,
) {
    val b = saved.bookmark
    fun choose(folderId: Long?) {
        onDismiss()
        if (folderId != b.folderId || b.archived) saved.onMove(folderId)
    }

    @Composable
    fun option(
        folderId: Long?,
        label: String,
    ) {
        val selected = !b.archived && b.folderId == folderId
        Row(
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton) { choose(folderId) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RadioButton(selected = selected, onClick = null)
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.instapaper_move_to)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                option(null, stringResource(R.string.saved_list))
                saved.folders.forEach { option(it.id, it.title) }
                NewFolder(Modifier.padding(top = 8.dp)) { title ->
                    onDismiss()
                    saved.onMoveToNewFolder(title)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** The article's tags (tap one to remove it), a new one to type, and the account's other tags to add. */
@Composable
private fun TagsDialog(
    saved: SavedArticle,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.instapaper_tags)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TagsEditor(saved.bookmark, saved.tags, saved.onAddTag, saved.onRemoveTag)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

/** The tags of [bookmark] as removable chips, a field for a new one, and [allTags] it doesn't have yet to add with a tap. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsEditor(
    bookmark: InstapaperBookmark,
    allTags: List<InstapaperTag>,
    onAddTag: (String) -> Unit,
    onRemoveTag: (InstapaperTag) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    val others = allTags.filter { tag -> bookmark.tags.none { it.name.equals(tag.name, ignoreCase = true) } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (bookmark.tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                bookmark.tags.forEach { tag ->
                    InputChip(
                        selected = true,
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
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.instapaper_add_tag)) },
            singleLine = true,
            trailingIcon = {
                IconButton(
                    onClick = {
                        onAddTag(name)
                        name = ""
                    },
                    enabled = name.isNotBlank(),
                ) { Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.instapaper_add_tag)) }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (others.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                others.forEach { tag ->
                    AssistChip(
                        onClick = { onAddTag(tag.name) },
                        label = { Text(tag.name) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
        }
    }
}

/**
 * Opens right after a feed article is saved: like it, archive it, put it in a folder (with an
 * Instapaper account) and tag it at once. Every tap applies straight away; closing the sheet leaves
 * the article as it is (in Saved).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun JustSavedSheet(
    bookmark: InstapaperBookmark,
    /** Saved in the Instapaper account; false when saved on this phone (no folders). */
    account: Boolean,
    folders: List<InstapaperFolder>,
    tags: List<InstapaperTag>,
    onLike: () -> Unit,
    onArchive: () -> Unit,
    onMove: (Long?) -> Unit,
    onMoveToNewFolder: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (InstapaperTag) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(if (account) R.string.instapaper_saved else R.string.saved_done),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = bookmark.liked,
                    onClick = onLike,
                    label = { Text(stringResource(R.string.instapaper_like)) },
                    leadingIcon = {
                        Icon(
                            painterResource(if (bookmark.liked) R.drawable.ic_favorite_filled else R.drawable.ic_favorite),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
                FilterChip(
                    selected = bookmark.archived,
                    onClick = onArchive,
                    label = { Text(stringResource(R.string.instapaper_archive)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_archive), contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
            if (account) {
                Text(stringResource(R.string.instapaper_folder), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !bookmark.archived && bookmark.folderId == null,
                        onClick = { if (bookmark.folderId != null || bookmark.archived) onMove(null) },
                        label = { Text(stringResource(R.string.saved_list)) },
                    )
                    folders.forEach { folder ->
                        FilterChip(
                            selected = bookmark.folderId == folder.id,
                            onClick = { if (bookmark.folderId != folder.id) onMove(folder.id) },
                            label = { Text(folder.title) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_folder), contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                    }
                }
                NewFolder(onCreate = onMoveToNewFolder)
            }
            Text(stringResource(R.string.instapaper_tags), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            TagsEditor(bookmark, tags, onAddTag, onRemoveTag)
            Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.action_done)) }
        }
    }
}

/**
 * "New folder" under a list of folders: a tap shows a field for its name, and adding makes the
 * folder and puts the article in it ([onCreate] gets the name).
 */
@Composable
private fun NewFolder(
    modifier: Modifier = Modifier,
    onCreate: (String) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    if (!open) {
        TextButton(onClick = { open = true }, modifier = modifier) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.folder_new), modifier = Modifier.padding(start = 8.dp))
        }
    } else {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.folder_new)) },
            singleLine = true,
            trailingIcon = {
                IconButton(
                    onClick = {
                        onCreate(name.trim())
                        name = ""
                        open = false
                    },
                    enabled = name.isNotBlank(),
                ) { Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.action_add)) }
            },
            modifier = modifier.fillMaxWidth(),
        )
    }
}
