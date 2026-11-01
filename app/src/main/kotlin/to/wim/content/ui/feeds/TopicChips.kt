package to.wim.content.ui.feeds

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import to.wim.content.ui.reader.SheetHeader
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import to.wim.content.R
import to.wim.content.data.ChipOrder
import to.wim.content.data.Folder
import to.wim.content.ui.home.rememberFolderIconPainter

/**
 * The topics as chips, the same in the Add feed, Edit feed and Save/Organise sheets: [leading] comes
 * first (No topic, or Like); [extra] are names the article has that aren't topics (yet), shown
 * plainly. With [actions], an **Edit topics** text button at the end opens the Manage topics sheet
 * over the sheet ([ManageTopicsSheet]); Done or Back comes back to the sheet as it was.
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
 * Save/Organise sheets, over them. The topics as editing chips (drag to reorder, tap to rename,
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
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
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

/**
 * Chips to reorder and edit (#314): topics while editing, and feeds in Settings › Manage feeds. Each
 * has a drag handle, its icon, its name and a pen. Dragging a chip moves it among the others at once
 * (they wrap over lines, `ChipOrder`); the new order is stored when it is dropped. Tapping a chip
 * edits it. Screen readers get Move up and Move down. [isError] draws a chip's name in the error
 * colour (a feed that fails to download).
 */
@OptIn(ExperimentalLayoutApi::class)
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
    val topics = items
    // A working copy, so a dragged chip moves before the new order is stored.
    val order = remember { mutableStateListOf<T>() }
    var dragged by remember { mutableStateOf<String?>(null) }
    // Where the finger is, within the row, and where it touched the dragged chip.
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var grip by remember { mutableStateOf(Offset.Zero) }
    val boxes = remember { mutableStateMapOf<String, ChipOrder.Box>() }
    if (dragged == null && order.toList() != topics) {
        order.clear()
        order.addAll(topics)
    }
    val currentTopics by rememberUpdatedState(topics)
    val currentOnReorder by rememberUpdatedState(onReorder)
    val haptics = LocalHapticFeedback.current
    val reorder = stringResource(R.string.action_reorder)
    val moveUp = stringResource(R.string.action_move_up)
    val moveDown = stringResource(R.string.action_move_down)
    LaunchedEffect(topics) { boxes.keys.retainAll(topics.map(key).toSet()) }

    fun step(
        id: String,
        by: Int,
    ): Boolean {
        val ids = currentTopics.map(key)
        val from = ids.indexOf(id)
        val moved = ChipOrder.moved(ids, from, from + by)
        if (moved === ids) return false
        currentOnReorder(moved)
        return true
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        order.forEach { topic ->
            val id = key(topic)
            androidx.compose.runtime.key(id) {
                val isDragging = dragged == id
                Surface(
                    onClick = { onEdit(topic) },
                    shape = FilterChipDefaults.shape,
                    color = if (isDragging) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = FilterChipDefaults.filterChipBorder(enabled = true, selected = false),
                    shadowElevation = if (isDragging) 6.dp else 0.dp,
                    modifier =
                        Modifier
                            .height(40.dp)
                            .zIndex(if (isDragging) 1f else 0f)
                            .onGloballyPositioned { c ->
                                val b = c.boundsInParent()
                                boxes[id] = ChipOrder.Box(b.left, b.top, b.right, b.bottom)
                            }.graphicsLayer {
                                if (isDragging) {
                                    val box = boxes[id]
                                    if (box != null) {
                                        translationX = pointer.x - grip.x - box.left
                                        translationY = pointer.y - grip.y - box.top
                                    }
                                }
                            }.semantics(mergeDescendants = true) {
                                contentDescription = "$editLabel: ${label(topic)}"
                                customActions =
                                    listOf(
                                        CustomAccessibilityAction(moveUp) { step(id, -1) },
                                        CustomAccessibilityAction(moveDown) { step(id, 1) },
                                    )
                            },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
                        Icon(
                            painterResource(R.drawable.ic_drag_handle),
                            contentDescription = reorder,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier =
                                Modifier
                                    .padding(start = 4.dp)
                                    .size(32.dp)
                                    .padding(6.dp)
                                    .pointerInput(id) {
                                        detectDragGestures(
                                            onDragStart = { at ->
                                                val box = boxes[id] ?: return@detectDragGestures
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                // The handle sits 4dp in from the chip's left edge.
                                                grip = Offset(at.x + 4.dp.toPx(), at.y + (box.bottom - box.top - size.height) / 2)
                                                pointer = Offset(box.left + grip.x, box.top + grip.y)
                                                dragged = id
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                pointer += amount
                                                val list = order.map(key)
                                                val from = list.indexOf(id)
                                                val to = ChipOrder.indexAt(list.map { boxes[it] ?: ChipOrder.Box(0f, 0f, 0f, 0f) }, pointer.x, pointer.y)
                                                if (to >= 0 && to != from) order.add(to, order.removeAt(from))
                                            },
                                            onDragEnd = {
                                                dragged = null
                                                val ids = order.map(key)
                                                if (ids != currentTopics.map(key)) currentOnReorder(ids)
                                            },
                                            onDragCancel = {
                                                dragged = null
                                                order.clear()
                                                order.addAll(currentTopics)
                                            },
                                        )
                                    },
                        )
                        icon(topic)
                        Text(
                            label(topic),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isError(topic)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp).widthIn(max = 200.dp),
                        )
                        Icon(painterResource(R.drawable.ic_edit), contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
