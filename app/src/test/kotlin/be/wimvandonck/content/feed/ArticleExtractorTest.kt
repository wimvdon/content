package be.wimvandonck.content.feed

import be.wimvandonck.content.data.needsFullText
import be.wimvandonck.content.sync.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The plain parts around the article extractor. Finding the article itself is the web app's code,
 * which the WebView runs; its tests (web/test/articleExtractor.test.ts, with the sample pages in
 * testdata/articles) cover both apps.
 */
class ArticleExtractorTest {
    @Test
    fun `the script hands the page over as JavaScript strings`() {
        val html = "<p>\"Quotes\", a backslash \\ and a\nnew line</p></script>"
        val script = ArticleExtractor.script(html, "https://example.com/a?b=\"c\"")
        assertTrue(script.startsWith("extractArticle("))
        assertTrue(script.endsWith(")"))
        val args = Json.parse("[" + script.removePrefix("extractArticle(").removeSuffix(")") + "]")
        assertEquals(listOf(html, "https://example.com/a?b=\"c\""), args)
    }

    @Test
    fun `reads the article from the WebView's result`() {
        assertEquals("<p>Café & \"more\"</p>", ArticleExtractor.result("\"<p>Café & \\\"more\\\"</p>\""))
        assertEquals("<p>é</p>", ArticleExtractor.result("\"<p>\\u00e9</p>\""))
        // No article, an error in the page (null), or something unexpected.
        assertNull(ArticleExtractor.result("null"))
        assertNull(ArticleExtractor.result(null))
        assertNull(ArticleExtractor.result("\"  \""))
        assertNull(ArticleExtractor.result("42"))
        assertNull(ArticleExtractor.result("not json"))
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

    @Test
    fun `a long teaser that links to its own article counts as a summary`() {
        val link = "https://example.com/2026/10/story/"
        val teaser = "<p>${"word ".repeat(400)}</p><p><a href=\"$link\">Read full article</a></p><p><a href=\"$link#comments\">Comments</a></p>"
        assertTrue(needsFullText(teaser, link))
        assertTrue(needsFullText(teaser.replace("https://example.com", "http://www.example.com"), link))
        // Links elsewhere don't make a long text a summary.
        assertFalse(needsFullText("<p>${"word ".repeat(400)}</p><p><a href=\"https://example.com/other\">More</a></p>", link))
    }
}
