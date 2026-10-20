package be.wimvandonck.reader.data

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
    fun `offers To read, the folders, a folder being made, and Archive only for new saves`() {
        val folders = listOf("Brussels", "Research")
        assertEquals(listOf(Place.ToRead, Place.Folder("Brussels"), Place.Folder("Research")), SaveChoices.places(folders, Place.ToRead, offerArchive = false))
        assertEquals(
            listOf(Place.ToRead, Place.Folder("Brussels"), Place.Folder("Research"), Place.Folder("Later"), Place.Archive),
            SaveChoices.places(folders, Place.Folder("Later"), offerArchive = true),
        )
        // An archived article organised from the reader still shows where it is.
        assertEquals(Place.Archive, SaveChoices.places(folders, Place.Archive, offerArchive = false).last())
        // A folder already in use isn't offered twice, whatever its capitals.
        assertEquals(3, SaveChoices.places(folders, Place.Folder("research"), offerArchive = false).size)
    }

    @Test
    fun `a new folder joins the folder of that name, and a blank name is none`() {
        assertEquals(Place.Folder("Research"), SaveChoices.newFolder(" research ", listOf("Research")))
        assertEquals(Place.Folder("Later"), SaveChoices.newFolder("Later ", listOf("Research")))
        assertNull(SaveChoices.newFolder("  ", listOf("Research")))
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
}
