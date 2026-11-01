package to.wim.content.data

/**
 * Reordering chips that wrap over several lines (#314): the topics' chips in the Add feed and
 * Organise sheets are dragged into a new order. Plain Kotlin, so it is unit tested: which chip a
 * dragged point is over, and a list with one entry moved.
 */
object ChipOrder {
    /** Where a chip is, in pixels within its row: left, top, right and bottom. */
    data class Box(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        fun contains(
            x: Float,
            y: Float,
        ) = x in left..right && y in top..bottom
    }

    /**
     * The index of the chip under ([x], [y]); between chips, the nearest one on that line (or,
     * off every line, the nearest of all). -1 without chips.
     */
    fun indexAt(
        boxes: List<Box>,
        x: Float,
        y: Float,
    ): Int {
        if (boxes.isEmpty()) return -1
        boxes.indexOfFirst { it.contains(x, y) }.takeIf { it >= 0 }?.let { return it }
        val line = boxes.indices.filter { y in boxes[it].top..boxes[it].bottom }.ifEmpty { boxes.indices.toList() }
        return line.minBy { distance(boxes[it], x, y) }
    }

    private fun distance(
        box: Box,
        x: Float,
        y: Float,
    ): Float {
        val dx = maxOf(box.left - x, 0f, x - box.right)
        val dy = maxOf(box.top - y, 0f, y - box.bottom)
        return dx * dx + dy * dy
    }

    /** [list] with the entry at [from] moved to [to]; the same list when either is out of range. */
    fun <T> moved(
        list: List<T>,
        from: Int,
        to: Int,
    ): List<T> {
        if (from !in list.indices || to !in list.indices || from == to) return list
        return list.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** Where a chip goes: its group, and its place in that group. */
    data class Slot(
        val group: Int,
        val index: Int,
    )

    /** A place to drop a chip: a chip at [slot], or an empty group's place ([empty]), and where it is. */
    data class Target(
        val slot: Slot,
        val box: Box,
        val empty: Boolean = false,
    )

    /**
     * Where a chip of group [ownGroup] dragged to ([x], [y]) goes, over chips in groups (Manage
     * feeds' topics): the nearest target ([indexAt]). In its own group it takes that chip's place;
     * in another group it goes before or after that chip, on the side the point is; an empty
     * group's place is the start of that group. null without targets.
     */
    fun slotAt(
        targets: List<Target>,
        ownGroup: Int,
        x: Float,
        y: Float,
    ): Slot? {
        val i = indexAt(targets.map { it.box }, x, y)
        if (i < 0) return null
        val target = targets[i]
        if (target.empty || target.slot.group == ownGroup) return target.slot
        val center = (target.box.left + target.box.right) / 2
        return if (x > center) target.slot.copy(index = target.slot.index + 1) else target.slot
    }

    /**
     * [groups] with [key] taken out of its group and put at [to] (the place clamped to that group);
     * the same list when it doesn't move or isn't there.
     */
    fun <K> movedAcross(
        groups: List<List<K>>,
        key: K,
        to: Slot,
    ): List<List<K>> {
        val from = groups.indexOfFirst { key in it }
        if (from < 0 || to.group !in groups.indices) return groups
        val result = groups.map { it.toMutableList() }
        result[from].remove(key)
        val target = result[to.group]
        target.add(to.index.coerceIn(0, target.size), key)
        return if (result == groups) groups else result
    }
}
