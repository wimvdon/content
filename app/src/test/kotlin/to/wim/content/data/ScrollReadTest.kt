package to.wim.content.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollReadTest {
    private fun article(
        id: String,
        read: Boolean = false,
    ) = Article("https://a.example.com/feed", id, "Title", null, null, "", null, 0, read)

    private val articles = listOf(article("1"), article("2", read = true), article("3"), article("4"))
    private val all = articles.map { it.key }.toSet()

    @Test
    fun `articles above the first visible one count as read, unread ones only`() {
        val keys = ScrollRead.scrolledPast(articles, firstVisibleArticle = 3, seen = all)
        assertEquals(listOf(Article.keyOf("https://a.example.com/feed", "1"), Article.keyOf("https://a.example.com/feed", "3")), keys.toList())
    }

    @Test
    fun `nothing at the top of the list or above it`() {
        assertEquals(emptySet<String>(), ScrollRead.scrolledPast(articles, firstVisibleArticle = 0, seen = all))
        // The Unread/All filter row is still visible.
        assertEquals(emptySet<String>(), ScrollRead.scrolledPast(articles, firstVisibleArticle = -1, seen = all))
    }

    @Test
    fun `past the end means every unread article`() {
        assertEquals(3, ScrollRead.scrolledPast(articles, firstVisibleArticle = 99, seen = all).size)
    }

    @Test
    fun `articles that were never on screen are not marked`() {
        // New articles arrived above the list (a refresh): the first visible one moved down, but 1 was never seen.
        val seen = setOf(articles[2].key, articles[3].key)
        assertEquals(setOf(articles[2].key), ScrollRead.scrolledPast(articles, firstVisibleArticle = 3, seen = seen))
    }
}
