package be.wimvandonck.content.data

import be.wimvandonck.content.data.db.LibraryTables
import be.wimvandonck.content.data.db.ReadElsewhereRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryRowsTest {
    private val sample =
        Library(
            folders = listOf(Folder("f1", "News", "newspaper"), Folder("f2", "Tech", "memory")),
            feeds = listOf(Feed("https://b/feed", "B", folderId = "f2"), Feed("https://a/feed", "A", "https://a", "f1", "https://a/icon.png", renamed = true)),
            articles =
                listOf(
                    Article("https://a/feed", "1", "One", "https://a/1", "Ann", "<p>Tab\there</p>", 10, 20, read = true, imageUrl = "https://a/p.jpg"),
                    Article("https://b/feed", "x", "X", null, null, "", null, 30),
                ),
            items =
                listOf(
                    Item(
                        key = "a/1",
                        url = "https://a/1",
                        title = "One",
                        description = "Desc",
                        imageUrl = "https://a/p.jpg",
                        feedUrl = "https://a/feed",
                        articleId = "1",
                        createdAt = 5,
                        readingList = ReadingListEntry(ReadingState.Archived, 6),
                        bookmark = BookmarkEntry(7, "Research"),
                        liked = true,
                        tags = listOf("work", "Ünïcode \"quoted\""),
                        note = "A note\non two lines",
                        highlights = listOf(Highlight("h1", "quote", "before ", " after", 2, "why", 8)),
                        progress = 0.5f,
                    ),
                    Item(key = "c/2", url = "https://c/2", title = "Two", createdAt = 9, readingList = ReadingListEntry(ReadingState.ToRead, 9)),
                ),
            readElsewhere = mapOf("https://a/feed 9" to 99L),
        )

    private fun roundTrip(library: Library): Library =
        LibraryRows.library(
            LibraryTables(
                folders = library.folders.mapIndexed(LibraryRows::folder).reversed(),
                feeds = library.feeds.mapIndexed(LibraryRows::feed).reversed(),
                articles = library.articles.map(LibraryRows::article),
                items = library.items.map(LibraryRows::item),
                readElsewhere = library.readElsewhere.map { (k, v) -> ReadElsewhereRow(k, v) },
            ),
        )

    @Test
    fun `the library comes back from its rows unchanged, in the user's order`() {
        assertEquals(sample, roundTrip(sample))
    }

    @Test
    fun `rows that no longer fit are left out or repaired`() {
        val broken =
            LibraryTables(
                folders = emptyList(),
                feeds = listOf(LibraryRows.feed(0, Feed("https://a/feed", "A", folderId = "gone"))),
                articles = listOf(LibraryRows.article(Article("https://removed/feed", "1", "t", null, null, "", null, 1))),
                items = listOf(LibraryRows.item(sample.items[0]).copy(tags = "not json", highlights = "[{\"id\":\"\"}]", progress = 7f)),
            )
        val library = LibraryRows.library(broken)
        assertNull(library.feeds.single().folderId)
        assertTrue(library.articles.isEmpty())
        val item = library.items.single()
        assertEquals(emptyList<String>(), item.tags)
        assertEquals(emptyList<Highlight>(), item.highlights)
        assertEquals(1f, item.progress)
    }

    @Test
    fun `only what changed is written`() {
        assertTrue(LibraryChanges.between(sample, sample.copy()).isEmpty)

        val read = sample.setRead(Article.keyOf("https://b/feed", "x"), true)
        val changes = LibraryChanges.between(sample, read)
        assertEquals(listOf("https://b/feed x"), changes.articles.map { it.key })
        assertNull(changes.feeds)
        assertNull(changes.folders)
        assertTrue(changes.items.isEmpty())

        val removed = sample.removeFeed("https://a/feed").setReadingState("c/2", null).setLiked("a/1", false)
        val more = LibraryChanges.between(sample, removed)
        assertEquals(listOf("https://a/feed 1"), more.deletedArticles)
        assertEquals(listOf("c/2"), more.deletedItems)
        assertEquals(listOf("a/1"), more.items.map { it.key })
        assertEquals(listOf("https://b/feed"), more.feeds?.map { it.url })
    }

    @Test
    fun `an empty database is filled with everything`() {
        val all = LibraryChanges.all(sample)
        assertEquals(sample.folders, all.folders)
        assertEquals(sample.feeds, all.feeds)
        assertEquals(sample.articles, all.articles)
        assertEquals(sample.items, all.items)
        assertEquals(sample.readElsewhere, all.readElsewhere)
    }

    @Test
    fun `read marks from elsewhere are written when they change`() {
        val changed = sample.copy(readElsewhere = mapOf("x 1" to 5L))
        val changes = LibraryChanges.between(sample, changed)
        assertEquals(mapOf("x 1" to 5L), changes.readElsewhere)
        assertEquals(listOf("https://a/feed 9"), changes.deletedReadElsewhere)
    }
}
