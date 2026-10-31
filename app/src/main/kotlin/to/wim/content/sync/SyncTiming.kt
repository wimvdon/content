package to.wim.content.sync

/** When sync runs while the app is in use (see SyncManager). The web app uses the same timing. */
object SyncTiming {
    /** A change (a read mark, a new feed) is synced this long after the last one in a row. */
    const val CHANGE_DELAY_MS = 1_000L

    /**
     * While the app is on screen and the live connection is down, it syncs this often, so changes
     * from other devices still come in. With the live connection they come in at once.
     */
    const val VISIBLE_INTERVAL_MS = 60_000L

    /** Coming back or the next minute skips sync when it ran this recently. A refresh never does. */
    const val MIN_GAP_MS = 10_000L

    /** True when a sync is worth doing: never synced, or not within the last [MIN_GAP_MS]. */
    fun isDue(
        lastSyncedAt: Long?,
        now: Long,
    ): Boolean = lastSyncedAt == null || now - lastSyncedAt !in 0 until MIN_GAP_MS
}
