package to.wim.content.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test
import to.wim.content.ui.reader.ReaderMenu.Entry

class ReaderMenuTest {
    @Test
    fun `a feed article not saved can be marked unread and switch its text`() {
        assertEquals(
            listOf(Entry.MarkUnread, Entry.ToggleText, Entry.OpenInBrowser, Entry.SignIn, Entry.Report),
            ReaderMenu.entries(feedArticle = true, saved = false, hasLink = true, canSignIn = true, canReport = true),
        )
    }

    @Test
    fun `once saved, also right after Save in the reader, it is a saved article's menu`() {
        val saved = listOf(Entry.OpenInBrowser, Entry.Report, Entry.Remove)
        assertEquals(saved, ReaderMenu.entries(feedArticle = true, saved = true, hasLink = true, canSignIn = false, canReport = true))
        // The same as one opened from Saved.
        assertEquals(saved, ReaderMenu.entries(feedArticle = false, saved = true, hasLink = true, canSignIn = false, canReport = true))
    }

    @Test
    fun `without a link only what needs none`() {
        assertEquals(listOf(Entry.MarkUnread), ReaderMenu.entries(feedArticle = true, saved = false, hasLink = false, canSignIn = false, canReport = false))
    }
}
