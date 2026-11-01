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
        // Only News has something unread: one chip, which would filter nothing.
        val news = setOf(bbc.url, npr.url)
        assertFalse(FeedChips.of(feeds, folders, listOf("News"), FeedSelection.All, news).rowShown)
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
}
