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
    fun `the launcher offers the first folders, which open the app on them (#251)`() {
        val folders = (1..6).map { Folder("f$it", "Folder $it") }
        assertEquals(folders.take(LauncherFolders.MAX), LauncherFolders.pick(folders))
        assertEquals("folder:f1", LauncherFolders.idOf("f1"))
        assertEquals("f1", LauncherFolders.folderOf(LauncherFolders.requestOf("f1")))
        assertNull(LauncherFolders.folderOf("folder:"))
        assertNull(LauncherFolders.folderOf("shared-with-me"))
        assertNull(LauncherFolders.folderOf(null))
    }
}
