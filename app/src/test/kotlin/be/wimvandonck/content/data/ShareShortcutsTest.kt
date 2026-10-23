package be.wimvandonck.content.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareShortcutsTest {
    @Test
    fun `a shortcut id says who was picked in the share menu (#235)`() {
        val id = ShareShortcuts.idOf(" Ann@Example.com ")
        assertEquals("share:ann@example.com", id)
        assertEquals("ann@example.com", ShareShortcuts.emailOf(id))
        assertNull(ShareShortcuts.emailOf(null))
        assertNull(ShareShortcuts.emailOf("pinned:something"))
        assertNull(ShareShortcuts.emailOf("share:"))
    }

    @Test
    fun `the share menu gets the most recent few`() {
        val many = (1..10).map { "p$it@example.com" }
        assertEquals(many.take(ShareShortcuts.MAX), ShareShortcuts.pick(many))
        assertEquals(emptyList<String>(), ShareShortcuts.pick(emptyList()))
    }
}
