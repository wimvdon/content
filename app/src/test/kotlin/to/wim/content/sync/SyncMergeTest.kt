package to.wim.content.sync

import to.wim.content.data.Article
import to.wim.content.data.Feed
import to.wim.content.data.Folder
import to.wim.content.data.Library
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {
    private fun feed(
        url: String,
        title: String = url,
        folderId: String? = null,
    ) = SyncFeed(url, title, renamed = false, folderId = folderId, siteUrl = null, iconUrl = null)

    private val base =
        SyncDocument(
            folders = listOf(SyncFolder("f1", "News", "folder"), SyncFolder("f2", "Tech", "folder")),
            feeds = listOf(feed("a", folderId = "f1"), feed("b"), feed("c")),
        )

    @Test
    fun `changes on either side are kept`() {
        val local = base.copy(feeds = base.feeds.map { if (it.url == "a") it.copy(title = "A!", renamed = true) else it })
        val remote = base.copy(feeds = base.feeds + feed("d"))
        val merged = SyncMerge.merge(base, local, remote)
        assertEquals(listOf("a", "b", "c", "d"), merged.feeds.map { it.url })
        assertEquals("A!", merged.feeds.first().title)
    }

    @Test
    fun `removals on either side are kept`() {
        val local = base.copy(feeds = base.feeds.filterNot { it.url == "b" })
        val remote = base.copy(folders = base.folders.filterNot { it.id == "f1" })
        val merged = SyncMerge.merge(base, local, remote)
        assertEquals(listOf("a", "c"), merged.feeds.map { it.url })
        assertEquals(listOf("f2"), merged.folders.map { it.id })
        // Its folder is gone, so the feed is in no folder.
        assertEquals(null, merged.feeds.first().folderId)
    }

    @Test
    fun `when both changed the same thing this device wins, but an edit beats a removal`() {
        val local = base.copy(feeds = base.feeds.map { if (it.url == "a") it.copy(title = "Local") else it }.filterNot { it.url == "c" })
        val remote = base.copy(feeds = base.feeds.map { if (it.url == "a" || it.url == "c") it.copy(title = "Remote") else it })
        val merged = SyncMerge.merge(base, local, remote)
        assertEquals("Local", merged.feeds.first { it.url == "a" }.title)
        assertEquals("Remote", merged.feeds.first { it.url == "c" }.title)
    }

    @Test
    fun `the side that reordered decides the order`() {
        val local = base.copy(feeds = base.feeds.reversed())
        val remote = base.copy(feeds = base.feeds + feed("d"))
        assertEquals(listOf("c", "b", "a", "d"), SyncMerge.merge(base, local, remote).feeds.map { it.url })
        assertEquals(listOf("a", "b", "c", "d"), SyncMerge.merge(base, base, remote).feeds.map { it.url })
    }

    @Test
    fun `a first sync without base combines both sides`() {
        val local = SyncDocument(feeds = listOf(feed("a"), feed("x")))
        val remote = SyncDocument(feeds = listOf(feed("a", title = "Remote"), feed("y")))
        val merged = SyncMerge.merge(null, local, remote)
        assertEquals(listOf("a", "y", "x"), merged.feeds.map { it.url })
        assertEquals("a", merged.feeds.first().title)
    }

    private val library =
        Library(
            feeds = listOf(Feed("a", "a", folderId = "f1"), Feed("b", "b"), Feed("c", "c")),
            folders = listOf(Folder("f1", "News"), Folder("f2", "Tech")),
            articles =
                listOf(
                    Article("a", "1", "t", null, null, "", publishedAt = 10, fetchedAt = 10, read = true),
                    Article("b", "1", "t", null, null, "", publishedAt = 20, fetchedAt = 20, read = false),
                    Article("c", "1", "t", null, null, "", publishedAt = 30, fetchedAt = 30, read = false),
                ),
        )

    @Test
    fun `a version 2 file's read marks are kept for the articles this device doesn't have`() {
        val old = SyncDocument.decode("""{"format":"rss-reader-sync","version":2,"feeds":[{"url":"a"}],"read":{"a 1":10,"z 9":5}}""")!!
        assertEquals(mapOf("a 1" to 10L, "z 9" to 5L), old.legacyRead)
        val merged = SyncMerge.merge(null, SyncMerge.localDocument(library), old)
        val applied = SyncMerge.apply(library, merged)
        assertEquals(mapOf("z 9" to 5L), applied.readElsewhere)
        // Never written back.
        assertFalse(SyncDocument.encode(merged).contains("z 9"))
    }

    @Test
    fun `apply makes the library match the merged document`() {
        val merged =
            SyncDocument(
                folders = listOf(SyncFolder("f2", "Tech", "code")),
                feeds = listOf(feed("c", title = "C", folderId = "f2"), feed("a"), feed("new")),
            )
        val applied = SyncMerge.apply(library, merged)
        assertEquals(listOf("c", "a", "new"), applied.feeds.map { it.url })
        assertEquals("C", applied.feeds.first().title)
        assertEquals("f2", applied.feeds.first().folderId)
        assertEquals(listOf("code"), applied.folders.map { it.icon })
        // b was removed with its articles; read state isn't the document's (per-item sync).
        assertEquals(setOf("a 1", "c 1"), applied.articles.map { it.key }.toSet())
        assertTrue(applied.article("a 1")!!.read)
        assertFalse(applied.article("c 1")!!.read)
        assertTrue(SyncMerge.hasNewFeeds(library, merged))
        assertFalse(SyncMerge.hasNewFeeds(applied, merged))
    }

    @Test
    fun `syncing the same state twice changes nothing`() {
        val doc = SyncMerge.localDocument(library)
        val merged = SyncMerge.merge(doc, doc, doc)
        assertEquals(doc, merged)
        assertEquals(library, SyncMerge.apply(library, merged))
    }

    @Test
    fun `settings merge per setting, this device winning a setting both changed`() {
        val b = base.copy(settings = mapOf("theme" to "system", "fullArticles" to "true", "showImages" to "true", "instapaper.token" to "t"))
        val local = b.copy(settings = b.settings + ("theme" to "dark"))
        val remote = b.copy(settings = b.settings + mapOf("theme" to "light", "fullArticles" to "false") - "instapaper.token")
        val merged = SyncMerge.merge(b, local, remote).settings
        assertEquals("dark", merged["theme"])
        assertEquals("false", merged["fullArticles"])
        assertEquals("true", merged["showImages"])
        assertFalse("signed out elsewhere", "instapaper.token" in merged)
    }

    @Test
    fun `a device without synced settings takes the file's instead of its defaults`() {
        val local = base.copy(settings = mapOf("theme" to "system", "listView" to "medium"))
        val remote = base.copy(settings = mapOf("theme" to "dark", "instapaper.token" to "t"))
        assertEquals(
            mapOf("theme" to "dark", "listView" to "medium", "instapaper.token" to "t"),
            SyncMerge.merge(base, local, remote).settings,
        )
        assertEquals(mapOf("theme" to "dark", "listView" to "medium", "instapaper.token" to "t"), SyncMerge.merge(null, local, remote).settings)
    }
}
