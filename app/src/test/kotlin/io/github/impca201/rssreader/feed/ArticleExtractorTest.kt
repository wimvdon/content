package io.github.impca201.rssreader.feed

import io.github.impca201.rssreader.data.needsFullText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleExtractorTest {
    private val paragraph =
        "This is a sentence of the real article, with enough words, commas and length to count as a paragraph of text."

    private fun page(body: String) =
        """
        <!DOCTYPE html>
        <html><head><title>Page</title><script>var x = "<p>not text</p>";</script><style>p { color: red }</style></head>
        <body>$body</body></html>
        """.trimIndent()

    @Test
    fun `keeps the article and drops menus, sidebars, comments and scripts`() {
        val html =
            page(
                """
                <header><nav><a href="/">Home</a> <a href="/about">About</a></nav></header>
                <div class="layout">
                  <aside class="sidebar"><p>Sidebar text that is long enough to look like a paragraph, almost.</p></aside>
                  <article>
                    <h1>Title</h1>
                    <p>$paragraph</p>
                    <p>Second: <a href="/more">a <b>relative</b> link</a>, and <em>emphasis</em>. $paragraph</p>
                    <figure><img src="x.jpg"><figcaption>Caption</figcaption></figure>
                    <ul><li>One point<li>Another point</ul>
                    <script>track()</script>
                  </article>
                  <div id="comments"><p>A comment that should not be in the article, even if it is long enough.</p></div>
                </div>
                <footer><p>Copyright notice and other footer text that nobody reads at all.</p></footer>
                """,
            )
        val article = ArticleExtractor.extract(html, "https://example.com/posts/1")!!
        assertTrue(article.contains("<p>$paragraph</p>"))
        assertTrue(article.contains("<a href=\"https://example.com/more\">a <b>relative</b> link</a>"))
        assertTrue(article.contains("<em>emphasis</em>"))
        assertTrue(article.contains("<li>One point</li><li>Another point</li>"))
        // Pictures stay, with absolute addresses and their captions.
        assertTrue(article.contains("<figure><img src=\"https://example.com/posts/x.jpg\"><figcaption>Caption</figcaption></figure>"))
        listOf("Home", "Sidebar", "comment", "Copyright", "track()", "not text", "color: red").forEach {
            assertFalse("should not contain $it", article.contains(it))
        }
    }

    @Test
    fun `finds the article without an article tag by its paragraphs`() {
        val html =
            page(
                """
                <div class="menu"><a href="/a">Link one</a><a href="/b">Link two</a></div>
                <div class="content-wrapper"><div class="post-body">
                  <p>$paragraph</p><p>$paragraph</p><p>$paragraph</p>
                </div></div>
                <div class="related"><p>Related story with a long enough title to be a paragraph, really.</p></div>
                """,
            )
        val article = ArticleExtractor.extract(html, "https://example.com/")!!
        assertEquals(3, Regex("<p>").findAll(article).count())
        assertFalse(article.contains("Related"))
    }

    @Test
    fun `keeps sections split over sibling containers`() {
        val html =
            page(
                """
                <main>
                  <section><p>First part. $paragraph</p><p>$paragraph</p></section>
                  <section><p>Second part. $paragraph</p><p>$paragraph</p></section>
                </main>
                """,
            )
        val article = ArticleExtractor.extract(html, "https://example.com/")!!
        assertTrue(article.contains("First part"))
        assertTrue(article.contains("Second part"))
    }

    @Test
    fun `closes paragraphs that the page leaves open and decodes entities once`() {
        val html = page("<article><p>Caf&eacute; &amp; more. $paragraph $paragraph<p>Next &lt;tag&gt;. $paragraph</article>")
        val article = ArticleExtractor.extract(html, "https://example.com/")!!
        assertTrue(article.contains("<p>Café &amp; more."))
        assertTrue(article.contains("<p>Next &lt;tag&gt;."))
    }

    @Test
    fun `drops script links and in-page anchors but keeps their text`() {
        val html = page("<article><p><a href=\"javascript:evil()\">Click</a> <a href=\"#top\">Top</a> $paragraph $paragraph $paragraph</p></article>")
        val article = ArticleExtractor.extract(html, "https://example.com/")!!
        assertFalse(article.contains("href"))
        assertTrue(article.contains("Click Top"))
    }

    @Test
    fun `keeps video embeds and drops other iframes`() {
        val html =
            page(
                """
                <article><p>$paragraph</p>
                  <iframe src="https://www.youtube.com/embed/dQw4w9WgXcQ?rel=0" width="560"></iframe>
                  <iframe src="https://ads.example.net/slot/123"></iframe>
                  <video poster="/poster.jpg"><source src="/clip.mp4" type="video/mp4"></video>
                  <p>$paragraph</p><p>$paragraph</p></article>
                """,
            )
        val article = ArticleExtractor.extract(html, "https://example.com/a/")!!
        assertTrue(article.contains("<iframe src=\"https://www.youtube.com/embed/dQw4w9WgXcQ?rel=0\"></iframe>"))
        assertFalse(article.contains("ads.example.net"))
        assertTrue(article.contains("<video src=\"https://example.com/clip.mp4\" poster=\"https://example.com/poster.jpg\"></video>"))
    }

    @Test
    fun `returns null for pages without an article`() {
        assertNull(ArticleExtractor.extract(page("<nav><a href=\"/\">Home</a></nav><p>Too short.</p>"), "https://example.com/"))
        assertNull(ArticleExtractor.extract("", "https://example.com/"))
        assertNull(ArticleExtractor.extract("not < html > at all", "https://example.com/"))
    }

    @Test
    fun `decodes pages in the charset they declare`() {
        val bytes = "<meta charset=\"iso-8859-1\"><p>Café</p>".toByteArray(Charsets.ISO_8859_1)
        assertTrue(ArticleExtractor.decode(bytes).contains("Café"))
        assertTrue(ArticleExtractor.decode("<p>Café</p>".toByteArray()).contains("Café"))
    }

    @Test
    fun `only short feed texts count as summaries`() {
        assertTrue(needsFullText("<p>A short summary.</p>"))
        assertFalse(needsFullText("<p>${"word ".repeat(400)}</p>"))
    }
}
