package be.wimvandonck.reader.instapaper

import be.wimvandonck.reader.data.Article
import be.wimvandonck.reader.data.Feed
import be.wimvandonck.reader.data.Library
import be.wimvandonck.reader.data.LibraryCodec
import be.wimvandonck.reader.data.ReadingState
import be.wimvandonck.reader.data.item
import be.wimvandonck.reader.data.keepAsBookmark
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedItemsTest {
    private val feedArticle = Article("https://news.example/feed", "7", "From the feed", "https://news.example/story", null, "<p>Feed text</p>", 1, 1)

    private var library = Library(feeds = listOf(Feed("https://news.example/feed", "News")), articles = listOf(feedArticle))
    private var clock = 1_000_000L
    private val storage = ItemsSavedStorage(read = { library }, update = { change -> change(library).also { library = it } }, now = { clock })
    private val saved = LocalSaved(storage) { clock }

    private fun list(view: InstapaperView) = runBlocking { saved.list(view, 0, 50).bookmarks }

    @Test
    fun `a migrated sample keeps every feed, read mark and saved article`() {
        // What a phone had before #106: library.txt and the "saved" file.
        val legacy =
            LibraryCodec.decode(
                LibraryCodec.encode(
                    Library(
                        feeds = listOf(Feed("https://news.example/feed", "News", folderId = null)),
                        articles = listOf(feedArticle.copy(read = true), feedArticle.copy(id = "8", link = "https://news.example/other")),
                    ),
                ),
            )
        val tag = InstapaperTag(3, "work")
        val savedData =
            LocalSavedCodec.decode(
                LocalSavedCodec.encode(
                    LocalSavedData(
                        bookmarks =
                            listOf(
                                InstapaperBookmark(1, "https://www.news.example/story/", "Saved title", "", 100, liked = true, archived = false, folderId = null, tags = listOf(tag)),
                                InstapaperBookmark(2, "https://elsewhere.example/a", "Elsewhere", "Desc", 200, liked = false, archived = true, folderId = null, tags = emptyList()),
                                // The same link written another way: one item, the newest wins.
                                InstapaperBookmark(4, "http://elsewhere.example/a#x", "Elsewhere again", "", 300, liked = false, archived = false, folderId = null, tags = listOf(tag)),
                                InstapaperBookmark(5, null, "No link", "", 50, liked = false, archived = false, folderId = null, tags = emptyList()),
                            ),
                        tags = listOf(tag),
                        nextId = 6,
                    ),
                ),
            )
        val migrated = SavedItems.migrate(legacy, savedData)

        assertEquals(legacy.feeds, migrated.feeds)
        assertEquals(legacy.articles, migrated.articles)
        assertTrue(migrated.article(feedArticle.key)!!.read)
        assertEquals(2, migrated.items.size)

        val story = migrated.item("news.example/story")!!
        assertEquals("Saved title", story.title)
        assertEquals("https://news.example/feed", story.feedUrl)
        assertEquals("7", story.articleId)
        assertTrue(story.toRead)
        assertTrue(story.liked)
        assertEquals(listOf("work"), story.tags)
        assertEquals(100_000L, story.readingList?.addedAt)

        val elsewhere = migrated.item("elsewhere.example/a")!!
        assertEquals("Elsewhere again", elsewhere.title)
        assertTrue(elsewhere.toRead)
        assertEquals(listOf("work"), elsewhere.tags)
        assertEquals(300_000L, elsewhere.readingList?.addedAt)
    }

    @Test
    fun `saving a feed article through Saved makes its item, and lists, likes, archive and tags work on it`() =
        runBlocking {
            val bookmark = saved.save("https://news.example/story", null)!!
            assertEquals("From the feed", bookmark.title)
            val item = library.items.single()
            assertEquals("7", item.articleId)
            assertEquals(SavedItems.idOf(item.key), bookmark.id)

            assertTrue(saved.like(bookmark.id, true))
            saved.move(bookmark.id, "archive")
            assertTrue(library.items.single().archived)
            assertEquals(listOf(bookmark.id), list(InstapaperView.Archive).map { it.id })
            assertEquals(listOf(bookmark.id), list(InstapaperView.Liked).map { it.id })

            val tags = saved.setTags(bookmark.id, listOf("Work", "news"), emptyList())
            assertEquals(listOf("Work", "news"), tags.map { it.name })
            assertEquals(listOf("Work", "news"), library.items.single().tags)
            val renamed = saved.renameTag(tags.first().id, "news")
            assertEquals("news", renamed.name)
            assertEquals(listOf("news"), library.items.single().tags)
            assertEquals(listOf("news"), saved.tags().map { it.name })

            // Saving it again, written another way, brings it back to read at the top: still one item.
            clock += 5_000
            val again = saved.save("http://www.news.example/story/", null)!!
            assertEquals(bookmark.id, again.id)
            assertEquals(1, library.items.size)
            assertEquals(ReadingState.ToRead, library.items.single().readingList?.state)
        }

    @Test
    fun `deleting from Saved takes the item off the reading list, and a bookmark keeps it`() =
        runBlocking {
            val a = saved.save("https://a.example/1", "One")!!
            val b = saved.save("https://a.example/2", "Two")!!
            library = library.keepAsBookmark(library.items.first { it.url == "https://a.example/2" }.key, "Tools", 5)
            saved.delete(a.id)
            saved.delete(b.id)
            assertNull(library.item("a.example/1"))
            val kept = library.item("a.example/2")
            assertNotNull(kept)
            assertFalse(kept!!.onReadingList)
            assertEquals("Tools", kept.bookmark?.collection)
            assertTrue(list(InstapaperView.Home).isEmpty())
        }

    @Test
    fun `ids stay the same between calls`() {
        assertEquals(SavedItems.idOf("a.example/1"), SavedItems.idOf("a.example/1"))
        assertTrue(SavedItems.idOf("a.example/1") != SavedItems.idOf("a.example/2"))
        assertEquals(SavedItems.tagIdOf("Work"), SavedItems.tagIdOf("work"))
        assertTrue(SavedItems.idOf("x") > 0)
    }
}
