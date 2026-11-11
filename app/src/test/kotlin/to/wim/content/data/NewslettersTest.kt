package to.wim.content.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import to.wim.content.sync.SyncRows

class NewslettersTest {
    private val email = "https://www.instapaper.com/read/42"

    /** Saved with a newsletter (unread when [unread]) and an article with a web address. */
    private fun library(unread: Boolean = true): Library {
        var lib = Library().addToReadingList(email, "A newsletter", 2).first
        lib = lib.addToReadingList("https://a.example/1", "An article", 1).first
        return lib.changeItem("instapaper.com/read/42") { it.copy(unread = unread) }
    }

    @Test
    fun `a newsletter is an article with Instapaper's page as its address`() {
        assertTrue(Newsletters.isNewsletter(email))
        assertTrue(Newsletters.isNewsletter("https://instapaper.com/read/7"))
        assertFalse(Newsletters.isNewsletter("https://a.example/1"))
        assertFalse(Newsletters.isNewsletter("https://www.instapaper.com/u"))
        assertFalse(Newsletters.isNewsletter(null))
    }

    @Test
    fun `marking read reads it, and nothing else`() {
        val lib = library()
        assertTrue(Newsletters.hasUnread(lib))
        val read = Newsletters.markRead(lib, "instapaper.com/read/42")
        assertFalse(Newsletters.hasUnread(read))
        // Already read, or not there: the same library.
        assertTrue(Newsletters.markRead(read, "instapaper.com/read/42") === read)
        assertTrue(Newsletters.markRead(read, "nothing") === read)
    }

    @Test
    fun `archiving or removing it reads it`() {
        val archived = library().setReadingState("instapaper.com/read/42", ReadingState.Archived)
        assertFalse(archived.item("instapaper.com/read/42")!!.unread)
        assertFalse(Newsletters.hasUnread(archived))
        assertFalse(Newsletters.hasUnread(Saved.remove(library(), "instapaper.com/read/42")))
        // Back to read later: it stays read.
        assertFalse(archived.setReadingState("instapaper.com/read/42", ReadingState.ToRead).item("instapaper.com/read/42")!!.unread)
    }

    @Test
    fun `mark all as read takes the unread ones shown, and Undo brings them back`() {
        val lib = library()
        val keys = Newsletters.unreadKeys(Saved.items(lib, SavedFilter()))
        assertEquals(listOf("instapaper.com/read/42"), keys)
        val read = Newsletters.markAllRead(lib, keys)
        assertFalse(Newsletters.hasUnread(read))
        assertTrue(Newsletters.hasUnread(Newsletters.undoMarkAllRead(read, keys)))
        // Archived meanwhile: Undo leaves it read.
        val archived = read.setReadingState("instapaper.com/read/42", ReadingState.Archived)
        assertFalse(Newsletters.hasUnread(Newsletters.undoMarkAllRead(archived, keys)))
    }

    @Test
    fun `the Newsletters chip shows the newsletters, and only while there are any`() {
        val lib = library(unread = false)
        val chosen = SavedFilter(newslettersOnly = true)
        assertEquals(listOf("instapaper.com/read/42"), Saved.items(lib, chosen).map { it.key })
        assertTrue(Saved.showsNewsletters(lib, SavedFilter()))
        assertTrue(Saved.showsChips(emptyList(), showsLiked = false, showsNewsletters = true))
        val none = Saved.remove(lib, "instapaper.com/read/42")
        assertFalse(Saved.showsNewsletters(none, SavedFilter()))
        // Still chosen: it stays, so it can be let go.
        assertTrue(Saved.showsNewsletters(none, chosen))
        assertFalse(chosen.plain)
        assertTrue(SavedFilter().plain)
    }

    @Test
    fun `an unread newsletter's card says so`() {
        assertTrue(Saved.note(library().item("instapaper.com/read/42")!!).unread)
        assertFalse(Saved.note(library().item("a.example/1")!!).unread)
    }

    @Test
    fun `unread syncs, and read on either device is read`() {
        val item = library().item("instapaper.com/read/42")!!
        val data = SyncRows.itemData(item)
        assertEquals(true, data["unread"])
        assertTrue(SyncRows.itemOf(item.key, data, null)!!.unread)
        // A row from an app before this has no "unread": read.
        assertFalse(SyncRows.itemOf(item.key, data - "unread", null)!!.unread)
        // Both changed since the base: read on one side wins.
        val base = item
        val local = item.copy(liked = true)
        val remote = item.copy(unread = false)
        assertFalse(SyncRows.mergeItems(base, local, remote).unread)
        assertTrue(SyncRows.mergeItems(base, local, item.copy(note = "x")).unread)
    }

    @Test
    fun `the local database keeps it`() {
        val item = library().item("instapaper.com/read/42")!!
        assertTrue(LibraryRows.item(LibraryRows.item(item)).unread)
    }
}
