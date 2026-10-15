package io.github.impca201.rssreader.data

import io.github.impca201.rssreader.feed.Opml
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
        val merged = Library().addFeed(Feed(a, "A")).merge(a, parsed(*many.toTypedArray()), now = 1_000)
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

    private val tech = Folder("tech", "Tech", FolderIcon.Tech)
    private val withFolder = twoFeeds.addFolder(tech).moveFeed(a, "tech")

    @Test
    fun `folders can be added, edited and removed, keeping their feeds`() {
        assertEquals(listOf(tech), withFolder.folders)
        assertEquals("tech", withFolder.feed(a)!!.folderId)
        assertSame(withFolder, withFolder.addFolder(Folder("tech", "Again")))
        assertSame(withFolder, withFolder.addFolder(Folder("blank", "  ")))
        val renamed = withFolder.updateFolder("tech", " Gadgets ", FolderIcon.Code)
        assertEquals(Folder("tech", "Gadgets", FolderIcon.Code), renamed.folder("tech"))
        val removed = withFolder.removeFolder("tech")
        assertTrue(removed.folders.isEmpty())
        assertEquals(2, removed.feeds.size)
        assertNull(removed.feed(a)!!.folderId)
    }

    @Test
    fun `a feed can't be moved into a folder that doesn't exist`() {
        assertNull(twoFeeds.moveFeed(a, "nope").feed(a)!!.folderId)
        assertNull(withFolder.moveFeed(a, null).feed(a)!!.folderId)
        assertEquals(listOf(b), withFolder.feedsIn(null).map { it.url })
    }

    @Test
    fun `a folder selection covers its feeds only`() {
        val inTech = FeedSelection.InFolder("tech")
        assertEquals(2, withFolder.unreadCount(inTech))
        assertEquals(listOf("2", "1"), withFolder.articlesFor(inTech, unreadOnly = false).map { it.id })
        val marked = withFolder.markAllRead(inTech)
        assertEquals(0, marked.unreadCount(inTech))
        assertEquals(1, marked.unreadCount(FeedSelection.All))
        val counts = withFolder.unreadCounts()
        assertEquals(3, counts[FeedSelection.All])
        assertEquals(2, counts[inTech])
        assertEquals(1, counts[FeedSelection.Single(b)])
    }

    @Test
    fun `mark all read can be undone with the keys it reports`() {
        val inTech = FeedSelection.InFolder("tech")
        val keys = withFolder.unreadKeys(inTech)
        assertEquals(setOf(Article.keyOf(a, "1"), Article.keyOf(a, "2")), keys)
        assertEquals(withFolder, withFolder.markAllRead(inTech).setRead(keys, false))
    }

    @Test
    fun `a removed feed or folder falls back to all articles`() {
        assertEquals(FeedSelection.InFolder("tech"), withFolder.shown(FeedSelection.InFolder("tech")))
        assertEquals(FeedSelection.All, withFolder.removeFolder("tech").shown(FeedSelection.InFolder("tech")))
        assertEquals(FeedSelection.Single(b), withFolder.shown(FeedSelection.Single(b)))
        assertEquals(FeedSelection.All, withFolder.removeFeed(b).shown(FeedSelection.Single(b)))
    }

    @Test
    fun `import adds feeds into folders matched by title`() {
        var ids = 0
        val entries =
            listOf(
                Opml.Entry(a, "A again", folder = "News"),
                Opml.Entry("https://c.example.com/feed", "C", folder = "tech"),
                Opml.Entry("https://d.example.com/feed", "", folder = "News"),
                Opml.Entry("https://e.example.com/feed", "E"),
            )
        val (imported, added) = withFolder.import(entries) { "new${ids++}" }
        assertEquals(3, added)
        // Known feeds stay as they are; "tech" matches the Tech folder; News is created once.
        assertEquals("tech", imported.feed(a)!!.folderId)
        assertEquals("tech", imported.feed("https://c.example.com/feed")!!.folderId)
        assertEquals(listOf("Tech", "News"), imported.folders.map { it.title })
        assertEquals("new0", imported.feed("https://d.example.com/feed")!!.folderId)
        assertEquals("https://d.example.com/feed", imported.feed("https://d.example.com/feed")!!.title)
        assertNull(imported.feed("https://e.example.com/feed")!!.folderId)
    }

    @Test
    fun `export names each feed's folder`() {
        assertEquals(
            listOf(Opml.Entry(a, "Feed", "Tech"), Opml.Entry(b, "Feed", null)),
            withFolder.toOpmlEntries(),
        )
    }

    @Test
    fun `a whole refresh is merged at once, newest first across feeds`() {
        val library = Library().addFeed(Feed(a, "A")).addFeed(Feed(b, "B"))
        val merged =
            library.mergeAll(
                mapOf(a to parsed(item("1", 100), item("3", 300)), b to parsed(item("2", 200))),
                now = 1_000,
            )
        assertEquals(listOf("3", "2", "1"), merged.articlesFor(FeedSelection.All, unreadOnly = false).map { it.id })
        assertSame(library, library.mergeAll(emptyMap(), now = 1_000))
    }

    @Test
    fun `dates in the future count as now`() {
        val merged = Library().addFeed(Feed(a, "A")).merge(a, parsed(item("1", 5_000)), now = 1_000)
        assertEquals(1_000L, merged.articles.single().publishedAt)
    }
}
