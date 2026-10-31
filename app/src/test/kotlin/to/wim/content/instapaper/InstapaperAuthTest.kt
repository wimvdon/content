package to.wim.content.instapaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstapaperAuthTest {
    private val back = InstapaperAuth.REDIRECT_URI

    @Test
    fun `builds Instapaper's sign-in address`() {
        val url = InstapaperAuth.authorizeUrl("abc123")
        assertTrue(url.startsWith("https://www.instapaper.com/oauth2/authorize?"))
        val query = url.substringAfter('?').split('&').associate { it.substringBefore('=') to it.substringAfter('=') }
        assertEquals(InstapaperAuth.CLIENT_ID, query["client_id"])
        assertEquals("code", query["response_type"])
        assertEquals("abc123", query["state"])
        assertEquals("https%3A%2F%2Frss-reader-web.wim-d57.workers.dev%2Finstapaper%2Fcallback", query["redirect_uri"])
    }

    @Test
    fun `makes a long random state each time`() {
        val first = InstapaperAuth.newState()
        assertTrue(Regex("[0-9a-f]{32}").matches(first))
        assertNotEquals(first, InstapaperAuth.newState())
    }

    @Test
    fun `recognises the address Instapaper sends the user back to`() {
        assertTrue(InstapaperAuth.isCallback("$back?code=1&state=2"))
        assertTrue(InstapaperAuth.isCallback(back))
        assertTrue(InstapaperAuth.isCallback("$back#top"))
        assertFalse(InstapaperAuth.isCallback("https://evil.example/instapaper/callback?code=1&state=2"))
        assertFalse(InstapaperAuth.isCallback("$back/extra?code=1"))
        assertFalse(InstapaperAuth.isCallback("https://rss-reader-web.wim-d57.workers.dev/"))
    }

    @Test
    fun `reads the code and state`() {
        assertEquals(InstapaperAuth.Callback.Code("1a2b", "xyz"), InstapaperAuth.parseCallback("$back?code=1a2b&state=xyz"))
        assertEquals(InstapaperAuth.Callback.Code("a b", "x&y"), InstapaperAuth.parseCallback("$back?code=a%20b&state=x%26y#frag"))
    }

    @Test
    fun `reads a refusal, and anything incomplete or foreign`() {
        assertEquals(InstapaperAuth.Callback.Denied, InstapaperAuth.parseCallback("$back?error=access_denied&state=xyz"))
        assertEquals(InstapaperAuth.Callback.Invalid, InstapaperAuth.parseCallback("$back?code=1a2b"))
        assertEquals(InstapaperAuth.Callback.Invalid, InstapaperAuth.parseCallback("$back?state=xyz"))
        assertEquals(InstapaperAuth.Callback.Invalid, InstapaperAuth.parseCallback(back))
        assertEquals(InstapaperAuth.Callback.Invalid, InstapaperAuth.parseCallback("https://evil.example/?code=1&state=2"))
    }

    @Test
    fun `reads the session from the Worker's answer`() {
        assertEquals(
            InstapaperSession("tok", 42L, "me@example.com"),
            InstapaperAuth.sessionFrom("""{"access_token": "tok", "user": {"id": 42, "username": "me@example.com"}}"""),
        )
        assertEquals(InstapaperSession("tok", null, ""), InstapaperAuth.sessionFrom("""{"access_token": "tok"}"""))
        assertNull(InstapaperAuth.sessionFrom("""{"access_token": ""}"""))
        assertNull(InstapaperAuth.sessionFrom("""{"user": {}}"""))
        assertNull(InstapaperAuth.sessionFrom("<html>"))
    }
}
