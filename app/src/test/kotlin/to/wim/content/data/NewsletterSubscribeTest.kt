package to.wim.content.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsletterSubscribeTest {
    @Test
    fun `each way opens its own site, over https`() {
        assertEquals("https://kill-the-newsletter.com/", NewsletterSubscribe.site(NewsletterSubscribe.Way.Feed))
        assertEquals("https://www.instapaper.com/docs/save/email", NewsletterSubscribe.site(NewsletterSubscribe.Way.Instapaper))
        NewsletterSubscribe.Way.entries.forEach { assertTrue(NewsletterSubscribe.site(it).startsWith("https://")) }
    }

    @Test
    fun `Instapaper asks to connect first, then shows where the address is`() {
        assertEquals(NewsletterSubscribe.InstapaperStep.Connect, NewsletterSubscribe.instapaperStep(connected = false))
        assertEquals(NewsletterSubscribe.InstapaperStep.FindAddress, NewsletterSubscribe.instapaperStep(connected = true))
    }
}
