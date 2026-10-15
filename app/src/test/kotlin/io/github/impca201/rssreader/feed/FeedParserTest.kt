package io.github.impca201.rssreader.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedParserTest {
    private fun parse(
        xml: String,
        base: String = "https://example.com/feed.xml",
    ) = FeedParser.parse(xml.toByteArray(), base)

    @Test
    fun `reads an RSS 2 feed`() {
        val feed =
            parse(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/" xmlns:dc="http://purl.org/dc/elements/1.1/">
                  <channel>
                    <title>Example &amp; Co</title>
                    <link>https://example.com/</link>
                    <item>
                      <title>First post</title>
                      <link>https://example.com/first</link>
                      <guid isPermaLink="false">post-1</guid>
                      <pubDate>Tue, 03 Jun 2008 11:05:30 GMT</pubDate>
                      <dc:creator>Ann</dc:creator>
                      <description>Short</description>
                      <content:encoded><![CDATA[<p>The <b>full</b> text</p>]]></content:encoded>
                    </item>
                  </channel>
                </rss>
                """.trimIndent(),
            )
        assertEquals("Example & Co", feed.title)
        assertEquals("https://example.com/", feed.siteUrl)
        val item = feed.items.single()
        assertEquals("post-1", item.id)
        assertEquals("First post", item.title)
        assertEquals("https://example.com/first", item.link)
        assertEquals("Ann", item.author)
        assertEquals("<p>The <b>full</b> text</p>", item.html)
        assertEquals(1_212_491_130_000L, item.publishedAt)
    }

    @Test
    fun `uses a permalink guid as the link and the link as the id`() {
        val feed =
            parse(
                """
                <rss version="2.0"><channel><title>T</title>
                  <item><title>A</title><guid>https://example.com/a</guid></item>
                  <item><title>B</title><link>/b</link></item>
                </channel></rss>
                """.trimIndent(),
            )
        assertEquals("https://example.com/a", feed.items[0].link)
        assertEquals("https://example.com/b", feed.items[1].link)
        assertEquals("https://example.com/b", feed.items[1].id)
    }

    @Test
    fun `ignores elements from other namespaces with the same name`() {
        val feed =
            parse(
                """
                <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/"><channel><title>T</title>
                  <item><media:title>Wrong</media:title><title>Right</title><link>https://example.com/x</link></item>
                </channel></rss>
                """.trimIndent(),
            )
        assertEquals("Right", feed.items.single().title)
    }

    @Test
    fun `reads an RSS 1 (RDF) feed`() {
        val feed =
            parse(
                """
                <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" xmlns="http://purl.org/rss/1.0/"
                    xmlns:dc="http://purl.org/dc/elements/1.1/">
                  <channel><title>RDF feed</title><link>https://example.org/</link></channel>
                  <item><title>One</title><link>https://example.org/1</link><dc:date>2024-05-01T10:00:00+02:00</dc:date></item>
                </rdf:RDF>
                """.trimIndent(),
            )
        assertEquals("RDF feed", feed.title)
        assertEquals("One", feed.items.single().title)
        assertEquals(1_714_550_400_000L, feed.items.single().publishedAt)
    }

    @Test
    fun `reads an Atom feed with relative links and xhtml content`() {
        val feed =
            parse(
                """
                <feed xmlns="http://www.w3.org/2005/Atom" xml:base="https://blog.example.com/">
                  <title type="html">My &lt;i&gt;blog&lt;/i&gt;</title>
                  <link rel="self" href="/atom.xml"/>
                  <link href="/"/>
                  <author><name>Bo</name></author>
                  <entry>
                    <id>tag:example,2024:1</id>
                    <title>Hello</title>
                    <link rel="alternate" href="posts/hello"/>
                    <updated>2024-01-02T03:04:05Z</updated>
                    <content type="xhtml"><div xmlns="http://www.w3.org/1999/xhtml"><p>Hi <a href="x">there</a></p></div></content>
                  </entry>
                </feed>
                """.trimIndent(),
            )
        assertEquals("My blog", feed.title)
        assertEquals("https://blog.example.com/", feed.siteUrl)
        val entry = feed.items.single()
        assertEquals("tag:example,2024:1", entry.id)
        assertEquals("https://blog.example.com/posts/hello", entry.link)
        assertEquals("Bo", entry.author)
        assertEquals("<p>Hi <a href=\"x\">there</a></p>", entry.html)
        assertEquals(1_704_164_645_000L, entry.publishedAt)
    }

    @Test
    fun `escapes plain text Atom content`() {
        val feed =
            parse(
                """
                <feed xmlns="http://www.w3.org/2005/Atom"><title>T</title>
                  <entry><id>1</id><title>A</title><summary>1 &lt; 2
                and more</summary></entry>
                </feed>
                """.trimIndent(),
            )
        assertEquals("1 &lt; 2<br>and more", feed.items.single().html)
    }

    @Test
    fun `accepts HTML entities and leading whitespace that strict XML rejects`() {
        val feed = parse("\n  <rss version=\"2.0\"><channel><title>Caf&eacute;&nbsp;news &mdash; daily</title></channel></rss>")
        assertEquals("Café news — daily", feed.title)
    }

    @Test
    fun `gives items without guid or link a stable id`() {
        val xml = "<rss><channel><title>T</title><item><title>Only a title</title></item></channel></rss>"
        assertEquals(parse(xml).items.single().id, parse(xml).items.single().id)
        assertTrue(parse(xml).items.single().id.startsWith("hash:"))
    }

    @Test
    fun `uses the start of the text when an item has no title`() {
        val feed = parse("<rss><channel><title>T</title><item><description>Just a note</description></item></channel></rss>")
        assertEquals("Just a note", feed.items.single().title)
    }

    @Test(expected = NotAFeedException::class)
    fun `rejects a web page`() {
        parse("<!DOCTYPE html><html><head><title>Page</title></head><body><p>Hi<br></p></body></html>")
    }

    @Test(expected = NotAFeedException::class)
    fun `rejects other XML`() {
        parse("<sitemap><url>https://example.com</url></sitemap>")
    }

    @Test
    fun `does not read local files named in the document`() {
        val xml =
            """
            <?xml version="1.0"?>
            <!DOCTYPE rss [<!ENTITY xxe SYSTEM "file:///etc/hostname">]>
            <rss><channel><title>A&xxe;B</title></channel></rss>
            """.trimIndent()
        val title =
            try {
                parse(xml).title
            } catch (_: NotAFeedException) {
                null
            }
        assertTrue("title was $title", title == null || title == "AB")
    }

    @Test
    fun `finds item pictures in Media RSS, enclosures and the text`() {
        val feed =
            parse(
                """
                <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/"><channel><title>T</title>
                  <item><title>Media</title><link>https://e.com/1</link>
                    <media:content url="https://e.com/small.jpg" medium="image" width="300"/>
                    <media:content url="https://e.com/big.jpg" medium="image" width="1200"/>
                    <media:content url="https://e.com/clip.mp4" medium="video"/>
                  </item>
                  <item><title>Group</title><link>https://e.com/2</link>
                    <media:group><media:thumbnail url="http://e.com/thumb.jpg"/></media:group>
                  </item>
                  <item><title>Enclosure</title><link>https://e.com/3</link>
                    <enclosure url="https://e.com/podcast.mp3" type="audio/mpeg"/>
                    <enclosure url="https://e.com/cover.png" type="image/png"/>
                  </item>
                  <item><title>Text</title><link>https://e.com/posts/4</link>
                    <description><![CDATA[<p>Hi <img src="pic.webp"></p>]]></description>
                  </item>
                  <item><title>None</title><link>https://e.com/5</link><description>Just text</description></item>
                </channel></rss>
                """.trimIndent(),
            )
        assertEquals(
            listOf("https://e.com/big.jpg", "https://e.com/thumb.jpg", "https://e.com/cover.png", "https://e.com/posts/pic.webp", null),
            feed.items.map { it.imageUrl },
        )
    }

    @Test
    fun `finds Atom enclosure pictures`() {
        val feed =
            parse(
                """
                <feed xmlns="http://www.w3.org/2005/Atom"><title>T</title>
                  <entry><id>1</id><title>A</title><link href="https://e.com/a"/>
                    <link rel="enclosure" type="image/jpeg" href="/img/a.jpg"/></entry>
                </feed>
                """.trimIndent(),
            )
        assertEquals("https://e.com/img/a.jpg", feed.items.single().imageUrl)
    }

    @Test
    fun `keeps a missing date empty`() {
        val feed = parse("<rss><channel><title>T</title><item><title>A</title><link>https://e.com/a</link></item></channel></rss>")
        assertNull(feed.items.single().publishedAt)
    }
}
