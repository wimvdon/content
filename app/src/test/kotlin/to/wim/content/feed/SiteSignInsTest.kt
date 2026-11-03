package to.wim.content.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiteSignInsTest {
    private val standaard = SiteSignIn("standaard.be", "sid=1; plan=pro", 5)
    private val other = SiteSignIn("news.example.com", "x=2", 6)

    @Test
    fun `names the site of an address`() {
        assertEquals("standaard.be", SiteSignIns.siteOf("https://www.standaard.be/cnt/dmf2026"))
        assertEquals("m.standaard.be", SiteSignIns.siteOf("https://M.Standaard.be/a"))
        assertNull(SiteSignIns.siteOf("http://www.standaard.be/a"))
        assertNull(SiteSignIns.siteOf("not an address"))
        assertNull(SiteSignIns.siteOf("https://localhost/a"))
    }

    @Test
    fun `sends a site's cookies only to that site and its subdomains`() {
        val all = listOf(standaard, other)
        assertEquals("sid=1; plan=pro", SiteSignIns.cookieFor("https://www.standaard.be/cnt/1", all))
        assertEquals("sid=1; plan=pro", SiteSignIns.cookieFor("https://m.standaard.be/cnt/1", all))
        assertNull(SiteSignIns.cookieFor("https://notstandaard.be/1", all))
        assertNull(SiteSignIns.cookieFor("https://standaard.be.evil.com/1", all))
        assertNull(SiteSignIns.cookieFor("http://www.standaard.be/1", all))
        assertNull(SiteSignIns.cookieFor("https://example.com/1", all))
        assertEquals("x=2", SiteSignIns.cookieFor("https://news.example.com/1", all))
    }

    @Test
    fun `the closest site wins`() {
        val parent = SiteSignIn("example.com", "p=1", 1)
        assertEquals("x=2", SiteSignIns.cookieFor("https://news.example.com/1", listOf(parent, other)))
        assertEquals("p=1", SiteSignIns.cookieFor("https://www.example.com/1", listOf(parent, other)))
    }

    @Test
    fun `reads cookie names and keeps sign-ins`() {
        assertEquals(listOf("sid", "plan"), SiteSignIns.cookieNames("sid=1; plan=pro"))
        assertEquals(listOf(standaard, other), SiteSignIns.decode(SiteSignIns.encode(listOf(standaard, other))))
        assertEquals(emptyList<SiteSignIn>(), SiteSignIns.decode("nonsense"))
    }

    @Test
    fun `keeps what the site renews, adds and clears, as a browser does`() {
        val now = 1_800_000_000_000L
        assertEquals(
            "sid=2; plan=pro; token=t",
            SiteSignIns.updated("standaard.be", "sid=1; plan=pro", listOf("sid=2; Path=/; Secure; HttpOnly", "token=t; Domain=.standaard.be; Max-Age=3600"), now),
        )
        // Cleared: Max-Age 0, an empty value, or an Expires in the past.
        assertEquals("plan=pro", SiteSignIns.updated("standaard.be", "sid=1; plan=pro", listOf("sid=; Max-Age=0"), now))
        assertEquals("plan=pro", SiteSignIns.updated("standaard.be", "sid=1; plan=pro", listOf("sid=x; Expires=Thu, 01 Jan 1970 00:00:00 GMT"), now))
        // An Expires to come keeps it.
        assertEquals("sid=3; plan=pro", SiteSignIns.updated("standaard.be", "sid=1; plan=pro", listOf("sid=3; Expires=Wed, 01 Jan 2031 00:00:00 GMT"), now))
    }

    @Test
    fun `ignores another domain's cookies and says when nothing changes`() {
        assertNull(SiteSignIns.updated("standaard.be", "sid=1", listOf("ad=1; Domain=tracker.example"), 0))
        assertNull(SiteSignIns.updated("standaard.be", "sid=1", listOf("sid=1; Path=/"), 0))
        assertNull(SiteSignIns.updated("standaard.be", "sid=1", emptyList(), 0))
        assertEquals("sid=1; m=1", SiteSignIns.updated("standaard.be", "sid=1", listOf("m=1; Domain=m.standaard.be"), 0))
    }
}
