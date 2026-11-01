package to.wim.content.ui.feeds

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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Spacer
import to.wim.content.ui.reader.SheetActions
import to.wim.content.ui.reader.SheetHeader
import to.wim.content.ui.reader.SheetSection
import to.wim.content.ui.home.TopicChip
import to.wim.content.data.chosenTopic
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.LaunchedEffect
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
import to.wim.content.R
import to.wim.content.ui.reader.ConfirmRemoveDialog
import to.wim.content.ui.reader.ContentSheet
import to.wim.content.data.Feed
import to.wim.content.data.Folder
import to.wim.content.data.IconCatalog
import to.wim.content.rssReaderApp
import to.wim.content.ui.home.rememberFolderIconPainter
import androidx.compose.foundation.selection.selectableGroup
import to.wim.content.ui.home.FeedIcon
import to.wim.content.ui.home.FeedIconSize

/**
 * New folder ([folder] null) or edit one: a title, and an icon from the whole Material Symbols
 * set, searchable by English name or topic. A bottom sheet (#256): the icon grid is too much for a
 * dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FolderSheet(
    folder: Folder?,
    onSave: (title: String, icon: String) -> Unit,
    onDismiss: () -> Unit,
    /** Remove (the header's trash can) when editing an existing topic; called once confirmed. */
    onRemove: (() -> Unit)? = null,
) {
    var removing by rememberSaveable { mutableStateOf(false) }
    val catalog = LocalContext.current.rssReaderApp.iconCatalog
    var title by rememberSaveable { mutableStateOf(folder?.title.orEmpty()) }
    var icon by rememberSaveable { mutableStateOf(folder?.icon ?: IconCatalog.DEFAULT) }
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query) { catalog.search(query) }
    ContentSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SheetHeader(
                stringResource(if (folder == null) R.string.folder_new else R.string.folder_edit),
                onCancel = onDismiss,
                onDelete = if (folder != null && onRemove != null) ({ removing = true }) else null,
            )
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
            // Cancel at the bottom left, Save at the right; Remove is the header's trash can.
            SheetActions(onCancel = onDismiss) {
                Button(onClick = { onSave(title.trim(), icon) }, enabled = title.isNotBlank()) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
    if (removing && folder != null && onRemove != null) {
        ConfirmRemoveDialog(
            title = stringResource(R.string.folder_remove_title),
            text = stringResource(R.string.folder_remove_body, folder.title),
            onRemove = onRemove,
            onDismiss = { removing = false },
        )
    }
}

private val ICON_GRID_HEIGHT = 240.dp

/**
 * Edit a feed, as a sheet like Add feed: its name (blank brings back the feed's own title), its
 * address, why its last download failed (if it did), its topic as chips (No topic, then every
 * topic, with Edit topics as in Add feed), Remove as a trash can at the top right (asking first,
 * in a dialog), Cancel at the bottom left and Save at the right, as in every sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedSheet(
    feed: Feed,
    topics: List<Folder>,
    problem: String?,
    onSave: (title: String, topicId: String?) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
    topicActions: TopicEditActions? = null,
) {
    var title by rememberSaveable { mutableStateOf(feed.title) }
    var topicId by rememberSaveable { mutableStateOf(feed.folderId?.takeIf { id -> topics.any { it.id == id } }) }
    var removing by rememberSaveable { mutableStateOf(false) }
    // A topic made in the sheet: chosen once it exists.
    var made by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(topics, made) {
        val name = made ?: return@LaunchedEffect
        topics.firstOrNull { it.title.equals(name, ignoreCase = true) }?.let {
            topicId = it.id
            made = null
        }
    }
    val chosen = chosenTopic(topicId, topics)
    ContentSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetHeader(stringResource(R.string.feed_edit), onCancel = onDismiss, onDelete = { removing = true })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FeedIcon(feed, FeedIconSize.Large)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(MAX_FEED_TITLE_LENGTH) },
                    label = { Text(stringResource(R.string.feed_name)) },
                    supportingText = { Text(stringResource(R.string.feed_name_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.weight(1f),
                )
            }
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
            SheetSection(stringResource(R.string.feed_folder))
            TopicChipRow(
                topics = topics,
                selected = { chosen == it.id },
                // One topic: tapping the chosen one again is No topic.
                onToggle = { topicId = if (chosen == it.id) null else it.id },
                actions = topicActions,
                onCreated = { made = it },
                leading = { TopicChip(stringResource(R.string.folder_none), icon = null, selected = chosen == null, enabled = true) { topicId = null } },
            )
            SheetActions(onCancel = onDismiss) {
                Button(onClick = { onSave(title, chosen) }) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
    if (removing) {
        ConfirmRemoveDialog(
            title = stringResource(R.string.feeds_remove_title),
            text = stringResource(R.string.feeds_remove_body, feed.title),
            onRemove = onRemove,
            onDismiss = { removing = false },
        )
    }
}

private const val MAX_TITLE_LENGTH = 40
private const val MAX_FEED_TITLE_LENGTH = 80
