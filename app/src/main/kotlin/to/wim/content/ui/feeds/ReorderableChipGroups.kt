package to.wim.content.ui.feeds

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import to.wim.content.R
import to.wim.content.data.ChipOrder

/** One group of chips to reorder: its [id], a [header] above it (none: no header) and its [items]. */
internal class ChipGroup<T>(
    val id: String,
    val items: List<T>,
    val header: (@Composable () -> Unit)? = null,
)

/**
 * Chips to reorder and edit (#314), in groups: the topics while editing (one group), and the feeds
 * by topic in Manage feeds. Each chip has a drag handle, its icon, its name and a pen. Dragging a
 * chip moves it among the others at once (they wrap over lines, `ChipOrder`); with [onMove] it can
 * also be dragged into another group, an empty one too ([emptyLabel] marks its place). The new
 * order, or the new group, is stored when it is dropped. Tapping a chip edits it. Screen readers get
 * Move up and Move down within the group. [isError] draws a chip's name in the error colour (a feed
 * that fails to download).
 *
 * The drag is followed by the whole row, not by the chip: a chip dragged into another group moves to
 * another place among the row's children, and a drag held by the chip itself could end there (the
 * drop into another topic was then lost). A press on a chip's handle starts it; the row takes the
 * gesture before the chips and the sheet (it is read in the first pass), so the sheet doesn't scroll
 * and the chip isn't tapped while dragging. A tap on the handle still opens the chip.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> ReorderableChipGroups(
    groups: List<ChipGroup<T>>,
    key: (T) -> String,
    label: (T) -> String,
    icon: @Composable (T) -> Unit,
    onReorder: (groupId: String, keys: List<String>) -> Unit,
    onEdit: (T) -> Unit,
    /** A chip dropped into another group: that group and its keys in their new order. */
    onMove: ((item: T, groupId: String, keys: List<String>) -> Unit)? = null,
    /** Shown in an empty group, as the place to drop a chip; null: nothing. */
    emptyLabel: String? = null,
    editLabel: String = stringResource(R.string.folder_edit),
    isError: (T) -> Boolean = { false },
) {
    val stored = groups.map { group -> group.items.map(key) }
    // A working copy while dragging, so a dragged chip moves before the new order is stored; kept
    // after the drop until the stored order catches up.
    var working by remember { mutableStateOf<List<List<String>>?>(null) }
    var dragged by remember { mutableStateOf<String?>(null) }
    var dragFrom by remember { mutableIntStateOf(-1) }
    // Where the finger is, within the window, and where it touched the dragged chip.
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var grip by remember { mutableStateOf(Offset.Zero) }
    // Where each chip is, and each empty group's place (by EMPTY + the group's id).
    val boxes = remember { mutableStateMapOf<String, ChipOrder.Box>() }
    // Where each chip's drag handle is, and where the row itself is, within the window.
    val handles = remember { mutableStateMapOf<String, ChipOrder.Box>() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(stored) {
        if (dragged == null) working = null
        val live = stored.flatten().toSet() + groups.map { EMPTY + it.id }
        boxes.keys.retainAll(live)
        handles.keys.retainAll(live)
    }
    val order = working ?: stored
    val byKey = groups.flatMap { it.items }.associateBy(key)
    val currentGroups by rememberUpdatedState(groups)
    val currentStored by rememberUpdatedState(stored)
    val currentOnReorder by rememberUpdatedState(onReorder)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentByKey by rememberUpdatedState(byKey)
    val haptics = LocalHapticFeedback.current
    val reorder = stringResource(R.string.action_reorder)
    val moveUp = stringResource(R.string.action_move_up)
    val moveDown = stringResource(R.string.action_move_down)

    fun step(
        id: String,
        by: Int,
    ): Boolean {
        val group = currentStored.indexOfFirst { id in it }
        if (group < 0) return false
        val ids = currentStored[group]
        val from = ids.indexOf(id)
        val moved = ChipOrder.moved(ids, from, from + by)
        if (moved === ids) return false
        currentOnReorder(currentGroups[group].id, moved)
        return true
    }

    fun drop(id: String) {
        val now = working ?: return
        dragged = null
        val to = now.indexOfFirst { id in it }
        val from = dragFrom
        when {
            to < 0 || from < 0 -> working = null
            to != from -> {
                val item = currentByKey[id]
                val move = currentOnMove
                if (item != null && move != null) move(item, currentGroups[to].id, now[to]) else working = null
            }
            now[to] != currentStored[to] -> currentOnReorder(currentGroups[to].id, now[to])
            else -> working = null
        }
    }

    fun start(
        id: String,
        at: Offset,
    ) {
        val box = boxes[id] ?: return
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        grip = Offset(at.x - box.left, at.y - box.top)
        pointer = at
        working = currentStored
        dragFrom = currentStored.indexOfFirst { id in it }
        dragged = id
    }

    fun moveTo(id: String) {
        val now = working ?: return
        val own = now.indexOfFirst { id in it }
        val targets =
            now.flatMapIndexed { gi, ids ->
                if (ids.isEmpty()) {
                    val box = boxes[EMPTY + currentGroups[gi].id]
                    if (box == null || currentOnMove == null) emptyList() else listOf(ChipOrder.Target(ChipOrder.Slot(gi, 0), box, empty = true))
                } else if (gi != own && currentOnMove == null) {
                    emptyList()
                } else {
                    ids.mapIndexedNotNull { i, k -> boxes[k]?.let { ChipOrder.Target(ChipOrder.Slot(gi, i), it) } }
                }
            }
        val slot = ChipOrder.slotAt(targets, own, pointer.x, pointer.y) ?: return
        val next = ChipOrder.movedAcross(now, id, slot)
        if (next !== now) working = next
    }

    FlowRow(
        modifier =
            Modifier
                .onGloballyPositioned { c -> origin = c.boundsInRoot().topLeft }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val at = origin + down.position
                        val id = handles.entries.firstOrNull { it.value.contains(at.x, at.y) }?.key ?: return@awaitEachGesture
                        // Past the touch slop it is a drag; lifted before, a tap (the chip opens).
                        var travelled = Offset.Zero
                        while (true) {
                            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                            if (!change.pressed) return@awaitEachGesture
                            travelled += change.positionChangeIgnoreConsumed()
                            if (travelled.getDistance() > viewConfiguration.touchSlop) {
                                change.consume()
                                break
                            }
                        }
                        start(id, at)
                        pointer = at + travelled
                        moveTo(id)
                        while (true) {
                            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                            if (change == null) {
                                dragged = null
                                working = null
                                return@awaitEachGesture
                            }
                            change.consume()
                            if (!change.pressed) {
                                drop(id)
                                return@awaitEachGesture
                            }
                            pointer = origin + change.position
                            moveTo(id)
                        }
                    }
                },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        groups.forEachIndexed { g, group ->
            group.header?.let { header ->
                androidx.compose.runtime.key(HEADER + group.id) {
                    Box(Modifier.fillMaxWidth().padding(top = if (g > 0) 8.dp else 0.dp)) { header() }
                }
            }
            val keys = order.getOrNull(g).orEmpty()
            if (keys.isEmpty() && emptyLabel != null && onMove != null) {
                androidx.compose.runtime.key(EMPTY + group.id) {
                    Surface(
                        shape = FilterChipDefaults.shape,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier =
                            Modifier
                                .height(40.dp)
                                .onGloballyPositioned { c ->
                                    val b = c.boundsInRoot()
                                    boxes[EMPTY + group.id] = ChipOrder.Box(b.left, b.top, b.right, b.bottom)
                                },
                    ) {
                        Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                            Text(emptyLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            keys.forEach { id ->
                val item = byKey[id] ?: return@forEach
                androidx.compose.runtime.key(id) {
                    val isDragging = dragged == id
                    Surface(
                        onClick = { onEdit(item) },
                        shape = FilterChipDefaults.shape,
                        color = if (isDragging) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = false),
                        shadowElevation = if (isDragging) 6.dp else 0.dp,
                        modifier =
                            Modifier
                                .height(40.dp)
                                .zIndex(if (isDragging) 1f else 0f)
                                .onGloballyPositioned { c ->
                                    val b = c.boundsInRoot()
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
                                    contentDescription = "$editLabel: ${label(item)}"
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
                                        // The whole 32dp square is where a drag starts.
                                        .onGloballyPositioned { c ->
                                            val b = c.boundsInRoot()
                                            handles[id] = ChipOrder.Box(b.left, b.top, b.right, b.bottom)
                                        }.padding(6.dp),
                            )
                            icon(item)
                            Text(
                                label(item),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isError(item)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
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
}

/** Keys of a group's header and of an empty group's place, apart from the chips' own keys. */
private const val HEADER = "\u0000header:"
private const val EMPTY = "\u0000empty:"
