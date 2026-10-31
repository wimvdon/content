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
}
