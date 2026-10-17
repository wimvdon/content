package be.wimvandonck.reader.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncTimingTest {
    @Test
    fun `a sync is due when there was none or the last one is more than a few seconds ago`() {
        val now = 1_000_000L
        assertTrue(SyncTiming.isDue(null, now))
        assertFalse(SyncTiming.isDue(now, now))
        assertFalse(SyncTiming.isDue(now - SyncTiming.MIN_GAP_MS + 1, now))
        assertTrue(SyncTiming.isDue(now - SyncTiming.MIN_GAP_MS, now))
        assertTrue(SyncTiming.isDue(now - SyncTiming.VISIBLE_INTERVAL_MS, now))
    }

    @Test
    fun `a last sync in the future (clock changed) doesn't block syncing`() {
        assertTrue(SyncTiming.isDue(2_000_000L, 1_000_000L))
    }
}
