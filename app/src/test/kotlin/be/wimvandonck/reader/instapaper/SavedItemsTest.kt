package be.wimvandonck.reader.instapaper

import be.wimvandonck.reader.data.Article
import be.wimvandonck.reader.data.Feed
import be.wimvandonck.reader.data.Library
import be.wimvandonck.reader.data.LibraryCodec
import be.wimvandonck.reader.data.item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedItemsTest {
    private val feedArticle = Article("https://news.example/feed", "7", "From the feed", "https://news.example/story", null, "<p>Feed text</p>", 1, 1)


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
}
