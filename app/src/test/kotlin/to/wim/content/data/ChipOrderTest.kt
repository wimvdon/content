package to.wim.content.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ChipOrderTest {
    // Two lines: three chips on the first, one on the second.
    private val boxes =
        listOf(
            ChipOrder.Box(0f, 0f, 80f, 32f),
            ChipOrder.Box(88f, 0f, 200f, 32f),
            ChipOrder.Box(208f, 0f, 300f, 32f),
            ChipOrder.Box(0f, 40f, 120f, 72f),
        )

    @Test
    fun `finds the chip under the point`() {
        assertEquals(0, ChipOrder.indexAt(boxes, 10f, 10f))
        assertEquals(2, ChipOrder.indexAt(boxes, 250f, 20f))
        assertEquals(3, ChipOrder.indexAt(boxes, 50f, 60f))
    }

    @Test
    fun `between chips or past the end of a line, the nearest on that line`() {
        assertEquals(1, ChipOrder.indexAt(boxes, 87f, 16f))
        assertEquals(2, ChipOrder.indexAt(boxes, 340f, 16f))
        assertEquals(3, ChipOrder.indexAt(boxes, 300f, 56f))
        // Below every line: the nearest of all.
        assertEquals(3, ChipOrder.indexAt(boxes, 60f, 200f))
        assertEquals(-1, ChipOrder.indexAt(emptyList(), 0f, 0f))
    }

    @Test
    fun `moves one entry and keeps the others in order`() {
        assertEquals(listOf("b", "c", "a"), ChipOrder.moved(listOf("a", "b", "c"), 0, 2))
        assertEquals(listOf("c", "a", "b"), ChipOrder.moved(listOf("a", "b", "c"), 2, 0))
        val same = listOf("a", "b")
        assertSame(same, ChipOrder.moved(same, 0, 5))
        assertSame(same, ChipOrder.moved(same, 1, 1))
    }

    @Test
    fun `a chip dragged over another group goes before or after the chip it is over`() {
        val targets =
            listOf(
                ChipOrder.Target(ChipOrder.Slot(0, 0), ChipOrder.Box(0f, 0f, 100f, 40f)),
                ChipOrder.Target(ChipOrder.Slot(0, 1), ChipOrder.Box(110f, 0f, 210f, 40f)),
                ChipOrder.Target(ChipOrder.Slot(1, 0), ChipOrder.Box(0f, 80f, 100f, 120f)),
                ChipOrder.Target(ChipOrder.Slot(2, 0), ChipOrder.Box(0f, 160f, 200f, 200f), empty = true),
            )
        // In its own group: that chip's place.
        assertEquals(ChipOrder.Slot(0, 1), ChipOrder.slotAt(targets, ownGroup = 0, x = 200f, y = 20f))
        // In another group: before the chip on its left half, after it on its right half.
        assertEquals(ChipOrder.Slot(1, 0), ChipOrder.slotAt(targets, ownGroup = 0, x = 20f, y = 100f))
        assertEquals(ChipOrder.Slot(1, 1), ChipOrder.slotAt(targets, ownGroup = 0, x = 80f, y = 100f))
        // An empty group: its start, wherever the point is in it.
        assertEquals(ChipOrder.Slot(2, 0), ChipOrder.slotAt(targets, ownGroup = 0, x = 190f, y = 180f))
        assertEquals(null, ChipOrder.slotAt(emptyList(), ownGroup = 0, x = 0f, y = 0f))
    }

    @Test
    fun `moves an entry into another group, or within its own`() {
        val groups = listOf(listOf("a", "b"), listOf("c"), emptyList())
        assertEquals(listOf(listOf("b"), listOf("a", "c"), emptyList()), ChipOrder.movedAcross(groups, "a", ChipOrder.Slot(1, 0)))
        assertEquals(listOf(listOf("a", "b"), emptyList(), listOf("c")), ChipOrder.movedAcross(groups, "c", ChipOrder.Slot(2, 5)))
        assertEquals(listOf(listOf("b", "a"), listOf("c"), emptyList()), ChipOrder.movedAcross(groups, "a", ChipOrder.Slot(0, 1)))
        assertSame(groups, ChipOrder.movedAcross(groups, "a", ChipOrder.Slot(0, 0)))
        assertSame(groups, ChipOrder.movedAcross(groups, "x", ChipOrder.Slot(1, 0)))
        assertSame(groups, ChipOrder.movedAcross(groups, "a", ChipOrder.Slot(7, 0)))
    }
}
