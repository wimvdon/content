package be.wimvandonck.reader.instapaper

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private class MemoryStorage(
    var data: LocalSavedData = LocalSavedData(),
) : LocalSavedStorage {
    override suspend fun read(): LocalSavedData = data

    override suspend fun update(change: (LocalSavedData) -> LocalSavedData): LocalSavedData = change(data).also { data = it }
}

class LocalSavedTest {
    private val storage = MemoryStorage()
    private var clock = 1_000_000L
    private val saved = LocalSaved(storage) { clock }

    private fun save(
        url: String,
        title: String? = null,
    ): InstapaperBookmark =
        runBlocking {
            clock += 1000
            saved.save(url, title)!!
        }

    private fun list(view: InstapaperView) = runBlocking { saved.list(view, 0, 50) }

    @Test
    fun `saved articles show newest first in Saved, and saving one again brings it back to the top`() =
        runBlocking {
            val a = save("https://a.example/1", "One")
            val b = save("https://a.example/2", "Two")
            assertEquals(listOf(b.id, a.id), list(InstapaperView.Home).bookmarks.map { it.id })
            saved.move(a.id, "archive")
            val again = save("https://a.example/1", null)
            assertEquals(a.id, again.id)
            assertEquals("One", again.title)
            assertFalse(again.archived)
            assertEquals(listOf(a.id, b.id), list(InstapaperView.Home).bookmarks.map { it.id })
            assertEquals(2, list(InstapaperView.Home).total)
        }

    @Test
    fun `a saved article without a title shows its address`() {
        assertEquals("https://a.example/x", save("https://a.example/x").title)
    }

    @Test
    fun `like and archive move articles between the lists`() =
        runBlocking {
            val a = save("https://a.example/1")
            assertTrue(saved.like(a.id, true))
            saved.move(a.id, "archive")
            assertEquals(emptyList<Long>(), list(InstapaperView.Home).bookmarks.map { it.id })
            assertEquals(listOf(a.id), list(InstapaperView.Archive).bookmarks.map { it.id })
            assertEquals(listOf(a.id), list(InstapaperView.Liked).bookmarks.map { it.id })
            saved.move(a.id, "home")
            assertEquals(listOf(a.id), list(InstapaperView.Home).bookmarks.map { it.id })
            saved.delete(a.id)
            assertEquals(0, list(InstapaperView.Liked).total)
        }

    @Test
    fun `there are no folders`() =
        runBlocking {
            val a = save("https://a.example/1")
            assertEquals(emptyList<InstapaperFolder>(), saved.folders())
            for (call in listOf<suspend () -> Unit>({ saved.move(a.id, "12") }, { saved.createFolder("Work") }, { saved.deleteFolder(1) })) {
                try {
                    call()
                    fail("folders should be refused")
                } catch (e: InstapaperException) {
                    assertEquals(InstapaperProblem.Invalid, e.problem)
                }
            }
        }

    @Test
    fun `tags are shared by name, listed in order, and go away when no article has them`() =
        runBlocking {
            val a = save("https://a.example/1")
            val b = save("https://a.example/2")
            val tagsA = saved.setTags(a.id, listOf("Travel", "cooking"), emptyList())
            assertEquals(listOf("Travel", "cooking"), tagsA.map { it.name })
            val tagsB = saved.setTags(b.id, listOf("Cooking"), emptyList())
            // Same tag, whatever the capitals.
            assertEquals(tagsA[1].id, tagsB.single().id)
            assertEquals(listOf("cooking", "Travel"), saved.tags().map { it.name })
            assertEquals(listOf(b.id, a.id), list(InstapaperView.WithTag("cooking")).bookmarks.map { it.id })
            saved.setTags(a.id, emptyList(), listOf(tagsA[0].id))
            assertEquals(listOf("cooking"), saved.tags().map { it.name })
            saved.delete(b.id)
            saved.delete(a.id)
            assertEquals(emptyList<InstapaperTag>(), saved.tags())
        }

    @Test
    fun `renaming a tag renames it on every article, and joins it with a tag of that name`() =
        runBlocking {
            val a = save("https://a.example/1")
            val b = save("https://a.example/2")
            val travel = saved.setTags(a.id, listOf("Travel"), emptyList()).single()
            val trips = saved.setTags(b.id, listOf("Trips"), emptyList()).single()
            assertEquals("Journeys", saved.renameTag(travel.id, "Journeys").name)
            assertEquals(listOf("Journeys"), list(InstapaperView.WithTag("Journeys")).bookmarks.flatMap { it.tags }.map { it.name })
            val joined = saved.renameTag(trips.id, "journeys")
            assertEquals(travel.id, joined.id)
            assertEquals(listOf("Journeys"), saved.tags().map { it.name })
            assertEquals(2, list(InstapaperView.WithTag("Journeys")).total)
        }

    @Test
    fun `everything survives storage, and unreadable text starts empty`() {
        save("https://a.example/1", "One")
        val b = save("https://a.example/2", "Two \"quoted\"")
        runBlocking {
            saved.setTags(b.id, listOf("Travel"), emptyList())
            saved.like(b.id, true)
            saved.move(b.id, "archive")
        }
        val text = LocalSavedCodec.encode(storage.data)
        assertEquals(storage.data, LocalSavedCodec.decode(text))
        assertEquals(LocalSavedData(), LocalSavedCodec.decode(null))
        assertEquals(LocalSavedData(), LocalSavedCodec.decode("not json"))
        // Ids never repeat, even when the counter was lost.
        val withoutCounter = LocalSavedCodec.decode(text.replace("\"nextId\":", "\"lost\":"))
        assertTrue(withoutCounter.nextId > withoutCounter.bookmarks.maxOf { it.id })
        assertTrue(withoutCounter.nextId > withoutCounter.tags.maxOf { it.id })
    }

    @Test
    fun `the Saved screen works on top of it like on the account, without folders`() {
        // Unconfined runs each step at once, so the answers are in the state straight away.
        val model = InstapaperModel(saved, CoroutineScope(Dispatchers.Unconfined), onSignedOut = {}, account = false)
        model.start()
        assertFalse(model.state.value.account)
        assertTrue(runBlocking { model.save("https://a.example/1", "One") })
        val justSaved = model.state.value.justSaved
        assertNotNull(justSaved)
        model.toggleLike(justSaved!!)
        model.toggleArchive(model.state.value.justSaved!!)
        val stored = storage.data.bookmarks.single()
        assertTrue(stored.liked)
        assertTrue(stored.archived)
        assertEquals(emptyList<InstapaperFolder>(), model.state.value.folders)
        assertEquals(null, model.state.value.problem)
    }
}
