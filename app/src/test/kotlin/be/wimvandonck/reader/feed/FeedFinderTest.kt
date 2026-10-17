package be.wimvandonck.reader.feed

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedFinderTest {
    private val rss = "<rss><channel><title>Blog</title><item><title>A</title><link>https://example.com/a</link></item></channel></rss>"

    /** A fake web: address → document. Records which addresses were fetched. */
    private class FakeWeb(
        private val pages: Map<String, String>,
    ) {
        val fetched = mutableListOf<String>()

        suspend fun fetch(url: String): Fetched {
            fetched += url
            val page = pages[url] ?: throw FetchException(FetchError.NotFound)
            return Fetched(url, page.toByteArray())
        }
    }

    private fun find(
        web: FakeWeb,
        input: String,
    ) = runBlocking { FeedFinder(web::fetch).find(input) }

    @Test
    fun `an address that is a feed is used as is`() {
        val result = find(FakeWeb(mapOf("https://example.com/rss" to rss)), "example.com/rss")
        assertEquals("https://example.com/rss", (result as FindResult.Found).url)
        assertEquals("Blog", result.feed.title)
    }

    @Test
    fun `a web page leads to the feed it links to`() {
        val page = """<html><head><link rel="alternate" type="application/rss+xml" href="/posts.xml"></head></html>"""
        val web = FakeWeb(mapOf("https://example.com" to page, "https://example.com/posts.xml" to rss))
        assertEquals("https://example.com/posts.xml", (find(web, "https://example.com") as FindResult.Found).url)
    }

    @Test
    fun `a page without feed links is tried at common paths`() {
        val web = FakeWeb(mapOf("https://example.com" to "<html></html>", "https://example.com/rss.xml" to rss))
        assertEquals("https://example.com/rss.xml", (find(web, "example.com") as FindResult.Found).url)
    }

    @Test
    fun `reports when there is no feed`() {
        val web = FakeWeb(mapOf("https://example.com" to "<html></html>"))
        assertEquals(FindResult.NoFeed, find(web, "example.com"))
        assertEquals(1 + FeedDiscovery.commonPaths.size, web.fetched.size)
    }

    @Test
    fun `reports invalid addresses and download problems`() {
        assertEquals(FindResult.InvalidAddress, find(FakeWeb(emptyMap()), "not an address"))
        assertEquals(FindResult.Failed(FetchError.NotFound), find(FakeWeb(emptyMap()), "example.com"))
    }
}
