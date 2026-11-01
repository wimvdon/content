package to.wim.content.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListEndTest {
    @Test
    fun marksAllReadAtTheEndOfUnread() {
        assertTrue(ListEnd.showsMarkAllRead(unreadOnly = true, unreadHere = 3))
    }

    @Test
    fun nothingToMarkWhenAllIsRead() {
        assertFalse(ListEnd.showsMarkAllRead(unreadOnly = true, unreadHere = 0))
    }

    @Test
    fun notInAll() {
        // In All the read ones are listed too, so a button at its end would mark more than you scrolled through.
        assertFalse(ListEnd.showsMarkAllRead(unreadOnly = false, unreadHere = 3))
    }
}
