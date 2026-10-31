package to.wim.content.sync

import to.wim.content.instapaper.InstapaperSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedInstapaperTest {
    private val ann = InstapaperSession("token-a", 7, "ann")
    private val annAgain = InstapaperSession("token-b", 7, "ann")
    private val bob = InstapaperSession("token-c", 9, "bob")

    @Test
    fun `a sign-in goes into the file and comes back the same`() {
        assertEquals(ann, SharedInstapaper.decode(SharedInstapaper.encode(ann)))
        val noId = InstapaperSession("t", null, "")
        assertEquals(noId, SharedInstapaper.decode(SharedInstapaper.encode(noId)))
    }

    @Test
    fun `empty, damaged or tokenless values are no sign-in`() {
        assertNull(SharedInstapaper.decode(null))
        assertNull(SharedInstapaper.decode(""))
        assertNull(SharedInstapaper.decode("nonsense"))
        assertNull(SharedInstapaper.decode("""{"username":"ann"}"""))
        assertNull(SharedInstapaper.decode("""{"token":""}"""))
    }

    @Test
    fun `this device sends its sign-in, a disconnect, or nothing when it never had one`() {
        assertEquals(SharedInstapaper.encode(ann), SharedInstapaper.local(ann, null))
        // Signed in at the last sync and not now: disconnected here, so everywhere.
        assertEquals("", SharedInstapaper.local(null, SharedInstapaper.encode(ann)))
        // Never connected (a new device, or one from before this): the file's stays.
        assertNull(SharedInstapaper.local(null, null))
        assertEquals("", SharedInstapaper.local(null, ""))
    }

    @Test
    fun `another device's sign-in connects this one, its disconnect disconnects it`() {
        assertEquals(SharedInstapaper.Action.SignIn(ann), SharedInstapaper.action(null, SharedInstapaper.encode(ann)))
        assertEquals(SharedInstapaper.Action.SignIn(annAgain), SharedInstapaper.action(ann, SharedInstapaper.encode(annAgain)))
        assertEquals(SharedInstapaper.Action.SignOut, SharedInstapaper.action(ann, ""))
    }

    @Test
    fun `nothing to do when it is already the same, or the file has none`() {
        assertEquals(SharedInstapaper.Action.None, SharedInstapaper.action(ann, SharedInstapaper.encode(ann)))
        assertEquals(SharedInstapaper.Action.None, SharedInstapaper.action(ann, null))
        assertEquals(SharedInstapaper.Action.None, SharedInstapaper.action(null, ""))
        // Damaged: this device's sign-in stays.
        assertEquals(SharedInstapaper.Action.None, SharedInstapaper.action(ann, "nonsense"))
    }

    @Test
    fun `a new token for the same user keeps what was synced, another user doesn't`() {
        assertTrue(SharedInstapaper.sameUser(ann, annAgain))
        assertFalse(SharedInstapaper.sameUser(ann, bob))
        assertFalse(SharedInstapaper.sameUser(null, ann))
        assertFalse(SharedInstapaper.sameUser(InstapaperSession("x", null, ""), InstapaperSession("y", null, "")))
    }
}
