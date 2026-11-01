package to.wim.content.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleFilterTest {
    private val feed = "https://a.example.com/feed"

    private fun article(
        id: String,
        title: String,
        read: Boolean = false,
    ) = Article(feedUrl = feed, id = id, title = title, link = null, author = null, html = "", publishedAt = id.toLong(), fetchedAt = 0L, read = read)

    @Test
    fun `words are separated by commas, semicolons or new lines, trimmed, without empty ones or duplicates`() {
        assertEquals(
            listOf("crypto", "apple watch", "ai"),
            ArticleFilter.words(" Crypto, apple   watch\n\n;AI ,crypto,\r\n "),
        )
        assertTrue(ArticleFilter.parse("").isEmpty)
        assertTrue(ArticleFilter.parse(" , \n ").isEmpty)
    }

    @Test
    fun `a word matches as a whole word, ignoring case`() {
        val filter = ArticleFilter.parse("AI")
        assertTrue(filter.hidesTitle("New AI rules"))
        assertTrue(filter.hidesTitle("ai: a recap"))
        assertTrue(filter.hidesTitle("What the (AI) said"))
        assertTrue(filter.hidesTitle("AI"))
        assertFalse(filter.hidesTitle("He said so"))
        assertFalse(filter.hidesTitle("Thailand"))
        assertFalse(filter.hidesTitle(""))
    }

    @Test
    fun `a later occurrence counts when an earlier one is inside a word`() {
        assertTrue(ArticleFilter.parse("ai").hidesTitle("Thailand bets on AI"))
    }

    @Test
    fun `a phrase matches as a whole, and letters with accents count as letters`() {
        val filter = ArticleFilter.parse("apple watch, café")
        assertTrue(filter.hidesTitle("The Apple Watch review"))
        assertFalse(filter.hidesTitle("Apple announces a watch"))
        assertTrue(filter.hidesTitle("Best CAFÉ in town"))
        assertFalse(filter.hidesTitle("Cafés everywhere"))
    }

    @Test
    fun `no words hide nothing`() {
        val library = Library(feeds = listOf(Feed(feed, "A")), articles = listOf(article("1", "Crypto news")))
        assertFalse(ArticleFilter.parse("").hidesTitle("Crypto news"))
        assertSame(library, library.withoutHidden(ArticleFilter.parse(" ")))
    }

    @Test
    fun `hidden articles leave the list and the unread counts`() {
        val library =
            Library(
                feeds = listOf(Feed(feed, "A")),
                articles = listOf(article("1", "Crypto crash"), article("2", "Gardening tips"), article("3", "More CRYPTO", read = true)),
            )
        val shown = library.withoutHidden(ArticleFilter.parse("crypto"))
        assertEquals(listOf("2"), shown.articlesFor(FeedSelection.All, unreadOnly = false).map { it.id })
        assertEquals(1, shown.unreadCount(FeedSelection.All))
        assertEquals(2, library.unreadCount(FeedSelection.All))
    }
}
