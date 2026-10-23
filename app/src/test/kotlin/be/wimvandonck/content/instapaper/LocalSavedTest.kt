package be.wimvandonck.content.instapaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The file Saved without an account used before #106, read once to move it into the reading list. */
class LocalSavedTest {
    private val tag = InstapaperTag(3, "Travel")
    private val data =
        LocalSavedData(
            bookmarks =
                listOf(
                    InstapaperBookmark(1, "https://a.example/1", "One", "", 100, liked = false, archived = false, folderId = null, tags = emptyList()),
                    InstapaperBookmark(2, "https://a.example/2", "Two \"quoted\"", "", 200, liked = true, archived = true, folderId = null, tags = listOf(tag)),
                ),
            tags = listOf(tag),
            nextId = 4,
        )

    @Test
    fun `everything survives storage, and unreadable text starts empty`() {
        val text = LocalSavedCodec.encode(data)
        assertEquals(data, LocalSavedCodec.decode(text))
        assertEquals(LocalSavedData(), LocalSavedCodec.decode(null))
        assertEquals(LocalSavedData(), LocalSavedCodec.decode("not json"))
        // Ids never repeat, even when the counter was lost.
        val withoutCounter = LocalSavedCodec.decode(text.replace("\"nextId\":", "\"lost\":"))
        assertTrue(withoutCounter.nextId > withoutCounter.bookmarks.maxOf { it.id })
        assertTrue(withoutCounter.nextId > withoutCounter.tags.maxOf { it.id })
    }
}
