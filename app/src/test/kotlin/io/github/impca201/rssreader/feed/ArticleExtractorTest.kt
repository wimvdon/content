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
    fun `a div per paragraph becomes a paragraph`() {
        val html =
            page(
                """
                <div class="article-body">
                  <div class="paragraph">One. $paragraph</div>
                  <div class="paragraph">Two, with <a href="/x">a link</a>. $paragraph</div>
                  <div class="paragraph"><span>Three.</span> $paragraph<br>Same paragraph.</div>
                </div>
                """,
            )
        val article = ArticleExtractor.extract(html, "https://example.com/")!!
        assertTrue(article, article.contains("<p>One. $paragraph</p>"))
        assertTrue(article, article.contains("<p>Two, with <a href=\"https://example.com/x\">a link</a>. $paragraph</p>"))
        assertTrue(article, article.contains("<p>Three. $paragraph<br>Same paragraph.</p>"))
        val blocks = ArticleBlocks.split(article, "https://example.com/")
        assertEquals(3, blocks.size)
    }

    @Test
    fun `utility class names don't hide the article`() {
        val html =
            page(
                """
                <div class="relative overflow-hidden shadow-lg">
                  <div class="text-lg leading-7 md:hidden-none">
                    <p>$paragraph</p><p>$paragraph</p>
                    <p class="md:hidden">Visible on phones. $paragraph</p>
                    <p class="hidden">Hidden everywhere. $paragraph</p>
                    <div class="share-buttons"><p>Share this article with friends on every network there is.</p></div>
                  </div>
                </div>
                """,
            )
        val article = ArticleExtractor.extract(html, "https://example.com/")!!
        assertTrue(article.contains("Visible on phones."))
        assertFalse(article.contains("Hidden everywhere."))
        assertFalse(article.contains("Share this"))
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
    fun `keeps every paragraph when each sits in boxes of its own (VRT NWS)`() {
        val body =
            (1..8).joinToString("") { n -> """<div class=""><div class="flex flex-col gap-4"><p>Body $n. $paragraph</p></div></div>""" }
        val html =
            page(
                """
                <article class="grid"><div class="min-w-0"><div class="flex flex-col"><div class="flex flex-col gap-3">
                  <div class="pb-4"><div class="flex flex-col">
                    <h1>Title</h1>
                    <div class="order-5 prose-article-body-r"><p>Lead paragraph. $paragraph $paragraph</p></div>
                  </div></div>
                  <div class="pb-4"><div class="bg-primary"><div class="prose-article-body-m">
                    <ul class="prose-article-body-r"><li>Summary one. $paragraph</li><li>Summary two. $paragraph</li><li>Summary three. $paragraph</li></ul>
                  </div></div></div>
                  $body
                </div></div></div></article>
                """,
            )
        val article = ArticleExtractor.extract(html, "https://example.com/")!!
        assertTrue(article.contains("Lead paragraph"))
        for (n in 1..8) assertTrue("Body $n is missing", article.contains("Body $n."))
    }

    @Test
    fun `joins an article split into look-alike containers with ads in between (Ars Technica)`() {
        fun part(name: String) =
            """
            <div class="my-2.5 mx-auto"><div class="relative">
              <div class="post-content post-content-double"><p>$name. $paragraph</p><p>$paragraph</p><p>$paragraph</p></div>
            </div><div class="hidden lg:block"><div class="ad ad--rail"></div></div></div>
            <div class="ad-wrapper"><div class="ad ad--mid-content"><p>Advertisement text that is long enough to count.</p></div></div>
            """
        val html = page("<main><article class=\"double-column\">${part("First part")}${part("Second part")}${part("Third part")}</article></main>")
        val article = ArticleExtractor.extract(html, "https://example.com/")!!
        assertTrue(article.contains("First part"))
        assertTrue(article.contains("Second part"))
        assertTrue(article.contains("Third part"))
        assertFalse(article.contains("Advertisement"))
    }

    @Test
    fun `keeps words that a site puts in buttons inside the text`() {
        val html = page("""<article><p>At 280 <button type="button">light years</button> from here. $paragraph $paragraph $paragraph</p><button>Share</button></article>""")
        val article = ArticleExtractor.extract(html, "https://example.com/")!!
        assertTrue(article.contains("At 280 light years from here."))
        assertFalse(article.contains("Share"))
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
