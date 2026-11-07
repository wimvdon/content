package to.wim.content.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListEndTest {
    @Test
    fun marksAllReadWhileSomethingIsUnread() {
        assertTrue(ListEnd.showsMarkAllRead(unreadHere = 3))
    }

    @Test
    fun nothingToMarkWhenAllIsRead() {
        assertFalse(ListEnd.showsMarkAllRead(unreadHere = 0))
    }
}
