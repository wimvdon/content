package io.github.impca201.rssreader.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedDiscoveryTest {
    @Test
    fun `adds https to bare addresses and upgrades http and feed links`() {
        assertEquals("https://example.com", FeedDiscovery.normalizeUrl("example.com"))
        assertEquals("https://example.com/feed", FeedDiscovery.normalizeUrl("  http://example.com/feed "))
        assertEquals("https://example.com/rss", FeedDiscovery.normalizeUrl("feed://example.com/rss"))
        assertEquals("https://example.com/rss", FeedDiscovery.normalizeUrl("feed:https://example.com/rss"))
        assertEquals("https://Example.com/a?b=c", FeedDiscovery.normalizeUrl("HTTPS://Example.com/a?b=c"))
    }

    @Test
    fun `rejects what can't be a web address`() {
        assertNull(FeedDiscovery.normalizeUrl(""))
        assertNull(FeedDiscovery.normalizeUrl("hello world"))
        assertNull(FeedDiscovery.normalizeUrl("notahost"))
        assertNull(FeedDiscovery.normalizeUrl("ftp://example.com/feed"))
        assertNull(FeedDiscovery.normalizeUrl("javascript:alert(1)"))
    }

    @Test
    fun `finds feeds announced in a page head`() {
        val html =
            """
            <html><head>
              <link rel="stylesheet" href="/style.css">
              <link rel="alternate" type="application/rss+xml" title="RSS" href="/feed.xml">
              <LINK REL='alternate' TYPE='application/atom+xml' HREF='https://example.com/atom?a=1&amp;b=2'>
              <link rel="alternate" hreflang="nl" href="/nl/">
              <link rel="alternate" type="application/rss+xml" href="/feed.xml">
            </head></html>
            """.trimIndent()
        assertEquals(
            listOf("https://example.com/feed.xml", "https://example.com/atom?a=1&b=2"),
            FeedDiscovery.findFeedLinks(html, "https://example.com/blog/"),
        )
    }

    @Test
    fun `common feed paths are on the site root`() {
        assertEquals("https://example.com/feed", FeedDiscovery.commonFeedUrls("https://example.com/blog/post?x=1").first())
    }
}
