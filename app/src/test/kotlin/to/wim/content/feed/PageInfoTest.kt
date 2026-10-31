package to.wim.content.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageInfoTest {
    @Test
    fun `reads the page's title, description and picture, Open Graph first`() {
        val info =
            PageInfo.parse(
                """<html><head><title>Plain title</title>
                <meta property="og:title" content=" Shared title ">
                <meta name="description" content="What it is about">
                <meta property="og:image" content="/img/p.jpg"></head><body></body></html>""",
                "https://example.com/a/b",
            )
        assertEquals("Shared title", info.title)
        assertEquals("What it is about", info.description)
        assertEquals("https://example.com/img/p.jpg", info.imageUrl)
    }

    @Test
    fun `falls back to the page title, and keeps only https pictures`() {
        val info = PageInfo.parse("""<title>Only a title</title><meta property="og:image" content="http://example.com/p.jpg">""", "http://example.com/")
        assertEquals("Only a title", info.title)
        assertNull(info.description)
        assertNull(info.imageUrl)
        assertNull(PageInfo.parse("", "https://example.com/").title)
    }
}
