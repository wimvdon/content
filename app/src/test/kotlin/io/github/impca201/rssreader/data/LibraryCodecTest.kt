package io.github.impca201.rssreader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryCodecTest {
    private val library =
        Library(
            feeds =
                listOf(
                    Feed("https://a.example.com/feed", "Tabs\tand\nlines", "https://a.example.com", iconUrl = "https://a.example.com/icon.png"),
                    Feed("https://b.example.com/feed", "B", null, folderId = "f1"),
                ),
            folders = listOf(Folder("f1", "Tech\tnews", "devices"), Folder("f2", "Empty")),
            articles =
                listOf(
                    Article(
                        feedUrl = "https://a.example.com/feed",
                        id = "id with \\ backslash",
                        title = "Title",
                        link = "https://a.example.com/1",
                        author = "Ann",
                        html = "<p>Line one\r\nLine\ttwo \\n not a newline</p>",
                        publishedAt = 1_700_000_000_000,
                        fetchedAt = 1_700_000_100_000,
                        read = true,
                        imageUrl = "https://a.example.com/1.jpg",
                    ),
                    Article(
                        feedUrl = "https://b.example.com/feed",
                        id = "2",
                        title = "",
                        link = null,
                        author = null,
                        html = "",
                        publishedAt = null,
                        fetchedAt = 5,
                    ),
                ),
        )

    @Test
    fun `a library survives encoding and decoding`() {
        assertEquals(library, LibraryCodec.decode(LibraryCodec.encode(library)))
    }

    @Test
    fun `files from before pictures existed still load`() {
        val old =
            "rss-reader-library 1\n" +
                "F\thttps://a.example.com/feed\tA\t\n" +
                "A\thttps://a.example.com/feed\t1\tTitle\thttps://a.example.com/1\t\t\t5\t1\t<p>x</p>\n"
        val article = LibraryCodec.decode(old).articles.single()
        assertEquals(null, article.imageUrl)
        assertEquals("<p>x</p>", article.html)
    }

    @Test
    fun `feeds in a folder that is missing from the file load without one`() {
        val text =
            "rss-reader-library 1\n" +
                "D\tf1\tTech\tSports\n" +
                "F\thttps://a.example.com/feed\tA\t\tf1\n" +
                "F\thttps://b.example.com/feed\tB\t\tgone\n"
        val decoded = LibraryCodec.decode(text)
        // Icons stored by v0.0.3 (a fixed list) become their Material Symbols names.
        assertEquals(listOf(Folder("f1", "Tech", "sports-soccer")), decoded.folders)
        assertEquals(listOf("f1", null), decoded.feeds.map { it.folderId })
    }

    @Test
    fun `empty and unknown input gives an empty library`() {
        assertEquals(Library(), LibraryCodec.decode(""))
        assertEquals(Library(), LibraryCodec.decode("something else\nF\turl\ttitle\t"))
    }

    @Test
    fun `broken lines and orphan articles are skipped`() {
        val text =
            LibraryCodec.encode(library) +
                "A\tbroken\n" +
                "X\tunknown record\n" +
                "A\thttps://gone.example.com/feed\t1\tT\t\t\t\t1\t0\t\n"
        assertEquals(library, LibraryCodec.decode(text))
    }
}
