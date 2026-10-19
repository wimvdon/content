package be.wimvandonck.reader.services

import be.wimvandonck.reader.data.Library
import be.wimvandonck.reader.data.ReadingState
import be.wimvandonck.reader.data.Saved
import be.wimvandonck.reader.data.SavedTab
import be.wimvandonck.reader.data.addToReadingList
import be.wimvandonck.reader.data.changeItem
import be.wimvandonck.reader.data.item
import be.wimvandonck.reader.data.setLiked
import be.wimvandonck.reader.data.setReadingState
import be.wimvandonck.reader.data.setTags
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceSyncTest {
    /** A reading-list service in memory: what it has, a cursor per change, and a call that can fail. */
    private class FakeService(
        override val id: String = "fake",
        override val features: Set<ServiceFeature> = setOf(ServiceFeature.Progress),
        /** Lists everything each time (as Raindrop), so what is missing was deleted there. */
        val complete: Boolean = false,
    ) : ReadingListService {
        val entries = LinkedHashMap<String, RemoteEntry>()
        private val changedAt = HashMap<String, Int>()
        private val deletedAt = HashMap<String, Int>()
        var clock = 1
        var nextId = 100
        var failAfter = Int.MAX_VALUE
        val calls = mutableListOf<String>()

        private fun call(name: String) {
            if (failAfter-- <= 0) throw ServiceException(ServiceProblem.RateLimited)
            calls += name
        }

        /** Another app changes the account. */
        fun put(entry: RemoteEntry) {
            entries[entry.remoteId] = entry
            changedAt[entry.remoteId] = ++clock
        }

        fun delete(remoteId: String) {
            entries.remove(remoteId)
            deletedAt[remoteId] = ++clock
        }

        override suspend fun changes(
            cursor: String?,
            full: Boolean,
        ): RemoteChanges {
            call("changes")
            if (complete) return RemoteChanges(entries.values.toList(), emptyList(), clock.toString(), complete = true)
            val since = cursor?.toInt() ?: 0
            return RemoteChanges(
                entries.values.filter { (changedAt[it.remoteId] ?: 0) > since },
                deletedAt.filterValues { it > since }.keys.toList(),
                clock.toString(),
            )
        }

        override suspend fun add(
            url: String,
            title: String,
            tags: List<String>,
            archived: Boolean,
            folder: String?,
            note: String,
        ): RemoteEntry {
            call("add $url")
            val existing = entries.values.firstOrNull { it.url == url }
            val entry =
                (existing ?: RemoteEntry("${nextId++}", url, title, addedAt = 5, archived = false, liked = false, tags = emptyList()))
                    .copy(title = title, tags = tags, archived = archived, folder = folder, note = note.takeIf { ServiceFeature.Notes in features })
            put(entry)
            return entry
        }

        override suspend fun update(
            have: RemoteEntry,
            want: RemoteEntry,
        ): RemoteEntry {
            call("update ${have.remoteId}")
            val entry = want.copy(remoteId = have.remoteId)
            put(entry)
            return entry
        }

        override suspend fun remove(remoteId: String) {
            call("remove $remoteId")
            delete(remoteId)
        }

        override suspend fun text(remoteId: String): String? = null
    }

    private var library = Library()
    private var state = ServiceState()
    private val service = FakeService()

    private fun sync(): ServiceProblem? =
        runBlocking {
            val result = ServiceSync.run(service, state) { change -> change(library).also { library = it } }
            state = result.state
            result.problem
        }

    /** Syncs another service with its own state, as ServiceManager does for each connected one. */
    private fun sync(
        other: FakeService,
        otherState: ServiceState,
        isDefault: Boolean,
    ): ServiceState =
        runBlocking {
            ServiceSync.run(other, otherState, isDefault) { change -> change(library).also { library = it } }.state
        }

    private fun remote(
        id: String,
        url: String,
        archived: Boolean = false,
        liked: Boolean = false,
        tags: List<String> = emptyList(),
        folder: String? = null,
    ) = RemoteEntry(id, url, "Title $id", "Desc", null, addedAt = 1_000, archived = archived, liked = liked, tags = tags, folder = folder)

    @Test
    fun `folders come in, filing here and archiving go out, and the folder is one place`() {
        service.put(remote("1", "https://a.example/1", folder = "Work"))
        assertNull(sync())
        assertEquals("Work", library.item("a.example/1")?.folder)
        assertTrue(Saved.isIn(library.item("a.example/1")!!, SavedTab.Folder("work")))
        // Filed elsewhere here: sent once.
        library = Saved.file(library, "a.example/1", "Later")
        assertNull(sync())
        assertEquals("Later", service.entries["1"]!!.folder)
        // Archiving takes it out of its folder, here and there.
        library = library.setReadingState("a.example/1", ReadingState.Archived)
        assertNull(library.item("a.example/1")!!.folder)
        assertNull(sync())
        assertTrue(service.entries["1"]!!.archived)
        assertNull(service.entries["1"]!!.folder)
        // Filed at the service while unchanged here: it comes back to read, in that folder.
        service.put(service.entries["1"]!!.copy(archived = false, folder = "Work"))
        assertNull(sync())
        assertEquals("Work", Saved.folderOf(library.item("a.example/1")!!))
    }

    @Test
    fun `the whole account comes in on the first sync, with tags`() {
        service.put(remote("1", "https://a.example/1", tags = listOf("Work")))
        service.put(remote("2", "https://a.example/2", archived = true, liked = true))
        assertNull(sync())
        assertEquals(2, library.items.size)
        assertEquals(ReadingState.ToRead, library.item("a.example/1")?.readingList?.state)
        assertEquals(listOf("Work"), library.item("a.example/1")?.tags)
        val two = library.item("a.example/2")!!
        assertTrue(two.archived)
        assertTrue(two.liked)
        assertEquals(1_000L, two.readingList?.addedAt)
        assertEquals("Desc", two.description)
        // Nothing to send back.
        assertEquals(listOf("changes"), service.calls)
    }

    @Test
    fun `adding, archiving, liking and tagging here reach the service, and only once`() {
        library = library.addToReadingList("https://b.example/x", "X", 10).first
        sync()
        val id = state.rows["b.example/x"]!!.remoteId
        assertEquals("https://b.example/x", service.entries[id]?.url)

        library = library.setReadingState("b.example/x", ReadingState.Archived).setLiked("b.example/x", true).setTags("b.example/x", listOf("news"))
        sync()
        val entry = service.entries[id]!!
        assertTrue(entry.archived)
        assertTrue(entry.liked)
        assertEquals(listOf("news"), entry.tags)

        service.calls.clear()
        sync()
        assertEquals(listOf("changes"), service.calls)
    }

    @Test
    fun `taking an item out of Saved removes it at the service`() {
        library = Saved.file(library.addToReadingList("https://b.example/x", "X", 10).first, "b.example/x", "Tools")
        sync()
        val id = state.rows["b.example/x"]!!.remoteId
        assertEquals("Tools", service.entries[id]!!.folder)
        library = Saved.remove(library, "b.example/x")
        sync()
        assertNull(service.entries[id])
        assertFalse(state.rows.containsKey("b.example/x"))
    }

    @Test
    fun `a change at the service and one here to other fields both stay`() {
        service.put(remote("1", "https://a.example/1", tags = listOf("a")))
        sync()
        // Here: liked, tag b added. There: archived, tag a removed, tag c added.
        library = library.setLiked("a.example/1", true).setTags("a.example/1", listOf("a", "b"))
        service.put(service.entries["1"]!!.copy(archived = true, tags = listOf("c")))
        sync()
        val item = library.item("a.example/1")!!
        assertTrue(item.liked)
        assertTrue(item.archived)
        assertEquals(setOf("b", "c"), item.tags.toSet())
        val entry = service.entries["1"]!!
        assertTrue(entry.liked)
        assertTrue(entry.archived)
        assertEquals(setOf("b", "c"), entry.tags.toSet())
    }

    @Test
    fun `when both changed the same field Reader wins`() {
        service.put(remote("1", "https://a.example/1"))
        sync()
        library = library.setReadingState("a.example/1", ReadingState.Archived)
        service.put(service.entries["1"]!!.copy(archived = false, title = "New"))
        service.put(service.entries["1"]!!.copy(archived = false))
        sync()
        assertTrue(library.item("a.example/1")!!.archived)
        assertTrue(service.entries["1"]!!.archived)
        assertEquals("New", library.item("a.example/1")!!.title)
    }

    @Test
    fun `deleted at the service leaves the reading list, unless it was changed here`() {
        service.put(remote("1", "https://a.example/1"))
        service.put(remote("2", "https://a.example/2"))
        sync()
        library = library.setLiked("a.example/2", true)
        service.delete("1")
        service.delete("2")
        sync()
        assertNull(library.item("a.example/1"))
        // Changed here: it stays, and is saved again.
        assertTrue(library.item("a.example/2")!!.liked)
        assertTrue(service.entries.values.any { it.url == "https://a.example/2" && it.liked })
    }

    @Test
    fun `taken off here and changed at the service, the edit stays`() {
        service.put(remote("1", "https://a.example/1"))
        sync()
        library = library.setReadingState("a.example/1", null)
        service.put(service.entries["1"]!!.copy(liked = true))
        sync()
        assertTrue(library.item("a.example/1")!!.liked)
        assertTrue(library.item("a.example/1")!!.onReadingList)
        assertTrue(service.entries.containsKey("1"))
    }

    @Test
    fun `a saved link the service already has joins it, and a feed article becomes the same item`() {
        service.put(remote("1", "https://www.a.example/1/"))
        library = library.addToReadingList("http://a.example/1", null, 10).first
        sync()
        assertEquals(1, library.items.size)
        assertEquals("1", state.rows["a.example/1"]?.remoteId)
        assertEquals(1, service.entries.size)
    }

    @Test
    fun `a failure keeps Reader's data and what got through, and the rest goes next time`() {
        library = library.addToReadingList("https://b.example/1", "1", 10).first.addToReadingList("https://b.example/2", "2", 11).first
        service.failAfter = 2
        assertEquals(ServiceProblem.RateLimited, sync())
        assertEquals(2, library.items.size)
        assertEquals(1, state.rows.size)
        service.failAfter = Int.MAX_VALUE
        assertNull(sync())
        assertEquals(2, state.rows.size)
        assertEquals(2, service.entries.size)
    }

    @Test
    fun `a reading list that lost most of its articles at once removes nothing at the service`() {
        repeat(30) { service.put(remote("$it", "https://a.example/$it")) }
        sync()
        assertEquals(30, library.items.size)
        library = Library()
        assertEquals(ServiceProblem.Refused, sync())
        assertEquals(30, service.entries.size)
        // A few at a time is fine.
        library = Library()
        state = ServiceState()
        sync()
        repeat(3) { library = library.setReadingState("a.example/$it", null) }
        assertNull(sync())
        assertEquals(27, service.entries.size)
    }

    @Test
    fun `progress goes to services that keep it`() {
        library = library.addToReadingList("https://b.example/1", "1", 10).first
        sync()
        library = library.copy(items = library.items.map { it.copy(progress = 0.5f) })
        sync()
        assertEquals(0.5f, service.entries.values.single().progress, 0.001f)
    }

    @Test
    fun `each service keeps its own items, and new saves go only to the default one`() {
        val raindrop = FakeService("raindrop")
        service.put(remote("1", "https://a.example/1"))
        raindrop.put(remote("9", "https://r.example/9"))
        sync()
        var raindropState = sync(raindrop, ServiceState(), isDefault = false)
        assertEquals("fake", library.item("a.example/1")?.service)
        assertEquals("raindrop", library.item("r.example/9")?.service)
        // Nothing is copied: each has only its own.
        sync()
        raindropState = sync(raindrop, raindropState, isDefault = false)
        assertEquals(setOf("https://a.example/1"), service.entries.values.map { it.url }.toSet())
        assertEquals(setOf("https://r.example/9"), raindrop.entries.values.map { it.url }.toSet())
        // A new save goes to the default service only, and is its own from then on.
        library = library.addToReadingList("https://b.example/new", "New", 10).first
        sync(raindrop, raindropState, isDefault = false)
        assertEquals(1, raindrop.entries.size)
        sync()
        assertEquals("fake", library.item("b.example/new")?.service)
        assertTrue(service.entries.values.any { it.url == "https://b.example/new" })
        // A change to Raindrop's item goes to Raindrop.
        library = library.setLiked("r.example/9", true)
        sync(raindrop, raindropState, isDefault = false)
        sync()
        assertTrue(raindrop.entries["9"]!!.liked)
        assertFalse(service.entries.values.any { it.url == "https://r.example/9" })
    }

    @Test
    fun `items synced before they knew their service become that service's own`() {
        library = library.addToReadingList("https://b.example/1", "1", 10).first
        sync()
        // As stored by an older version: in the service's rows, without a service.
        library = library.copy(items = library.items.map { it.copy(service = null) })
        val raindrop = FakeService("raindrop")
        sync()
        assertEquals("fake", library.item("b.example/1")?.service)
        // So making the other service the default doesn't copy it there.
        sync(raindrop, ServiceState(), isDefault = true)
        assertTrue(raindrop.entries.isEmpty())
    }

    @Test
    fun `a service that lists everything deletes what is missing from the list`() {
        val raindrop = FakeService("raindrop", features = setOf(ServiceFeature.Notes), complete = true)
        raindrop.put(remote("1", "https://r.example/1"))
        raindrop.put(remote("2", "https://r.example/2"))
        var rs = sync(raindrop, ServiceState(), isDefault = true)
        assertEquals(2, library.items.count { it.readingList != null })
        raindrop.entries.remove("2")
        rs = sync(raindrop, rs, isDefault = true)
        assertNull(library.item("r.example/2")?.readingList)
        assertEquals(setOf("1"), rs.rows.values.map { it.remoteId }.toSet())
        assertEquals(1, raindrop.entries.size)
    }

    @Test
    fun `notes go to services that keep them, and theirs come in`() {
        val raindrop = FakeService("raindrop", features = setOf(ServiceFeature.Notes))
        raindrop.put(remote("1", "https://r.example/1").copy(note = "From there"))
        var rs = sync(raindrop, ServiceState(), isDefault = true)
        assertEquals("From there", library.item("r.example/1")?.note)
        library = library.changeItem("r.example/1") { it.copy(note = "Changed here") }
        rs = sync(raindrop, rs, isDefault = true)
        assertEquals("Changed here", raindrop.entries["1"]!!.note)
        // A service without notes never gets one.
        library = library.addToReadingList("https://b.example/1", "1", 10).first.changeItem("b.example/1") { it.copy(note = "Mine") }
        sync()
        assertNull(service.entries.values.single { it.url == "https://b.example/1" }.note)
        assertEquals("Mine", library.item("b.example/1")?.note)
    }

    @Test
    fun `entries are stored and read back`() {
        val entry = RemoteEntry("7", "https://a/1", "T", addedAt = 5, archived = true, liked = true, tags = listOf("x"), progress = 0.25f, note = "A note", extra = "Folder")
        assertEquals(entry, ServiceRows.decode(ServiceRows.encode(entry)))
        assertNull(ServiceRows.decode("nope"))
    }
}
