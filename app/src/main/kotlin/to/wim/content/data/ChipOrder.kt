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
}
