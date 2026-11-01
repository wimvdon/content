package to.wim.content.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstapaperLinksTest {
    @Test
    fun `an article without an address gets Instapaper's page, and the page names it again`() {
        val link = InstapaperLinks.readLink(1234)
        assertEquals("https://www.instapaper.com/read/1234", link)
        assertEquals(1234L, InstapaperLinks.bookmarkId(link))
        assertEquals(1234L, InstapaperLinks.bookmarkId("https://instapaper.com/read/1234/"))
    }

    @Test
    fun `other addresses are not Instapaper articles`() {
        assertNull(InstapaperLinks.bookmarkId("https://www.instapaper.com/u"))
        assertNull(InstapaperLinks.bookmarkId("https://www.instapaper.com/read/12/extra"))
        assertNull(InstapaperLinks.bookmarkId("http://www.instapaper.com/read/12"))
        assertNull(InstapaperLinks.bookmarkId("https://example.com/read/12"))
        assertNull(InstapaperLinks.bookmarkId(null))
    }
}
