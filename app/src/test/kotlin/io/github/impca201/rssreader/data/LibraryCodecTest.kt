package io.github.impca201.rssreader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryCodecTest {
    private val library =
        Library(
            feeds =
                listOf(
                    Feed("https://a.example.com/feed", "Tabs\tand\nlines", "https://a.example.com"),
                    Feed("https://b.example.com/feed", "B", null),
                ),
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
