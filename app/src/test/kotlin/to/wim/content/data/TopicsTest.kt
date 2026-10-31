package to.wim.content.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class TopicsTest {
    private fun saved(
        key: String,
        vararg tags: String,
    ) = Item(
        key = key,
        url = "https://$key",
        title = key,
        createdAt = 1L,
        readingList = ReadingListEntry(ReadingState.ToRead, 1L),
        tags = tags.toList(),
    )

    private val library =
        Library(
            feeds = listOf(Feed("https://a/feed", "A", folderId = "f1"), Feed("https://b/feed", "B", folderId = "f2"), Feed("https://c/feed", "C")),
            folders = listOf(Folder("f1", "Tech", "computer"), Folder("f2", "News", "newspaper")),
            items = listOf(saved("x.example/1", "tech", "Recipes"), saved("x.example/2", "News"), saved("x.example/3", "recipes")),
        ).feedFoldersToTags()

    @Test
    fun `every tag has its topic, made the same on every device`() {
        val settled = library.topicsForTags()
        // Tech and News have one already (case ignored); Recipes gets one at the end, once.
        assertEquals(listOf("Tech", "News", "Recipes"), settled.folders.map { it.title })
        assertEquals(Folder("topic:recipes", "Recipes"), settled.folders.last())
        assertEquals("topic:recipes", topicIdFor(" RECIPES "))
        assertSame(settled, settled.topicsForTags())
    }

    @Test
    fun `topics with the same name become one, with the feeds of both`() {
        val twice = library.copy(folders = library.folders + Folder("f3", "tech", "star"), feeds = library.feeds + Feed("https://d/feed", "D", folderId = "f3"))
        val joined = twice.topicsForTags()
        assertEquals(listOf("f1", "f2", "topic:recipes"), joined.folders.map { it.id })
        assertEquals("f1", joined.feed("https://d/feed")!!.folderId)
    }

    @Test
    fun `adding a topic never makes a second one with the same name`() {
        assertSame(library, library.addTopic("new", " news "))
        assertSame(library, library.addTopic("new", "  "))
        val added = library.addTopic("new", " Sports ", "sports-soccer")
        assertEquals(Folder("new", "Sports", "sports-soccer"), added.folders.last())
        assertEquals("f2", library.topicNamed("NEWS")!!.id)
        assertNull(library.topicNamed("Sports"))
    }

    @Test
    fun `renaming a topic renames it on its feeds and saved articles`() {
        val renamed = library.topicsForTags().renameTopic("f1", "Technology", "memory")
        assertEquals(Folder("f1", "Technology", "memory"), renamed.folder("f1"))
        assertEquals(listOf("Technology"), renamed.feed("https://a/feed")!!.tags)
        assertEquals(listOf("Technology", "Recipes"), renamed.item("x.example/1")!!.tags)
        // A blank name changes nothing.
        assertSame(library, library.renameTopic("f1", " ", "memory"))
    }

    @Test
    fun `renaming a topic to another one's name joins the two`() {
        val joined = library.topicsForTags().renameTopic("topic:recipes", "news", "restaurant")
        // News stays, with its icon; Recipes' articles get News instead.
        assertEquals(listOf("f1", "f2"), joined.folders.map { it.id })
        assertEquals("newspaper", joined.folder("f2")!!.icon)
        assertEquals(listOf("tech", "News"), joined.item("x.example/1")!!.tags)
        assertEquals(listOf("News"), joined.item("x.example/3")!!.tags)
        // A topic with feeds joining another: its feeds move along.
        val feedsMoved = library.topicsForTags().renameTopic("f1", "News", "computer")
        assertEquals("f2", feedsMoved.feed("https://a/feed")!!.folderId)
        assertEquals(listOf("News"), feedsMoved.feed("https://a/feed")!!.tags)
    }

    @Test
    fun `removing a topic keeps its feeds and saved articles, without it`() {
        val removed = library.topicsForTags().removeTopic("f1")
        assertEquals(listOf("f2", "topic:recipes"), removed.folders.map { it.id })
        assertNull(removed.feed("https://a/feed")!!.folderId)
        assertEquals(emptyList<String>(), removed.feed("https://a/feed")!!.tags)
        assertEquals(listOf("Recipes"), removed.item("x.example/1")!!.tags)
        assertEquals(3, removed.items.size)
    }

    @Test
    fun `counts feeds and saved articles per topic, and orders names like the topics`() {
        val settled = library.topicsForTags()
        assertEquals(
            mapOf("f1" to TopicCount(1, 1, 1), "f2" to TopicCount(1, 1, 1), "topic:recipes" to TopicCount(0, 2, 2)),
            settled.topicCounts(),
        )
        assertEquals(listOf("tech", "News", "recipes", "Zoo"), settled.inTopicOrder(listOf("Zoo", "recipes", "News", "tech")))
        assertEquals(listOf("Tech", "News", "Recipes"), settled.topicNames())
        assertEquals("computer", settled.topicIcons()["tech"])
    }

    @Test
    fun `saving a feed article gives it the feed's topic`() {
        val article = Article("https://a/feed", "1", "Post", "https://a.example/post", null, "", null, 0L)
        val withArticle = library.topicsForTags().copy(articles = listOf(article))
        val (saved, item) = withArticle.addToReadingList("https://a.example/post", "Post", 5L)
        val tagged = saved.tagWithFeedFolder(item!!.key)
        // In the topic's spelling of a tag already in use ("tech" on another article).
        assertEquals(listOf("tech"), tagged.item(item.key)!!.tags)
    }

    @Test
    fun `Feeds' places are the topics with feeds, and Back goes up a level`() {
        val settled = library.topicsForTags()
        assertEquals(listOf("f1", "f2"), feedTopics(settled.feeds, settled.folders).map { it.id })
        // From a feed to its topic, from a topic to All, from All nowhere (the app closes).
        assertEquals(FeedSelection.InFolder("f1"), FeedSelection.Single("https://a/feed").up(settled.feeds))
        assertEquals(FeedSelection.All, FeedSelection.Single("https://c/feed").up(settled.feeds))
        assertEquals(FeedSelection.All, FeedSelection.InFolder("f1").up(settled.feeds))
        assertNull(FeedSelection.All.up(settled.feeds))
    }

    @Test
    fun `reorders topics, and one topic's feeds without moving the others`() {
        val settled = library.topicsForTags()
        assertEquals(listOf("topic:recipes", "f1", "f2"), settled.reorderTopics(listOf("topic:recipes", "f1")).folders.map { it.id })
        assertSame(settled, settled.reorderTopics(listOf("f1", "f2", "topic:recipes")))
        val more = settled.copy(feeds = settled.feeds + Feed("https://d/feed", "D", folderId = "f1"))
        // a(f1), b(f2), c(none), d(f1): f1's feeds swap places, b and c stay where they are.
        val swapped = more.reorderFeedsIn("f1", listOf("https://d/feed", "https://a/feed"))
        assertEquals(listOf("https://d/feed", "https://b/feed", "https://c/feed", "https://a/feed"), swapped.feeds.map { it.url })
        assertSame(more, more.reorderFeedsIn("f1", listOf("https://a/feed", "https://d/feed")))
    }

    @Test
    fun `merging keeps the topic picked, with the other's feeds and saved articles`() {
        val merged = library.topicsForTags().mergeTopic("f1", "f2")
        assertEquals(listOf("f2", "topic:recipes"), merged.folders.map { it.id })
        assertEquals("f2", merged.feed("https://a/feed")!!.folderId)
        assertEquals(listOf("News", "Recipes"), merged.item("x.example/1")!!.tags)
        val settled = library.topicsForTags()
        assertSame(settled, settled.mergeTopic("f1", "f1"))
        assertSame(settled, settled.mergeTopic("gone", "f1"))
    }


    @Test
    fun `Feeds' chips leave out topics with nothing in the state shown, except the one chosen`() {
        val article = { feed: String, id: String, read: Boolean -> Article(feed, id, id, null, null, "", null, 0L, read = read) }
        val withArticles = library.topicsForTags().copy(articles = listOf(article("https://a/feed", "1", false), article("https://b/feed", "2", true)))
        // Tech has an unread article; News only a read one.
        assertEquals(listOf("Tech"), withArticles.feedChips(unreadOnly = true, chosen = null))
        assertEquals(listOf("Tech", "News"), withArticles.feedChips(unreadOnly = false, chosen = null))
        // The chosen one stays, so it doesn't disappear under your finger.
        assertEquals(listOf("Tech", "News"), withArticles.feedChips(unreadOnly = true, chosen = "news"))
    }

    @Test
    fun `Saved's chips leave out topics and Liked with nothing in the state shown, except the chosen ones`() {
        val archived = saved("x.example/9", "Old").copy(readingList = ReadingListEntry(ReadingState.Archived, 1L), liked = true)
        val lib = library.copy(items = library.items + archived).topicsForTags()
        assertEquals(listOf("tech", "News", "Recipes"), Saved.chipTags(lib, SavedFilter(SavedTab.ToRead)))
        assertEquals(listOf("Old"), Saved.chipTags(lib, SavedFilter(SavedTab.Archive)))
        assertEquals(listOf("tech", "Old"), Saved.chipTags(lib, SavedFilter(SavedTab.Archive, tags = setOf("tech"))))
        assertFalse(Saved.showsLiked(lib, SavedFilter(SavedTab.ToRead)))
        assertTrue(Saved.showsLiked(lib, SavedFilter(SavedTab.Archive)))
        assertTrue(Saved.showsLiked(lib, SavedFilter(SavedTab.ToRead, likedOnly = true)))
    }
}
