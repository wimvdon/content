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
}
