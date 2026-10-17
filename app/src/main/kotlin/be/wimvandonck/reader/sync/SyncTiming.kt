package be.wimvandonck.reader.sync

/** When sync runs while the app is in use (see SyncManager). The web app uses the same timing. */
object SyncTiming {
    /** A change (a read mark, a new feed) is synced this long after the last one in a row. */
    const val CHANGE_DELAY_MS = 2_000L

    /** While the app is on screen, it syncs this often, so changes from other devices come in. */
    const val VISIBLE_INTERVAL_MS = 60_000L

    /** Coming back, refreshing or the next minute skips sync when it ran this recently. */
    const val MIN_GAP_MS = 10_000L

    /** True when a sync is worth doing: never synced, or not within the last [MIN_GAP_MS]. */
    fun isDue(
        lastSyncedAt: Long?,
        now: Long,
    ): Boolean = lastSyncedAt == null || now - lastSyncedAt !in 0 until MIN_GAP_MS
}
