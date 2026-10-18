package be.wimvandonck.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleSearchTest {
    private val a = "https://a.example.com/feed"
    private val b = "https://b.example.com/feed"

    private fun article(
        feed: String,
        id: String,
        title: String,
        html: String = "",
        time: Long = 0,
        author: String? = null,
    ) = Article(feed, id, title, link = null, author = author, html = html, publishedAt = time, fetchedAt = 0)

    private val library =
        Library(
            feeds = listOf(Feed(a, "Kitchen notes"), Feed(b, "Tech daily")),
            articles =
                listOf(
                    article(a, "1", "Café culture in Brussels", "<p>Coffee and <b>waffles</b></p>", time = 100),
                    article(a, "2", "Bread at home", "<p>A bakery story about the café next door</p>", time = 300),
                    article(b, "3", "New phones", "<p>Batteries &amp; screens</p>", time = 200, author = "Ann Smith"),
                    article("https://gone.example/feed", "4", "Café of a removed feed", time = 400),
                ),
        )

    private fun ids(query: String) = ArticleSearch(library).search(query).map { it.id }

    @Test
    fun `finds words in the title and the text, ignoring case and accents`() {
        assertEquals(listOf("1", "2"), ids("CAFE"))
        assertEquals(listOf("1"), ids("waffles"))
        assertEquals(listOf("3"), ids("batteries & screens"))
    }

    @Test
    fun `title matches come first, then newest first`() {
        // Article 2 is newer but only mentions the café in its text.
        assertEquals(listOf("1", "2"), ids("café"))
    }

    @Test
    fun `every word must appear, anywhere`() {
        assertEquals(listOf("1"), ids("brussels coffee"))
        assertTrue(ids("brussels phones").isEmpty())
    }

    @Test
    fun `also matches the feed's name and the author`() {
        assertEquals(listOf("2", "1"), ids("kitchen"))
        assertEquals(listOf("3"), ids("smith"))
    }

    @Test
    fun `an empty query finds nothing, and removed feeds are left out`() {
        assertTrue(ids("  ").isEmpty())
        assertEquals(listOf("1", "2"), ids("cafe"))
    }

    @Test
    fun `results are capped`() {
        assertEquals(1, ArticleSearch(library).search("cafe", limit = 1).size)
    }
}
