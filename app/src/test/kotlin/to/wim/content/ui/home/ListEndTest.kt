package to.wim.content.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import to.wim.content.data.Item
import to.wim.content.data.SharedBy

class ListEndTest {
    @Test
    fun marksAllReadWhileSomethingIsUnread() {
        assertTrue(ListEnd.showsMarkAllRead(unreadHere = 3))
    }

    @Test
    fun nothingToMarkWhenAllIsRead() {
        assertFalse(ListEnd.showsMarkAllRead(unreadHere = 0))
    }

    private fun shared(read: Boolean?) =
        Item(
            key = "a.example/${read}",
            url = "https://a.example/$read",
            title = "A",
            createdAt = 1,
            sharedBy = read?.let { SharedBy("ann@example.com", "Ann", at = 1, inInbox = true, read = it) },
        )

    @Test
    fun theInboxMarksAllReadWhileSomethingSharedIsUnread() {
        assertTrue(ListEnd.inboxHasUnread(listOf(shared(read = true), shared(read = false))))
        assertFalse(ListEnd.inboxHasUnread(listOf(shared(read = true))))
        assertFalse(ListEnd.inboxHasUnread(listOf(shared(read = null))))
        assertFalse(ListEnd.inboxHasUnread(emptyList()))
    }
}
