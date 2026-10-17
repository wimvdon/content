package be.wimvandonck.reader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollReadTest {
    private fun article(
        id: String,
        read: Boolean = false,
    ) = Article("https://a.example.com/feed", id, "Title", null, null, "", null, 0, read)

    private val articles = listOf(article("1"), article("2", read = true), article("3"), article("4"))

    @Test
    fun `articles above the first visible one count as read, unread ones only`() {
        val keys = ScrollRead.scrolledPast(articles, firstVisibleArticle = 3)
        assertEquals(listOf(Article.keyOf("https://a.example.com/feed", "1"), Article.keyOf("https://a.example.com/feed", "3")), keys.toList())
    }

    @Test
    fun `nothing at the top of the list or above it`() {
        assertEquals(emptySet<String>(), ScrollRead.scrolledPast(articles, firstVisibleArticle = 0))
        // The Unread/All filter row is still visible.
        assertEquals(emptySet<String>(), ScrollRead.scrolledPast(articles, firstVisibleArticle = -1))
    }

    @Test
    fun `past the end means every unread article`() {
        assertEquals(3, ScrollRead.scrolledPast(articles, firstVisibleArticle = 99).size)
    }
}
