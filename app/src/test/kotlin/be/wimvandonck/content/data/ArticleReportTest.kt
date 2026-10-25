package be.wimvandonck.content.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

class ArticleReportTest {
    private fun params(url: String): Map<String, String> =
        URI(url).rawQuery.split('&').associate { part ->
            val (k, v) = part.split('=', limit = 2)
            k to URLDecoder.decode(v, "UTF-8")
        }

    @Test
    fun `opens a new issue with the link, the problem and the app`() {
        val report = ArticleReport.report("https://www.example.com/a?b=1&c=2", ArticleReport.Problem.JunkLeft, "  The footer stays.  ", "Android", "4.4.0")
        assertTrue(report.url.startsWith("https://github.com/wimvdon/content/issues/new?"))
        assertFalse(report.cut)
        val p = params(report.url)
        assertEquals("parser", p["labels"])
        assertEquals("Article cleanup: junk left in on example.com", p["title"])
        assertEquals("**Article:** https://www.example.com/a?b=1&c=2\n**Problem:** Junk left in\n**App:** Android 4.4.0\n\nThe footer stays.", p["body"])
    }

    @Test
    fun `leaves out an empty comment and cuts a long one`() {
        val empty = params(ArticleReport.report("https://example.com/a", ArticleReport.Problem.Other, " ", "web", "1").url)
        assertFalse(empty.getValue("body").endsWith("\n"))
        val long = params(ArticleReport.report("https://example.com/a", ArticleReport.Problem.Other, "x".repeat(5000), "web", "1").url)
        assertEquals(ArticleReport.MAX_COMMENT, long.getValue("body").substringAfter("\n\n").length)
    }

    @Test
    fun `spaces are encoded so the browser keeps them`() {
        val url = ArticleReport.report("https://example.com/a", ArticleReport.Problem.TextMissing, "a b", "web", "1").url
        assertFalse(url.contains('+'))
        assertFalse(url.contains(' '))
    }

    @Test
    fun `sends the text Content shows`() {
        val report = ArticleReport.report("https://example.com/a", ArticleReport.Problem.TextMissing, "", "Android", "5.0.0", "First paragraph.\n\nSecond.")
        assertFalse(report.cut)
        assertTrue(params(report.url).getValue("body").endsWith("**Text in Content:**\n\n````\nFirst paragraph.\n\nSecond.\n````"))
    }

    @Test
    fun `cuts a long text short at a word so the address fits`() {
        val text = (1..3000).joinToString(" ") { "word$it" }
        val report = ArticleReport.report("https://example.com/a", ArticleReport.Problem.TextMissing, "", "Android", "5.0.0", text)
        assertTrue(report.cut)
        assertTrue(report.url.length <= ArticleReport.MAX_URL)
        val body = params(report.url).getValue("body")
        assertTrue(body.contains("word1 word2"))
        assertTrue(body.contains(Regex("""word\d+${Regex.escape(ArticleReport.CUT_NOTE)}""")))
    }

    @Test
    fun `the text keeps one paragraph per block`() {
        assertEquals("Title\n\nOne two.\n\nA point", ArticleReport.textOf("<h2>Title</h2><p>One <b>two</b>.</p><ul><li>A point</li></ul>"))
    }
}
