package io.github.impca201.rssreader.data

import io.github.impca201.rssreader.feed.ParsedFeed
import io.github.impca201.rssreader.feed.ParsedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {
    private val a = "https://a.example.com/feed"
    private val b = "https://b.example.com/feed"

    private fun item(
        id: String,
        time: Long? = null,
        title: String = "Title $id",
    ) = ParsedItem(id = id, title = title, link = "https://x/$id", author = null, html = "<p>$id</p>", publishedAt = time)

    private fun parsed(vararg items: ParsedItem) = ParsedFeed(title = "Feed", siteUrl = "https://site", items = items.toList())

    private val twoFeeds =
        Library()
            .addFeed(Feed(a, "A"))
            .addFeed(Feed(b, "B"))
            .merge(a, parsed(item("1", 100), item("2", 300)), now = 1_000)
            .merge(b, parsed(item("1", 200)), now = 1_000)

    @Test
    fun `adding a feed twice keeps one`() {
        assertEquals(1, Library().addFeed(Feed(a, "A")).addFeed(Feed(a, "Again")).feeds.size)
    }

    @Test
    fun `merge adds new articles as unread and takes the feed's title`() {
        val library = Library().addFeed(Feed(a, a)).merge(a, parsed(item("1", 100)), now = 5)
        val article = library.articles.single()
        assertFalse(article.read)
        assertEquals(5, article.fetchedAt)
        assertEquals("Feed", library.feed(a)?.title)
        assertEquals("https://site", library.feed(a)?.siteUrl)
    }

    @Test
    fun `merge keeps read state and first-seen time of known articles`() {
        val read = twoFeeds.setRead(Article.keyOf(a, "1"), true)
        val merged = read.merge(a, parsed(item("1", 100, title = "New title"), item("3", 400)), now = 9_999)
        val one = merged.article(Article.keyOf(a, "1"))!!
        assertTrue(one.read)
        assertEquals("New title", one.title)
        assertEquals(1_000, one.fetchedAt)
        // Article 2 left the feed but is kept.
        assertTrue(merged.article(Article.keyOf(a, "2")) != null)
        assertEquals(3, merged.articles.count { it.feedUrl == a })
    }

    @Test
    fun `merge takes the item's picture and keeps a known one when the feed drops it`() {
        val withPicture = Library().addFeed(Feed(a, "A")).merge(a, parsed(item("1").copy(imageUrl = "https://x/1.jpg")), now = 1)
        assertEquals("https://x/1.jpg", withPicture.articles.single().imageUrl)
        assertEquals("https://x/1.jpg", withPicture.merge(a, parsed(item("1")), now = 2).articles.single().imageUrl)
    }

    @Test
    fun `merge ignores duplicate items and unknown feeds`() {
        val merged = Library().addFeed(Feed(a, "A")).merge(a, parsed(item("1"), item("1")), now = 1)
        assertEquals(1, merged.articles.size)
        assertSame(merged, merged.merge("https://unknown", parsed(item("x")), now = 1))
    }

    @Test
    fun `merge keeps only the newest articles of a feed`() {
        val many = (1..Library.MAX_ARTICLES_PER_FEED + 10).map { item("$it", it.toLong()) }
        val merged = Library().addFeed(Feed(a, "A")).merge(a, parsed(*many.toTypedArray()), now = 0)
        assertEquals(Library.MAX_ARTICLES_PER_FEED, merged.articles.size)
        assertNull(merged.article(Article.keyOf(a, "1")))
    }

    @Test
    fun `articles are listed newest first, by feed and unread filter`() {
        assertEquals(listOf("2", "1", "1"), twoFeeds.articlesFor(FeedSelection.All, unreadOnly = false).map { it.id })
        assertEquals(listOf(a, b, a), twoFeeds.articlesFor(FeedSelection.All, unreadOnly = false).map { it.feedUrl })
        assertEquals(listOf("2", "1"), twoFeeds.articlesFor(FeedSelection.Single(a), unreadOnly = false).map { it.id })

        val read = twoFeeds.setRead(Article.keyOf(a, "2"), true)
        assertEquals(2, read.articlesFor(FeedSelection.All, unreadOnly = true).size)
        // Unless kept on screen because it was just read.
        assertEquals(3, read.articlesFor(FeedSelection.All, unreadOnly = true, keep = setOf(Article.keyOf(a, "2"))).size)
    }

    @Test
    fun `undated articles sort by when they were first seen`() {
        val library = Library().addFeed(Feed(a, "A")).merge(a, parsed(item("old", 500), item("undated")), now = 900)
        assertEquals("undated", library.articlesFor(FeedSelection.All, unreadOnly = false).first().id)
    }

    @Test
    fun `unread counts and mark all read follow the selection`() {
        assertEquals(3, twoFeeds.unreadCount(FeedSelection.All))
        val aRead = twoFeeds.markAllRead(FeedSelection.Single(a))
        assertEquals(0, aRead.unreadCount(FeedSelection.Single(a)))
        assertEquals(1, aRead.unreadCount(FeedSelection.Single(b)))
        assertEquals(0, aRead.markAllRead(FeedSelection.All).unreadCount(FeedSelection.All))
    }

    @Test
    fun `set read changes one article and can undo`() {
        val key = Article.keyOf(b, "1")
        val read = twoFeeds.setRead(key, true)
        assertTrue(read.article(key)!!.read)
        assertFalse(read.setRead(key, false).article(key)!!.read)
        assertSame(read, read.setRead(key, true))
    }

    @Test
    fun `removing a feed removes its articles`() {
        val removed = twoFeeds.removeFeed(a)
        assertEquals(listOf(b), removed.feeds.map { it.url })
        assertTrue(removed.articles.all { it.feedUrl == b })
    }

    @Test
    fun `very long articles are shortened for storage`() {
        val huge = ParsedItem("1", "T", null, null, "x".repeat(Library.MAX_HTML_LENGTH + 5), null)
        val merged = Library().addFeed(Feed(a, "A")).merge(a, ParsedFeed("T", null, listOf(huge)), now = 0)
        assertEquals(Library.MAX_HTML_LENGTH, merged.articles.single().html.length)
    }
}
