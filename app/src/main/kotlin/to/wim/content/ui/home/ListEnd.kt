package to.wim.content.ui.home

import to.wim.content.data.Item

/**
 * The end of Feeds and the Inbox (2026-11-07). Mark all as read is the FAB: in Feeds, in Unread and
 * All alike, while something in the section shown is unread; in the Inbox while something shared
 * with you is unread. With Mark as read on scroll on, Feeds' Unread list ends in All caught up, a
 * block as tall as the list's window, so the last articles can scroll off the top (and be marked
 * read) too; once it fills the screen the list turns into the All caught up page (owner's wish).
 * Plain Kotlin, tested on the JVM.
 */
object ListEnd {
    fun showsMarkAllRead(unreadHere: Int): Boolean = unreadHere > 0

    fun inboxHasUnread(items: List<Item>): Boolean = items.any { it.sharedBy?.read == false }

    /** The FAB has its words while the header is (mostly) shown: at the top, or scrolling up. */
    fun fabExpanded(headerCollapsedFraction: Float): Boolean = headerCollapsedFraction < 0.5f

    /**
     * All caught up after the last article: only with Mark as read on scroll, in Unread (All keeps
     * read articles, so it never empties), and only under articles.
     */
    fun showsCaughtUpEnd(
        markReadOnScroll: Boolean,
        unreadOnly: Boolean,
        articles: Int,
    ): Boolean = markReadOnScroll && unreadOnly && articles > 0

    /**
     * Scrolled all the way into All caught up: it is the last of [totalItems] and the first one on
     * screen, so every article has gone past the top.
     */
    fun reachedCaughtUp(
        caughtUpEnd: Boolean,
        firstVisibleIndex: Int,
        totalItems: Int,
    ): Boolean = caughtUpEnd && totalItems > 0 && firstVisibleIndex == totalItems - 1

    /**
     * How tall the All caught up block is: the list's window minus its padding, so scrolled to the
     * end the block fills the screen and every article has gone past the top. Never less than [minimum].
     */
    fun caughtUpHeight(
        viewportHeight: Int,
        beforePadding: Int,
        afterPadding: Int,
        minimum: Int,
    ): Int = maxOf(viewportHeight - beforePadding - afterPadding, minimum)
}
