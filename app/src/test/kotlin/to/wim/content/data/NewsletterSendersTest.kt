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
    fun `an article's sender is the one it belongs to, with what is combined into it`() {
        val articles = listOf(bozarNews, bozarEvents, platformer, old)
        assertEquals("bozar.be", NewsletterSenders().senderOf(articles, bozarNews)?.key)
        assertEquals(2, NewsletterSenders().senderOf(articles, bozarNews)?.count)
        val merged = NewsletterSenders().merged("name:de standaard", "bozar.be", "Bozar")
        val sender = merged.senderOf(articles, old)!!
        assertEquals("bozar.be", sender.key)
        assertEquals(3, sender.count)
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

    @Test
    fun `the tag is stored as a fifth field that an old app ignores, and a line without it reads as before`() {
        val senders = NewsletterSenders().renamed("bozar.be", "Bozar").movedTo("bozar.be", "2", "1").movedTo("substack.com/platformer", NewsletterAddresses.NO_TAG, "1")
        val text = senders.encode()
        assertEquals("bozar.be\tBozar\t\t0\tt:2", text.lines()[0])
        assertEquals("substack.com/platformer\t\t\t0\t-", text.lines()[1])
        assertEquals(senders, NewsletterSenders.decode(text))
        assertEquals("2", NewsletterSenders.decode(text).rule("bozar.be")?.tag)
        assertEquals(NewsletterAddresses.NO_TAG, NewsletterSenders.decode(text).rule("substack.com/platformer")?.tag)
        // A line from before the tag.
        assertNull(NewsletterSenders.decode("bozar.be\tBozar\t\t0").rule("bozar.be")?.tag)
        // What an old app reads from a line with the tag: its first four fields, as before.
        assertEquals(listOf("bozar.be", "Bozar", "", "0"), text.lines()[0].split('\t').take(4))
        // A tag alone is a choice worth keeping; an unreadable tag is dropped, the rest read.
        assertEquals(NewsletterSenders(listOf(SenderRule("a", tag = "x"))), NewsletterSenders.decode("a\t\t\t0\tt:x"))
        assertEquals(NewsletterSenders(listOf(SenderRule("a", "A"))), NewsletterSenders.decode("a\tA\t\t0\tgarbage"))
        assertEquals(NewsletterSenders(), NewsletterSenders.decode("a\t\t\t0\tt:"))
    }

    @Test
    fun `picking the tag it is sent to clears the move, and merging a sender drops its own move`() {
        val moved = NewsletterSenders().movedTo("bozar.be", "2", "1")
        assertEquals("2", moved.rule("bozar.be")?.tag)
        assertEquals(NewsletterSenders(), moved.movedTo("bozar.be", "1", "1"))
        val merged = moved.merged("bozar.be", "substack.com/platformer", "Platformer")
        assertNull(merged.rule("bozar.be")?.tag)
    }

    @Test
    fun `a moved sender's newsletters are listed under the target tag's feed, and no other`() {
        val library = twoTags.copy(articles = listOf(bozarNews, bozarEvents, platformer))
        val senders = NewsletterSenders().movedTo("bozar.be", "2", "1")
        val view = library.withMovedSenders(senders)
        assertEquals(listOf(feed2Url, feed2Url, feedUrl), view.articles.map { it.feedUrl })
        assertEquals(listOf("2", "1"), view.articlesFor(FeedSelection.Single(feed2Url), unreadOnly = false).map { it.id })
        assertEquals(listOf("3"), view.articlesFor(FeedSelection.Single(feedUrl), unreadOnly = false).map { it.id })
        assertEquals(2, view.unreadCount(FeedSelection.InFolder("2")))
        assertEquals(1, view.unreadCount(FeedSelection.InFolder("1")))
        // The senders of each feed follow.
        assertEquals(listOf("Bozar Events"), senders.list(view.articles, feed2Url).map { it.name })
        assertEquals(listOf("Platformer"), senders.list(view.articles, feedUrl).map { it.name })
        assertEquals(feed2Url, senders.senderOf(view.articles, view.articles.first())?.feedUrl)
        // The same library when nothing is moved; and the stored library is never changed.
        assertSame(library, library.withMovedSenders(NewsletterSenders()))
        assertEquals(feedUrl, library.articles.first().feedUrl)
    }

    @Test
    fun `a sender moved to No tag, to a tag without a feed or to the tag it is in`() {
        val library = twoTags.copy(articles = listOf(bozarNews))
        assertEquals(feedNoTagUrl, library.withMovedSenders(NewsletterSenders().movedTo("bozar.be", NewsletterAddresses.NO_TAG, "1")).articles.single().feedUrl)
        assertEquals(feedUrl, library.withMovedSenders(NewsletterSenders().movedTo("bozar.be", "gone", "1")).articles.single().feedUrl)
        assertEquals(feedUrl, library.withMovedSenders(NewsletterSenders(listOf(SenderRule("bozar.be", tag = "1")))).articles.single().feedUrl)
        // An RSS feed's article of the same author is never moved.
        val rssArticle = issue("9", "Bozar", "https://bozar.be", feedUrl = rss.url)
        assertEquals(rss.url, twoTags.copy(articles = listOf(rssArticle)).withMovedSenders(NewsletterSenders().movedTo("bozar.be", "2", "1")).articles.single().feedUrl)
    }

    @Test
    fun `a combined sender's newsletters all follow the move of the sender they are combined into`() {
        val library = twoTags.copy(articles = listOf(bozarNews, bozarSite, platformer))
        val senders = NewsletterSenders().merged("bozar-mail.be", "bozar.be", "Bozar").movedTo("bozar.be", "2", "1")
        assertEquals(listOf(feed2Url, feed2Url, feedUrl), library.withMovedSenders(senders).articles.map { it.feedUrl })
    }

    @Test
    fun `read state keeps working for moved newsletters, because their key stays the same`() {
        val library = twoTags.copy(articles = listOf(bozarNews, bozarEvents, platformer))
        val senders = NewsletterSenders().movedTo("bozar.be", "2", "1")
        val view = library.withMovedSenders(senders)
        assertEquals(library.articles.map { it.key }, view.articles.map { it.key })
        assertEquals(bozarNews.key, view.article(bozarNews.key)?.key)
        // Marking what the list shows as read marks the stored articles, not copies.
        val keys = view.unreadKeys(FeedSelection.Single(feed2Url))
        assertEquals(setOf(bozarNews.key, bozarEvents.key), keys)
        val stored = library.setRead(keys, true)
        assertEquals(1, stored.unreadCount(FeedSelection.All))
        assertEquals(0, stored.withMovedSenders(senders).unreadCount(FeedSelection.Single(feed2Url)))
        assertEquals(feedUrl, stored.articles.first().feedUrl)
        // A mark kept from before the move still applies after it.
        val readBefore = library.setRead(setOf(bozarNews.key), true).withMovedSenders(senders)
        assertEquals(1, readBefore.unreadCount(FeedSelection.Single(feed2Url)))
        // Muted senders still leave the moved lists.
        assertEquals(listOf("3"), view.withoutMuted(senders.muted("bozar.be", true)).articles.map { it.id })
    }

    @Test
    fun `the tag a sender is sent to is read from the stored library, whatever it is shown under`() {
        val library = twoTags.copy(articles = listOf(bozarNews, platformer, issue("7", "X", null, feedUrl = feedNoTagUrl)))
        val senders = NewsletterSenders().movedTo("bozar.be", "2", "1")
        assertEquals("1", NewsletterSenders.originalTag(library, senders, "bozar.be"))
        assertEquals(NewsletterAddresses.NO_TAG, NewsletterSenders.originalTag(library, senders, "name:x"))
        assertNull(NewsletterSenders.originalTag(library, senders, "nobody.be"))
    }
}
