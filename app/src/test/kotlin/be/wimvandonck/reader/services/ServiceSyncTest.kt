package be.wimvandonck.reader.services

import be.wimvandonck.reader.data.Library
import be.wimvandonck.reader.data.ServiceImport
import be.wimvandonck.reader.data.ServiceScope
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

        override suspend fun folders(): List<String> = entries.values.mapNotNull { it.folder }.distinct()

        override var origins: Map<String, String> = emptyMap()
    }

    private var library = Library()
    private var state = ServiceState()
    private val service = FakeService()

    /** Connecting imports everything once, as ServiceManager does; then each sync keeps the scope in step. */
    private fun sync(
        scope: ServiceScope = ServiceScope.Everything,
        editedAt: Map<String, Long> = emptyMap(),
    ): ServiceProblem? =
        runBlocking {
            if (state.cursor == null && state.rows.isEmpty()) {
                val imported = ServiceSync.import(service, state, ServiceImport(0, 0)) { change -> change(library).also { library = it } }
                state = imported.state
                if (imported.problem != null) return@runBlocking imported.problem
            }
            val result = ServiceSync.run(service, state, scope, editedAt = editedAt) { change -> change(library).also { library = it } }
            state = result.state
            result.problem
        }

    /** Syncs another service with its own state and scope, as ServiceManager does for each connected one. */
    private fun sync(
        other: FakeService,
        otherState: ServiceState,
        scope: ServiceScope = ServiceScope.Everything,
        elsewhere: Set<String> = emptySet(),
    ): ServiceState =
        runBlocking {
            var start = otherState
            if (start.cursor == null && start.rows.isEmpty()) start = ServiceSync.import(other, start, ServiceImport(0, 0)) { change -> change(library).also { library = it } }.state
            ServiceSync.run(other, start, scope, elsewhere = elsewhere) { change -> change(library).also { library = it } }.state
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
    fun `connecting imports the whole account, with tags`() {
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
        assertEquals(listOf("changes", "changes"), service.calls)
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
    fun `when both changed the same field the later change wins`() {
        service.put(remote("1", "https://a.example/1"))
        sync()
        // Liked here at 100; there unliked... and changed at 50 (earlier): Reader's stays.
        library = library.setLiked("a.example/1", true)
        service.put(service.entries["1"]!!.copy(liked = false, title = "New", tags = listOf("x"), changedAt = 50))
        assertNull(sync(editedAt = mapOf("a.example/1" to 100L)))
        assertTrue(library.item("a.example/1")!!.liked)
        assertEquals("New", library.item("a.example/1")!!.title)
        // Filed here at 200, filed elsewhere there at 300 (later): the service's wins.
        library = Saved.file(library, "a.example/1", "Here")
        service.put(service.entries["1"]!!.copy(folder = "There", changedAt = 300))
        assertNull(sync(editedAt = mapOf("a.example/1" to 200L)))
        assertEquals("There", Saved.folderOf(library.item("a.example/1")!!))
        assertEquals("There", service.entries["1"]!!.folder)
        // Without a time from the service, Reader's change stays.
        library = Saved.file(library, "a.example/1", "Mine")
        service.put(service.entries["1"]!!.copy(folder = "Theirs", changedAt = 0))
        assertNull(sync(editedAt = mapOf("a.example/1" to 400L)))
        assertEquals("Mine", Saved.folderOf(library.item("a.example/1")!!))
        assertEquals("Mine", service.entries["1"]!!.folder)
    }

    @Test
    fun `what came in is not sent back, and nothing is sent just because the sides differ`() {
        service.put(remote("1", "https://a.example/1", tags = listOf("a")))
        sync()
        service.put(service.entries["1"]!!.copy(liked = true))
        service.calls.clear()
        sync()
        assertTrue(library.item("a.example/1")!!.liked)
        assertEquals(listOf("changes"), service.calls)
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
        // The import and the sync each ask for the changes, then one article gets through.
        service.failAfter = 3
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
    fun `a service that lists everything deletes what is missing from the list`() {
        val raindrop = FakeService("raindrop", features = setOf(ServiceFeature.Notes), complete = true)
        raindrop.put(remote("1", "https://r.example/1"))
        raindrop.put(remote("2", "https://r.example/2"))
        var rs = sync(raindrop, ServiceState())
        assertEquals(2, library.items.count { it.readingList != null })
        raindrop.entries.remove("2")
        rs = sync(raindrop, rs)
        assertNull(library.item("r.example/2")?.readingList)
        assertEquals(setOf("1"), rs.rows.values.map { it.remoteId }.toSet())
        assertEquals(1, raindrop.entries.size)
    }

    @Test
    fun `notes go to services that keep them, and theirs come in`() {
        val raindrop = FakeService("raindrop", features = setOf(ServiceFeature.Notes))
        raindrop.put(remote("1", "https://r.example/1").copy(note = "From there"))
        var rs = sync(raindrop, ServiceState())
        assertEquals("From there", library.item("r.example/1")?.note)
        library = library.changeItem("r.example/1") { it.copy(note = "Changed here") }
        rs = sync(raindrop, rs)
        assertEquals("Changed here", raindrop.entries["1"]!!.note)
        // A service without notes never gets one.
        library = library.addToReadingList("https://b.example/1", "1", 10).first.changeItem("b.example/1") { it.copy(note = "Mine") }
        sync()
        assertNull(service.entries.values.single { it.url == "https://b.example/1" }.note)
        assertTrue(rs.rows.isNotEmpty())
        assertEquals("Mine", library.item("b.example/1")?.note)
    }

    @Test
    fun `a save reaches every service whose scope covers its folder`() {
        val raindrop = FakeService("raindrop", features = setOf(ServiceFeature.Notes))
        val work = ServiceScope(setOf("Work"))
        library = library.addToReadingList("https://b.example/1", "1", 10).first
        sync()
        var rs = sync(raindrop, ServiceState(), work)
        // To read is only in the first one's scope.
        assertEquals(1, service.entries.size)
        assertTrue(raindrop.entries.isEmpty())
        // Filed in Work: it reaches Raindrop too, and the two stay in step through Reader.
        library = Saved.file(library, "b.example/1", "Work")
        sync()
        rs = sync(raindrop, rs, work)
        assertEquals("Work", raindrop.entries.values.single().folder)
        raindrop.put(raindrop.entries.values.single().copy(liked = true))
        rs = sync(raindrop, rs, work)
        sync()
        assertTrue(library.item("b.example/1")!!.liked)
        assertTrue(service.entries.values.single().liked)
    }

    @Test
    fun `a scope losing a folder archives its articles there, and gaining it brings them back`() {
        val work = ServiceScope(setOf(ServiceScope.TO_READ, "Work"))
        library = Saved.file(library.addToReadingList("https://b.example/1", "1", 10).first, "b.example/1", "Work")
        sync(work)
        val id = state.rows["b.example/1"]!!.remoteId
        assertEquals("Work", service.entries[id]!!.folder)
        // Work leaves the scope: archived there, never deleted, and it stays in Reader as it is.
        val toRead = work.with("Work", on = false)
        assertNull(sync(toRead))
        assertTrue(service.entries[id]!!.archived)
        assertFalse(state.rows["b.example/1"]!!.linked)
        assertEquals("Work", Saved.folderOf(library.item("b.example/1")!!))
        // Out of step: changes here no longer go there.
        library = library.setLiked("b.example/1", true)
        service.calls.clear()
        sync(toRead)
        assertEquals(listOf("changes"), service.calls)
        // Filing it out of a synced folder here does the same: To read is in scope, Later isn't.
        library = Saved.file(library.addToReadingList("https://b.example/2", "2", 11).first, "b.example/2", null)
        sync(toRead)
        val two = state.rows["b.example/2"]!!.remoteId
        library = Saved.file(library, "b.example/2", "Later")
        sync(toRead)
        assertTrue(service.entries[two]!!.archived)
        assertTrue(service.entries.containsKey(two))
        // Work back in the scope: in step again, out of the archive there.
        assertNull(sync(work))
        assertTrue(state.rows["b.example/1"]!!.linked)
        assertFalse(service.entries[id]!!.archived)
        assertTrue(service.entries[id]!!.liked)
    }

    @Test
    fun `an import lands each article in its place, and leaves the rest there until it changes`() =
        runBlocking {
            service.put(remote("1", "https://a.example/1"))
            service.put(remote("2", "https://a.example/2", folder = "Research", liked = true, tags = listOf("x")))
            service.put(remote("3", "https://a.example/3", archived = true))
            service.put(remote("4", "https://a.example/4", folder = "Brussels"))
            val result = ServiceSync.import(service, ServiceState(), ServiceImport(0, 0, folders = setOf("Research"), archive = true)) { change -> change(library).also { library = it } }
            state = result.state
            assertEquals(2, result.imported)
            val two = library.item("a.example/2")!!
            assertEquals("Research", Saved.folderOf(two))
            assertTrue(two.liked)
            assertEquals(listOf("x"), two.tags)
            assertTrue(library.item("a.example/3")!!.archived)
            assertNull(library.item("a.example/1"))
            assertNull(library.item("a.example/4"))
            // The next sync takes nothing else in, and removes nothing there.
            assertNull(sync())
            assertNull(library.item("a.example/1"))
            assertEquals(4, service.entries.size)
            // Changed there since: now it comes in.
            service.put(service.entries["4"]!!.copy(liked = true))
            assertNull(sync())
            assertEquals("Brussels", Saved.folderOf(library.item("a.example/4")!!))
            // Something new there comes in too.
            service.put(remote("5", "https://a.example/5"))
            assertNull(sync())
            assertTrue(library.item("a.example/5")!!.onReadingList)
        }

    @Test
    fun `after another device's import, what Reader doesn't have yet stays there`() {
        service.put(remote("1", "https://a.example/1"))
        library = library.addToReadingList("https://a.example/2", "2", 10).first
        service.put(remote("2", "https://a.example/2", liked = true))
        // A first look with no rows here, as on a second device: no import runs here.
        val result = runBlocking { ServiceSync.run(service, ServiceState()) { change -> change(library).also { library = it } } }
        assertNull(result.problem)
        assertNull(library.item("a.example/1"))
        // The link Reader has joins the service's.
        assertTrue(result.state.rows["a.example/2"]!!.linked)
        assertFalse(result.state.rows["a.example/1"]!!.linked)
    }

    @Test
    fun `deleted at one service, an article another keeps in step stays in Saved`() {
        service.put(remote("1", "https://a.example/1"))
        sync()
        service.delete("1")
        runBlocking {
            state = ServiceSync.run(service, state, elsewhere = setOf("a.example/1")) { change -> change(library).also { library = it } }.state
        }
        assertTrue(library.item("a.example/1")!!.onReadingList)
    }

    @Test
    fun `the origin of each folder is kept with the state`() {
        service.put(remote("1", "https://a.example/1"))
        service.origins = mapOf("Research / Media" to "42")
        sync()
        assertEquals(mapOf("Research / Media" to "42"), state.origins)
        assertEquals(mapOf("Research / Media" to "42"), ServiceRows.decodeOrigins(ServiceRows.encodeOrigins(state.origins)))
        // A new connection starts from what was stored.
        val again = FakeService()
        runBlocking { ServiceSync.run(again, state) { change -> change(library).also { library = it } } }
        assertEquals("42", again.origins["Research / Media"])
    }

    @Test
    fun `entries are stored and read back`() {
        val entry = RemoteEntry("7", "https://a/1", "T", addedAt = 5, archived = true, liked = true, tags = listOf("x"), progress = 0.25f, note = "A note", extra = "Folder", changedAt = 9, linked = false)
        assertEquals(entry, ServiceRows.decode(ServiceRows.encode(entry)))
        assertNull(ServiceRows.decode("nope"))
        // Rows from before #145 were all in step.
        assertTrue(ServiceRows.decode("""{"id":"1","url":"https://a/1"}""")!!.linked)
    }

    @Test
    fun `an article another device sent, kept there without tracking parameters, joins the saved one`() {
        library = library.addToReadingList("https://a.example/1?utm_source=rss&id=7", "One", 10).first
        service.put(remote("9", "https://a.example/1?id=7"))
        assertNull(sync())
        assertEquals(1, library.items.count { it.readingList != null })
        assertEquals("9", state.rows["a.example/1?utm_source=rss&id=7"]?.remoteId)
        assertEquals(1, service.entries.size)
    }

    @Test
    fun `after connecting too, a new article there finds the saved one by its link without tracking parameters`() {
        state = ServiceState(cursor = "0")
        library = library.addToReadingList("https://www.b.example/post/?utm_medium=feed", "Post", 10).first
        service.put(remote("3", "https://b.example/post"))
        assertNull(sync())
        assertEquals(1, library.items.count { it.readingList != null })
        assertTrue(state.rows.values.single { it.remoteId == "3" }.linked)
        assertEquals(1, service.entries.size)
    }

    @Test
    fun `tracking parameters don't make another article, other parameters do`() {
        val loose = be.wimvandonck.reader.data.ItemKeys::loose
        assertEquals(loose("https://a.example/x?id=2"), loose("http://www.a.example/x/?utm_source=rss&id=2&fbclid=z"))
        assertEquals(loose("https://a.example/x"), loose("https://a.example/x?utm_campaign=y"))
        assertTrue(loose("https://a.example/x?id=2") != loose("https://a.example/x?id=3"))
    }
}
