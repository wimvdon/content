package to.wim.content.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import to.wim.content.data.NewsletterUnsubscribe.Answer

class NewsletterUnsubscribeTest {
    private val feedUrl = "https://w.example/newsletters/k7q2m9abcdef/0123456789abcdef0123456789abcdef"

    private fun issue(
        id: String,
        author: String,
        site: String?,
        time: Long,
        feedUrl: String = this.feedUrl,
    ) = Article(feedUrl, id, "Issue", null, author, "", time, time, authorUrl = site)

    private fun entry(id: String) = "urn:content:newsletter:k7q2m9abcdef:$id"

    @Test
    fun `the email id comes from a newsletter article's id`() {
        assertEquals("0123456789abcdef", NewsletterUnsubscribe.emailIdOf(entry("0123456789abcdef")))
        assertNull(NewsletterUnsubscribe.emailIdOf("urn:content:newsletter:k7q2m9abcdef"))
        assertNull(NewsletterUnsubscribe.emailIdOf("urn:content:newsletter:k7q2m9abcdef:XYZ"))
        assertNull(NewsletterUnsubscribe.emailIdOf("https://example.com/post"))
        assertNull(NewsletterUnsubscribe.emailIdOf(null))
    }

    @Test
    fun `the route is the newsletter feed's address with unsubscribe`() {
        assertEquals("$feedUrl/unsubscribe", NewsletterUnsubscribe.routeOf(feedUrl))
        assertEquals("$feedUrl/unsubscribe", NewsletterUnsubscribe.routeOf("$feedUrl/"))
        assertNull(NewsletterUnsubscribe.routeOf("https://example.com/feed.xml"))
        assertNull(NewsletterUnsubscribe.routeOf(null))
    }

    @Test
    fun `only a newsletter feed's email can be unsubscribed from`() {
        assertTrue(NewsletterUnsubscribe.canUnsubscribe(issue(entry("0123456789abcdef"), "Bozar", "https://bozar.be", 1)))
        assertFalse(NewsletterUnsubscribe.canUnsubscribe(issue("post-1", "Bozar", "https://bozar.be", 1)))
        assertFalse(NewsletterUnsubscribe.canUnsubscribe(issue(entry("0123456789abcdef"), "Bozar", null, 1, feedUrl = "https://example.com/feed.xml")))
        assertFalse(NewsletterUnsubscribe.canUnsubscribe(null))
    }

    @Test
    fun `the newest email of the sender is the one used`() {
        val older = issue(entry("000000000000000a"), "Bozar", "https://bozar.be", 10)
        val newer = issue(entry("000000000000000b"), "Bozar Events", "https://bozar.be", 30)
        val other = issue(entry("000000000000000d"), "Platformer", "https://platformer.news", 40)
        val articles = listOf(older, newer, other)
        val bozar = NewsletterSenders().list(articles, feedUrl).first { it.key == "bozar.be" }
        assertEquals(newer, NewsletterUnsubscribe.newestOf(articles, bozar))
    }

    @Test
    fun `the feed loader's answer says what to do`() {
        assertEquals(Answer.Done, NewsletterUnsubscribe.answerOf(mapOf("done" to true)))
        assertEquals(Answer.Open("https://example.com/unsub?u=1"), NewsletterUnsubscribe.answerOf(mapOf("open" to "https://example.com/unsub?u=1")))
        assertEquals(Answer.Mailto("mailto:leave@example.com?subject=unsubscribe"), NewsletterUnsubscribe.answerOf(mapOf("mailto" to "mailto:leave@example.com?subject=unsubscribe")))
        assertEquals(Answer.None, NewsletterUnsubscribe.answerOf(mapOf("none" to true)))
    }

    @Test
    fun `an answer that isn't one, or would open something else, is refused`() {
        assertNull(NewsletterUnsubscribe.answerOf(null))
        assertNull(NewsletterUnsubscribe.answerOf("done"))
        assertNull(NewsletterUnsubscribe.answerOf(emptyMap<String, Any>()))
        assertNull(NewsletterUnsubscribe.answerOf(mapOf("open" to "http://example.com/unsub")))
        assertNull(NewsletterUnsubscribe.answerOf(mapOf("open" to "javascript:alert(1)")))
        assertNull(NewsletterUnsubscribe.answerOf(mapOf("mailto" to "https://example.com")))
        assertNull(NewsletterUnsubscribe.answerOf(mapOf("done" to false)))
    }
}
