package to.wim.content.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import to.wim.content.data.SectionOrder.Drop
import to.wim.content.data.SectionOrder.Heading
import to.wim.content.data.SectionOrder.Row

class SectionOrderTest {
    // News: a, b; Tech: (empty); No tag: c.
    private val entries = SectionOrder.flatten(listOf("news" to listOf("a", "b"), "tech" to emptyList(), "" to listOf("c")))

    @Test
    fun `flattens the sections into headings and rows`() {
        assertEquals(listOf(Heading("news"), Row("a"), Row("b"), Heading("tech"), Heading(""), Row("c")), entries)
        assertEquals("news", SectionOrder.sectionOf(entries, "b"))
        assertEquals("", SectionOrder.sectionOf(entries, "c"))
        assertEquals(listOf("a", "b"), SectionOrder.rowsOf(entries, "news"))
        assertEquals(emptyList<String>(), SectionOrder.rowsOf(entries, "tech"))
    }

    @Test
    fun `a row can go anywhere under the first heading, passing headings`() {
        assertFalse(SectionOrder.canMoveTo(entries, 0))
        assertTrue(SectionOrder.canMoveTo(entries, 4))
        // b passes the Tech heading: it is in Tech now.
        val down = SectionOrder.moved(entries, 2, 3)
        assertEquals(listOf(Heading("news"), Row("a"), Heading("tech"), Row("b"), Heading(""), Row("c")), down)
        // A heading itself doesn't move, nor anything above the first heading.
        assertSame(entries, SectionOrder.moved(entries, 3, 4))
        assertSame(entries, SectionOrder.moved(entries, 1, 0))
    }

    @Test
    fun `a drop under another heading moves the row there, in its place`() {
        val down = SectionOrder.moved(entries, 2, 3)
        assertEquals(Drop.Moved("b", "tech", listOf("b")), SectionOrder.dropped(down, "b", "news", listOf("a", "b")))
        // Into No tag, before c.
        val loose = SectionOrder.moved(entries, 1, 4)
        assertEquals(Drop.Moved("a", "", listOf("a", "c")), SectionOrder.dropped(loose, "a", "news", listOf("a", "b")))
    }

    @Test
    fun `a drop in its own section reorders it, or changes nothing`() {
        val swapped = SectionOrder.moved(entries, 1, 2)
        assertEquals(Drop.Reordered("news", listOf("b", "a")), SectionOrder.dropped(swapped, "a", "news", listOf("a", "b")))
        assertNull(SectionOrder.dropped(entries, "a", "news", listOf("a", "b")))
    }
}
