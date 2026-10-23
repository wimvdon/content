package be.wimvandonck.content.data

import be.wimvandonck.content.sync.AccountClient
import be.wimvandonck.content.sync.SyncRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharingTest {
    private val ann = SharedBy("ann@example.com", "Ann")

    private fun saved(
        key: String,
        at: Long,
        vararg tags: String,
    ) = Item(key = key, url = "https://$key", title = key, createdAt = at, readingList = ReadingListEntry(ReadingState.ToRead, at), tags = tags.toList())

    @Test
    fun `shared articles wait in Shared with me, with who shared them, the message and the tag`() {
        val message = InboxMessage("m1", ann, listOf(SharedArticle("https://a.example/1", "One"), SharedArticle("https://a.example/2", "Two")), tag = "Reading club", message = " Read these ")
        val (lib, got) = Sharing.takeIn(Library(), listOf(message), 100)
        assertEquals(2, got.count)
        assertEquals(listOf(ann), got.from)
        val one = lib.item("a.example/1")!!
        assertNull(one.readingList)
        assertTrue(one.waitingShared)
        assertEquals(ann.copy(message = "Read these", at = 100, waits = true), one.sharedBy)
        assertEquals(listOf("Reading club"), one.tags)
        assertEquals(setOf("a.example/1", "a.example/2"), Saved.items(lib, SavedFilter(SavedTab.Shared)).map { it.key }.toSet())
        assertTrue(Saved.items(lib, SavedFilter(SavedTab.ToRead)).isEmpty())
        assertEquals(2, Saved.sharedWaiting(lib))
    }

    @Test
    fun `put in Saved it leaves Shared with me, and removing it later drops it`() {
        val (lib, _) = Sharing.takeIn(Library(), listOf(InboxMessage("m", ann, listOf(SharedArticle("https://a.example/1", "One")), message = "Hi")), 100)
        val toRead = lib.changeItem("a.example/1") { it.copy(readingList = ReadingListEntry(ReadingState.ToRead, 200)) }
        val item = toRead.item("a.example/1")!!
        assertEquals(false, item.waitingShared)
        assertEquals("Hi", item.sharedBy?.message)
        assertEquals(0, Saved.sharedWaiting(toRead))
        assertEquals(listOf("a.example/1"), Saved.items(toRead, SavedFilter(SavedTab.ToRead)).map { it.key })
        // Removed from Saved: gone, not back in Shared with me.
        assertNull(toRead.setReadingState("a.example/1", null).item("a.example/1"))
    }

    @Test
    fun `an article already saved keeps its place and its first sender`() {
        val mine = saved("a.example/1", 5, "Mine").copy(readingList = ReadingListEntry(ReadingState.Archived, 5), sharedBy = SharedBy("bob@example.com"))
        val (lib, got) = Sharing.takeIn(Library(items = listOf(mine)), listOf(InboxMessage("m", ann, listOf(SharedArticle("https://a.example/1", "One")), "club")), 100)
        val item = lib.item("a.example/1")!!
        assertEquals(ReadingState.Archived, item.readingList?.state)
        assertEquals("bob@example.com", item.sharedBy?.email)
        assertEquals(listOf("Mine", "club"), item.tags)
        assertEquals(1, got.count)
        // The same again changes nothing.
        assertEquals(0, Sharing.takeIn(lib, listOf(InboxMessage("m", ann, listOf(SharedArticle("https://a.example/1", "One")), "club")), 200).second.count)
    }

    @Test
    fun `a tag's saved articles are what a tag share sends, newest first`() {
        val lib = Library(items = listOf(saved("a.example/1", 1, "club"), saved("a.example/2", 3, "Club"), saved("a.example/3", 2, "other")))
        assertEquals(listOf("https://a.example/2", "https://a.example/1"), Sharing.tagArticles(lib, "CLUB").map { it.url })
    }

    @Test
    fun `who shared it syncs with the item and stays once known`() {
        val item = saved("a.example/1", 1).copy(sharedBy = ann.copy(message = "Hi", at = 5))
        val data = SyncRows.itemData(item)
        assertEquals(item.sharedBy, SyncRows.itemOf(item.key, data, null)?.sharedBy)
        assertNull(SyncRows.itemOf(item.key, SyncRows.itemData(item.copy(sharedBy = null)), null)?.sharedBy)
        assertEquals(item.sharedBy, SyncRows.mergeItems(null, item.copy(sharedBy = null), item).sharedBy)
        assertEquals(item, LibraryRows.item(LibraryRows.item(item)))
    }

    @Test
    fun `reads the inbox the account sends`() {
        val text = """{"messages":[{"id":"m1","at":1,"from":{"email":"ann@example.com","name":"Ann"},"items":[{"url":"https://a.example/1","title":"One","imageUrl":"http://x"}],"tag":null,"message":"Hi"},{"id":"bad"}]}"""
        val messages = AccountClient.inboxFrom(text)
        assertEquals(listOf(InboxMessage("m1", ann, listOf(SharedArticle("https://a.example/1", "One")), null, "Hi")), messages)
        assertTrue(AccountClient.inboxFrom("nonsense").isEmpty())
    }
}
