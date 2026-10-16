package io.github.impca201.rssreader.sync

import io.github.impca201.rssreader.data.Article
import io.github.impca201.rssreader.data.Feed
import io.github.impca201.rssreader.data.Folder
import io.github.impca201.rssreader.data.Library
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
            read = mapOf("a 1" to 10L),
        )

    @Test
    fun `changes on either side are kept`() {
        val local = base.copy(feeds = base.feeds.map { if (it.url == "a") it.copy(title = "A!", renamed = true) else it })
        val remote = base.copy(feeds = base.feeds + feed("d"), read = base.read + ("b 1" to 20L))
        val merged = SyncMerge.merge(base, local, remote)
        assertEquals(listOf("a", "b", "c", "d"), merged.feeds.map { it.url })
        assertEquals("A!", merged.feeds.first().title)
        assertEquals(setOf("a 1", "b 1"), merged.read.keys)
    }

    @Test
    fun `removals on either side are kept`() {
        val local = base.copy(feeds = base.feeds.filterNot { it.url == "b" })
        val remote = base.copy(folders = base.folders.filterNot { it.id == "f1" }, read = emptyMap())
        val merged = SyncMerge.merge(base, local, remote)
        assertEquals(listOf("a", "c"), merged.feeds.map { it.url })
        assertEquals(listOf("f2"), merged.folders.map { it.id })
        // Its folder is gone, so the feed is in no folder.
        assertEquals(null, merged.feeds.first().folderId)
        assertTrue(merged.read.isEmpty())
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
        val local = SyncDocument(feeds = listOf(feed("a"), feed("x")), read = mapOf("x 1" to 1L))
        val remote = SyncDocument(feeds = listOf(feed("a", title = "Remote"), feed("y")), read = mapOf("y 1" to 2L))
        val merged = SyncMerge.merge(null, local, remote)
        assertEquals(listOf("a", "y", "x"), merged.feeds.map { it.url })
        assertEquals("a", merged.feeds.first().title)
        assertEquals(setOf("x 1", "y 1"), merged.read.keys)
    }

    @Test
    fun `unread again on this device wins over the old read mark`() {
        val local = base.copy(read = emptyMap())
        assertTrue(SyncMerge.merge(base, local, base).read.isEmpty())
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
    fun `articles this device doesn't have count as unchanged`() {
        val withMissing = base.copy(read = base.read + ("z 9" to 5L))
        val local = SyncMerge.localDocument(library, withMissing)
        assertEquals(setOf("a 1", "z 9"), local.read.keys)
    }

    @Test
    fun `apply makes the library match the merged document`() {
        val merged =
            SyncDocument(
                folders = listOf(SyncFolder("f2", "Tech", "code")),
                feeds = listOf(feed("c", title = "C", folderId = "f2"), feed("a"), feed("new")),
                read = mapOf("c 1" to 30L),
            )
        val applied = SyncMerge.apply(library, merged)
        assertEquals(listOf("c", "a", "new"), applied.feeds.map { it.url })
        assertEquals("C", applied.feeds.first().title)
        assertEquals("f2", applied.feeds.first().folderId)
        assertEquals(listOf("code"), applied.folders.map { it.icon })
        // b was removed with its articles; a's article is unread again, c's is read.
        assertEquals(setOf("a 1", "c 1"), applied.articles.map { it.key }.toSet())
        assertFalse(applied.article("a 1")!!.read)
        assertTrue(applied.article("c 1")!!.read)
        assertTrue(SyncMerge.hasNewFeeds(library, merged))
        assertFalse(SyncMerge.hasNewFeeds(applied, merged))
    }

    @Test
    fun `syncing the same state twice changes nothing`() {
        val doc = SyncMerge.localDocument(library, null)
        val merged = SyncMerge.merge(doc, doc, doc)
        assertEquals(doc, merged)
        assertEquals(library, SyncMerge.apply(library, merged))
    }
}
