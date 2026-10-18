package be.wimvandonck.reader.instapaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedLinkTest {
    @Test
    fun `a link with the title as subject`() {
        assertEquals(SharedLink("https://example.com/a", "A title"), SharedLink.of("A title", "https://example.com/a"))
    }

    @Test
    fun `a title and a link in the text`() {
        assertEquals(SharedLink("https://example.com/a", "A title"), SharedLink.of(null, "A title https://example.com/a"))
        assertEquals(SharedLink("https://example.com/a", "A title"), SharedLink.of("", "A title\nhttps://example.com/a"))
    }

    @Test
    fun `only a link has no title`() {
        assertEquals(SharedLink("https://example.com/a", null), SharedLink.of(null, "  https://example.com/a  "))
    }

    @Test
    fun `the first link is used and punctuation after it is left out`() {
        assertEquals("https://example.com/a", SharedLink.of(null, "See https://example.com/a. And https://example.com/b")?.url)
        assertEquals("http://example.com/x", SharedLink.of(null, "(http://example.com/x)")?.url)
    }

    @Test
    fun `long text around the link is not a title`() {
        val text = "First line\nSecond line\nhttps://example.com/a"
        assertNull(SharedLink.of(null, text)?.title)
        assertNull(SharedLink.of(null, "x".repeat(SharedLink.MAX_TITLE + 1) + " https://example.com/a")?.title)
    }

    @Test
    fun `a link only in the subject still counts`() {
        assertEquals(SharedLink("https://example.com/a", null), SharedLink.of("https://example.com/a", ""))
    }

    @Test
    fun `no web link means nothing to save`() {
        assertNull(SharedLink.of("Title", "Just some text"))
        assertNull(SharedLink.of(null, "mailto:me@example.com"))
        assertNull(SharedLink.of(null, null))
    }
}
