package to.wim.content.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PullToMarkTest {
    private val threshold = 100f

    @Test
    fun `pulling up past the end fills the ring at half the finger's distance`() {
        val pull = PullToMark.drag(0f, -100f, threshold)
        assertEquals(50f, pull, 0f)
        assertEquals(0.5f, PullToMark.progress(pull, threshold), 0f)
        assertFalse(PullToMark.armed(pull, threshold))
    }

    @Test
    fun `far enough, letting go marks all as read`() {
        val pull = PullToMark.drag(PullToMark.drag(0f, -120f, threshold), -80f, threshold)
        assertEquals(100f, pull, 0f)
        assertTrue(PullToMark.armed(pull, threshold))
        assertEquals(1f, PullToMark.progress(pull, threshold), 0f)
    }

    @Test
    fun `once far enough, moving back a little cancels, and pulling up again takes it back`() {
        val slack = 12f
        // At its furthest: armed.
        assertTrue(PullToMark.armed(130f, peak = 130f, threshold = threshold, slack = slack))
        // A little back (within the slack): still armed, a shaky finger doesn't cancel.
        assertTrue(PullToMark.armed(125f, peak = 130f, threshold = threshold, slack = slack))
        // Further back, though still past the threshold: cancelled.
        assertFalse(PullToMark.armed(115f, peak = 130f, threshold = threshold, slack = slack))
        // Below the threshold: never.
        assertFalse(PullToMark.armed(95f, peak = 95f, threshold = threshold, slack = slack))
    }

    @Test
    fun `a flick back down as you let go cancels, a still or upward release doesn't`() {
        assertTrue(PullToMark.cancelsOnRelease(900f))
        assertFalse(PullToMark.cancelsOnRelease(0f))
        assertFalse(PullToMark.cancelsOnRelease(-900f))
    }

    @Test
    fun `back down gives way, never below nothing or past its limit`() {
        assertEquals(10f, PullToMark.drag(60f, 100f, threshold), 0f)
        assertEquals(0f, PullToMark.drag(10f, 100f, threshold), 0f)
        assertEquals(150f, PullToMark.drag(140f, -1000f, threshold), 0f)
        assertFalse(PullToMark.armed(0f, 0f))
        assertEquals(0f, PullToMark.progress(50f, 0f), 0f)
    }
}
