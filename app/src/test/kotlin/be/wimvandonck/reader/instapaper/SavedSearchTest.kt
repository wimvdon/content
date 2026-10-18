package be.wimvandonck.reader.instapaper

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedSearchTest {
    private fun bookmark(
        id: Long,
        title: String = "Title $id",
        url: String = "https://www.site.example/$id",
        description: String = "",
        time: Long = id,
        tags: List<InstapaperTag> = emptyList(),
    ) = InstapaperBookmark(id, url, title, description, time, liked = false, archived = false, folderId = null, tags = tags)

    @Test
    fun `reads Unread, each folder and Archive in that order`() {
        val views = SavedSearch.views(listOf(InstapaperFolder(7, "Later", 1), InstapaperFolder(8, "Work", 2)))
        assertEquals(listOf(InstapaperView.Home, InstapaperView.InFolder(7), InstapaperView.InFolder(8), InstapaperView.Archive), views)
    }

    @Test
    fun `reads a list page by page up to the limit, without doubles`() =
        runBlocking {
            val lists =
                mapOf(
                    InstapaperView.Home to (1L..5L).map { bookmark(it) },
                    InstapaperView.Archive to (4L..30L).map { bookmark(it) },
                )
            val calls = mutableListOf<String>()
            val saved =
                SavedSearch.load(listOf(InstapaperView.Home, InstapaperView.Archive), pageSize = 2, perList = 6) { view, offset, limit ->
                    calls += "$view $offset $limit"
                    val all = lists[view].orEmpty()
                    InstapaperPage(all.drop(offset).take(limit), all.size)
                }
            // Home: 5 in three pages; Archive: stops at 6 (4 and 5 were already there).
            assertEquals(listOf(1L, 2, 3, 4, 5, 6, 7, 8, 9), saved.bookmarks.map { it.id })
            assertEquals(6, calls.size)
            assertEquals("Archive 4 2", calls.last())
            assertFalse(saved.incomplete)
        }

    @Test
    fun `keeps what it has when Instapaper stops answering`() =
        runBlocking {
            val saved =
                SavedSearch.load(listOf(InstapaperView.Home, InstapaperView.Archive)) { view, _, _ ->
                    if (view == InstapaperView.Archive) throw InstapaperException(InstapaperProblem.RateLimited)
                    InstapaperPage(listOf(bookmark(1)), 1)
                }
            assertEquals(listOf(1L), saved.bookmarks.map { it.id })
            assertTrue(saved.incomplete)
        }

    @Test(expected = InstapaperException::class)
    fun `a refused token is passed on`() {
        runBlocking {
            SavedSearch.load(listOf(InstapaperView.Home)) { _, _, _ -> throw InstapaperException(InstapaperProblem.SignedOut) }
        }
    }

    @Test
    fun `finds title, website, description and tags, ignoring case and accents, title matches first`() {
        val bookmarks =
            listOf(
                bookmark(1, title = "Café guide", time = 10),
                bookmark(2, title = "Weekend", description = "The best cafe in town", time = 30),
                bookmark(3, title = "Recipes", url = "https://www.cooking.example/a", tags = listOf(InstapaperTag(1, "Baking"))),
                bookmark(4, title = "Phones"),
            )
        fun ids(query: String) = SavedSearch.search(bookmarks, query).map { it.id }
        assertEquals(listOf(1L, 2L), ids("CAFE"))
        assertEquals(listOf(3L), ids("cooking"))
        assertEquals(listOf(3L), ids("baking recipes"))
        assertEquals(emptyList<Long>(), ids("cafe phones"))
        assertEquals(emptyList<Long>(), ids("  "))
    }
}
