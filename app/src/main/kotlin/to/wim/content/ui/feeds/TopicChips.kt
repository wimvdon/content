package to.wim.content.ui.feeds

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
import to.wim.content.ui.reader.SheetHeader
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
 * With [alwaysEditing] (the Manage topics sheet itself) every chip has a drag handle and a pen: drag
 * a chip to reorder the topics, tap it (or the pen) to rename it, change its icon or remove it;
 * renaming it to another topic's name joins the two. Removing asks first, under the chips. **New
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
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    val editable = actions != null && alwaysEditing
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (editable) {
            ReorderableChips(
                items = topics,
                key = { it.id },
                label = { it.title },
                icon = { Icon(rememberFolderIconPainter(it.icon), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
                onReorder = { actions!!.onReorder(it) },
                onEdit = { editing = it.id },
            )
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
                    TextButton(onClick = { creating = true }, enabled = enabled, contentPadding = ButtonDefaults.TextButtonWithIconContentPadding) {
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
        val toRemove = topics.firstOrNull { it.id == removing }
        if (toRemove != null && actions != null) {
            // Remove topic? asks here, in the sheet, instead of a dialog over it.
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.folder_remove_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.folder_remove_body, toRemove.title), style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { removing = null }) { Text(stringResource(R.string.action_cancel)) }
                    TextButton(
                        onClick = {
                            removing = null
                            actions.onRemove(toRemove)
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.action_remove)) }
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
            onRemove = {
                editing = null
                removing = edited.id
            },
        )
    }
}

/**
 * Manage topics as a sheet: from Settings, and from Edit topics in the Add feed, Edit feed and
 * Save/Organise sheets, as a page of that sheet (with a back arrow instead of Done). The topics as editing chips (drag to reorder, tap to rename,
 * change the icon or remove; a name already taken joins the two), then New topic. Every change is
 * stored at once, so the sheet has **Done** at the top right instead of Cancel.
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
            SheetHeader(stringResource(R.string.manage_title), onCancel = onDismiss, closeLabel = stringResource(R.string.action_done))
            Text(stringResource(R.string.manage_topics_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TopicChipRow(topics = topics, selected = { false }, onToggle = {}, actions = actions, onCreated = {}, alwaysEditing = true)
        }
    }
}

/** Chips to reorder and edit in one group: the topics while editing ([ReorderableChipGroups]). */
@Composable
internal fun <T> ReorderableChips(
    items: List<T>,
    key: (T) -> String,
    label: (T) -> String,
    icon: @Composable (T) -> Unit,
    onReorder: (keys: List<String>) -> Unit,
    onEdit: (T) -> Unit,
    editLabel: String = stringResource(R.string.folder_edit),
    isError: (T) -> Boolean = { false },
) {
    ReorderableChipGroups(
        groups = listOf(ChipGroup("", items)),
        key = key,
        label = label,
        icon = icon,
        onReorder = { _, keys -> onReorder(keys) },
        onEdit = onEdit,
        editLabel = editLabel,
        isError = isError,
    )
}
