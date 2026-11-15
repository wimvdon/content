package to.wim.content.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedChipsTest {
    private val folders = listOf(Folder("f1", "News"), Folder("f2", "Tech"))
    private val bbc = Feed("https://bbc/feed", "BBC", folderId = "f1", tags = listOf("News"))
    private val npr = Feed("https://npr/feed", "NPR", folderId = "f1", tags = listOf("News"))
    private val verge = Feed("https://verge/feed", "The Verge", folderId = "f2", tags = listOf("Tech"))
    private val blog = Feed("https://blog/feed", "A blog")
    private val gone = Feed("https://gone/feed", "Topic removed", folderId = "f9")
    private val feeds = listOf(bbc, npr, verge, blog, gone)
    private val topics = listOf("News", "Tech")

    @Test
    fun `on All, the topics then the feeds without a topic, and no topic's feeds`() {
        val chips = FeedChips.of(feeds, folders, topics, FeedSelection.All)
        assertEquals(topics, chips.topics)
        assertEquals(listOf(blog, gone), chips.loose)
        assertNull(chips.chosenTopic)
        assertTrue(chips.topicFeeds.isEmpty())
        assertNull(chips.chosenFeed)
    }

    @Test
    fun `a topic chosen shows its feeds`() {
        val chips = FeedChips.of(feeds, folders, topics, FeedSelection.Tagged("news"))
        assertEquals("news", chips.chosenTopic)
        assertEquals(listOf(bbc, npr), chips.topicFeeds)
        assertEquals(chips, FeedChips.of(feeds, folders, topics, FeedSelection.Tagged("news")))
        // Chosen as a folder (from a shortcut) it is the same topic.
        assertEquals(listOf(bbc, npr), FeedChips.of(feeds, folders, topics, FeedSelection.InFolder("f1")).topicFeeds)
    }

    @Test
    fun `a feed in a topic keeps its topic chosen and its feeds, with the feed chosen`() {
        val chips = FeedChips.of(feeds, folders, topics, FeedSelection.Single(npr.url))
        assertEquals("News", chips.chosenTopic)
        assertEquals(listOf(bbc, npr), chips.topicFeeds)
        assertEquals(npr.url, chips.chosenFeed)
        assertEquals(listOf(blog, gone), chips.loose)
    }

    @Test
    fun `one row - all topics and loose feeds, or the chosen topic alone with its feeds`() {
        val all = FeedChips.of(feeds, folders, topics, FeedSelection.All)
        assertEquals(topics, all.rowTopics)
        assertEquals(listOf(blog, gone), all.rowLoose)
        // A topic chosen (in any case): only that topic, in its own spelling, and no loose feeds.
        val news = FeedChips.of(feeds, folders, topics, FeedSelection.Tagged("news"))
        assertEquals(listOf("News"), news.rowTopics)
        assertTrue(news.rowLoose.isEmpty())
        // A feed in a topic: the same.
        val one = FeedChips.of(feeds, folders, topics, FeedSelection.Single(npr.url))
        assertEquals(listOf("News"), one.rowTopics)
        assertTrue(one.rowLoose.isEmpty())
        // A feed without a topic: everything stays, the feed lit among the loose ones.
        val loose = FeedChips.of(feeds, folders, topics, FeedSelection.Single(blog.url))
        assertEquals(topics, loose.rowTopics)
        assertEquals(listOf(blog, gone), loose.rowLoose)
    }

    @Test
    fun `the row shows only with more than one chip, or with something chosen`() {
        assertTrue(FeedChips.of(feeds, folders, topics, FeedSelection.All).rowShown)
        // Only News has something unread, in two feeds: the one chip stays, as it leads to BBC and NPR.
        val news = setOf(bbc.url, npr.url)
        assertTrue(FeedChips.of(feeds, folders, listOf("News"), FeedSelection.All, news).rowShown)
        // Only one of its feeds has something unread: the chip would lead nowhere, so no row.
        assertFalse(FeedChips.of(feeds, folders, listOf("News"), FeedSelection.All, setOf(bbc.url)).rowShown)
        // One topic and one loose feed: two chips, each narrows the list.
        assertTrue(FeedChips.of(feeds, folders, listOf("News"), FeedSelection.All, news + blog.url).rowShown)
        // Only one loose feed: hidden too.
        assertFalse(FeedChips.of(feeds, folders, emptyList(), FeedSelection.All, setOf(blog.url)).rowShown)
        // Chosen, it stays, so it can be let go.
        assertTrue(FeedChips.of(feeds, folders, listOf("News"), FeedSelection.Tagged("News"), news).rowShown)
        assertTrue(FeedChips.of(feeds, folders, emptyList(), FeedSelection.Single(blog.url), setOf(blog.url)).rowShown)
    }

    @Test
    fun `a feed without a topic is chosen in the first row`() {
        val chips = FeedChips.of(feeds, folders, topics, FeedSelection.Single(blog.url))
        assertNull(chips.chosenTopic)
        assertTrue(chips.topicFeeds.isEmpty())
        assertEquals(blog.url, chips.chosenFeed)
        assertEquals(listOf(blog, gone), chips.loose)
    }

    @Test
    fun `the chosen topic stays a chip when it has nothing in the state shown`() {
        val chips = FeedChips.of(feeds, folders, listOf("Tech"), FeedSelection.Single(bbc.url))
        assertEquals(listOf("Tech", "News"), chips.topics)
        assertEquals("News", chips.chosenTopic)
    }

    @Test
    fun `a feed with only a tag, no folder, is in that topic, not loose`() {
        val tagged = Feed("https://t/feed", "Tagged", tags = listOf("Tech"))
        val chips = FeedChips.of(feeds + tagged, folders, topics, FeedSelection.Single(tagged.url))
        assertEquals("Tech", chips.chosenTopic)
        assertEquals(listOf(verge, tagged), chips.topicFeeds)
        assertTrue(tagged !in chips.loose)
    }

    @Test
    fun `a feed with nothing in the tab shown is no chip, except the one chosen`() {
        val shown = setOf(npr.url, gone.url)
        val all = FeedChips.of(feeds, folders, topics, FeedSelection.All, shown)
        assertEquals(listOf(gone), all.loose)
        val topic = FeedChips.of(feeds, folders, topics, FeedSelection.Tagged("News"), shown)
        assertEquals(listOf(npr), topic.topicFeeds)
        // The feed chosen stays, so it doesn't disappear under your finger.
        val chosen = FeedChips.of(feeds, folders, topics, FeedSelection.Single(bbc.url), shown)
        assertEquals(listOf(bbc, npr), chosen.topicFeeds)
        assertEquals(listOf(blog, gone), FeedChips.of(feeds, folders, topics, FeedSelection.Single(blog.url), shown).loose)
    }

    @Test
    fun `the unread dot shows only in All, on a chip with something unread`() {
        assertTrue(FeedChips.showsUnreadDot(unreadOnly = false, unread = 3))
        assertFalse(FeedChips.showsUnreadDot(unreadOnly = false, unread = 0))
        // In Unread every chip shown has something unread: a dot would say nothing.
        assertFalse(FeedChips.showsUnreadDot(unreadOnly = true, unread = 3))
    }

    private val letters = Feed("https://w.example/newsletters/k7q2m9abcdef/0123456789abcdef0123456789abcdef", "Newsletters", folderId = "f1", tags = listOf("News"))

    private fun sender(key: String) = NewsletterSender(letters.url, key, key, listOf(SenderMember(key, key)), 1, 1, false, 0)

    @Test
    fun `a newsletter feed's senders are chips of their own after it, wherever its chip is`() {
        val all = feeds + letters
        val senders = listOf(sender("bozar.be"), sender("substack.com/platformer"))
        // Nothing chosen: like its feed, a tagged feed's senders wait under its topic; a feed without a tag shows them at once.
        assertTrue(FeedChips.of(all, folders, topics, FeedSelection.All, senders = senders).senders.isEmpty())
        val untagged = letters.copy(folderId = null, tags = emptyList())
        val looseSenders = senders.map { it.copy(feedUrl = untagged.url) }
        assertEquals(looseSenders, FeedChips.of(feeds + untagged, folders, topics, FeedSelection.All, senders = looseSenders).sendersOf(untagged.url))
        // Its topic chosen: the topic's feeds, then after Newsletters its senders.
        val topic = FeedChips.of(all, folders, topics, FeedSelection.Tagged("News"), senders = senders)
        assertEquals(listOf(bbc, npr, letters), topic.topicFeeds)
        assertEquals(senders, topic.sendersOf(letters.url))
        assertTrue(topic.sendersOf(bbc.url).isEmpty())
        // A sender chosen: the row stays the same, with that sender chosen and not its feed.
        val picked = FeedChips.of(all, folders, topics, FeedSelection.Sender(letters.url, "bozar.be"), senders = senders)
        assertEquals("News", picked.chosenTopic)
        assertEquals(listOf(bbc, npr, letters), picked.topicFeeds)
        assertEquals("bozar.be", picked.chosenSender)
        assertFalse(picked.feedChosen(letters.url))
        assertTrue(FeedChips.of(all, folders, topics, FeedSelection.Single(letters.url), senders = senders).feedChosen(letters.url))
        // A feed's only sender gets a chip of its own too.
        assertEquals(senders.take(1), FeedChips.of(all, folders, topics, FeedSelection.Tagged("News"), senders = senders.take(1)).senders)
        assertEquals(senders.take(1), FeedChips.of(all, folders, topics, FeedSelection.Sender(letters.url, "bozar.be"), senders = senders.take(1)).senders)
    }
}
