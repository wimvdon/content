package to.wim.content.ui.reader

/**
 * Whether a sheet may close on a swipe, a tap beside it or Back. While the keyboard is open,
 * and for a moment after it opens or closes or after a page of the sheet opens or closes, the sheet
 * is still moving to its new height: a close then is not what the user asked for (the sheet used to
 * disappear when the keyboard went away, or on Edit topics). The keyboard closes instead, and the
 * sheet stays. The Cancel and Done buttons always close. Plain Kotlin, unit tested.
 */
class SheetDismissGuard(
    private val settleMillis: Long = SETTLE_MILLIS,
) {
    private var changedAt = Long.MIN_VALUE / 2

    /** The keyboard opened or closed, or a page opened or closed, at [now]. */
    fun changed(now: Long) {
        changedAt = now
    }

    /** True when a close at [now] is the user's: no keyboard, and the sheet has settled. */
    fun allowsDismiss(
        now: Long,
        keyboardOpen: Boolean,
    ): Boolean = !keyboardOpen && now - changedAt >= settleMillis

    companion object {
        /** About as long as the keyboard and the sheet take to move. */
        const val SETTLE_MILLIS = 600L
    }
}
