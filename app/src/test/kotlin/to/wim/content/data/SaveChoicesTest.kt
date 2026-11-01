package to.wim.content.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveChoicesTest {
    private fun saved(
        key: String,
        state: ReadingState = ReadingState.ToRead,
        folder: String? = null,
    ) = Item(key, "https://$key", key, createdAt = 0, readingList = ReadingListEntry(state, 1), folder = folder)

    private val library = Library(items = listOf(saved("a.example/1"), saved("a.example/2", folder = "Work"), saved("a.example/3", ReadingState.Archived)))

    @Test
    fun `the sheet starts where the item is, with what it has`() {
        assertEquals(Place.ToRead, SaveChoices.of(library.item("a.example/1")!!).place)
        assertEquals(Place.Folder("Work"), SaveChoices.of(library.item("a.example/2")!!).place)
        assertEquals(Place.Archive, SaveChoices.of(library.item("a.example/3")!!).place)
        val noted = library.item("a.example/1")!!.copy(liked = true, tags = listOf("ai"), note = "Mine")
        assertEquals(SaveChoice(Place.ToRead, liked = true, tags = listOf("ai"), note = "Mine"), SaveChoices.of(noted))
    }

    @Test
    fun `offers To read and Archive first, then the folders and a folder being made`() {
        val folders = listOf("Brussels", "Research")
        assertEquals(listOf(Place.ToRead, Place.Archive, Place.Folder("Brussels"), Place.Folder("Research")), SaveChoices.places(folders, Place.ToRead))
        assertEquals(
            listOf(Place.ToRead, Place.Archive, Place.Folder("Brussels"), Place.Folder("Research"), Place.Folder("Later")),
            SaveChoices.places(folders, Place.Folder("Later")),
        )
        // A folder already in use isn't offered twice, whatever its capitals.
        assertEquals(4, SaveChoices.places(folders, Place.Folder("research")).size)
    }

    @Test
    fun `a new folder joins the folder of that name, and a blank name is none`() {
        assertEquals(Place.Folder("Research"), SaveChoices.newFolder(" research ", listOf("Research")))
        assertEquals(Place.Folder("Later"), SaveChoices.newFolder("Later ", listOf("Research")))
        assertNull(SaveChoices.newFolder("  ", listOf("Research")))
    }

    @Test
    fun `Save to Archive is offered while saving, not organising or only sending`() {
        assertTrue(SaveChoices.offersArchive(organising = false, SaveChoice()))
        assertEquals(false, SaveChoices.offersArchive(organising = true, SaveChoice()))
        assertEquals(false, SaveChoices.offersArchive(organising = false, SaveChoice(keep = false)))
    }

    @Test
    fun `Save to Archive keeps the like, topics and note and saves into the Archive`() {
        val choice = SaveChoice(Place.ToRead, liked = true, tags = listOf("ai"), note = "Mine")
        val archived = SaveChoices.toArchive(choice)
        assertEquals(choice.copy(place = Place.Archive), archived)
        val (_, item) = SaveChoices.saveNew(Library(), "https://b.example/1", "B", 10, archived)
        assertTrue(item!!.archived)
        assertEquals(listOf("ai"), item.tags)
        // Just saved from the reader, then Save to Archive: the saved article moves to the Archive.
        val moved = SaveChoices.apply(library, "a.example/1", SaveChoices.toArchive(SaveChoices.of(library.item("a.example/1")!!)))
        assertTrue(moved.item("a.example/1")!!.archived)
    }

    @Test
    fun `a new save lands in To read, a folder or the Archive, with its like, tags and note`() {
        val (toRead, a) = SaveChoices.saveNew(Library(), "https://b.example/1", "B", 10, SaveChoice())
        assertTrue(Saved.isIn(a!!, SavedTab.ToRead))
        assertEquals(a, toRead.item("b.example/1"))
        val (_, b) = SaveChoices.saveNew(Library(), "https://b.example/1", null, 10, SaveChoice(Place.Folder("Work"), liked = true, tags = listOf("x"), note = " A note "))
        assertEquals("Work", Saved.folderOf(b!!))
        assertEquals(true, b.liked)
        assertEquals(listOf("x"), b.tags)
        assertEquals("A note", b.note)
        val (_, c) = SaveChoices.saveNew(Library(), "https://b.example/1", null, 10, SaveChoice(Place.Archive))
        assertTrue(c!!.archived)
        assertNull(SaveChoices.saveNew(Library(), "not a link", null, 10, SaveChoice()).second)
    }

    @Test
    fun `saving a link saved before keeps its like and tags and adds the sheet's`() {
        val before = Library(items = listOf(saved("b.example/1").copy(liked = true, tags = listOf("old"))))
        val (_, item) = SaveChoices.saveNew(before, "https://b.example/1", null, 10, SaveChoice(tags = listOf("new")))
        assertEquals(true, item!!.liked)
        assertEquals(listOf("old", "new"), item.tags)
    }

    @Test
    fun `organising writes the place, like, tags and note, and nothing when unchanged`() {
        val key = "a.example/2"
        assertSame(library, SaveChoices.apply(library, key, SaveChoices.of(library.item(key)!!)))
        val moved = SaveChoices.apply(library, key, SaveChoice(Place.Folder("Later"), liked = true, tags = listOf("a", "A", " b "), note = " Mine "))
        val item = moved.item(key)!!
        assertEquals("Later", Saved.folderOf(item))
        assertEquals(listOf("a", "b"), item.tags)
        assertEquals("Mine", item.note)
        assertTrue(item.liked)
        assertTrue(SaveChoices.apply(moved, key, SaveChoices.of(item).copy(place = Place.Archive)).item(key)!!.archived)
        // Back from the archive to To read.
        val back = SaveChoices.apply(library, "a.example/3", SaveChoice(Place.ToRead))
        assertTrue(Saved.isIn(back.item("a.example/3")!!, SavedTab.ToRead))
        // An item that isn't saved, or isn't there, stays as it is.
        assertSame(library, SaveChoices.apply(library, "gone.example", SaveChoice()))
    }

    @Test
    fun `the Tags menu turns a tag on and off, case ignored`() {
        val choice = SaveChoice(tags = listOf("News"))
        assertEquals(listOf("News", "Brussels"), SaveChoices.withTag(choice, " Brussels ").tags)
        assertEquals(emptyList<String>(), SaveChoices.withTag(choice, "news").tags)
        assertSame(choice, SaveChoices.withTag(choice, "  "))
    }

    @Test
    fun `the sheet offers every tag in a steady order, a new one last`() {
        val all = listOf("ai", "News")
        assertEquals(listOf("ai", "News"), SaveChoices.tagChoices(all, emptyList()))
        // Turning one on doesn't move it; a tag made in the sheet comes last, once.
        assertEquals(listOf("ai", "News"), SaveChoices.tagChoices(all, listOf("news")))
        assertEquals(listOf("ai", "News", "Brussels"), SaveChoices.tagChoices(all, listOf("Brussels", "AI")))
    }

    @Test
    fun `the Tags button shows the tags, else Liked, else nothing`() {
        assertEquals("News, Brussels", SaveChoices.tagsLabel(SaveChoice(liked = true, tags = listOf("News", "Brussels")), "Liked"))
        assertEquals("Liked", SaveChoices.tagsLabel(SaveChoice(liked = true), "Liked"))
        assertNull(SaveChoices.tagsLabel(SaveChoice(), "Liked"))
    }

    @Test
    fun `Remove right after saving takes the save back`() {
        // A link that wasn't saved goes again.
        val (added, item) = library.addToReadingList("https://b.example/new", "New", 5)
        assertNull(SaveChoices.undoSave(added, item!!.key, before = null).item(item.key))
        // One saved before (here archived) is as it was.
        val before = library.item("a.example/3")
        val again = library.changeItem("a.example/3") { it.copy(readingList = ReadingListEntry(ReadingState.ToRead, 9), liked = true) }
        assertEquals(before, SaveChoices.undoSave(again, "a.example/3", before).item("a.example/3"))
    }

    @Test
    fun `Save takes a folder and a tag still being typed`() {
        val choice = SaveChoice(tags = listOf("News"))
        val typed = SaveChoices.withTyped(choice, folder = " work ", tag = "Brussels", folders = listOf("Work"))
        assertEquals(Place.Folder("Work"), typed.place)
        assertEquals(listOf("News", "Brussels"), typed.tags)
        assertSame(choice, SaveChoices.withTyped(choice, folder = "", tag = "news", folders = emptyList()))
    }

    @Test
    fun `a topic renamed or removed on the sheet changes its choice too`() {
        val choice = SaveChoice(tags = listOf("Tech", "News"))
        assertEquals(listOf("Technology", "News"), SaveChoices.withTopicRenamed(choice, "tech", " Technology ").tags)
        // Renamed to a topic it has already: one tag.
        assertEquals(listOf("News"), SaveChoices.withTopicRenamed(choice, "Tech", "news").tags)
        assertSame(choice, SaveChoices.withTopicRenamed(choice, "Recipes", "Food"))
        assertSame(choice, SaveChoices.withTopicRenamed(choice, "Tech", " "))
        assertEquals(listOf("News"), SaveChoices.withoutTopic(choice, "TECH").tags)
        assertSame(choice, SaveChoices.withoutTopic(choice, "Recipes"))
    }
}
