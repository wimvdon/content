package io.github.impca201.rssreader.data

import io.github.impca201.rssreader.data.FeedArrangement.Row
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FeedArrangementTest {
    private val news = Folder("n", "News")
    private val tech = Folder("t", "Tech")
    private val library =
        Library(
            folders = listOf(news, tech),
            feeds =
                listOf(
                    Feed("https://a", "A", folderId = "n"),
                    Feed("https://b", "B", folderId = "t"),
                    Feed("https://c", "C"),
                    Feed("https://d", "D", folderId = "n"),
                ),
        )

    private fun keys(rows: List<Row>) = rows.map { it.key }

    @Test
    fun `rows list each folder with its feeds, then the feeds outside folders`() {
        assertEquals(
            listOf("folder:n", "feed:https://a", "feed:https://d", "folder:t", "feed:https://b", "unfiled", "feed:https://c"),
            keys(FeedArrangement.rows(library)),
        )
        assertEquals(listOf("folder:n", "folder:t"), keys(FeedArrangement.rows(library, foldersOnly = true)))
        // Without folders there are no headers.
        assertEquals(listOf("feed:https://a", "feed:https://b"), keys(FeedArrangement.rows(Library(feeds = library.feeds.take(2)))))
    }

    @Test
    fun `dragging a feed under another folder moves it there, in that place`() {
        val rows = FeedArrangement.rows(library)
        // Drag C (index 6) to just after the Tech header (index 4).
        val moved = FeedArrangement.move(rows, from = 6, to = 4)
        val arranged = FeedArrangement.apply(library, moved)
        assertEquals("t", arranged.feed("https://c")!!.folderId)
        assertEquals(listOf("https://c", "https://b"), arranged.feedsIn("t").map { it.url })
        // Drag A below "No folder": it leaves its folder.
        val out = FeedArrangement.apply(library, FeedArrangement.move(rows, from = 1, to = 6))
        assertEquals(null, out.feed("https://a")!!.folderId)
    }

    @Test
    fun `feeds can't go above the first header, and No folder doesn't move`() {
        val rows = FeedArrangement.rows(library)
        assertSame(rows, FeedArrangement.move(rows, from = 1, to = 0))
        assertSame(rows, FeedArrangement.move(rows, from = 5, to = 2))
        assertSame(rows, FeedArrangement.move(rows, from = 9, to = 2))
        // Folders only move in the folders-only list.
        assertSame(rows, FeedArrangement.move(rows, from = 0, to = 3))
    }

    @Test
    fun `reordering folders keeps their feeds`() {
        val rows = FeedArrangement.rows(library, foldersOnly = true)
        val arranged = FeedArrangement.apply(library, FeedArrangement.move(rows, from = 1, to = 0))
        assertEquals(listOf("t", "n"), arranged.folders.map { it.id })
        assertEquals(library.feeds, arranged.feeds)
    }

    @Test
    fun `without folders, feeds simply reorder`() {
        val plain = Library(feeds = listOf(Feed("https://a", "A"), Feed("https://b", "B")))
        val arranged = FeedArrangement.apply(plain, FeedArrangement.move(FeedArrangement.rows(plain), from = 1, to = 0))
        assertEquals(listOf("https://b", "https://a"), arranged.feeds.map { it.url })
    }
}
