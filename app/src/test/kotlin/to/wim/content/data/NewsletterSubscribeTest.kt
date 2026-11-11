package to.wim.content.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsletterSubscribeTest {
    @Test
    fun `Instapaper asks to connect first, then shows where the address is`() {
        assertEquals(NewsletterSubscribe.InstapaperStep.Connect, NewsletterSubscribe.instapaperStep(connected = false))
        assertEquals(NewsletterSubscribe.InstapaperStep.FindAddress, NewsletterSubscribe.instapaperStep(connected = true))
        assertTrue(NewsletterSubscribe.INSTAPAPER_EMAIL.startsWith("https://"))
    }

    @Test
    fun `an own address needs a Content account, and a tag`() {
        assertEquals(NewsletterSubscribe.OwnStep.SignIn, NewsletterSubscribe.ownStep(signedIn = false))
        assertEquals(NewsletterSubscribe.OwnStep.Addresses, NewsletterSubscribe.ownStep(signedIn = true))
        assertTrue(NewsletterSubscribe.canCreate(" Tech ", busy = false))
        assertFalse(NewsletterSubscribe.canCreate("  ", busy = false))
        assertFalse(NewsletterSubscribe.canCreate("Tech", busy = true))
        assertFalse(NewsletterSubscribe.canCreate("x".repeat(101), busy = false))
    }

    @Test
    fun `a newsletter address's feed is recognised, and read from the feed loader's answer`() {
        val feed = "https://content-worker.wim.to/newsletters/k7q2m9abcdef/0123456789abcdef0123456789abcdef"
        assertEquals("k7q2m9abcdef", NewsletterAddresses.codeOfFeed(feed))
        assertNull(NewsletterAddresses.codeOfFeed("https://example.com/feed.xml"))
        assertNull(NewsletterAddresses.codeOfFeed("https://content-worker.wim.to/newsletters/k7q2m9abcdef/x"))
        val answer = mapOf("addresses" to listOf(mapOf("code" to "k7q2m9abcdef", "tag" to "Tech", "address" to "news+k7q2m9abcdef@in.wim.to", "feedUrl" to feed), mapOf("code" to "x")))
        assertEquals(listOf(NewsletterAddress("k7q2m9abcdef", "Tech", "news+k7q2m9abcdef@in.wim.to", feed)), NewsletterAddresses.listOf(answer))
        // A feed address that doesn't belong to the code is refused.
        assertNull(NewsletterAddresses.of(mapOf("code" to "aaaaaaaaaaaa", "address" to "a", "feedUrl" to feed)))
    }

    @Test
    fun `a new address's feed goes under the topic with its name`() {
        val topics = listOf(Folder("1", "Tech"), Folder("2", "News"))
        assertEquals("1", NewsletterAddresses.topicFor(topics, " tech ")?.id)
        assertNull(NewsletterAddresses.topicFor(topics, "Food"))
    }

    @Test
    fun `the addresses are grouped by their feed's topic, in the topics' order, No topic last`() {
        fun address(code: String) = NewsletterAddress(code, "Made as $code", "content+$code@in.wim.to", "https://w.example/newsletters/$code/0123456789abcdef0123456789abcdef")
        val topics = listOf(Folder("1", "Tech"), Folder("2", "News"))
        val a = address("aaaaaaaaaaaa")
        val b = address("bbbbbbbbbbbb")
        val c = address("cccccccccccc")
        val d = address("dddddddddddd")
        val feeds = listOf(Feed(a.feedUrl, "Weekly", folderId = "2"), Feed(b.feedUrl, "Daily", folderId = "1"), Feed(c.feedUrl, "Loose"))
        val groups = NewsletterAddresses.grouped(listOf(a, b, c, d), feeds, topics)
        assertEquals(listOf("1", "2", null), groups.map { it.topic?.id })
        assertEquals(listOf("Daily"), groups[0].addresses.map { it.name })
        // No topic: one without a topic, and one whose feed isn't here (its name as made).
        assertEquals(listOf("Loose", "Made as dddddddddddd"), groups[2].addresses.map { it.name })
        assertTrue(NewsletterAddresses.grouped(emptyList(), feeds, topics).isEmpty())
    }
}
