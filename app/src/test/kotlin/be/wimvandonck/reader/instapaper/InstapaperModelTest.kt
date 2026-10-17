package be.wimvandonck.reader.instapaper

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun bookmark(
    id: Long,
    liked: Boolean = false,
    archived: Boolean = false,
    folderId: Long? = null,
    tags: List<InstapaperTag> = emptyList(),
) = InstapaperBookmark(id, "https://a.example/$id", "Title $id", "", 0L, liked, archived, folderId, tags)

private class FakeService : InstapaperService {
    val calls = mutableListOf<String>()
    val lists = mutableMapOf<InstapaperView, List<InstapaperBookmark>>()
    var folders = listOf(InstapaperFolder(1, "A", 1), InstapaperFolder(2, "B", 2))
    var tags = listOf(InstapaperTag(1, "Cooking"))
    var tagsAfter: List<InstapaperTag> = emptyList()
    var failure: InstapaperException? = null

    private fun check() {
        failure?.let { throw it }
    }

    override suspend fun list(view: InstapaperView, offset: Int, limit: Int): InstapaperPage {
        calls += "list $view $offset"
        check()
        val all = lists[view].orEmpty()
        return InstapaperPage(all.drop(offset).take(limit), all.size)
    }

    override suspend fun save(url: String, title: String?): InstapaperBookmark? {
        calls += "save $url"
        check()
        return bookmark(42)
    }

    override suspend fun move(id: Long, section: String) {
        calls += "move $id $section"
        check()
    }

    override suspend fun like(id: Long, liked: Boolean): Boolean {
        calls += "like $id $liked"
        check()
        return liked
    }

    override suspend fun delete(id: Long) {
        calls += "delete $id"
        check()
    }

    override suspend fun setTags(id: Long, add: List<String>, removeIds: List<Long>): List<InstapaperTag> {
        calls += "tags $id +$add -$removeIds"
        check()
        return tagsAfter
    }

    override suspend fun folders(): List<InstapaperFolder> = folders

    // Like the real server: what is created, deleted or reordered is what the next reading returns.
    override suspend fun createFolder(title: String): InstapaperFolder {
        calls += "createFolder $title"
        return InstapaperFolder(9, title, 3).also { folders = folders + it }
    }

    override suspend fun deleteFolder(id: Long) {
        calls += "deleteFolder $id"
        folders = folders.filterNot { it.id == id }
    }

    override suspend fun reorderFolders(order: List<Pair<Long, Int>>): List<InstapaperFolder> {
        calls += "reorder $order"
        folders = order.sortedBy { it.second }.map { (id, position) -> InstapaperFolder(id, "F$id", position) }
        return folders
    }

    override suspend fun tags(): List<InstapaperTag> = tags

    override suspend fun renameTag(id: Long, name: String): InstapaperTag {
        calls += "renameTag $id $name"
        return InstapaperTag(id, name)
    }
}

class InstapaperModelTest {
    private val service = FakeService()
    private var signedOut = 0

    // Unconfined runs each step at once, so the fake service's answers are in the state straight away.
    private fun model(): InstapaperModel =
        InstapaperModel(service, CoroutineScope(Dispatchers.Unconfined), onSignedOut = { signedOut++ })

    @Test
    fun `starts with Unread, folders and tags`() {
        service.lists[InstapaperView.Home] = listOf(bookmark(1), bookmark(2))
        val model = model()
        model.start()
        val state = model.state.value
        assertEquals(listOf(1L, 2L), state.items.map { it.id })
        assertEquals(2, state.total)
        assertFalse(state.loading)
        assertEquals(2, state.folders.size)
        assertEquals(listOf(InstapaperTag(1, "Cooking")), state.tags)
        assertNull(state.problem)
    }

    @Test
    fun `shows another list`() {
        service.lists[InstapaperView.Archive] = listOf(bookmark(5, archived = true))
        val model = model()
        model.start()
        model.select(InstapaperView.Archive)
        assertEquals(InstapaperView.Archive, model.state.value.view)
        assertEquals(listOf(5L), model.state.value.items.map { it.id })
    }

    @Test
    fun `archiving leaves Unread, and only marks the item in the liked list`() {
        service.lists[InstapaperView.Home] = listOf(bookmark(1), bookmark(2))
        service.lists[InstapaperView.Liked] = listOf(bookmark(3, liked = true))
        val model = model()
        model.start()
        model.toggleArchive(model.state.value.items[0])
        assertTrue("move 1 archive" in service.calls)
        assertEquals(listOf(2L), model.state.value.items.map { it.id })
        assertEquals(1, model.state.value.total)

        model.select(InstapaperView.Liked)
        model.toggleArchive(model.state.value.items[0])
        assertEquals(listOf(3L), model.state.value.items.map { it.id })
        assertTrue(model.state.value.items[0].archived)
    }

