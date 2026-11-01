package to.wim.content.ui.feeds

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import to.wim.content.R
import to.wim.content.data.SectionOrder
import to.wim.content.ui.theme.ReorderableItemScope

/**
 * A list in sections whose rows are dragged by their handle (DESIGN.md, Reordering: "a list with
 * sections may build its own drag on the same look and handle"), as the design kit's
 * `ReorderableColumn` does it: the dragged row lifts with a shadow on `surfaceContainerHigh` and
 * swaps with its neighbour once it is past half of it, so the list never reflows under the finger.
 * It passes headings as it passes rows: dropped under another heading, it moves into that section
 * ([onMove]); in its own, [onReorder] gets the new order ([SectionOrder]). Screen readers get Move up
 * and Move down on the handle (also across headings).
 */
@Composable
internal fun <T> ReorderableSections(
    sections: List<Pair<String, List<T>>>,
    key: (T) -> String,
    onReorder: (group: String, keys: List<String>) -> Unit,
    onMove: (item: T, group: String, keys: List<String>) -> Unit,
    heading: @Composable (group: String) -> Unit,
    row: @Composable ReorderableItemScope.(item: T) -> Unit,
) {
    val stored = remember(sections) { SectionOrder.flatten(sections.map { (g, items) -> g to items.map(key) }) }
    val byKey = sections.flatMap { it.second }.associateBy(key)
    // A working copy while dragging, kept after the drop until the stored order comes back (or a second).
    var working by remember { mutableStateOf<List<SectionOrder.Entry>?>(null) }
    var dragged by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(stored) { if (dragged == null) working = null }
    LaunchedEffect(working, dragged) {
        if (working != null && dragged == null) {
            delay(1000)
            working = null
        }
    }
    val order = working ?: stored
    val heights = remember { mutableStateMapOf<String, Int>() }
    val currentStored by rememberUpdatedState(stored)
    val currentByKey by rememberUpdatedState(byKey)
    val currentOnReorder by rememberUpdatedState(onReorder)
    val currentOnMove by rememberUpdatedState(onMove)
    val haptics = LocalHapticFeedback.current
    val reorder = stringResource(R.string.action_reorder)
    val moveUp = stringResource(R.string.action_move_up)
    val moveDown = stringResource(R.string.action_move_down)
    val lifted = MaterialTheme.colorScheme.surfaceContainerHigh

    fun apply(
        k: String,
        from: String,
        before: List<String>,
        after: List<SectionOrder.Entry>,
    ): Boolean {
        when (val drop = SectionOrder.dropped(after, k, from, before)) {
            is SectionOrder.Drop.Moved -> currentByKey[drop.key]?.let { currentOnMove(it, drop.group, drop.keys) } ?: return false
            is SectionOrder.Drop.Reordered -> currentOnReorder(drop.group, drop.keys)
            null -> return false
        }
        return true
    }

    // One step for screen readers: stored at once.
    fun step(
        k: String,
        by: Int,
    ): Boolean {
        val list = currentStored
        val at = list.indexOfFirst { it.key == k }
        val from = SectionOrder.sectionOf(list, k) ?: return false
        return apply(k, from, SectionOrder.rowsOf(list, from), SectionOrder.moved(list, at, at + by))
    }

    Column {
        order.forEach { entry ->
            key(entry.key) {
                when (entry) {
                    is SectionOrder.Heading -> Box(Modifier.onSizeChanged { heights[entry.key] = it.height }) { heading(entry.group) }
                    is SectionOrder.Row -> byKey[entry.key]?.let { item ->
                        val k = entry.key
                        val isDragging = dragged == k
                        val handle =
                            Modifier
                                .semantics {
                                    contentDescription = reorder
                                    customActions =
                                        listOf(
                                            CustomAccessibilityAction(moveUp) { step(k, -1) },
                                            CustomAccessibilityAction(moveDown) { step(k, 1) },
                                        )
                                }.pointerInput(k) {
                                    var from = ""
                                    var before = emptyList<String>()
                                    detectDragGestures(
                                        onDragStart = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            val list = currentStored
                                            from = SectionOrder.sectionOf(list, k).orEmpty()
                                            before = SectionOrder.rowsOf(list, from)
                                            working = list
                                            dragged = k
                                            offset = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            offset += amount.y
                                            val now = working ?: return@detectDragGestures
                                            val at = now.indexOfFirst { it.key == k }
                                            // Past half the neighbour (a row or a heading): swap with it, keep the row under the finger.
                                            val next = now.getOrNull(at + 1)?.let { heights[it.key] }?.toFloat() ?: 0f
                                            val previous = now.getOrNull(at - 1)?.let { heights[it.key] }?.toFloat() ?: 0f
                                            if (offset > next / 2 && next > 0f && SectionOrder.canMoveTo(now, at + 1)) {
                                                working = SectionOrder.moved(now, at, at + 1)
                                                offset -= next
                                            } else if (offset < -previous / 2 && previous > 0f && SectionOrder.canMoveTo(now, at - 1)) {
                                                working = SectionOrder.moved(now, at, at - 1)
                                                offset += previous
                                            }
                                        },
                                        onDragEnd = {
                                            val after = working
                                            dragged = null
                                            offset = 0f
                                            if (after == null || !apply(k, from, before, after)) working = null
                                        },
                                        onDragCancel = {
                                            dragged = null
                                            offset = 0f
                                            working = null
                                        },
                                    )
                                }
                        Box(
                            Modifier
                                .onSizeChanged { heights[k] = it.height }
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer {
                                    if (isDragging) {
                                        translationY = offset
                                        shadowElevation = 8.dp.toPx()
                                    }
                                }.then(if (isDragging) Modifier.background(lifted) else Modifier),
                        ) {
                            ReorderableItemScope(isDragging, handle).row(item)
                        }
                    }
                }
            }
        }
    }
}
