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
}
