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
 * The topics as chips in the Add feed and Organise sheets (#314), each with its icon, the same in
 * both. [leading] comes first (No topic, or Like); [extra] are names the article has that aren't
 * topics (yet), shown plainly. At the end, with [actions], two text buttons (not chips, #314): **New topic** (the topic sheet: a name
 * and an icon; the new topic is chosen at once through [onCreated]) and **Edit**. Edit puts a drag
 * handle and a pen on every topic chip, and turns into **Done**: drag a chip to reorder the topics,
 * tap it (or the pen) to rename it, change its icon or remove it; renaming it to another topic's
 * name joins the two. Removing asks first, under the chips: no dialog opens over the sheet.
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
) {
    var editMode by rememberSaveable { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    val editable = actions != null && editMode
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (editable) {
            ReorderableTopicChips(topics, onReorder = { actions!!.onReorder(it) }, onEdit = { editing = it.id })
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
            // New topic and Edit are actions, not topics: text buttons, so they don't look like chips.
            if (actions != null) {
                if (!editable) {
                    TextButton(onClick = { creating = true }, enabled = enabled, contentPadding = ButtonDefaults.TextButtonWithIconContentPadding) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Text(stringResource(R.string.folder_new), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (topics.isNotEmpty() || editable) {
                    TextButton(
                        onClick = {
                            editMode = !editMode
                            removing = null
                        },
                        enabled = enabled,
                        contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                    ) {
                        Icon(
                            painterResource(if (editable) R.drawable.ic_check else R.drawable.ic_edit),
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Text(stringResource(if (editable) R.string.action_done else R.string.action_edit), modifier = Modifier.padding(start = 8.dp))
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
 * The topics' chips while editing (#314): a drag handle, the icon, the name and a pen. Dragging a
 * chip moves it among the others at once (they wrap over lines, `ChipOrder`); the new order is
 * stored when it is dropped. Tapping a chip edits it. Screen readers get Move up and Move down.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReorderableTopicChips(
    topics: List<Folder>,
    onReorder: (ids: List<String>) -> Unit,
    onEdit: (Folder) -> Unit,
) {
    // A working copy, so a dragged chip moves before the new order is stored.
    val order = remember { mutableStateListOf<Folder>() }
    var dragged by remember { mutableStateOf<String?>(null) }
    // Where the finger is, within the row, and where it touched the dragged chip.
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var grip by remember { mutableStateOf(Offset.Zero) }
    val boxes = remember { mutableStateMapOf<String, ChipOrder.Box>() }
    if (dragged == null && order.map { it.id to it.title to it.icon } != topics.map { it.id to it.title to it.icon }) {
        order.clear()
        order.addAll(topics)
    }
    val currentTopics by rememberUpdatedState(topics)
    val currentOnReorder by rememberUpdatedState(onReorder)
    val haptics = LocalHapticFeedback.current
    val reorder = stringResource(R.string.action_reorder)
    val moveUp = stringResource(R.string.action_move_up)
    val moveDown = stringResource(R.string.action_move_down)
    val editLabel = stringResource(R.string.folder_edit)
    LaunchedEffect(topics) { boxes.keys.retainAll(topics.map { it.id }.toSet()) }

    fun step(
        id: String,
        by: Int,
    ): Boolean {
        val ids = currentTopics.map { it.id }
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
            val id = topic.id
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
                                contentDescription = "$editLabel: ${topic.title}"
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
                                                val list = order.map { it.id }
                                                val from = list.indexOf(id)
                                                val to = ChipOrder.indexAt(list.map { boxes[it] ?: ChipOrder.Box(0f, 0f, 0f, 0f) }, pointer.x, pointer.y)
                                                if (to >= 0 && to != from) order.add(to, order.removeAt(from))
                                            },
                                            onDragEnd = {
                                                dragged = null
                                                val ids = order.map { it.id }
                                                if (ids != currentTopics.map { it.id }) currentOnReorder(ids)
                                            },
                                            onDragCancel = {
                                                dragged = null
                                                order.clear()
                                                order.addAll(currentTopics)
                                            },
                                        )
                                    },
                        )
                        Icon(rememberFolderIconPainter(topic.icon), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
                        Text(topic.title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp))
                        Icon(painterResource(R.drawable.ic_edit), contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
