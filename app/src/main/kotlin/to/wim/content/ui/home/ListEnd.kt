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
    /** How long the All caught up page takes to fade in (and the header to slide back) once scrolled into. */
    const val CAUGHT_UP_FADE_MS = 700

    /**
     * How visible All caught up at the end is, from where its top is ([top], px from the list's top)
     * and the stretch it fades in over: nothing while it is further down than [fadeDistance] (under
     * the last articles, also with one unread article left), then in slowly, fully there at the top.
     */
    fun caughtUpAlpha(
        top: Int,
        fadeDistance: Float,
    ): Float {
        if (fadeDistance <= 0f) return 1f
        val progress = (1f - top / fadeDistance).coerceIn(0f, 1f)
        return progress * progress
    }

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
