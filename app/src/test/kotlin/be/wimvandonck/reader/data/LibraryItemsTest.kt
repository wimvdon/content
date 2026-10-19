package be.wimvandonck.reader.data

import be.wimvandonck.reader.feed.ParsedFeed
import be.wimvandonck.reader.feed.ParsedItem
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
    fun `archiving keeps the bookmark, and keeping as bookmark keeps the reading list place`() {
        val key = saved().items.single().key
        val kept = saved().keepAsBookmark(key, " Research ", now = 3_000)
        val item = kept.item(key)!!
        assertTrue(item.toRead)
        assertEquals(BookmarkEntry(3_000, "Research"), item.bookmark)

        val archived = kept.setReadingState(key, ReadingState.Archived)
        assertTrue(archived.item(key)!!.archived)
        assertTrue(archived.item(key)!!.bookmarked)

        // Off the reading list: still a bookmark.
        val offList = archived.setReadingState(key, null)
        assertFalse(offList.item(key)!!.onReadingList)
        assertTrue(offList.item(key)!!.bookmarked)
    }

    @Test
    fun `removing a bookmark keeps the reading list place, and an item with nothing left goes`() {
        val key = saved().items.single().key
        val both = saved().keepAsBookmark(key, null, 3_000)
        val unbookmarked = both.removeBookmark(key)
        assertTrue(unbookmarked.item(key)!!.toRead)
        assertNull(unbookmarked.item(key)!!.bookmark)
        assertNull(unbookmarked.setReadingState(key, null).item(key))
    }

    @Test
    fun `read again puts a bookmark on the reading list and keeps it`() {
        val key = saved().items.single().key
        val bookmarkOnly = saved().keepAsBookmark(key, "Tools", 3_000).setReadingState(key, null)
        val item = bookmarkOnly.item(key)!!
        val again = bookmarkOnly.addToReadingList(item.url, null, 4_000).first.item(key)!!
        assertTrue(again.toRead)
        assertEquals(BookmarkEntry(3_000, "Tools"), again.bookmark)
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
        val archived = two.setReadingState(key, ReadingState.Archived).keepAsBookmark(key, null, 6_000)
        assertEquals(listOf(key), archived.readingList(ReadingState.Archived).map { it.key })
        assertEquals(listOf(key), archived.bookmarks().map { it.key })
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
}
