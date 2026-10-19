package be.wimvandonck.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BookmarksTest {
    private fun kept(
        library: Library,
        url: String,
        now: Long,
        collection: String? = null,
    ): Library = Bookmarks.add(library, url, null, now, collection).first

    @Test
    fun `a link's kind comes from its address`() {
        assertEquals(BookmarkKind.Video, Bookmarks.kindOf("https://www.youtube.com/watch?v=abc"))
        assertEquals(BookmarkKind.Video, Bookmarks.kindOf("https://youtu.be/abc"))
        assertEquals(BookmarkKind.Video, Bookmarks.kindOf("https://vimeo.com/12345"))
        assertEquals(BookmarkKind.Repo, Bookmarks.kindOf("https://github.com/owner/project"))
        assertEquals(BookmarkKind.Repo, Bookmarks.kindOf("https://codeberg.org/owner/project/issues"))
        // A code host's own pages and a user's page aren't repositories.
        assertEquals(BookmarkKind.Article, Bookmarks.kindOf("https://github.com/features/actions"))
        assertEquals(BookmarkKind.Article, Bookmarks.kindOf("https://github.com/owner"))
        // A site's home page is a tool; a page on it an article.
        assertEquals(BookmarkKind.Tool, Bookmarks.kindOf("https://excalidraw.com/"))
        assertEquals(BookmarkKind.Article, Bookmarks.kindOf("https://example.com/2024/a-story"))
        assertEquals(BookmarkKind.Article, Bookmarks.kindOf("not a link"))
    }

    @Test
    fun `articles are suggested for the reading list, other links for Bookmarks`() {
        assertEquals(SaveTo.ReadingList, Bookmarks.suggested("https://example.com/a-story"))
        assertEquals(SaveTo.Bookmark, Bookmarks.suggested("https://github.com/owner/project"))
        assertEquals(SaveTo.Bookmark, Bookmarks.suggested("https://excalidraw.com"))
    }

    @Test
    fun `adding keeps a link with its collection, note and tags, and keeps it on the reading list`() {
        val reading = Library().addToReadingList("https://example.com/a", "A", 1).first
        val (library, item) = Bookmarks.add(reading, "https://example.com/a", null, 5, " Research ", "Good one", listOf("ai", " "))
        assertNotNull(item)
        val stored = library.item(item!!.key)!!
        assertEquals(BookmarkEntry(5, "Research"), stored.bookmark)
        assertEquals("A", stored.title)
        assertEquals("Good one", stored.note)
        assertEquals(listOf("ai"), stored.tags)
        assertNotNull(stored.readingList)
        // Keeping it again keeps the first date and, without a new one, the collection.
        val again = Bookmarks.add(library, "https://example.com/a", null, 9).first.item(item.key)!!
        assertEquals(BookmarkEntry(5, "Research"), again.bookmark)
        assertNull(Bookmarks.add(Library(), "mailto:me@example.com", null, 1).second)
    }

    @Test
    fun `the filter picks a collection and a kind, newest first`() {
        val library =
            kept(Library(), "https://example.com/a", 1, "Research")
                .let { kept(it, "https://github.com/o/p", 2, "research") }
                .let { kept(it, "https://youtu.be/x", 3, "Design") }
                .let { kept(it, "https://example.com/b", 4) }
        assertEquals(listOf("example.com/b", "youtu.be/x", "github.com/o/p", "example.com/a"), Bookmarks.items(library, BookmarkFilter()).map { it.key })
        assertEquals(listOf("github.com/o/p", "example.com/a"), Bookmarks.items(library, BookmarkFilter("RESEARCH")).map { it.key })
        assertEquals(listOf("github.com/o/p"), Bookmarks.items(library, BookmarkFilter("Research", BookmarkKind.Repo)).map { it.key })
        assertEquals(listOf("Design", "Research"), Bookmarks.collections(library))
        assertEquals(mapOf("Design" to 1, "Research" to 2), Bookmarks.collectionCounts(library))
        // A collection that is gone falls back to All.
        assertEquals(BookmarkFilter(kind = BookmarkKind.Video), Bookmarks.shown(library, BookmarkFilter("Gone", BookmarkKind.Video)))
        assertEquals(BookmarkFilter("design"), Bookmarks.shown(library, BookmarkFilter("design")))
    }

    @Test
    fun `renaming a collection joins it with one of that name, removing one keeps its bookmarks`() {
        val library = kept(kept(Library(), "https://example.com/a", 1, "Reading"), "https://example.com/b", 2, "Research")
        val renamed = Bookmarks.renameCollection(library, "reading", "Research")
        assertEquals(listOf("Research"), Bookmarks.collections(renamed))
        assertEquals(library, Bookmarks.renameCollection(library, "Reading", "  "))
        val removed = Bookmarks.removeCollection(renamed, "RESEARCH")
        assertEquals(emptyList<String>(), Bookmarks.collections(removed))
        assertEquals(2, removed.bookmarks().size)
    }

    @Test
    fun `the save sheet puts a link on the reading list or in Bookmarks, with its note and tags`() {
        val (toRead, item) = Bookmarks.save(Library(), "https://example.com/a", "A", SaveTo.ReadingList, 3, collection = "Ignored", note = " Why ", tags = listOf("x"))
        val stored = toRead.item(item!!.key)!!
        assertEquals(ReadingState.ToRead, stored.readingList?.state)
        assertNull(stored.bookmark)
        assertEquals("Why", stored.note)
        assertEquals(listOf("x"), stored.tags)
        val (kept, tool) = Bookmarks.save(Library(), "https://excalidraw.com", null, SaveTo.Bookmark, 4, collection = "Tools", note = " ")
        val keptItem = kept.item(tool!!.key)!!
        assertEquals(BookmarkEntry(4, "Tools"), keptItem.bookmark)
        assertNull(keptItem.readingList)
        assertEquals("", keptItem.note)
    }

    @Test
    fun `read again puts a bookmark back on the reading list from the start, and it stays a bookmark`() {
        val archived =
            Library()
                .addToReadingList("https://example.com/a", "A", 1)
                .first
                .keepAsBookmark("example.com/a", null, 2)
                .changeItem("example.com/a") { it.copy(progress = 1f) }
                .setReadingState("example.com/a", ReadingState.Archived)
        val again = Bookmarks.readAgain(archived, "example.com/a", 9).item("example.com/a")!!
        assertEquals(ReadingListEntry(ReadingState.ToRead, 9), again.readingList)
        assertEquals(0f, again.progress)
        assertNotNull(again.bookmark)
        // Already to read: nothing changes.
        val toRead = Bookmarks.readAgain(archived, "example.com/a", 9)
        assertEquals(toRead, Bookmarks.readAgain(toRead, "example.com/a", 20))
    }

    @Test
    fun `removing a bookmark keeps an item on the reading list, otherwise the whole item goes`() {
        val both =
            Bookmarks.add(Library().addToReadingList("https://example.com/a", "A", 1).first, "https://example.com/a", null, 2, note = "n", tags = listOf("t")).first
        val stillReading = Bookmarks.remove(both, "example.com/a").item("example.com/a")!!
        assertNull(stillReading.bookmark)
        assertEquals("n", stillReading.note)
        val onlyKept = Bookmarks.add(Library(), "https://example.com/b", null, 2, note = "n", tags = listOf("t")).first
        assertEquals(emptyList<Item>(), Bookmarks.remove(onlyKept, "example.com/b").items)
    }

    @Test
    fun `editing changes the collection, note and tags of a bookmark only`() {
        val library = kept(Library(), "https://example.com/a", 1, "Old")
        val edited = Bookmarks.edit(library, "example.com/a", " ", "Note", listOf("a", "A", "b")).item("example.com/a")!!
        assertNull(edited.bookmark?.collection)
        assertEquals("Note", edited.note)
        assertEquals(listOf("a", "b"), edited.tags)
        val reading = Library().addToReadingList("https://example.com/c", "C", 1).first
        assertEquals(reading, Bookmarks.edit(reading, "example.com/c", "X", "Y", emptyList()))
    }
}
