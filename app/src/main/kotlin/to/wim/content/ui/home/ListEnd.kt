package to.wim.content.ui.home

/**
 * What the end of the feed article list shows. Mark all as read is a button under the last article
 * (the floating button is +, the screen's main action): only in Unread, while something in the
 * section shown is unread. Plain Kotlin, tested on the JVM.
 */
object ListEnd {
    fun showsMarkAllRead(
        unreadOnly: Boolean,
        unreadHere: Int,
    ): Boolean = unreadOnly && unreadHere > 0
}
