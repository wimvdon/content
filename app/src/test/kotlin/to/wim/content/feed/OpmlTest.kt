package to.wim.content.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpmlTest {
    @Test
    fun `reads feeds with their folders and skips duplicates and non-feeds`() {
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
                Opml.Entry("https://a.example.com/feed", "Blog A", folder = "Tech"),
                Opml.Entry("https://b.example.com/rss", "Blog B", folder = "Tech"),
            ),
            Opml.parse(opml.toByteArray()),
        )
    }

    @Test
    fun `reads an Inoreader export with its folders`() {
        // Shape of Inoreader's "Export to OPML": folders as outlines, http addresses, extra attributes.
        val opml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="1.0">
              <head><title>Subscriptions of Wim from Inoreader [https://www.inoreader.com]</title></head>
              <body>
                <outline text="News" title="News">
                  <outline text="The Verge" title="The Verge" type="rss" xmlUrl="http://www.theverge.com/rss/index.xml" htmlUrl="https://www.theverge.com/"/>
                </outline>
                <outline text="Daring Fireball" title="Daring Fireball" type="rss" xmlUrl="https://daringfireball.net/feeds/main" htmlUrl="https://daringfireball.net/"/>
              </body>
            </opml>
            """.trimIndent()
        assertEquals(
            listOf(
                Opml.Entry("https://www.theverge.com/rss/index.xml", "The Verge", folder = "News"),
                Opml.Entry("https://daringfireball.net/feeds/main", "Daring Fireball"),
            ),
            Opml.parse(opml.toByteArray()),
        )
    }

    @Test
    fun `what it writes, it reads back`() {
        val entries =
            listOf(
                Opml.Entry("https://e.com/feed?a=1&b=2", "Tom & Jerry's \"news\""),
                Opml.Entry("https://f.com/feed", "F", folder = "Fun & games"),
                Opml.Entry("https://g.com/feed", "G", folder = "Fun & games"),
            )
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
