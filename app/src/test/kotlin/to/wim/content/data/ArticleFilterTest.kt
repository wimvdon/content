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
    fun `entries are added as groups, each once, removed one by one and stored one per line`() {
        val one = ArticleFilter.withEntry(emptyList(), "  WIRED   coupon code ")
        assertEquals(listOf("wired coupon code"), one)
        // A second group, a duplicate (case ignored) and nothing typed change only what they should.
        val two = ArticleFilter.withEntry(one, "Crypto")
        assertEquals(listOf("wired coupon code", "crypto"), two)
        assertEquals(two, ArticleFilter.withEntry(two, "CRYPTO"))
        assertEquals(two, ArticleFilter.withEntry(two, "  "))
        // Several typed at once, as before, still become one chip each.
        assertEquals(listOf("wired coupon code", "crypto", "ai", "apple watch"), ArticleFilter.withEntry(two, "AI, apple watch"))
        assertEquals(listOf("crypto"), ArticleFilter.withoutEntry(two, "wired coupon code"))
        // Stored one per line, read back the same, and the filter matches the group as a whole.
        val text = ArticleFilter.text(two)
        assertEquals("wired coupon code\ncrypto", text)
        assertEquals(two, ArticleFilter.words(text))
        assertTrue(ArticleFilter.parse(text).hidesTitle("Best Buy coupon code", "WIRED"))
        assertFalse(ArticleFilter.parse(text).hidesTitle("Best Buy coupon code", "The Verge"))
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
    fun `an entry of several words needs every one, in any order, and accents count as letters`() {
        val filter = ArticleFilter.parse("apple watch, café")
        assertTrue(filter.hidesTitle("The Apple Watch review"))
        assertTrue(filter.hidesTitle("Apple announces a watch"))
        assertFalse(filter.hidesTitle("Apple announces a phone"))
        assertTrue(filter.hidesTitle("Best CAFÉ in town"))
        // Its plural counts, a longer word with accents doesn't.
        assertTrue(filter.hidesTitle("Cafés everywhere"))
        assertFalse(filter.hidesTitle("La cafétéria ferme"))
    }

    @Test
    fun `the feed's name counts too, so one publication's kind of article can go`() {
        val filter = ArticleFilter.parse("WIRED coupon code")
        assertTrue(filter.hidesTitle("Best Buy coupon code: 20% off", source = "WIRED"))
        // WIRED's own titles have the plural: "code" finds "Codes", and possessives count too.
        assertTrue(filter.hidesTitle("Kohl's Coupon Codes: 20% Off | November 2026", source = "WIRED"))
        assertTrue(ArticleFilter.parse("kohl").hidesTitle("Kohl’s deals"))
        assertTrue(ArticleFilter.parse("box").hidesTitle("Lunch boxes compared"))
        // Other words that start with it are still other words.
        assertFalse(ArticleFilter.parse("code").hidesTitle("Coders at work"))
        assertFalse(filter.hidesTitle("Best Buy coupon code: 20% off", source = "The Verge"))
        assertFalse(filter.hidesTitle("The best laptops of the year", source = "WIRED"))
        // One word that is the feed's name hides all of it.
        assertTrue(ArticleFilter.parse("wired").hidesTitle("The best laptops of the year", source = "WIRED"))
        val library =
            Library(
                feeds = listOf(Feed(feed, "WIRED"), Feed("https://b.example.com/feed", "The Verge")),
                articles = listOf(article("1", "Coupon code for shoes"), article("2", "Phones"), article("3", "Coupon code for shoes").copy(feedUrl = "https://b.example.com/feed")),
            )
        assertEquals(listOf("2", "3"), library.withoutHidden(filter).articles.map { it.id })
        assertEquals(1, filter.preview(library.articles) { a -> library.feeds.firstOrNull { it.url == a.feedUrl }?.title }.count)
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

    @Test
    fun `the preview counts what the words hide and lists the newest first`() {
        val articles = (1..8).map { article("$it", if (it % 2 == 0) "AI news $it" else "Gardening $it") } + article("20", "Said nothing")
        val preview = ArticleFilter.parse("ai").preview(articles, limit = 3)
        assertEquals(4, preview.count)
        assertEquals(listOf("8", "6", "4"), preview.newest.map { it.id })
    }

    @Test
    fun `no words, no preview`() {
        val preview = ArticleFilter.parse(" , ").preview(listOf(article("1", "AI news")))
        assertEquals(0, preview.count)
        assertTrue(preview.newest.isEmpty())
    }
}
