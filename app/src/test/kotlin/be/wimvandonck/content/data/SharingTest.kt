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
    fun `shared articles land in the inbox, unread, with who shared them`() {
        val message = InboxMessage("m1", ann, listOf(SharedArticle("https://a.example/1", "One"), SharedArticle("https://a.example/2", "Two")))
        val (lib, got) = Sharing.takeIn(Library(), listOf(message), 100)
        assertEquals(2, got.count)
        assertEquals(listOf(ann), got.from)
        val one = lib.item("a.example/1")!!
        assertNull(one.readingList)
        assertTrue(one.inInbox)
        assertEquals(ann.copy(at = 100, inInbox = true), one.sharedBy)
        assertTrue(one.tags.isEmpty())
        assertEquals(setOf("a.example/1", "a.example/2"), Inbox.items(lib).map { it.key }.toSet())
        assertEquals(2, Inbox.unread(lib))
        // Not in Saved: the inbox is a place of its own.
        assertTrue(Saved.items(lib, SavedFilter(SavedTab.All)).isEmpty())
    }

    @Test
    fun `saving it from the inbox keeps it there, and removing it from Saved keeps it too`() {
        val (lib, _) = Sharing.takeIn(Library(), listOf(InboxMessage("m", ann, listOf(SharedArticle("https://a.example/1", "One")))), 100)
        val toRead = lib.changeItem("a.example/1") { it.copy(readingList = ReadingListEntry(ReadingState.ToRead, 200)) }
        assertTrue(toRead.item("a.example/1")!!.inInbox)
        assertEquals(listOf("a.example/1"), Saved.items(toRead, SavedFilter(SavedTab.ToRead)).map { it.key })
        assertEquals(listOf("a.example/1"), Inbox.items(toRead).map { it.key })
        val unsaved = Saved.remove(toRead.changeItem("a.example/1") { it.copy(tags = listOf("x"), note = "n") }, "a.example/1")
        assertNull(unsaved.item("a.example/1")!!.readingList)
        assertTrue(unsaved.item("a.example/1")!!.tags.isEmpty())
        assertEquals(listOf("a.example/1"), Inbox.items(unsaved).map { it.key })
        // Removed from the inbox too: nothing of the user's is left on it, so it goes.
        assertNull(Inbox.remove(unsaved, "a.example/1").item("a.example/1"))
    }

    @Test
    fun `an article already saved arrives in the inbox too, and stays where it is in Saved`() {
        val mine = saved("a.example/1", 5, "Mine").copy(readingList = ReadingListEntry(ReadingState.Archived, 5))
        val (lib, got) = Sharing.takeIn(Library(items = listOf(mine)), listOf(InboxMessage("m", ann, listOf(SharedArticle("https://a.example/1", "One")))), 100)
        val item = lib.item("a.example/1")!!
        assertEquals(ReadingState.Archived, item.readingList?.state)
        assertEquals(listOf("Mine"), item.tags)
        assertEquals(1, got.count)
        assertEquals(listOf("a.example/1"), Inbox.items(lib).map { it.key })
        assertEquals(1, Inbox.unread(lib))
    }

    @Test
    fun `shared again, it comes back unread with the latest sender`() {
        val bob = SharedBy("bob@example.com", "Bob")
        val (first, _) = Sharing.takeIn(Library(), listOf(InboxMessage("m", bob, listOf(SharedArticle("https://a.example/1", "One")))), 100)
        val read = Inbox.markRead(first, "a.example/1")
        assertEquals(0, Inbox.unread(read))
        val removed = Inbox.remove(read.changeItem("a.example/1") { it.copy(readingList = ReadingListEntry(ReadingState.ToRead, 150)) }, "a.example/1")
        assertTrue(Inbox.items(removed).isEmpty())
        val (again, got) = Sharing.takeIn(removed, listOf(InboxMessage("n", ann, listOf(SharedArticle("https://a.example/1", "One")))), 200)
        assertEquals(1, got.count)
        assertEquals(ann.copy(at = 200, inInbox = true), again.item("a.example/1")!!.sharedBy)
        assertEquals(1, Inbox.unread(again))
    }

    @Test
    fun `the inbox is newest first, and marks read and unread`() {
        val (lib, _) =
            Sharing.takeIn(
                Library(),
                listOf(
                    InboxMessage("m", ann, listOf(SharedArticle("https://a.example/1", "One"))),
                ),
                100,
            )
        val (both, _) = Sharing.takeIn(lib, listOf(InboxMessage("n", ann, listOf(SharedArticle("https://a.example/2", "Two")))), 200)
        assertEquals(listOf("a.example/2", "a.example/1"), Inbox.items(both).map { it.key })
        val read = Inbox.markRead(both, "a.example/2")
        assertEquals(true, read.item("a.example/2")!!.sharedBy?.read)
        assertEquals(1, Inbox.unread(read))
        assertEquals(2, Inbox.unread(Inbox.markRead(read, "a.example/2", read = false)))
        // Not in the inbox: nothing changes.
        assertEquals(read, Inbox.markRead(read, "nope.example/x"))
    }

    @Test
    fun `who shared it syncs with the item and stays once known`() {
        val item = saved("a.example/1", 1).copy(sharedBy = ann.copy(at = 5, inInbox = true, read = true))
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
        assertEquals(listOf(InboxMessage("m1", ann, listOf(SharedArticle("https://a.example/1", "One")))), messages)
        assertTrue(AccountClient.inboxFrom("nonsense").isEmpty())
    }

    @Test
    fun `what is sent carries the picture and intro the library knows`() {
        val item = saved("a.example/saved", 1).copy(imageUrl = "https://a.example/pic.jpg", description = "The intro.")
        val feedArticle =
            Article(
                feedUrl = "https://b.example/feed",
                id = "1",
                title = "Feed title",
                link = "https://b.example/story",
                author = null,
                html = "<p>First words of the story.</p>",
                publishedAt = null,
                fetchedAt = 0,
                imageUrl = "https://b.example/hero.jpg",
            )
        val library = Library(articles = listOf(feedArticle), items = listOf(item))
        assertEquals(SharedArticle("https://a.example/saved", "Shown title", "https://a.example/pic.jpg", "The intro."), Sharing.articleFor(library, "https://a.example/saved", "Shown title"))
        assertEquals(SharedArticle("https://b.example/story", "Feed title", "https://b.example/hero.jpg", "First words of the story."), Sharing.articleFor(library, "https://b.example/story", "Feed title"))
        // Unknown: just the link and its title.
        assertEquals(SharedArticle("https://c.example/", "C"), Sharing.articleFor(library, "https://c.example/", "C"))
    }
}
