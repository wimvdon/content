package to.wim.content.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsletterSendersTest {
    private val feedUrl = "https://w.example/newsletters/k7q2m9abcdef/0123456789abcdef0123456789abcdef"
    private val feed = Feed(url = feedUrl, title = "Newsletters", folderId = "1")
    private val rss = Feed(url = "https://example.com/feed.xml", title = "Example")

    private fun issue(
        id: String,
        author: String?,
        site: String?,
        time: Long = 0,
        read: Boolean = false,
        feedUrl: String = this.feedUrl,
    ) = Article(feedUrl, id, "Issue $id", null, author, "", time, time, read = read, authorUrl = site)

    private val bozarNews = issue("1", "Bozar", "https://bozar.be", time = 10)
    private val bozarEvents = issue("2", "Bozar Events", "https://bozar.be", time = 20)
    private val platformer = issue("3", "Platformer", "https://substack.com", time = 30)
    private val stratechery = issue("4", "Stratechery", "https://substack.com", time = 40, read = true)
    private val old = issue("5", "De Standaard", null, time = 5)
    private val bozarSite = issue("6", "Bozar", "https://bozar-mail.be", time = 15)

    @Test
    fun `a sender is its website, or on a shared platform its website and name, or else its name`() {
        assertEquals("bozar.be", NewsletterSenders.keyOf(bozarNews))
        assertEquals("bozar.be", NewsletterSenders.keyOf(bozarEvents))
        assertEquals("substack.com/platformer", NewsletterSenders.keyOf(platformer))
        assertEquals("name:de standaard", NewsletterSenders.keyOf(old))
        assertEquals("name:de standaard", NewsletterSenders.keyOf(issue("x", "  De   STANDAARD ", null)))
        assertNull(NewsletterSenders.keyOf(issue("x", null, null)))
        assertEquals("bozar.be", NewsletterSenders.siteOf("bozar.be"))
        assertEquals("substack.com", NewsletterSenders.siteOf("substack.com/platformer"))
        assertNull(NewsletterSenders.siteOf("name:de standaard"))
    }

    @Test
    fun `the senders of a feed, by name, with the newest issue's name and the counts`() {
        val list = NewsletterSenders().list(listOf(bozarNews, bozarEvents, platformer, stratechery, old, issue("9", "Other", null, feedUrl = rss.url)), feedUrl)
        assertEquals(listOf("Bozar Events", "De Standaard", "Platformer", "Stratechery"), list.map { it.name })
        val bozar = list.first()
        assertEquals("bozar.be", bozar.key)
        assertEquals(2, bozar.count)
        assertEquals(2, bozar.unread)
        assertEquals(20L, bozar.newestAt)
        assertEquals(0, list.last().unread)
        assertEquals(Feed(url = "https://bozar.be", title = "Bozar Events", iconUrl = "https://bozar.be/favicon.ico", folderId = "1"), bozar.shownAs(feed))
        assertEquals(feed.copy(title = "De Standaard"), list[1].shownAs(feed))
    }

    @Test
    fun `an article's sender is the one it belongs to`() {
        val articles = listOf(bozarNews, bozarEvents, platformer, old)
        assertEquals("bozar.be", NewsletterSenders().senderOf(articles, bozarNews)?.key)
        assertEquals(2, NewsletterSenders().senderOf(articles, bozarNews)?.count)
        assertNull(NewsletterSenders().senderOf(articles, issue("x", null, null)))
    }

    @Test
    fun `renaming shows the new name everywhere, and an empty name brings back the sender's own`() {
        val senders = NewsletterSenders().renamed("bozar.be", "  Bozar  Brussels ")
        assertEquals("Bozar Brussels", senders.rule("bozar.be")?.name)
        assertEquals("Bozar Brussels", senders.shown(feed, bozarEvents)?.title)
        assertEquals("Bozar Brussels", senders.list(listOf(bozarEvents), feedUrl).single().name)
        assertEquals(NewsletterSenders(), senders.renamed("bozar.be", " "))
        // Not a newsletter feed: nothing to show instead.
        assertNull(senders.shown(rss, bozarEvents.copy(feedUrl = rss.url)))
    }

    @Test
    fun `the names are stored as text and read back, skipping what can't be read`() {
        val senders = NewsletterSenders().renamed("bozar.be", "Bozar").renamed("substack.com/platformer", "Platformer")
        val text = senders.encode()
        assertEquals("bozar.be\tBozar\t\t0\nsubstack.com/platformer\tPlatformer\t\t0", text)
        assertEquals(senders, NewsletterSenders.decode(text))
        assertEquals(NewsletterSenders(), NewsletterSenders.decode(null))
        assertEquals(NewsletterSenders(listOf(SenderRule("a", "A"))), NewsletterSenders.decode("a\tA\t\t0\nbroken\n\t\t\t1\nnothing\t\t\t0"))
    }

    @Test
    fun `combined, muted and moved senders stored by an earlier version no longer apply, their names stay`() {
        val senders = NewsletterSenders.decode("bozar-mail.be\t\tbozar.be\t0\nbozar.be\tBozar\t\t1\tt:1\nsubstack.com/platformer\t\t\t1")
        assertEquals(NewsletterSenders(listOf(SenderRule("bozar.be", "Bozar"))), senders)
        assertEquals(2, senders.list(listOf(bozarNews, bozarSite), feedUrl).size)
        assertEquals("bozar.be\tBozar\t\t0", senders.encode())
    }

    @Test
    fun `a sender chosen shows only its newsletters, and Back goes to its feed`() {
        val library = Library(feeds = listOf(feed), articles = listOf(bozarNews, bozarEvents, platformer))
        val selection = FeedSelection.Sender(feedUrl, "bozar.be")
        assertEquals(listOf("2", "1"), library.articlesFor(selection, unreadOnly = false).map { it.id })
        assertEquals(2, library.unreadCount(selection))
        assertEquals(FeedSelection.Single(feedUrl), selection.up(library.feeds))
        assertEquals(selection, library.shown(selection))
        assertEquals(FeedSelection.All, Library().shown(selection))
    }

    @Test
    fun `the sender chips - a newsletter feed's senders with something in the tab, the one chosen kept`() {
        val library = Library(feeds = listOf(feed, rss), articles = listOf(bozarNews, platformer, stratechery))
        val senders = NewsletterSenders()
        assertEquals(listOf("Bozar", "Platformer", "Stratechery"), senders.chips(library, FeedSelection.Single(feedUrl), unreadOnly = false).map { it.name })
        assertEquals(listOf("Bozar", "Platformer"), senders.chips(library, FeedSelection.Single(feedUrl), unreadOnly = true).map { it.name })
        assertEquals(
            listOf("Bozar", "Platformer", "Stratechery"),
            senders.chips(library, FeedSelection.Sender(feedUrl, "substack.com/stratechery"), unreadOnly = true).map { it.name },
        )
        // Whatever is chosen: every newsletter feed's senders are chips, like feeds; an RSS feed has none.
        assertEquals(listOf("Bozar", "Platformer", "Stratechery"), senders.chips(library, FeedSelection.Single(rss.url), unreadOnly = false).map { it.name })
        assertEquals(listOf("Bozar", "Platformer"), senders.chips(library, FeedSelection.All, unreadOnly = true).map { it.name })
        assertEquals(emptyList<NewsletterSender>(), senders.chips(library.copy(feeds = listOf(rss)), FeedSelection.All, unreadOnly = false))
    }

    // Facts: when they come, and how often.
    private val day = 24 * 60 * 60 * 1000L

    @Test
    fun `cadence is the median gap between the newest ten issues, and unknown under three issues`() {
        assertNull(NewsletterSenders.cadence(emptyList()))
        assertNull(NewsletterSenders.cadence(listOf(0, day)))
        assertEquals(Cadence.Daily, NewsletterSenders.cadence(listOf(0, day, 2 * day)))
        // A weekdays-only newsletter is daily: the weekend gap is not the median.
        assertEquals(Cadence.Daily, NewsletterSenders.cadence(listOf(0, day, 2 * day, 3 * day, 6 * day, 7 * day)))
        assertEquals(Cadence.Weekly, NewsletterSenders.cadence(listOf(0, 7 * day, 14 * day)))
        // Twice a week is still about weekly; any order is fine.
        assertEquals(Cadence.Weekly, NewsletterSenders.cadence(listOf(7 * day, 0, 3 * day, 10 * day)))
        assertEquals(Cadence.EveryTwoWeeks, NewsletterSenders.cadence(listOf(0, 14 * day, 28 * day)))
        assertEquals(Cadence.Monthly, NewsletterSenders.cadence(listOf(0, 30 * day, 61 * day)))
        assertEquals(Cadence.NowAndThen, NewsletterSenders.cadence(listOf(0, 90 * day, 200 * day)))
        // The thresholds: 2, 10, 21 and 45 days belong to the faster one.
        assertEquals(Cadence.Daily, NewsletterSenders.cadence(listOf(0, 2 * day, 4 * day)))
        assertEquals(Cadence.Weekly, NewsletterSenders.cadence(listOf(0, 10 * day, 20 * day)))
        assertEquals(Cadence.EveryTwoWeeks, NewsletterSenders.cadence(listOf(0, 21 * day, 42 * day)))
        assertEquals(Cadence.Monthly, NewsletterSenders.cadence(listOf(0, 45 * day, 90 * day)))
        // Only the newest ten count: old weekly issues don't weigh on a sender now daily.
        val recent = (0L until 10L).map { 1000 * day + it * day }
        assertEquals(Cadence.Daily, NewsletterSenders.cadence(recent + (0L until 5L).map { it * 7 * day }))
    }

    @Test
    fun `a sender knows when its issues came and which is the newest`() {
        val sender = NewsletterSenders().list(listOf(bozarNews, bozarEvents, platformer), feedUrl).first { it.key == "bozar.be" }
        assertEquals(listOf(20L, 10L), sender.times)
        assertEquals(bozarEvents.key, sender.newestKey)
    }

    // Moving a sender to another tag.
    private val feed2Url = "https://w.example/newsletters/zzzzzzzzzzzz/fedcba9876543210fedcba9876543210"
    private val feed2 = Feed(url = feed2Url, title = "Newsletters", folderId = "2")
    private val feedNoTagUrl = "https://w.example/newsletters/yyyyyyyyyyyy/00112233445566778899aabbccddeeff"
    private val feedNoTag = Feed(url = feedNoTagUrl, title = "Newsletters", folderId = null)
    private val twoTags = Library(feeds = listOf(feed, feed2, feedNoTag, rss), folders = listOf(Folder("1", "News"), Folder("2", "Work")))
}
