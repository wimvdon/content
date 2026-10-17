package be.wimvandonck.reader.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiteIconTest {
    @Test
    fun `the largest announced icon wins, apple touch icons count as large`() {
        val page =
            """
            <html><head>
              <link rel="icon" href="/favicon-16.png" sizes="16x16">
              <link rel="icon" type="image/png" href="/favicon-32.png" sizes="32x32">
              <link rel="apple-touch-icon" href="/touch.png">
              <link rel="stylesheet" href="/site.css">
            </head></html>
            """.trimIndent()
        assertEquals("https://example.com/touch.png", SiteIcon.fromPage(page, "https://example.com/"))
    }

    @Test
    fun `shortcut icons, relative paths and http are handled`() {
        val page = """<link rel="shortcut icon" href="http://cdn.example.com/i/fav.ico">"""
        assertEquals("https://cdn.example.com/i/fav.ico", SiteIcon.fromPage(page, "https://example.com/blog/"))
        val relative = """<LINK REL='icon' HREF='img/icon.png' SIZES='48x48 96x96'>"""
        assertEquals("https://example.com/blog/img/icon.png", SiteIcon.fromPage(relative, "https://example.com/blog/"))
    }

    @Test
    fun `svg and inline icons are skipped, and a page without icons gives none`() {
        val page =
            """
            <link rel="icon" href="/icon.svg" type="image/svg+xml">
            <link rel="icon" href="data:image/png;base64,AAAA">
            """.trimIndent()
        assertNull(SiteIcon.fromPage(page, "https://example.com/"))
        assertNull(SiteIcon.fromPage("<html><body>hi</body></html>", "https://example.com/"))
    }

    @Test
    fun `home page and favicon fallback come from the site`() {
        assertEquals("https://www.example.com/", SiteIcon.homePage("http://www.example.com/blog", "https://feeds.example.net/rss"))
        assertEquals("https://feeds.example.net/", SiteIcon.homePage(null, "https://feeds.example.net/rss"))
        assertEquals("https://www.example.com/favicon.ico", SiteIcon.fallback("https://www.example.com/blog/"))
    }
}
