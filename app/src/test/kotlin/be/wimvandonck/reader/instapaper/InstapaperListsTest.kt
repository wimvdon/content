package be.wimvandonck.reader.instapaper

import be.wimvandonck.reader.data.Article
import be.wimvandonck.reader.data.Feed
import be.wimvandonck.reader.data.Library
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstapaperListsTest {
    private fun bookmark(
        id: Long,
        archived: Boolean = false,
        folderId: Long? = null,
        liked: Boolean = false,
        tags: List<InstapaperTag> = emptyList(),
    ) = InstapaperBookmark(id, "https://www.site.example/$id", "Saved $id", "A <short> summary", 1_700_000_000, liked, archived, folderId, tags)

    private val feed = Feed(url = "https://site.example/rss", title = "Site News", siteUrl = "https://www.site.example/", iconUrl = "https://site.example/icon.png")
    private val feedArticle =
        Article(
            feedUrl = feed.url,
            id = "x",
            title = "From the feed",
            link = "http://site.example/7/#top",
            author = "Ann",
            html = "<p>The feed's whole text</p>",
            publishedAt = 1L,
            fetchedAt = 1L,
            imageUrl = "https://site.example/7.jpg",
        )
    private val library = Library(feeds = listOf(feed), articles = listOf(feedArticle))

    @Test
    fun `a saved article saved from a feed shows like that feed article, with the feed above it`() {
        val a = InstapaperLists.article(bookmark(7), library)
        // Same address, apart from http, www., the trailing slash and the #part.
        assertEquals("<p>The feed's whole text</p>", a.html)
        assertEquals("https://site.example/7.jpg", a.imageUrl)
        assertEquals("Ann", a.author)
        // Still a saved article: its own key, title and time.
        assertEquals("instapaper 7", a.key)
        assertEquals("Saved 7", a.title)
        assertEquals(feed, InstapaperLists.source(bookmark(7), library))
    }

    @Test
    fun `without its feed article a saved article keeps its description and picture, under its website's feed or icon`() {
        val other = bookmark(8).copy(imageUrl = "https://site.example/8.jpg")
        val a = InstapaperLists.article(other, library)
        assertEquals("<p>A &lt;short&gt; summary</p>", a.html)
        assertEquals("https://site.example/8.jpg", a.imageUrl)
        // Another article of a site the user follows: that feed.
        assertEquals(feed, InstapaperLists.source(other, library))
        // A site the user doesn't follow: the website with its favicon.
        val elsewhere = bookmark(9).copy(url = "https://www.elsewhere.example/a")
        val source = InstapaperLists.source(elsewhere, library)
        assertEquals("elsewhere.example", source?.title)
        assertEquals("https://www.elsewhere.example/favicon.ico", source?.iconUrl)
        assertNull(InstapaperLists.source(bookmark(10).copy(url = null), library))
    }

    @Test
    fun `addresses match without http, www, a trailing slash or the part after #`() {
        assertEquals(InstapaperLists.addressKey("https://www.Site.example/a/"), InstapaperLists.addressKey("http://site.example/a#x"))
        assertNull(InstapaperLists.addressKey(" "))
    }

    @Test
    fun `a saved article shows like a feed article, without read state`() {
        val a = InstapaperLists.article(bookmark(7))
        assertEquals(InstapaperLists.FEED, a.feedUrl)
        assertEquals("instapaper 7", a.key)
        assertEquals("<p>A &lt;short&gt; summary</p>", a.html)
        assertEquals(1_700_000_000_000, a.publishedAt)
        assertFalse(a.read)
        assertTrue(InstapaperLists.isSaved(a))
        assertNull(InstapaperLists.article(bookmark(8).copy(url = "javascript:alert(1)")).link)
    }

    @Test
    fun `knows which list a saved article belongs in`() {
        assertTrue(InstapaperLists.inView(InstapaperView.Home, bookmark(1)))
        assertFalse(InstapaperLists.inView(InstapaperView.Home, bookmark(1, folderId = 5)))
        assertTrue(InstapaperLists.inView(InstapaperView.InFolder(5), bookmark(1, folderId = 5)))
        assertFalse(InstapaperLists.inView(InstapaperView.InFolder(5), bookmark(1, folderId = 5, archived = true)))
        assertTrue(InstapaperLists.inView(InstapaperView.Archive, bookmark(1, archived = true)))
        assertTrue(InstapaperLists.inView(InstapaperView.Liked, bookmark(1, liked = true, archived = true)))
        assertTrue(InstapaperLists.inView(InstapaperView.WithTag("ai"), bookmark(1, tags = listOf(InstapaperTag(1, "ai")))))
    }

    @Test
    fun `a change updates the list and drops what no longer belongs`() {
        val list = listOf(bookmark(1), bookmark(2))
        assertEquals(listOf(2L), InstapaperLists.apply(InstapaperView.Home, list, 1, BookmarkChange.Archived(true)).map { it.id })
        assertTrue(InstapaperLists.apply(InstapaperView.Home, list, 1, BookmarkChange.Liked(true))[0].liked)
        assertEquals(listOf(1L), InstapaperLists.apply(InstapaperView.Home, list, 2, BookmarkChange.Moved(9)).map { it.id })
        assertEquals(listOf(1L), InstapaperLists.apply(InstapaperView.Home, list, 2, BookmarkChange.Deleted).map { it.id })
        val back = InstapaperLists.patch(bookmark(3, archived = true, folderId = 9), BookmarkChange.Archived(false))!!
        assertFalse(back.archived)
        assertNull(back.folderId)
    }
}
