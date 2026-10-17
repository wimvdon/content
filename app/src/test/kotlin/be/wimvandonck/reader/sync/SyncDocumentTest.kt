package be.wimvandonck.reader.sync

import be.wimvandonck.reader.data.Article
import be.wimvandonck.reader.data.Feed
import be.wimvandonck.reader.data.Folder
import be.wimvandonck.reader.data.Library
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncDocumentTest {
    private val a = "https://a.example.com/feed"
    private val b = "https://b.example.com/feed"

    private val doc =
        SyncDocument(
            folders = listOf(SyncFolder("f1", "News", "newspaper")),
            feeds =
                listOf(
                    SyncFeed(a, "A", renamed = true, folderId = "f1", siteUrl = "https://a.example.com", iconUrl = null),
                    SyncFeed(b, "B", renamed = false, folderId = null, siteUrl = null, iconUrl = "https://b/icon.png"),
                ),
            read = mapOf("$a 1" to 100L, "$b x y" to 200L),
        )

    @Test
    fun `encodes and decodes the same document`() {
        assertEquals(doc, SyncDocument.decode(SyncDocument.encode(doc)))
    }

    @Test
    fun `leaves alone files that aren't sync files or are newer`() {
        assertNull(SyncDocument.decode("not json"))
        assertNull(SyncDocument.decode("""{"format":"other","version":1}"""))
        assertNull(SyncDocument.decode("""{"format":"rss-reader-sync","version":3}"""))
    }

    @Test
    fun `settings go into the file and back, and files without them still read`() {
        val doc = SyncDocument(settings = mapOf("theme" to "dark", "reader.textSize" to "20"))
        val text = SyncDocument.encode(doc)
        assertTrue(text.contains("\"version\":2"))
        assertEquals(doc, SyncDocument.decode(text))
        assertEquals(emptyMap<String, String>(), SyncDocument.decode("""{"format":"rss-reader-sync","version":1}""")!!.settings)
        assertEquals(
            mapOf("theme" to "light"),
            SyncDocument.decode("""{"format":"rss-reader-sync","version":2,"settings":{"theme":"light","bad":5}}""")!!.settings,
        )
    }

    @Test
    fun `skips broken entries and unknown folders`() {
        val text =
            """{"format":"rss-reader-sync","version":1,
              "folders":[{"id":"f1","title":"News","icon":"newspaper"},{"id":"","title":"x"},{"id":"f2","title":" "}],
              "feeds":[{"url":"$a","title":"A","folderId":"gone"},{"title":"no url"},5],
              "read":{"$a 1":100,"$a 2":"bad"}}"""
        val decoded = SyncDocument.decode(text)!!
        assertEquals(listOf("f1"), decoded.folders.map { it.id })
        assertEquals(listOf(SyncFeed(a, "A", false, null, null, null)), decoded.feeds)
        assertEquals(mapOf("$a 1" to 100L), decoded.read)
    }

    @Test
    fun `of takes folders, feeds and read articles from the library`() {
        val library =
            Library(
                feeds = listOf(Feed(a, "A", folderId = "f1")),
                folders = listOf(Folder("f1", "News")),
                articles =
                    listOf(
                        Article(a, "1", "t", null, null, "", publishedAt = 50, fetchedAt = 60, read = true),
                        Article(a, "2", "t", null, null, "", publishedAt = null, fetchedAt = 70, read = false),
                    ),
            )
        val of = SyncDocument.of(library)
        assertEquals(listOf("f1"), of.folders.map { it.id })
        assertEquals(listOf(a), of.feeds.map { it.url })
        assertEquals(mapOf("$a 1" to 50L), of.read)
    }

    @Test
    fun `pruning drops old read marks and those of removed feeds`() {
        val withExtra = doc.copy(read = doc.read + ("https://gone/feed 1" to 500L))
        assertEquals(mapOf("$b x y" to 200L), withExtra.pruned(keepSince = 150).read)
    }

    @Test
    fun `article keys give their feed`() {
        assertEquals(b, SyncDocument.feedUrlOf("$b x y"))
    }

    @Test
    fun `writes exactly the text the web app writes for the same file`() {
        // The same text is checked in web/test/sync.test.ts.
        val web =
            """{"format":"rss-reader-sync","version":2,"folders":[{"id":"f1","title":"News","icon":"newspaper"}],""" +
                """"feeds":[{"url":"https://a.example/feed","title":"A","renamed":true,"folderId":"f1","siteUrl":"https://a.example","iconUrl":null}],""" +
                """"read":{"https://a.example/feed 1":100,"https://a.example/feed 2":200},"settings":{"reader.textSize":"20","theme":"dark"}}"""
        assertEquals(web, SyncDocument.encode(SyncDocument.decode(web)!!))
    }
}
