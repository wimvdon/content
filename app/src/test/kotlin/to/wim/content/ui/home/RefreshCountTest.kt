package to.wim.content.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RefreshCountTest {
    @Test
    fun `counts what is in the list now and wasn't before`() {
        assertEquals(2, RefreshCount.added(setOf("a", "b"), listOf("c", "a", "d", "b")))
        // Gone from the list (read elsewhere, say) doesn't count against it.
        assertEquals(1, RefreshCount.added(setOf("a", "b"), listOf("c")))
        assertEquals(0, RefreshCount.added(setOf("a"), listOf("a")))
        assertEquals(0, RefreshCount.added(emptySet(), emptyList()))
    }

    @Test
    fun `says plus the number, at most 99, and nothing when nothing came in`() {
        assertEquals("+3", RefreshCount.label(3))
        assertEquals("+99", RefreshCount.label(250))
        assertNull(RefreshCount.label(0))
    }

    @Test
    fun `starts as Material's circle and ends as the cookie`() {
        val circle = RefreshCount.outline(0f, 40f).map { kotlin.math.hypot(it.x.toDouble(), it.y.toDouble()) }
        assertEquals(20.0, circle.min(), 0.01)
        assertEquals(20.0, circle.max(), 0.01)
        val cookie = RefreshCount.outline(1f, 40f).map { kotlin.math.hypot(it.x.toDouble(), it.y.toDouble()) }
        assertEquals(20.0, cookie.max(), 0.01)
        assertEquals(20 * (1 - 2 * to.wim.content.ui.Cookie.DEPTH), cookie.min(), 0.05)
    }
}
