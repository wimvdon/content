package be.wimvandonck.reader.instapaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstapaperListsTest {
    private fun bookmark(
        id: Long,
        archived: Boolean = false,
        folderId: Long? = null,
        liked: Boolean = false,
        tags: List<InstapaperTag> = emptyList(),
    ) = InstapaperBookmark(id, "https://www.site.example/$id", "Saved $id", "A <short> summary", 1_700_000_000, liked, archived, folderId, tags)

    @Test
    fun `a saved article shows like a feed article, without read state`() {
        val a = InstapaperLists.article(bookmark(7))
        assertEquals(InstapaperLists.FEED, a.feedUrl)
        assertEquals("instapaper 7", a.key)
        assertEquals("<p>A &lt;short&gt; summary</p>", a.html)
        assertEquals(1_700_000_000_000, a.publishedAt)
        assertFalse(a.read)
        assertTrue(InstapaperLists.isSaved(a))
        assertNull(InstapaperLists.article(bookmark(8).copy(url = "javascript:alert(1)")).link)
    }

    @Test
    fun `knows which list a saved article belongs in`() {
        assertTrue(InstapaperLists.inView(InstapaperView.Home, bookmark(1)))
        assertFalse(InstapaperLists.inView(InstapaperView.Home, bookmark(1, folderId = 5)))
        assertTrue(InstapaperLists.inView(InstapaperView.InFolder(5), bookmark(1, folderId = 5)))
        assertFalse(InstapaperLists.inView(InstapaperView.InFolder(5), bookmark(1, folderId = 5, archived = true)))
        assertTrue(InstapaperLists.inView(InstapaperView.Archive, bookmark(1, archived = true)))
        assertTrue(InstapaperLists.inView(InstapaperView.Liked, bookmark(1, liked = true, archived = true)))
        assertTrue(InstapaperLists.inView(InstapaperView.WithTag("ai"), bookmark(1, tags = listOf(InstapaperTag(1, "ai")))))
    }

    @Test
    fun `a change updates the list and drops what no longer belongs`() {
        val list = listOf(bookmark(1), bookmark(2))
        assertEquals(listOf(2L), InstapaperLists.apply(InstapaperView.Home, list, 1, BookmarkChange.Archived(true)).map { it.id })
        assertTrue(InstapaperLists.apply(InstapaperView.Home, list, 1, BookmarkChange.Liked(true))[0].liked)
        assertEquals(listOf(1L), InstapaperLists.apply(InstapaperView.Home, list, 2, BookmarkChange.Moved(9)).map { it.id })
        assertEquals(listOf(1L), InstapaperLists.apply(InstapaperView.Home, list, 2, BookmarkChange.Deleted).map { it.id })
        val back = InstapaperLists.patch(bookmark(3, archived = true, folderId = 9), BookmarkChange.Archived(false))!!
        assertFalse(back.archived)
        assertNull(back.folderId)
    }
}
