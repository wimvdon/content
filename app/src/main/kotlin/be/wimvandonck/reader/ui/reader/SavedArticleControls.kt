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

/** A saved Instapaper article in the reader: its state and what its buttons do. Null for feed articles. */
class SavedArticle(
    val bookmark: InstapaperBookmark,
    val folders: List<InstapaperFolder>,
    /** All the account's tags, to add one with a tap. */
    val tags: List<InstapaperTag>,
    val onLike: () -> Unit,
    val onArchive: () -> Unit,
    /** Into a folder, or back to Unread when null. */
    val onMove: (Long?) -> Unit,
    val onAddTag: (String) -> Unit,
    val onRemoveTag: (InstapaperTag) -> Unit,
    val onDelete: () -> Unit,
)

/** Which of the saved article's dialogs is open. */
enum class SavedDialog { Move, Tags, Delete }

/** Like and Archive, in the reader's top bar instead of Save. */
@Composable
fun SavedArticleButtons(saved: SavedArticle) {
    val b = saved.bookmark
    IconButton(onClick = saved.onLike) {
        Icon(
            painterResource(R.drawable.ic_favorite),
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

/** Move to folder, Tags and Delete for good, in the reader's ⋮ menu instead of Mark as unread. */
@Composable
fun SavedArticleMenuItems(onOpen: (SavedDialog) -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.instapaper_move_to)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_folder), contentDescription = null) },
        onClick = { onOpen(SavedDialog.Move) },
    )
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
                text = { Text(stringResource(R.string.instapaper_delete_body)) },
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

/** Unread or one of the folders, like choosing a feed's folder. */
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
                option(null, stringResource(R.string.filter_unread))
                saved.folders.forEach { option(it.id, it.title) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** The article's tags (tap one to remove it), a new one to type, and the account's other tags to add. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsDialog(
    saved: SavedArticle,
    onDismiss: () -> Unit,
) {
    val b = saved.bookmark
    var name by rememberSaveable { mutableStateOf("") }
    val others = saved.tags.filter { tag -> b.tags.none { it.name.equals(tag.name, ignoreCase = true) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.instapaper_tags)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    b.tags.forEach { tag ->
                        InputChip(
                            selected = true,
                            onClick = { saved.onRemoveTag(tag) },
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
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.instapaper_add_tag)) },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                saved.onAddTag(name)
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
                                onClick = { saved.onAddTag(tag.name) },
                                label = { Text(tag.name) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp)) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
