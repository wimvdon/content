package to.wim.content.ui.home

import to.wim.content.data.Item

/**
 * The end of Feeds and the Inbox (2026-11-07). Mark all as read is the FAB: in Feeds, in Unread and
 * All alike, while something in the section shown is unread; in the Inbox while something shared
 * with you is unread. With Mark as read on scroll on, Feeds' list ends in All caught up, a block as
 * tall as the list's window, so the last articles can scroll off the top (and be marked read) too.
 * Plain Kotlin, tested on the JVM.
 */
object ListEnd {
    fun showsMarkAllRead(unreadHere: Int): Boolean = unreadHere > 0

    fun inboxHasUnread(items: List<Item>): Boolean = items.any { it.sharedBy?.read == false }

    /** The FAB has its words while the header is (mostly) shown: at the top, or scrolling up. */
    fun fabExpanded(headerCollapsedFraction: Float): Boolean = headerCollapsedFraction < 0.5f

    /** All caught up after the last article: only with Mark as read on scroll, and only under articles. */
    fun showsCaughtUpEnd(
        markReadOnScroll: Boolean,
        articles: Int,
    ): Boolean = markReadOnScroll && articles > 0

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
