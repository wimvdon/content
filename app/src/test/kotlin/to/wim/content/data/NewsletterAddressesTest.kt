package to.wim.content.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsletterAddressesTest {
    private fun feedOf(code: String) = "https://w.example/newsletters/$code/0123456789abcdef0123456789abcdef"

    private fun address(
        code: String,
        tagId: String?,
        createdAt: Long = 0,
    ) = NewsletterAddress(code, NewsletterAddresses.tagHash(tagId), "content+$code@in.wim.to", feedOf(code), createdAt)

    private val tech = Folder("1", "Tech")
    private val news = Folder("topic:news", "News")

    @Test
    fun `a newsletter address's feed is recognised, and read from the feed loader's answer`() {
        val feed = feedOf("k7q2m9abcdef")
        assertEquals("k7q2m9abcdef", NewsletterAddresses.codeOfFeed(feed))
        assertTrue(NewsletterAddresses.isNewsletterFeed(feed))
        assertNull(NewsletterAddresses.codeOfFeed("https://example.com/feed.xml"))
        assertNull(NewsletterAddresses.codeOfFeed("https://w.example/newsletters/k7q2m9abcdef/x"))
        val hash = "a".repeat(64)
        val answer =
            mapOf(
                "addresses" to
                    listOf(
                        mapOf("code" to "k7q2m9abcdef", "tagHash" to hash, "address" to "news+k7q2m9abcdef@in.wim.to", "feedUrl" to feed, "createdAt" to 5.0),
                        mapOf("code" to "x"),
                    ),
            )
        assertEquals(listOf(NewsletterAddress("k7q2m9abcdef", hash, "news+k7q2m9abcdef@in.wim.to", feed, 5)), NewsletterAddresses.listOf(answer))
        // No tag; and a feed address that doesn't belong to the code is refused.
        assertNull(NewsletterAddresses.of(mapOf("code" to "k7q2m9abcdef", "tagHash" to null, "address" to "a", "feedUrl" to feed))!!.tagHash)
        assertNull(NewsletterAddresses.of(mapOf("code" to "aaaaaaaaaaaa", "address" to "a", "feedUrl" to feed)))
    }

    @Test
    fun `the feed loader knows a tag only by a hash of its id`() {
        val hash = NewsletterAddresses.tagHash("topic:news")!!
        assertTrue(hash.matches(Regex("[0-9a-f]{64}")))
        assertFalse(hash.contains("news"))
        assertEquals(hash, NewsletterAddresses.tagHash("topic:news"))
        // The same as the web app's (newsletterAddresses.test.ts).
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", NewsletterAddresses.tagHash("abc"))
        assertNull(NewsletterAddresses.tagHash(null))
    }

    @Test
    fun `every tag and No tag get an address, asked for once`() {
        val made = listOf(address("aaaaaaaaaaaa", "1"))
        assertEquals(listOf("topic:news", null), NewsletterAddresses.missing(listOf(tech, news), made))
        assertEquals(emptyList<String?>(), NewsletterAddresses.missing(listOf(tech), made + address("bbbbbbbbbbbb", null)))
        assertEquals("aaaaaaaaaaaa", NewsletterAddresses.forTag(made, "1")?.code)
        assertNull(NewsletterAddresses.forTag(made, null))
    }

    @Test
    fun `an address whose tag is gone is removed after a day, never the one without a tag`() {
        val day = NewsletterAddresses.ORPHAN_GRACE_MS
        val list = listOf(address("aaaaaaaaaaaa", "gone", createdAt = 0), address("bbbbbbbbbbbb", "new", createdAt = 2 * day), address("cccccccccccc", null), address("dddddddddddd", "1"))
        assertEquals(listOf("aaaaaaaaaaaa"), NewsletterAddresses.orphans(listOf(tech), list, now = 2 * day + 1).map { it.code })
    }

    @Test
    fun `the library gets one Newsletters feed per tag, under that tag`() {
        val a = address("aaaaaaaaaaaa", "1")
        val b = address("bbbbbbbbbbbb", null)
        val other = Feed("https://example.com/feed.xml", "Blog", folderId = "1")
        val library = Library(feeds = listOf(other), folders = listOf(tech))
        val result = NewsletterAddresses.reconcile(library, listOf(a, b), "Newsletters")
        assertEquals(listOf(other.url, a.feedUrl, b.feedUrl), result.feeds.map { it.url })
        val tagged = result.feed(a.feedUrl)!!
        assertEquals("1", tagged.folderId)
        assertEquals("Newsletters", tagged.title)
        // Its own name: a refresh keeps it.
        assertTrue(tagged.renamed)
        assertEquals(listOf("Tech"), tagged.tags)
        assertNull(result.feed(b.feedUrl)!!.folderId)
        // Nothing to do: the same library.
        assertSame(result, NewsletterAddresses.reconcile(result, listOf(a, b), "Nieuwsbrieven"))
    }

    @Test
    fun `newsletter feeds of no address, or of a tag that is gone, go, and a moved one goes back`() {
        val a = address("aaaaaaaaaaaa", "1")
        val gone = address("bbbbbbbbbbbb", "gone")
        val library =
            Library(
                feeds =
                    listOf(
                        Feed(a.feedUrl, "Newsletters", folderId = "topic:news", renamed = true),
                        Feed(gone.feedUrl, "Newsletters", renamed = true),
                        Feed(feedOf("cccccccccccc"), "Made by name", folderId = "1"),
                    ),
                folders = listOf(tech, news),
            )
        val result = NewsletterAddresses.reconcile(library, listOf(a, gone), "Newsletters")
        assertEquals("1", result.feed(a.feedUrl)!!.folderId)
        assertNull(result.feed(gone.feedUrl))
        assertNull(result.feed(feedOf("cccccccccccc")))
        // News has no address yet: its feed comes once it has one.
        assertEquals(1, result.feeds.size)
    }

    @Test
    fun `removing a tag removes its newsletter feed, its other feeds stay without a tag`() {
        val newsletter = Feed(feedOf("aaaaaaaaaaaa"), "Newsletters", folderId = "1", renamed = true)
        val blog = Feed("https://example.com/feed.xml", "Blog", folderId = "1")
        val removed = Library(feeds = listOf(newsletter, blog), folders = listOf(tech)).removeFolder("1")
        assertEquals(listOf(blog.copy(folderId = null)), removed.feeds)
    }

    @Test
    fun `Newsletter feeds lists every tag in its order, then No tag, each with its address`() {
        val a = address("aaaaaaaaaaaa", "topic:news")
        val rows = NewsletterAddresses.rows(listOf(tech, news), listOf(a))
        assertEquals(listOf("1", "topic:news", null), rows.map { it.topic?.id })
        assertEquals(listOf(null, "aaaaaaaaaaaa", null), rows.map { it.address?.code })
    }
}
