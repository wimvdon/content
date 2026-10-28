package be.wimvandonck.content.data

import be.wimvandonck.content.feed.PageInfo
import be.wimvandonck.content.feed.ParsedFeed
import be.wimvandonck.content.feed.ParsedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryItemsTest {
    private val feed = "https://a.example.com/feed"

    private val withArticle =
        Library()
            .addFeed(Feed(feed, "A"))
            .merge(
                feed,
                ParsedFeed(
                    title = "A",
                    siteUrl = null,
                    items = listOf(ParsedItem(id = "1", title = "Feed title", link = "https://www.example.com/story/", author = null, html = "<p>Story text</p>", publishedAt = 10, imageUrl = "https://example.com/p.jpg")),
                ),
                now = 100,
            )

    private fun saved(
        library: Library = withArticle,
        url: String = "http://example.com/story#top",
        now: Long = 1_000,
    ): Library = library.addToReadingList(url, null, now).first

    @Test
    fun `a link with tracking parameters is the same item, other parameters still count (#253)`() {
        val flipboard = "http://www.vulture.com/article/can-nirosta-steel-survive.html?utm_source=flipboard&utm_medium=s1&utm_campaign=nymag"
        assertEquals("vulture.com/article/can-nirosta-steel-survive.html", ItemKeys.of(flipboard))
        assertEquals(ItemKeys.of("https://vulture.com/article/can-nirosta-steel-survive.html"), ItemKeys.of(flipboard))
        assertEquals("a.example/p?id=3&b=2", ItemKeys.of("https://a.example/p?id=3&utm_source=x&b=2"))
        assertEquals("a.example/p", ItemKeys.of("https://a.example/p/?fbclid=1"))
        assertTrue(ItemKeys.of("https://a.example/p?id=3") != ItemKeys.of("https://a.example/p?id=4"))
        // Keys without tracking parameters stay exactly as before.
        assertEquals("a.example/p?b=2&a=1", ItemKeys.of("https://a.example/p?b=2&a=1"))
    }

    @Test
    fun `duplicates from before become one item with everything of both (#253)`() {
        val old = "vulture.com/x.html?utm_source=flipboard"
        val library =
            Library(
                items =
                    listOf(
                        Item(
                            key = old, url = "http://www.vulture.com/x.html?utm_source=flipboard", title = "X", createdAt = 10,
                            readingList = ReadingListEntry(ReadingState.Archived, 10), tags = listOf("tv"), note = "Mine",
                            highlights = listOf(Highlight("h1", "quote", createdAt = 11)), progress = 0.5f,
                        ),
                        Item(
                            key = "vulture.com/x.html", url = "https://www.vulture.com/x.html", title = "X", createdAt = 20,
                            readingList = ReadingListEntry(ReadingState.Archived, 20), tags = listOf("TV", "recap"), liked = true,
                        ),
                        Item(key = "other.example/y", url = "https://other.example/y", title = "Y", createdAt = 5, liked = true),
                    ),
            )
        val joined = library.joinDuplicateItems()
        assertEquals(listOf("vulture.com/x.html", "other.example/y"), joined.items.map { it.key })
        val item = joined.item("vulture.com/x.html")!!
        assertEquals("https://www.vulture.com/x.html", item.url)
        assertEquals(listOf("TV", "recap"), item.tags)
        assertEquals("Mine", item.note)
        assertEquals(listOf("h1"), item.highlights.map { it.id })
        assertTrue(item.liked)
        assertEquals(0.5f, item.progress)
        assertEquals(10L, item.createdAt)
        assertEquals(ReadingListEntry(ReadingState.Archived, 20), item.readingList)
        // Nothing to join: the same library.
        assertSame(joined, joined.joinDuplicateItems())
    }

    @Test
    fun `an item synced in under a key from before moves to its new key`() {
        val library = Library(items = listOf(Item(key = "a.example/1?utm_medium=feed", url = "https://a.example/1?utm_medium=feed", title = "One", createdAt = 1, liked = true)))
        assertEquals(listOf("a.example/1"), library.joinDuplicateItems().items.map { it.key })
    }

    @Test
    fun `links written another way are one item`() {
        assertEquals(ItemKeys.of("https://www.Example.com/story/"), ItemKeys.of("http://example.com/story#part"))
        assertNull(ItemKeys.of(" "))
        assertFalse(ItemKeys.isWebLink("javascript:alert(1)"))
    }

    @Test
    fun `saving a feed article adds a membership to its item, made from the feed article, without a copy`() {
        val library = saved()
        val item = library.items.single()
        assertEquals("Feed title", item.title)
        assertEquals(feed, item.feedUrl)
        assertEquals("1", item.articleId)
        assertEquals("https://example.com/p.jpg", item.imageUrl)
        assertEquals("Story text", item.description)
        assertTrue(item.toRead)
        // The feed article stays as it was: the item is the same link, not a second article.
        assertEquals(1, library.articles.size)
        assertSame(item, library.itemFor(library.articles.single()))
        // Saving the same link again (another way of writing it) keeps one item, back to read at the top.
        val again = saved(library.setReadingState(item.key, ReadingState.Archived), url = "https://example.com/story", now = 2_000)
        assertEquals(1, again.items.size)
        assertTrue(again.items.single().toRead)
        assertEquals(2_000L, again.items.single().readingList?.addedAt)
    }

    @Test
    fun `only web links become items`() {
        val (library, item) = Library().addToReadingList("ftp://example.com/x", "X", 1)
        assertNull(item)
        assertTrue(library.items.isEmpty())
    }

    @Test
    fun `archiving takes an item out of its folder, and off the list it goes when nothing is left`() {
        val key = saved().items.single().key
        val filed = Saved.file(saved(), key, "Research")
        val archived = filed.setReadingState(key, ReadingState.Archived)
        assertTrue(archived.item(key)!!.archived)
        assertNull(archived.item(key)!!.folder)
        assertNull(archived.setReadingState(key, null).item(key))
    }

    @Test
    fun `tags are cleaned, listed and renamed, and a rename to an existing tag joins them`() {
        val key = saved().items.single().key
        val tagged = saved().setTags(key, listOf(" work ", "Work", "", "news"))
        assertEquals(listOf("work", "news"), tagged.item(key)!!.tags)
        assertEquals(listOf("news", "work"), tagged.itemTags())
        assertEquals(listOf("news"), tagged.renameTag("WORK", "news").item(key)!!.tags)
        assertSame(tagged, tagged.renameTag("work", " "))
    }

    @Test
    fun `lists are newest first`() {
        val two = saved(saved(), url = "https://other.example/x", now = 5_000)
        assertEquals(listOf("other.example/x", "example.com/story"), two.readingList(ReadingState.ToRead).map { it.key })
        val key = two.items.first().key
        val archived = two.setReadingState(key, ReadingState.Archived)
        assertEquals(listOf(key), archived.readingList(ReadingState.Archived).map { it.key })
        assertNotNull(archived.item(key))
    }

    @Test
    fun `removing a feed keeps the items saved from it`() {
        val library = saved().removeFeed(feed)
        assertTrue(library.articles.isEmpty())
        assertEquals(1, library.items.size)
    }

    @Test
    fun `a saved feed article is tagged with its feed's folder, in the spelling already in use`() {
        val inFolder = withArticle.addFolder(Folder("f", "Tech", "devices")).moveFeed(feed, "f")
        val (library, item) = inFolder.addToReadingList("https://example.com/story", null, 1)
        assertEquals(listOf("Tech"), library.tagWithFeedFolder(item!!.key).item(item.key)!!.tags)
        // A tag "tech" already in use keeps its spelling, and isn't added twice.
        val other = library.addToReadingList("https://other.example/x", null, 2).first.setTags("other.example/x", listOf("tech"))
        assertEquals(listOf("tech"), other.tagWithFeedFolder(item.key).item(item.key)!!.tags)
        val twice = other.tagWithFeedFolder(item.key).tagWithFeedFolder(item.key)
        assertEquals(listOf("tech"), twice.item(item.key)!!.tags)
        // A feed outside folders, or a link that isn't a feed article: nothing.
        val loose = withArticle.addToReadingList("https://example.com/story", null, 1).first
        assertSame(loose, loose.tagWithFeedFolder("example.com/story"))
        val elsewhere = inFolder.addToReadingList("https://elsewhere.example/a", null, 1).first
        assertSame(elsewhere, elsewhere.tagWithFeedFolder("elsewhere.example/a"))
    }

    @Test
    fun `highlighting a feed article saves it to To read, notes change and highlights go`() {
        val h = Highlight("h1", "Story", createdAt = 5)
        val library = withArticle.addHighlight("https://www.example.com/story/", "Feed title", h, 100)
        val item = library.itemForLink("https://www.example.com/story/")!!
        assertEquals(ReadingState.ToRead, item.readingList?.state)
        assertEquals(listOf(h), item.highlights)
        val noted = library.setHighlightNote(item.key, "h1", " why ")
        assertEquals("why", noted.item(item.key)!!.highlights.single().note)
        // Removing the last highlight leaves the article saved.
        val removed = noted.removeHighlight(item.key, "h1")
        assertTrue(removed.item(item.key)!!.highlights.isEmpty())
        assertNotNull(removed.item(item.key)!!.readingList)
        assertSame(withArticle, withArticle.addHighlight("mailto:x@example.com", null, h, 1))
    }

    @Test
    fun `a saved feed article gets its feed's tags (#187)`() {
        val tagged = withArticle.copy(feeds = withArticle.feeds.map { it.copy(tags = listOf("News", "Brussels")) })
        val (library, item) = tagged.addToReadingList("https://example.com/story", null, 1)
        assertEquals(listOf("News", "Brussels"), library.tagWithFeedFolder(item!!.key).item(item.key)!!.tags)
    }

    @Test
    fun `a shared link with a title still gets the page's picture and description (#267, #268)`() {
        val info = PageInfo("Page title", "Page description", "https://example.com/og.jpg")
        val shared = Item(key = "example.com/a", url = "https://example.com/a", title = "Title from the app", createdAt = 1)
        assertTrue(shared.needsDescription())
        val described = shared.describedBy(info)
        assertEquals("Title from the app", described.title)
        assertEquals("Page description", described.description)
        assertEquals("https://example.com/og.jpg", described.imageUrl)
        assertFalse(described.needsDescription())
        // A link without a title takes the page's; what an item already has stays.
        assertEquals("Page title", shared.copy(title = shared.url).describedBy(info).title)
        val full = shared.copy(description = "Own", imageUrl = "https://example.com/own.jpg")
        assertFalse(full.needsDescription())
        assertEquals(full, full.describedBy(info))
        // A page without a picture leaves none.
        assertNull(shared.describedBy(PageInfo(null, null, null)).imageUrl)
    }
}
