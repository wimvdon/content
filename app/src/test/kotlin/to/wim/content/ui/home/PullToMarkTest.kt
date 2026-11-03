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
    fun `back down gives way, never below nothing or past its limit`() {
        assertEquals(10f, PullToMark.drag(60f, 100f, threshold), 0f)
        assertEquals(0f, PullToMark.drag(10f, 100f, threshold), 0f)
        assertEquals(150f, PullToMark.drag(140f, -1000f, threshold), 0f)
        assertFalse(PullToMark.armed(0f, 0f))
        assertEquals(0f, PullToMark.progress(50f, 0f), 0f)
    }
}
