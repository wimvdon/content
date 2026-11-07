package to.wim.content.ui.home

import to.wim.content.data.Item

/**
 * When Mark all as read shows, in the floating toolbar beside + (2026-11-07): in Feeds, in Unread and
 * All alike, while something in the section shown is unread; in the Inbox while something shared
 * with you is unread. Plain Kotlin, tested on the JVM.
 */
object ListEnd {
    fun showsMarkAllRead(unreadHere: Int): Boolean = unreadHere > 0

    fun inboxHasUnread(items: List<Item>): Boolean = items.any { it.sharedBy?.read == false }
}