    @Test
    fun `an archived item goes back to Unread`() {
        service.lists[InstapaperView.Archive] = listOf(bookmark(4, archived = true))
        val model = model()
        model.start()
        model.select(InstapaperView.Archive)
        model.toggleArchive(model.state.value.items[0])
        assertTrue("move 4 home" in service.calls)
        assertTrue(model.state.value.items.isEmpty())
    }

    @Test
    fun `unliking leaves the liked list but not the others`() {
        service.lists[InstapaperView.Liked] = listOf(bookmark(1, liked = true))
        service.lists[InstapaperView.Home] = listOf(bookmark(2, liked = true))
        val model = model()
        model.start()
        model.toggleLike(model.state.value.items[0])
        assertTrue("like 2 false" in service.calls)
        assertFalse(model.state.value.items[0].liked)
        model.select(InstapaperView.Liked)
        model.toggleLike(model.state.value.items[0])
        assertTrue(model.state.value.items.isEmpty())
    }

    @Test
    fun `moves to a folder and back to Unread`() {
        service.lists[InstapaperView.Home] = listOf(bookmark(1))
        service.lists[InstapaperView.Archive] = listOf(bookmark(2, archived = true))
        val model = model()
        model.start()
        model.moveTo(model.state.value.items[0], 2L)
        assertTrue("move 1 2" in service.calls)
        assertTrue(model.state.value.items.isEmpty())

        model.select(InstapaperView.Archive)
        val archived = model.state.value.items[0]
        model.open(archived)
        model.moveTo(archived, 1L)
        // No longer archived, so out of the Archive list; the reader keeps it, changed.
        assertTrue(model.state.value.items.isEmpty())
        val moved = model.state.value.open!!
        assertEquals(1L, moved.folderId)
        assertFalse(moved.archived)
        model.moveTo(moved, null)
        assertTrue("move 2 home" in service.calls)
        assertNull(model.state.value.open!!.folderId)
    }

    @Test
    fun `the open article follows changes and is gone once deleted`() {
        service.lists[InstapaperView.Home] = listOf(bookmark(1), bookmark(2))
        val model = model()
        model.start()
        val first = model.state.value.items[0]
        model.open(first)
        model.toggleArchive(first)
        assertEquals(listOf(2L), model.state.value.items.map { it.id })
        assertTrue(model.state.value.open!!.archived)
        model.toggleLike(model.state.value.open!!)
        assertTrue(model.state.value.open!!.liked)
        model.delete(model.state.value.open!!)
        assertNull(model.state.value.open)
        assertEquals(1, model.state.value.total)
    }

    @Test
    fun `deletes`() {
        service.lists[InstapaperView.Home] = listOf(bookmark(1), bookmark(2))
        val model = model()
        model.start()
        model.delete(model.state.value.items[0])
        assertTrue("delete 1" in service.calls)
        assertEquals(listOf(2L), model.state.value.items.map { it.id })
    }

    @Test
    fun `adds and removes tags`() {
        service.lists[InstapaperView.Home] = listOf(bookmark(1))
        val model = model()
        model.start()
        service.tagsAfter = listOf(InstapaperTag(1, "Cooking"))
        model.addTag(model.state.value.items[0], "  Cooking ")
        assertTrue("tags 1 +[Cooking] -[]" in service.calls)
        assertEquals(listOf(InstapaperTag(1, "Cooking")), model.state.value.items[0].tags)

        // The same tag again (any capitalisation) and an empty name do nothing.
        val calls = service.calls.size
        model.addTag(model.state.value.items[0], "cooking")
        model.addTag(model.state.value.items[0], "   ")
        assertEquals(calls, service.calls.size)

        service.tagsAfter = emptyList()
        model.removeTag(model.state.value.items[0], InstapaperTag(1, "Cooking"))
        assertTrue("tags 1 +[] -[1]" in service.calls)
        assertTrue(model.state.value.items[0].tags.isEmpty())
    }

    @Test
    fun `an item leaves a tag's list when it loses the tag`() {
        val cooking = InstapaperTag(1, "Cooking")
        service.lists[InstapaperView.WithTag("Cooking")] = listOf(bookmark(1, tags = listOf(cooking)))
        val model = model()
        model.start()
        model.select(InstapaperView.WithTag("Cooking"))
        service.tagsAfter = emptyList()
        model.removeTag(model.state.value.items[0], cooking)
        assertTrue(model.state.value.items.isEmpty())
    }

    @Test
    fun `loads more without repeating items`() {
        service.lists[InstapaperView.Home] = (1L..30L).map { bookmark(it) }
        val model = model()
        model.start()
        assertEquals(25, model.state.value.items.size)
        assertTrue(model.state.value.canLoadMore)
        model.loadMore()
        assertEquals(30, model.state.value.items.size)
        assertEquals((1L..30L).toList(), model.state.value.items.map { it.id })
        assertFalse(model.state.value.canLoadMore)
    }

