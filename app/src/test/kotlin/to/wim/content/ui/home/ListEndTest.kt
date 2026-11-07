package to.wim.content.ui.home

import org.junit.Assert.assertEquals
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

    @Test
    fun theFabHasItsWordsWhileTheHeaderShows() {
        assertTrue(ListEnd.fabExpanded(headerCollapsedFraction = 0f))
        assertTrue(ListEnd.fabExpanded(headerCollapsedFraction = 0.3f))
        assertFalse(ListEnd.fabExpanded(headerCollapsedFraction = 0.5f))
        assertFalse(ListEnd.fabExpanded(headerCollapsedFraction = 1f))
    }

    @Test
    fun allCaughtUpEndsTheUnreadListOnlyWithMarkAsReadOnScroll() {
        assertTrue(ListEnd.showsCaughtUpEnd(markReadOnScroll = true, unreadOnly = true, articles = 3))
        assertFalse(ListEnd.showsCaughtUpEnd(markReadOnScroll = false, unreadOnly = true, articles = 3))
        // All keeps read articles: it never becomes the All caught up page.
        assertFalse(ListEnd.showsCaughtUpEnd(markReadOnScroll = true, unreadOnly = false, articles = 3))
        // No articles: the page's own All caught up shows instead.
        assertFalse(ListEnd.showsCaughtUpEnd(markReadOnScroll = true, unreadOnly = true, articles = 0))
    }

    @Test
    fun scrolledIntoAllCaughtUpWhenItIsTheFirstItemOnScreen() {
        // 12 items: day headers and articles, then All caught up as the last.
        assertTrue(ListEnd.reachedCaughtUp(caughtUpEnd = true, firstVisibleIndex = 11, totalItems = 12))
        // The last articles are still (partly) on screen.
        assertFalse(ListEnd.reachedCaughtUp(caughtUpEnd = true, firstVisibleIndex = 9, totalItems = 12))
        // No All caught up at the end (setting off, or All): the last article is just the last item.
        assertFalse(ListEnd.reachedCaughtUp(caughtUpEnd = false, firstVisibleIndex = 11, totalItems = 12))
        assertFalse(ListEnd.reachedCaughtUp(caughtUpEnd = true, firstVisibleIndex = 0, totalItems = 0))
    }

    @Test
    fun allCaughtUpFillsTheListSoTheLastArticlesScrollPastTheTop() {
        assertEquals(1800, ListEnd.caughtUpHeight(viewportHeight = 2000, beforePadding = 0, afterPadding = 200, minimum = 500))
        assertEquals(1700, ListEnd.caughtUpHeight(viewportHeight = 2000, beforePadding = 100, afterPadding = 200, minimum = 500))
        // Before the list is measured (or a tiny window): never smaller than the minimum.
        assertEquals(500, ListEnd.caughtUpHeight(viewportHeight = 0, beforePadding = 0, afterPadding = 200, minimum = 500))
    }
}
