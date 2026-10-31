package to.wim.content.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmailLinksTest {
    @Test
    fun `reads the token of an emailed link`() {
        assertEquals("abc.def", EmailLinks.tokenOf("https://content.wim.to/signin#token=abc.def"))
        assertEquals("abc", EmailLinks.tokenOf("https://content.wim.to/signin#x=1&token=abc"))
        assertNull(EmailLinks.tokenOf("https://content.wim.to/signin"))
        assertNull(EmailLinks.tokenOf("https://evil.example/signin#token=abc"))
        assertNull(EmailLinks.tokenOf("https://content.wim.to/instapaper/callback?code=1"))
    }

    @Test
    fun `reads what a used link gave`() {
        assertEquals(EmailLinkResult("k.s", "me@example.com"), EmailLinks.resultFrom("""{"key":"k.s","email":"me@example.com"}"""))
        assertEquals(EmailLinkResult(null, "me@example.com"), EmailLinks.resultFrom("""{"email":"me@example.com"}"""))
        assertNull(EmailLinks.resultFrom("""{"key":"k.s"}"""))
        assertNull(EmailLinks.resultFrom("not json"))
    }

    @Test
    fun `asks Gravatar for the address in one form`() {
        // SHA-256 of "me@example.com".
        val url = Gravatar.url("  Me@Example.com ")
        assertEquals(Gravatar.url("me@example.com"), url)
        assertEquals(true, url.startsWith("https://gravatar.com/avatar/"))
        assertEquals(true, url.endsWith("?s=96&d=404"))
        assertEquals(64, url.removePrefix("https://gravatar.com/avatar/").substringBefore('?').length)
    }

    @Test
    fun `reads the Gravatar name the feed loader found (#234)`() {
        assertEquals("Ann Example", Gravatar.nameFrom("""{"name":" Ann Example "}"""))
        assertEquals("", Gravatar.nameFrom("""{"name":""}"""))
        assertEquals("", Gravatar.nameFrom("""{}"""))
        assertEquals("", Gravatar.nameFrom("not json"))
    }
}
