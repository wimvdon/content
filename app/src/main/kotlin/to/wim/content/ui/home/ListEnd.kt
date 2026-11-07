package to.wim.content.ui.home

/**
 * When Feeds shows Mark all as read: in its floating toolbar beside + (2026-11-07), in Unread and All
 * alike, while something in the section shown is unread. Plain Kotlin, tested on the JVM.
 */
object ListEnd {
    fun showsMarkAllRead(unreadHere: Int): Boolean = unreadHere > 0
}
