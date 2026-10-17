package be.wimvandonck.reader.instapaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class InstapaperClientTest {
    @Test
    fun `asks for each kind of list`() {
        assertEquals("/bookmarks?limit=25&offset=0&section=home", InstapaperClient.listPath(InstapaperView.Home, 0, 25))
        assertEquals("/bookmarks?limit=10&offset=25&section=archive", InstapaperClient.listPath(InstapaperView.Archive, 25, 10))
        assertEquals("/bookmarks?limit=25&offset=0&section=liked", InstapaperClient.listPath(InstapaperView.Liked, 0, 25))
        assertEquals("/bookmarks?limit=25&offset=0&section=folder&folder_id=9", InstapaperClient.listPath(InstapaperView.InFolder(9), 0, 25))
        assertEquals("/bookmarks?limit=25&offset=0&section=tag&tag=Cooking+%26+baking", InstapaperClient.listPath(InstapaperView.WithTag("Cooking & baking"), 0, 25))
    }

    @Test
    fun `names the problem from the status`() {
        assertEquals(InstapaperProblem.SignedOut, InstapaperClient.problemForStatus(401))
        assertEquals(InstapaperProblem.NotApproved, InstapaperClient.problemForStatus(403))
        assertEquals(InstapaperProblem.RateLimited, InstapaperClient.problemForStatus(429))
        assertEquals(InstapaperProblem.Invalid, InstapaperClient.problemForStatus(400))
        assertEquals(InstapaperProblem.Invalid, InstapaperClient.problemForStatus(404))
        assertEquals(InstapaperProblem.ServerError, InstapaperClient.problemForStatus(500))
        assertEquals(InstapaperProblem.ServerError, InstapaperClient.problemForStatus(502))
    }

    @Test
    fun `reads a page of bookmarks`() {
        val page =
            InstapaperClient.parsePage(
                """
                {"bookmarks": [
                  {"id": 7, "url": "https://www.example.com/a", "title": "A", "description": "About A", "time": 1700000000,
                   "liked": true, "archived": false, "folder_id": 3, "progress": {"percentage": 0.5, "timestamp": 1},
                   "tags": [{"id": 1, "name": "Cooking", "slug": "cooking", "count": 2}]},
                  {"id": 8, "url": null, "title": null, "time": 5},
                  "not a bookmark",
                  {"title": "no id"}
                ], "total": 40}
                """.trimIndent(),
            )
        assertEquals(40, page.total)
        assertEquals(listOf(7L, 8L), page.bookmarks.map { it.id })
        val first = page.bookmarks[0]
        assertEquals("A", first.title)
        assertEquals("About A", first.description)
        assertEquals(1700000000L, first.time)
        assertTrue(first.liked)
        assertFalse(first.archived)
        assertEquals(3L, first.folderId)
        assertEquals(listOf(InstapaperTag(1, "Cooking")), first.tags)
        assertEquals("example.com", first.host)
        val second = page.bookmarks[1]
        assertEquals("", second.title)
        assertNull(second.url)
        assertNull(second.folderId)
        assertEquals("", second.host)
    }

    @Test
    fun `counts the bookmarks when the answer has no total`() {
        assertEquals(1, InstapaperClient.parsePage("""{"bookmarks": [{"id": 1}]}""").total)
    }

    @Test
    fun `only links to web addresses`() {
        fun link(url: String?) = InstapaperClient.parseBookmark(mapOf("id" to 1L, "url" to url))?.link
        assertEquals("https://a.example/x", link("https://a.example/x"))
        assertEquals("http://a.example/x", link("http://a.example/x"))
        assertNull(link("javascript:alert(1)"))
        assertNull(link("intent://evil#Intent;end"))
        assertNull(link("instapaper://private-content/1"))
        assertNull(link(null))
    }

    @Test
    fun `reads folders in the user's order, and tags`() {
        val folders = InstapaperClient.parseFolderList("""{"folders": [{"id": 2, "title": "B", "position": 2}, {"id": 1, "title": "A", "position": 1}]}""")
        assertEquals(listOf(InstapaperFolder(1, "A", 1), InstapaperFolder(2, "B", 2)), folders)
        assertEquals(listOf(InstapaperTag(4, "Baking")), InstapaperClient.parseTagList("""{"tags": [{"id": 4, "name": "Baking"}, {"name": "no id"}]}""", "tags"))
        assertEquals(emptyList<InstapaperFolder>(), InstapaperClient.parseFolderList("{}"))
    }

    @Test
    fun `an answer that isn't what was expected is a server problem`() {
        for (bad in listOf("<html>", "[]", "")) {
            val error = assertThrows(InstapaperException::class.java) { InstapaperClient.parsePage(bad) }
            assertEquals(InstapaperProblem.ServerError, error.problem)
        }
    }
}
