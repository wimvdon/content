package be.wimvandonck.reader.data

import be.wimvandonck.reader.sync.SyncRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedTest {
    private fun saved(
        key: String,
        added: Long,
        state: ReadingState = ReadingState.ToRead,
        folder: String? = null,
        liked: Boolean = false,
        tags: List<String> = emptyList(),
        url: String = "https://$key",
    ) = Item(key, url, key, createdAt = 0, readingList = ReadingListEntry(state, added), folder = folder, liked = liked, tags = tags)

    private val library =
        Library(
            items =
                listOf(
                    saved("a.example/1", 1),
                    saved("a.example/2", 2, folder = "Work", tags = listOf("ai", "news")),
                    saved("a.example/3", 3, folder = "work", liked = true, tags = listOf("ai")),
                    saved("a.example/4", 4, state = ReadingState.Archived, tags = listOf("old")),
                    saved("youtu.be/x", 5, url = "https://youtu.be/x"),
                    Item("b.example/5", "https://b.example/5", "Not saved", createdAt = 0, tags = listOf("ai")),
                ),
        )

    private fun keys(filter: SavedFilter) = Saved.items(library, filter).map { it.key }

    @Test
    fun `each item is in one place, and All has them all, newest first`() {
        assertEquals(listOf("youtu.be/x", "a.example/1"), keys(SavedFilter(SavedTab.ToRead)))
        assertEquals(listOf("a.example/3", "a.example/2"), keys(SavedFilter(SavedTab.Folder("WORK"))))
        assertEquals(listOf("a.example/4"), keys(SavedFilter(SavedTab.Archive)))
        assertEquals(listOf("youtu.be/x", "a.example/4", "a.example/3", "a.example/2", "a.example/1"), keys(SavedFilter(SavedTab.All)))
    }

    @Test
    fun `archiving takes it out of its folder, and Undo files it back there`() {
        val archived = library.setReadingState("a.example/2", ReadingState.Archived)
        assertNull(archived.item("a.example/2")!!.folder)
        assertEquals(listOf("a.example/3"), Saved.items(archived, SavedFilter(SavedTab.Folder("Work"))).map { it.key })
        // Undo: back to read, in the folder it was in, with its tags.
        val undone = Saved.file(archived, "a.example/2", "Work")
        assertEquals(ReadingState.ToRead, undone.item("a.example/2")!!.readingList!!.state)
        assertEquals(listOf("a.example/3", "a.example/2"), Saved.items(undone, SavedFilter(SavedTab.Folder("Work"))).map { it.key })
        assertEquals(listOf("ai", "news"), undone.item("a.example/2")!!.tags)
        // An item that was in To read goes back there.
        val toRead = Saved.file(library.setReadingState("a.example/1", ReadingState.Archived), "a.example/1", null)
        assertEquals(listOf("youtu.be/x", "a.example/1"), Saved.items(toRead, SavedFilter(SavedTab.ToRead)).map { it.key })
    }

    @Test
    fun `liked, tags (all of them) and the type filter within a tab`() {
        assertEquals(listOf("a.example/3"), keys(SavedFilter(SavedTab.All, likedOnly = true)))
        assertEquals(listOf("a.example/3", "a.example/2"), keys(SavedFilter(SavedTab.All, tags = setOf("AI"))))
        assertEquals(listOf("a.example/2"), keys(SavedFilter(SavedTab.All, tags = setOf("ai", "news"))))
        assertEquals(listOf("youtu.be/x"), keys(SavedFilter(SavedTab.All, type = SavedType.Video)))
        // Chips: the tab's tags, most used first.
        assertEquals(listOf("ai", "news"), Saved.tags(library, SavedTab.Folder("Work")))
        assertEquals(listOf("ai", "news", "old"), Saved.tags(library, SavedTab.All))
    }

    @Test
    fun `folders and their counts, and a filter without what is gone`() {
        assertEquals(listOf("Work"), Saved.folders(library))
        assertEquals(mapOf("Work" to 2), Saved.folderCounts(library))
        assertEquals(SavedFilter(SavedTab.ToRead), Saved.shown(library, SavedFilter(SavedTab.Folder("Gone"))))
        assertEquals(SavedFilter(SavedTab.All, tags = setOf("ai")), Saved.shown(library, SavedFilter(SavedTab.All, tags = setOf("ai", "nope"))))
        val same = SavedFilter(SavedTab.Folder("work"))
        assertSame(same, Saved.shown(library, same))
    }

    @Test
    fun `a tag stays chosen in another state, and the drawer counts what is still to read`() {
        // "old" is only in the Archive: To read with it shows nothing, but the tag stays chosen.
        val toReadOld = SavedFilter(SavedTab.ToRead, tags = setOf("old"))
        assertSame(toReadOld, Saved.shown(library, toReadOld))
        assertEquals(emptyList<String>(), keys(toReadOld))
        assertEquals(listOf("a.example/4"), keys(toReadOld.copy(tab = SavedTab.Archive)))
        assertEquals(listOf(SavedTab.ToRead, SavedTab.Archive, SavedTab.All), Saved.states)
        // Items in an old folder aren't in To read; the archived one isn't either.
        val toRead = library.copy(items = library.items + saved("c.example/6", 6, tags = listOf("AI")))
        assertEquals(mapOf("ai" to 1, "news" to 0, "old" to 0), Saved.toReadTagCounts(toRead))
        // The liked one is in a folder, so not in To read; liking one in To read counts it.
        assertEquals(0, Saved.likedToReadCount(library))
        assertEquals(1, Saved.likedToReadCount(toRead.setLiked("c.example/6", true)))
    }

    @Test
    fun `filing moves an item into a folder or back to read, archiving leaves the folder`() {
        val filed = Saved.file(library, "a.example/1", " Later ")
        assertEquals("Later", Saved.folderOf(filed.item("a.example/1")!!))
        val back = Saved.file(filed, "a.example/1", null)
        assertEquals(listOf("youtu.be/x", "a.example/1"), Saved.items(back, SavedFilter()).map { it.key })
        // An archived item filed comes back to read.
        val unarchived = Saved.file(library, "a.example/4", "Work")
        assertEquals(ReadingState.ToRead, unarchived.item("a.example/4")!!.readingList?.state)
        val archived = library.setReadingState("a.example/2", ReadingState.Archived)
        assertNull(archived.item("a.example/2")!!.folder)
        // An item not saved isn't filed.
        assertSame(library, Saved.file(library, "b.example/5", "Work"))
    }

    @Test
    fun `renaming a folder joins it with one of that name, removing one archives its items`() {
        val withOther = Saved.file(library, "a.example/1", "Later")
        assertEquals(listOf("Later"), Saved.folders(Saved.renameFolder(withOther, "work", "Later")))
        assertSame(library, Saved.renameFolder(library, "Work", " "))
        val toRead = Saved.items(library, SavedFilter()).map { it.key }
        val removed = Saved.removeFolder(library, "WORK")
        assertEquals(emptyList<String>(), Saved.folders(removed))
        // The folder's articles are archived, not back in To read.
        assertTrue(Saved.items(removed, SavedFilter(SavedTab.Archive)).map { it.key }.containsAll(listOf("a.example/2", "a.example/3")))
        assertEquals(toRead, Saved.items(removed, SavedFilter()).map { it.key })
    }

    @Test
    fun `bookmarks from before move into folders once`() {
        val old =
            Library(
                items =
                    listOf(
                        Item("c.example/1", "https://c.example/1", "C", createdAt = 0, bookmark = BookmarkEntry(7, "Research")),
                        Item("c.example/2", "https://c.example/2", "D", createdAt = 0, bookmark = BookmarkEntry(8, null), readingList = ReadingListEntry(ReadingState.Archived, 3)),
                        saved("a.example/1", 1),
                    ),
            )
        val settled = Saved.settle(old, "Bookmarks")
        assertEquals(ReadingListEntry(ReadingState.ToRead, 7), settled.item("c.example/1")!!.readingList)
        assertEquals("Research", settled.item("c.example/1")!!.folder)
        assertNull(settled.item("c.example/1")!!.bookmark)
        assertEquals("Bookmarks", Saved.folderOf(settled.item("c.example/2")!!))
        assertEquals(3L, settled.item("c.example/2")!!.readingList?.addedAt)
        assertSame(settled, Saved.settle(settled, "Bookmarks"))
    }

    @Test
    fun `a link's type comes from its address`() {
        assertEquals(SavedType.Video, Saved.typeOf("https://www.youtube.com/watch?v=1"))
        assertEquals(SavedType.Video, Saved.typeOf("https://vimeo.com/123"))
        assertEquals(SavedType.Audio, Saved.typeOf("https://open.spotify.com/episode/abc"))
        assertEquals(SavedType.Audio, Saved.typeOf("https://overcast.fm/+abc"))
        assertEquals(SavedType.Article, Saved.typeOf("https://open.spotify.com/track/abc"))
        assertEquals(SavedType.Article, Saved.typeOf("https://example.com/story"))
    }

    @Test
    fun `the folder syncs, and both sides changing it keeps this device's`() {
        val item = saved("a.example/2", 2, folder = "Work")
        val data = SyncRows.itemData(item)
        assertEquals("Work", data["folder"])
        assertEquals("Work", SyncRows.itemOf(item.key, data, null)!!.folder)
        val base = item.copy(folder = null)
        assertEquals("Here", SyncRows.mergeItems(base, item.copy(folder = "Here"), item.copy(folder = "There")).folder)
        assertEquals("There", SyncRows.mergeItems(base, base, item.copy(folder = "There")).folder)
    }

    @Test
    fun `only To read is rows, folders and the Archive are notes`() {
        // Saved is always notes now, To read too.
        assertEquals(true, Saved.showsNotes(SavedTab.ToRead))
        assertEquals(true, Saved.showsNotes(SavedTab.Folder("Work")))
        assertEquals(true, Saved.showsNotes(SavedTab.Archive))
    }

    @Test
    fun `a note card of a saved item and of a feed article`() {
        val item =
            Item(
                "www.a.example/1",
                "https://www.a.example/1",
                "A",
                imageUrl = "https://a.example/p.jpg",
                note = "Mine.",
                tags = listOf("ai"),
                liked = true,
                highlights = listOf(Highlight("h1", "Words.", createdAt = 1)),
                createdAt = 0,
            )
        assertEquals(NoteContent("A", "https://a.example/p.jpg", CardBody.Note("Mine."), "a.example", listOf("ai"), 1, true), Saved.note(item))
        val article =
            Article(feedUrl = "https://f.example/rss", id = "1", title = "B", link = "https://www.b.example/x", author = null, html = "<p>Start of <b>the</b> text.</p>", publishedAt = 1, fetchedAt = 1)
        assertEquals(NoteContent("B", null, CardBody.Description("Start of the text."), "b.example"), Saved.note(article))
        assertNull(Saved.note(article.copy(html = "", link = null)).body)
        assertEquals("", Saved.note(article.copy(link = null)).host)
    }

    @Test
    fun `a card shows the note, else the first highlight, else the description`() {
        val highlight = Highlight("h1", "The author's words.", createdAt = 1)
        val second = Highlight("h2", "Later words.", createdAt = 2)
        val item = Item("a.example/1", "https://a.example/1", "A", description = "The site's summary.", createdAt = 0)
        assertEquals(CardBody.Note("Mine."), Saved.cardBody(item.copy(note = " Mine. ", highlights = listOf(highlight))))
        assertEquals(CardBody.Quote("The author's words."), Saved.cardBody(item.copy(highlights = listOf(highlight, second))))
        // A highlight without a quote doesn't count.
        assertEquals(CardBody.Quote("Later words."), Saved.cardBody(item.copy(highlights = listOf(highlight.copy(quote = " "), second))))
        assertEquals(CardBody.Description("The site's summary."), Saved.cardBody(item))
        assertNull(Saved.cardBody(item.copy(description = "")))
    }

    @Test
    fun `folders become tags, once, and a library without folders stays the same (#187)`() {
        val moved = Saved.foldersToTags(library)
        assertEquals(listOf("ai", "news", "Work"), moved.item("a.example/2")!!.tags)
        // A tag of the same name, case ignored, isn't added twice.
        assertEquals(listOf("ai", "work"), moved.item("a.example/3")!!.tags)
        assertTrue(moved.items.none { it.folder != null })
        assertTrue(Saved.folders(moved).isEmpty())
        assertSame(moved, Saved.foldersToTags(moved))
    }
}
