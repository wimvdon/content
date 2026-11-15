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
    fun `merging puts one sender in another, which keeps its name and icon, and separating takes it out`() {
        val merged = NewsletterSenders().merged("bozar-mail.be", "bozar.be", "Bozar")
        assertEquals("bozar.be", merged.groupOf("bozar-mail.be"))
        assertEquals("Bozar", merged.rule("bozar.be")?.name)
        val shown = merged.shown(feed, bozarSite)!!
        assertEquals("Bozar", shown.title)
        assertEquals("https://bozar.be/favicon.ico", shown.iconUrl)
        val group = merged.list(listOf(bozarNews, bozarSite), feedUrl).single()
        assertEquals(listOf("bozar.be", "bozar-mail.be"), group.members.map { it.key })
        assertEquals(2, group.count)
        assertEquals(FeedSelection.Sender(feedUrl, "bozar.be", setOf("bozar.be", "bozar-mail.be")), FeedSelection.Sender.of(group))
        val split = merged.split("bozar-mail.be")
        assertEquals("bozar-mail.be", split.groupOf("bozar-mail.be"))
        assertEquals(2, split.list(listOf(bozarNews, bozarSite), feedUrl).size)
    }

    @Test
    fun `merging a group moves its members too, and never makes a loop`() {
        val senders =
            NewsletterSenders()
                .merged("substack.com/stratechery", "substack.com/platformer", "Platformer")
                .merged("substack.com/platformer", "bozar.be", "Bozar")
        assertEquals("bozar.be", senders.groupOf("substack.com/stratechery"))
        assertEquals("bozar.be", senders.groupOf("substack.com/platformer"))
        assertSame(senders, senders.merged("bozar.be", "substack.com/stratechery", "x"))
        // A loop from elsewhere (two devices at once) stops where it started.
        val loop = NewsletterSenders(listOf(SenderRule("a", into = "b"), SenderRule("b", into = "a")))
        assertEquals("a", loop.groupOf("a"))
    }

    @Test
    fun `a muted sender's newsletters leave the lists, and no other`() {
        val senders = NewsletterSenders().merged("bozar-mail.be", "bozar.be", "Bozar").muted("bozar.be", true)
        assertTrue(senders.isMuted(bozarSite))
        assertFalse(senders.isMuted(platformer))
        val library = Library(feeds = listOf(feed, rss), articles = listOf(bozarNews, bozarSite, platformer, issue("9", "Bozar", "https://bozar.be", feedUrl = rss.url)))
        assertEquals(listOf("3", "9"), library.withoutMuted(senders).articles.map { it.id })
        assertSame(library, library.withoutMuted(NewsletterSenders()))
        assertTrue(senders.list(library.articles, feedUrl).first { it.key == "bozar.be" }.muted)
        assertEquals(NewsletterSenders().merged("bozar-mail.be", "bozar.be", "Bozar"), senders.muted("bozar.be", false))
    }

    @Test
    fun `the choices are stored as text and read back, skipping what can't be read`() {
        val senders = NewsletterSenders().renamed("bozar.be", "Bozar").merged("substack.com/platformer", "bozar.be", "x").muted("name:de standaard", true)
        val text = senders.encode()
        assertEquals("bozar.be\tBozar\t\t0\nsubstack.com/platformer\t\tbozar.be\t0\nname:de standaard\t\t\t1", text)
        assertEquals(senders, NewsletterSenders.decode(text))
        assertEquals(NewsletterSenders(), NewsletterSenders.decode(null))
        assertEquals(NewsletterSenders(listOf(SenderRule("a", "A"))), NewsletterSenders.decode("a\tA\t\t0\nbroken\n\t\t\t1\nnothing\t\t\t0"))
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
        assertEquals(emptyList<NewsletterSender>(), senders.chips(library, FeedSelection.Single(rss.url), unreadOnly = false))
        assertEquals(emptyList<NewsletterSender>(), senders.chips(library, FeedSelection.All, unreadOnly = false))
    }
}
