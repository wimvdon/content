package io.github.impca201.rssreader.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpmlTest {
    @Test
    fun `reads feeds from folders and skips duplicates and non-feeds`() {
        val opml =
            """
            <?xml version="1.0"?>
            <opml version="1.0"><head><title>Subs</title></head><body>
              <outline text="Tech">
                <outline type="rss" text="Blog A" xmlUrl="http://a.example.com/feed"/>
                <outline type="rss" title="Blog B" text="ignored" xmlUrl="https://b.example.com/rss"/>
              </outline>
              <outline type="rss" text="Blog A again" xmlUrl="https://a.example.com/feed"/>
              <outline text="Just a link" htmlUrl="https://c.example.com"/>
            </body></opml>
            """.trimIndent()
        assertEquals(
            listOf(
                Opml.Entry("https://a.example.com/feed", "Blog A"),
                Opml.Entry("https://b.example.com/rss", "Blog B"),
            ),
            Opml.parse(opml.toByteArray()),
        )
    }

    @Test
    fun `what it writes, it reads back`() {
        val entries = listOf(Opml.Entry("https://e.com/feed?a=1&b=2", "Tom & Jerry's \"news\""))
        val written = Opml.write("My feeds", entries)
        assertTrue(written.contains("xmlUrl=\"https://e.com/feed?a=1&amp;b=2\""))
        assertEquals(entries, Opml.parse(written.toByteArray()))
    }

    @Test
    fun `other files give no feeds`() {
        assertEquals(emptyList<Opml.Entry>(), Opml.parse("not xml".toByteArray()))
        assertEquals(emptyList<Opml.Entry>(), Opml.parse("<rss><channel/></rss>".toByteArray()))
    }
}