    @Test
    fun `an article saved from a feed can be filed at once, then the sheet closes`() {
        val model = model()
        model.start()
        assertTrue(runBlocking { model.save("https://a.example/42", "Title") })
        assertEquals(42L, model.state.value.justSaved?.id)

        model.moveTo(model.state.value.justSaved!!, 2)
        assertTrue("move 42 2" in service.calls)
        assertEquals(2L, model.state.value.justSaved?.folderId)

        service.tagsAfter = listOf(InstapaperTag(1, "Cooking"))
        model.addTag(model.state.value.justSaved!!, "Cooking")
        assertEquals(listOf("Cooking"), model.state.value.justSaved?.tags?.map { it.name })

        model.closeJustSaved()
        assertNull(model.state.value.justSaved)
    }

    @Test
    fun `a refused save signs out and tells the caller`() {
        val model = model()
        service.failure = InstapaperException(InstapaperProblem.SignedOut)
        val thrown = runCatching { runBlocking { model.save("https://a.example/42", null) } }.exceptionOrNull()
        assertEquals(InstapaperProblem.SignedOut, (thrown as? InstapaperException)?.problem)
        assertEquals(1, signedOut)
        assertNull(model.state.value.justSaved)
    }

    @Test
    fun `an article can be moved into a folder made on the spot`() {
        service.lists[InstapaperView.Home] = listOf(bookmark(1), bookmark(2))
        val model = model()
        model.start()
        model.open(bookmark(1))
        model.moveToNewFolder(bookmark(1), "  Recipes ")
        assertTrue("createFolder Recipes" in service.calls)
        assertTrue("move 1 9" in service.calls)
        assertTrue(model.state.value.folders.any { it.id == 9L && it.title == "Recipes" })
        assertEquals(9L, model.state.value.open?.folderId)
        // It left Unread.
        assertEquals(listOf(2L), model.state.value.items.map { it.id })

        model.moveToNewFolder(bookmark(2), " ")
        assertEquals("a blank name makes no folder", 1, service.calls.count { it.startsWith("createFolder") })
    }

    @Test
    fun `creates, reorders and deletes folders`() {
        val model = model()
        model.start()
        model.createFolder("  New ")
        assertTrue("createFolder New" in service.calls)
        assertEquals(listOf(1L, 2L, 9L), model.state.value.folders.map { it.id })

        model.moveFolder(model.state.value.folders[1], -1)
        assertTrue("reorder [(2, 1), (1, 2), (9, 3)]" in service.calls)
        assertEquals(listOf(2L, 1L, 9L), model.state.value.folders.map { it.id })

        model.moveFolder(model.state.value.folders[0], -1)
        assertEquals("a move off the top does nothing", 1, service.calls.count { it.startsWith("reorder") })

        service.lists[InstapaperView.InFolder(1)] = emptyList()
        model.select(InstapaperView.InFolder(1))
        model.deleteFolder(InstapaperFolder(1, "A", 1))
        assertTrue("deleteFolder 1" in service.calls)
        assertEquals(InstapaperView.Home, model.state.value.view)
        assertTrue(model.state.value.folders.none { it.id == 1L })
    }

    @Test
    fun `renames a tag and follows it when it is the list shown`() {
        service.lists[InstapaperView.WithTag("Cooking")] = emptyList()
        val model = model()
        model.start()
        model.select(InstapaperView.WithTag("Cooking"))
        model.renameTag(InstapaperTag(1, "Cooking"), " Baking ")
        assertTrue("renameTag 1 Baking" in service.calls)
        assertEquals(InstapaperView.WithTag("Baking"), model.state.value.view)
    }

    @Test
    fun `shows a problem and stops loading`() {
        service.failure = InstapaperException(InstapaperProblem.Offline)
        val model = model()
        model.start()
        assertEquals(InstapaperProblem.Offline, model.state.value.problem)
        assertFalse(model.state.value.loading)
        assertEquals(0, signedOut)
    }

    @Test
    fun `signs out when Instapaper refuses the token`() {
        service.failure = InstapaperException(InstapaperProblem.SignedOut)
        val model = model()
        model.start()
        assertEquals(InstapaperProblem.SignedOut, model.state.value.problem)
        assertTrue(signedOut >= 1)
    }

    @Test
    fun `moves a folder by its place in the list`() {
        val folders = listOf(1L, 2L, 3L).map { InstapaperFolder(it, "F$it", it.toInt()) }
        assertEquals(listOf(2L to 1, 1L to 2, 3L to 3), reorderedPositions(folders, 1, -1))
        assertEquals(listOf(1L to 1, 3L to 2, 2L to 3), reorderedPositions(folders, 1, 1))
        // A folder dragged two places at once.
        assertEquals(listOf(2L to 1, 3L to 2, 1L to 3), reorderedPositions(folders, 0, 2))
        assertNull(reorderedPositions(folders, 0, -1))
        assertNull(reorderedPositions(folders, 2, 1))
        assertNull(reorderedPositions(folders, 5, 1))
    }
}
