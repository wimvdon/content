package be.wimvandonck.reader.sync

import be.wimvandonck.reader.data.Article
import be.wimvandonck.reader.data.Feed
import be.wimvandonck.reader.data.Highlight
import be.wimvandonck.reader.data.Library
import be.wimvandonck.reader.data.ReadingState
import be.wimvandonck.reader.data.addToReadingList
import be.wimvandonck.reader.data.changeItem
import be.wimvandonck.reader.data.item
import be.wimvandonck.reader.data.Saved
import be.wimvandonck.reader.data.setLiked
import be.wimvandonck.reader.data.setReadingState
import be.wimvandonck.reader.data.setTags
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemSyncTest {
    private val now = 1_800_000_000_000L
    private val day = 24 * 60 * 60 * 1000L
    private val feed = "https://news.example/feed"

    /** The account's items in memory, like the Worker: one version per save, conflicts for old bases, pages. */
    private class FakeItems(
        val pageSize: Int = 1000,
    ) : ItemsTarget {
        var version = 0L
        val rows = LinkedHashMap<String, ItemRow>()
        val sent = mutableListOf<String>()

        override suspend fun readItems(since: Long): ItemsPage {
            val changed = rows.values.filter { it.version > since }.sortedBy { it.version }
            if (changed.size <= pageSize) return ItemsPage(version, changed, false)
            val cut = changed[pageSize - 1].version
            val page = changed.filter { it.version <= cut }
            return ItemsPage(cut, page, true)
        }

        override suspend fun writeItems(rows: List<ItemChange>): ItemsWritten {
            sent += Json.write(rows.map { mapOf("key" to it.key, "baseVersion" to it.baseVersion, "data" to it.data) })
            val next = version + 1
            val saved = LinkedHashMap<String, Long>()
            val conflicts = mutableListOf<ItemRow>()
            for (change in rows) {
                val current = this.rows[change.key]
                if ((current?.version ?: 0L) != change.baseVersion) {
                    conflicts += current!!
                } else {
                    this.rows[change.key] = ItemRow(change.key, next, change.data)
                    saved[change.key] = next
                }
            }
            if (saved.isNotEmpty()) version = next
            return ItemsWritten(version, saved, conflicts)
        }
    }

    /** One device: its library and what it last synced. */
    private inner class Device(
        var library: Library,
    ) {
        var state = ItemSyncState()

        fun sync(account: FakeItems) =
            runBlocking {
                state = ItemSync.run(account, state, now) { change -> change(library).also { library = it } }
            }
    }

    private fun article(
        id: String,
        read: Boolean = false,
        date: Long = now - day,
    ) = Article(feed, id, "Title $id", "https://news.example/$id", null, "<p>SECRET ARTICLE TEXT $id</p>", date, date, read)

    private fun libraryWith(vararg articles: Article) = Library(feeds = listOf(Feed(feed, "News")), articles = articles.toList())

    @Test
    fun `items and read marks reach another device`() {
        val account = FakeItems()
        val phone = Device(libraryWith(article("1", read = true), article("2")))
        phone.library = phone.library.addToReadingList("https://news.example/2", null, now).first
        phone.sync(account)

        val laptop = Device(libraryWith(article("1"), article("2")))
        laptop.sync(account)
        assertTrue(laptop.library.article("$feed 1")!!.read)
        assertFalse(laptop.library.article("$feed 2")!!.read)
        val item = laptop.library.item("news.example/2")!!
        assertEquals(ReadingState.ToRead, item.readingList?.state)
        assertEquals("Title 2", item.title)
        assertEquals("2", item.articleId)
    }

    @Test
    fun `no article text is sent to the account`() {
        val account = FakeItems()
        val phone = Device(libraryWith(article("1", read = true), article("2")))
        phone.library = Saved.file(phone.library.addToReadingList("https://news.example/2", null, now).first, "news.example/2", "Research")
        assertTrue(phone.library.item("news.example/2")!!.description.contains("SECRET"))
        phone.sync(account)
        assertTrue(account.sent.isNotEmpty())
        for (payload in account.sent) {
            assertFalse(payload, payload.contains("SECRET"))
            assertFalse(payload, payload.contains("<p>"))
        }
    }

    @Test
    fun `changes to different parts of an item on two devices both stay`() {
        val account = FakeItems()
        val phone = Device(libraryWith(article("2")).addToReadingList("https://news.example/2", null, now).first)
        phone.sync(account)
        val laptop = Device(libraryWith(article("2")))
        laptop.sync(account)

        val key = "news.example/2"
        phone.library = phone.library.setLiked(key, true).setTags(key, listOf("work", "old"))
        laptop.library = Saved.file(laptop.library.setTags(key, listOf("news")), key, "Tools")
        phone.sync(account)
        // The laptop's save is refused (the phone saved first), merged and saved again.
        laptop.sync(account)
        phone.sync(account)

        for (device in listOf(phone, laptop)) {
            val item = device.library.item(key)!!
            assertTrue(item.liked)
            assertEquals(setOf("work", "old", "news"), item.tags.toSet())
            assertEquals("Tools", item.folder)
        }
        assertEquals(phone.library.items.map(SyncRows::itemData), laptop.library.items.map(SyncRows::itemData))
    }

    @Test
    fun `a removed tag on one side and an added one on the other both count`() {
        assertEquals(listOf("b", "c"), SyncRows.mergeTags(listOf("a", "b"), listOf("b"), listOf("a", "B", "c")))
        val h1 = Highlight("1", "q", createdAt = 1)
        val h2 = Highlight("2", "r", createdAt = 2)
        val h3 = Highlight("3", "s", createdAt = 3)
        // Local removed 1 and noted on 2; remote added 3.
        assertEquals(
            listOf(h2.copy(note = "n"), h3),
            SyncRows.mergeHighlights(listOf(h1, h2), listOf(h2.copy(note = "n")), listOf(h1, h2, h3)),
        )
        // Both changed 2: this device wins; remote edited 1 that local removed: the edit stays.
        assertEquals(
            listOf(h1.copy(note = "kept"), h2.copy(note = "local")),
            SyncRows.mergeHighlights(listOf(h1, h2), listOf(h2.copy(note = "local")), listOf(h1.copy(note = "kept"), h2.copy(note = "remote"))),
        )
    }

    @Test
    fun `when both changed the same thing the last device to sync wins, but an edit beats a removal`() {
        val account = FakeItems()
        val key = "news.example/2"
        val phone = Device(libraryWith(article("2")).addToReadingList("https://news.example/2", null, now).first)
        phone.sync(account)
        val laptop = Device(libraryWith(article("2")))
        laptop.sync(account)

        phone.library = phone.library.setReadingState(key, ReadingState.Archived)
        laptop.library = laptop.library.setReadingState(key, null)
        phone.sync(account)
        laptop.sync(account)
        phone.sync(account)
        // The phone archived it (an edit); the laptop took it off the list (a removal): the edit stays.
        assertEquals(ReadingState.Archived, laptop.library.item(key)?.readingList?.state)
        assertEquals(ReadingState.Archived, phone.library.item(key)?.readingList?.state)

        phone.library = phone.library.changeItem(key) { it.copy(note = "phone") }
        laptop.library = laptop.library.changeItem(key) { it.copy(note = "laptop") }
        phone.sync(account)
        laptop.sync(account)
        phone.sync(account)
        assertEquals("laptop", phone.library.item(key)?.note)
    }

    @Test
    fun `removing an item reaches the other device`() {
        val account = FakeItems()
        val key = "news.example/2"
        val phone = Device(libraryWith(article("2")).addToReadingList("https://news.example/2", null, now).first)
        phone.sync(account)
        val laptop = Device(libraryWith(article("2")))
        laptop.sync(account)
        phone.library = phone.library.setReadingState(key, null)
        assertNull(phone.library.item(key))
        phone.sync(account)
        laptop.sync(account)
        assertNull(laptop.library.item(key))
        assertEquals(null, account.rows["item:$key"]?.data)
    }

    @Test
    fun `an item made again after another device deleted it is saved`() {
        val account = FakeItems()
        val key = "news.example/2"
        val phone = Device(libraryWith(article("2")).addToReadingList("https://news.example/2", null, now).first)
        phone.sync(account)
        phone.library = phone.library.setReadingState(key, null)
        phone.sync(account)
        // A device that never saw it saves the same link.
        val laptop = Device(libraryWith(article("2")).addToReadingList("https://news.example/2", null, now).first)
        laptop.sync(account)
        assertEquals(ReadingState.ToRead, laptop.library.item(key)?.readingList?.state)
        assertEquals("toread", (account.rows["item:$key"]?.data?.get("readingList") as Map<*, *>)["state"])
        phone.sync(account)
        assertEquals(ReadingState.ToRead, phone.library.item(key)?.readingList?.state)
    }

    @Test
    fun `unread again on one device follows to the other`() {
        val account = FakeItems()
        val phone = Device(libraryWith(article("1", read = true)))
        phone.sync(account)
        val laptop = Device(libraryWith(article("1")))
        laptop.sync(account)
        assertTrue(laptop.library.article("$feed 1")!!.read)
        laptop.library = laptop.library.setRead("$feed 1", false)
        laptop.sync(account)
        phone.sync(account)
        assertFalse(phone.library.article("$feed 1")!!.read)
    }

    @Test
    fun `articles a device doesn't have yet arrive read, and their marks stay`() {
        val account = FakeItems()
        val phone = Device(libraryWith(article("1", read = true), article("2", read = true)))
        phone.sync(account)
        val laptop = Device(libraryWith(article("1")))
        laptop.sync(account)
        assertEquals(setOf("$feed 2"), laptop.library.readElsewhere.keys)
        // Nothing to save: the laptop's rows match the account's.
        val before = account.sent.size
        laptop.sync(account)
        assertEquals(before, account.sent.size)
        // Article 2 arrives with the next refresh, already read.
        val arrived =
            laptop.library.merge(
                feed,
                be.wimvandonck.reader.feed.ParsedFeed(
                    "News",
                    null,
                    listOf(be.wimvandonck.reader.feed.ParsedItem("2", "Title 2", "https://news.example/2", null, "", now - day)),
                ),
                now,
            )
        assertTrue(arrived.article("$feed 2")!!.read)
        assertTrue(arrived.readElsewhere.isEmpty())
    }

    @Test
    fun `old read marks leave the account without making anything unread`() {
        val account = FakeItems()
        val old = now - SyncRows.KEEP_READ_MS + day
        val phone = Device(libraryWith(article("1", read = true, date = old)))
        val laptop = Device(libraryWith(article("1", date = old)))
        phone.sync(account)
        laptop.sync(account)
        assertTrue(laptop.library.article("$feed 1")!!.read)

        // Two days later the mark is older than kept: the phone drops it from the account.
        val later = Device(phone.library).apply { state = phone.state }
        runBlocking { later.state = ItemSync.run(account, later.state, now + 2 * day) { change -> change(later.library).also { later.library = it } } }
        assertNull(account.rows["read:$feed 1"]?.data)
        assertTrue(later.library.article("$feed 1")!!.read)
        runBlocking { laptop.state = ItemSync.run(account, laptop.state, now + 2 * day) { change -> change(laptop.library).also { laptop.library = it } } }
        assertTrue(laptop.library.article("$feed 1")!!.read)
    }

    @Test
    fun `syncing again changes nothing, and big libraries go in pages`() {
        val account = FakeItems(pageSize = 3)
        val articles = (1..7).map { article("$it", read = true) }.toTypedArray()
        val phone = Device(libraryWith(*articles))
        phone.sync(account)
        assertEquals(7, account.rows.size)
        val sends = account.sent.size
        phone.sync(account)
        assertEquals(sends, account.sent.size)

        val laptop = Device(libraryWith(*articles.map { it.copy(read = false) }.toTypedArray()))
        laptop.sync(account)
        assertTrue(laptop.library.articles.all { it.read })
    }

    @Test
    fun `rows of a kind this version doesn't know are left alone`() {
        val account = FakeItems()
        account.rows["future:x"] = ItemRow("future:x", 1, mapOf("a" to 1L))
        account.rows["item:bad"] = ItemRow("item:bad", 1, mapOf("url" to "javascript:alert(1)"))
        account.version = 1
        val phone = Device(libraryWith())
        phone.sync(account)
        assertTrue(phone.library.items.isEmpty())
        assertTrue(account.sent.isEmpty())
    }

    @Test
    fun `the account's answers are read`() {
        val page = AccountClient.itemsPageFrom("""{"version":4,"rows":[{"key":"item:a","version":3,"data":{"url":"https://a"}},{"key":"read:f 1","version":4,"data":null},{"bad":1}],"more":true}""")
        assertEquals(4L, page.version)
        assertTrue(page.more)
        assertEquals(listOf(ItemRow("item:a", 3, mapOf("url" to "https://a")), ItemRow("read:f 1", 4, null)), page.rows)
        val written = AccountClient.itemsWrittenFrom("""{"version":5,"saved":[{"key":"item:a","version":5}],"conflicts":[{"key":"item:b","version":2,"data":null}]}""")
        assertEquals(ItemsWritten(5, mapOf("item:a" to 5L), listOf(ItemRow("item:b", 2, null))), written)
    }
}
