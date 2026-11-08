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
    fun theLineRunsRoundTheCookieOnlyOverTheLastStretch() {
        // Far down the screen, under the last article(s): no line yet.
        assertEquals(0f, ListEnd.caughtUpProgress(top = 900, fadeDistance = 600f), 0f)
        assertEquals(0f, ListEnd.caughtUpProgress(top = 600, fadeDistance = 600f), 0f)
        // Coming up: as far round as scrolled, all the way at the top.
        assertEquals(0.5f, ListEnd.caughtUpProgress(top = 300, fadeDistance = 600f), 0.001f)
        assertEquals(1f, ListEnd.caughtUpProgress(top = 0, fadeDistance = 600f), 0f)
        assertEquals(1f, ListEnd.caughtUpProgress(top = 50, fadeDistance = 0f), 0f)
    }

    @Test
    fun withAShortListNothingShowsBeforeScrolling() {
        // One unread article: All caught up starts 400 px from the top, closer than the stretch.
        val rest = ListEnd.caughtUpRestTop(top = 400, firstVisibleIndex = 0, firstVisibleOffset = 0, previous = null)
        assertEquals(400, rest)
        assertEquals(0f, ListEnd.caughtUpProgress(top = 400, fadeDistance = 600f, restTop = rest), 0f)
        // Scrolled 200 px: where it rests stays the same, and the line is half way.
        assertEquals(400, ListEnd.caughtUpRestTop(top = 200, firstVisibleIndex = 0, firstVisibleOffset = 200, previous = 400))
        assertEquals(0.5f, ListEnd.caughtUpProgress(top = 200, fadeDistance = 600f, restTop = 400), 0.001f)
        assertEquals(1f, ListEnd.caughtUpProgress(top = 0, fadeDistance = 600f, restTop = 400), 0f)
        // Past the first row, the last one worked out stays; never one known: the usual stretch.
        assertEquals(400, ListEnd.caughtUpRestTop(top = 100, firstVisibleIndex = 1, firstVisibleOffset = 20, previous = 400))
        assertEquals(null, ListEnd.caughtUpRestTop(top = 100, firstVisibleIndex = 1, firstVisibleOffset = 20, previous = null))
        assertEquals(0.5f, ListEnd.caughtUpProgress(top = 300, fadeDistance = 600f, restTop = null), 0.001f)
        // A long list: it rests far down, so the stretch is the usual one.
        assertEquals(0.5f, ListEnd.caughtUpProgress(top = 300, fadeDistance = 600f, restTop = 1800), 0.001f)
    }

    @Test
    fun anArticleLeavingTheListDoesNotStartTheLine() {
        // Two articles, one read, then a refresh: one is left and the end moves up to 400 px.
        // Seen further down a moment before (900 px) or not: at the top of the list, nothing shows.
        val before = ListEnd.caughtUpRestTop(top = 900, firstVisibleIndex = 0, firstVisibleOffset = 0, previous = null)
        val after = ListEnd.caughtUpRestTop(top = 400, firstVisibleIndex = 0, firstVisibleOffset = 0, previous = before)
        assertEquals(0f, ListEnd.caughtUpProgress(top = 400, fadeDistance = 600f, restTop = after), 0f)
    }

    @Test
    fun theTickAndTitleComeInSlowlyAndTheOutlineWithTheLine() {
        assertEquals(0f, ListEnd.caughtUpAlpha(0f), 0f)
        assertEquals(0.25f, ListEnd.caughtUpAlpha(0.5f), 0.001f)
        assertEquals(1f, ListEnd.caughtUpAlpha(1f), 0f)
        assertEquals(0f, ListEnd.caughtUpTrackAlpha(0f), 0f)
        assertEquals(0.4f, ListEnd.caughtUpTrackAlpha(0.1f), 0.001f)
        assertEquals(1f, ListEnd.caughtUpTrackAlpha(0.5f), 0f)
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
