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

    @Test
    fun `the share sheet has Send with Content and at most four people, the most recent first`() {
        val people = (1..12).map { "p$it@example.com" }
        assertEquals(people.take(4), ShareShortcuts.buttons(people))
        assertEquals(ShareShortcuts.MAX_BUTTONS, ShareShortcuts.buttons(people).size + 1)
        // Direct share never offers more people than there are buttons for.
        assertEquals(ShareShortcuts.buttons(ShareShortcuts.pick(people)), ShareShortcuts.pick(people))
    }
}
