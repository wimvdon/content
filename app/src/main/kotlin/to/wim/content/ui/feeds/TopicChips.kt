package to.wim.content.ui.feeds

import to.wim.content.ui.theme.ReorderableColumn
import to.wim.content.ui.theme.DragHandle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import to.wim.content.ui.reader.LocalInSheetPage
import to.wim.content.ui.reader.SheetActions
import to.wim.content.ui.reader.SheetHeader
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import to.wim.content.R
import to.wim.content.ui.reader.ContentSheet
import to.wim.content.data.Folder
import to.wim.content.ui.home.rememberFolderIconPainter

/**
 * The topics as chips, the same in the Add feed, Edit feed and Save/Organise sheets: [leading] comes
 * first (No topic, or Like); [extra] are names the article has that aren't topics (yet), shown
 * plainly. With [actions], an **Edit topics** text button at the end opens Manage topics
 * ([ManageTopicsSheet]) as a page of the same sheet ([ContentSheet]); its back arrow (or Back) comes
 * back to the sheet as it was.
 *
 * With [alwaysEditing] (the Manage topics sheet itself) the topics are rows with a grip at the end
 * (the design kit's ReorderableColumn): drag a row to reorder them, tap it to rename it, change its icon or remove it;
 * renaming it to another topic's name joins the two. Removing (the topic sheet's trash can) asks first, in a dialog. **New
 * topic** (the topic sheet: a name and an icon) comes after them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopicChipRow(
    topics: List<Folder>,
    selected: (Folder) -> Boolean,
    onToggle: (Folder) -> Unit,
    actions: TopicEditActions?,
    onCreated: (title: String) -> Unit,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    extra: List<String> = emptyList(),
    isExtraSelected: (String) -> Boolean = { true },
    onToggleExtra: (String) -> Unit = {},
    /** The Manage topics sheet: always the editing chips, with New topic, and no Edit topics. */
    alwaysEditing: Boolean = false,
) {
    var managing by rememberSaveable { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val editable = actions != null && alwaysEditing
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (editable) {
            // Rows, as every reorderable list (DESIGN.md, Reordering): the tag's icon and name, the
            // grip at the end; tap a row to rename it, change its icon or remove it.
            val editLabel = stringResource(R.string.folder_edit)
            ReorderableColumn(
                items = topics,
                key = { it.id },
                onMove = { from, to -> actions!!.onReorder(topics.map { it.id }.toMutableList().apply { add(to, removeAt(from)) }) },
            ) { topic ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(onClickLabel = editLabel) { editing = topic.id },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(rememberFolderIconPainter(topic.icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(topic.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    DragHandle(handleModifier)
                }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (!editable) {
                leading?.invoke()
                topics.forEach { topic ->
                    // No check mark: the fill shows it, and the chips keep their width and place.
                    FilterChip(
                        selected = selected(topic),
                        onClick = { onToggle(topic) },
                        enabled = enabled,
                        label = { Text(topic.title) },
                        leadingIcon = { Icon(rememberFolderIconPainter(topic.icon), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
                    )
                }
                extra.forEach { name ->
                    FilterChip(selected = isExtraSelected(name), onClick = { onToggleExtra(name) }, enabled = enabled, label = { Text(name) })
                }
            }
            // New topic and Edit topics are actions, not topics: text buttons, so they don't look like chips.
            if (actions != null) {
                if (editable) {
                    // Manage tags: New tag is the sheet's action (tonal, like Add feed in Manage feeds).
                    FilledTonalButton(onClick = { creating = true }, enabled = enabled, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Text(stringResource(R.string.folder_new), modifier = Modifier.padding(start = 8.dp))
                    }
                } else {
                    TextButton(onClick = { managing = true }, enabled = enabled, contentPadding = ButtonDefaults.TextButtonWithIconContentPadding) {
                        Icon(painterResource(R.drawable.ic_edit), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Text(stringResource(R.string.topics_edit), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }

    if (managing && actions != null) {
        ManageTopicsSheet(topics = topics, actions = actions, onDismiss = { managing = false })
    }
    if (creating && actions != null) {
        FolderSheet(
            folder = null,
            onSave = { title, icon ->
                creating = false
                actions.onNew(title, icon)
                onCreated(title.trim())
            },
            onDismiss = { creating = false },
        )
    }
    val edited = topics.firstOrNull { it.id == editing }
    if (edited != null && actions != null) {
        FolderSheet(
            folder = edited,
            onSave = { title, icon ->
                editing = null
                actions.onEdit(edited, title, icon)
            },
            onDismiss = { editing = null },
            // Asked in the topic sheet's own dialog first.
            onRemove = {
                editing = null
                actions.onRemove(edited)
            },
        )
    }
}

/**
 * Manage topics as a sheet: from Settings, and from Edit topics in the Add feed, Edit feed and
 * Save/Organise sheets, as a page of that sheet (with a back arrow instead of Done). The topics as editing chips (drag to reorder, tap to rename,
 * change the icon or remove; a name already taken joins the two), then New topic. Every change is
 * stored at once, so the sheet has **Done** at the bottom right instead of Cancel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageTopicsSheet(
    topics: List<Folder>,
    actions: TopicEditActions,
    onDismiss: () -> Unit,
) {
    ContentSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetHeader(stringResource(R.string.manage_title), onCancel = onDismiss)
            Text(stringResource(R.string.manage_topics_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TopicChipRow(topics = topics, selected = { false }, onToggle = {}, actions = actions, onCreated = {}, alwaysEditing = true)
            // Every change is stored at once: Done at the bottom right (on a page, the back arrow).
            if (!LocalInSheetPage.current) {
                // Done only closes: a text button.
                SheetActions(onCancel = onDismiss, cancel = false) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
                }
            }
        }
    }
}

