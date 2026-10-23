package be.wimvandonck.reader.data

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
        val url = ArticleReport.url("https://www.example.com/a?b=1&c=2", ArticleReport.Problem.JunkLeft, "  The footer stays.  ", "Android", "4.4.0")
        assertTrue(url.startsWith("https://github.com/wimvdon/content/issues/new?"))
        val p = params(url)
        assertEquals("parser", p["labels"])
        assertEquals("Article cleanup: junk left in on example.com", p["title"])
        assertEquals("**Article:** https://www.example.com/a?b=1&c=2\n**Problem:** Junk left in\n**App:** Android 4.4.0\n\nThe footer stays.", p["body"])
    }

    @Test
    fun `leaves out an empty comment and cuts a long one`() {
        val empty = params(ArticleReport.url("https://example.com/a", ArticleReport.Problem.Other, " ", "web", "1"))
        assertFalse(empty.getValue("body").endsWith("\n"))
        val long = params(ArticleReport.url("https://example.com/a", ArticleReport.Problem.Other, "x".repeat(5000), "web", "1"))
        assertEquals(ArticleReport.MAX_COMMENT, long.getValue("body").substringAfter("\n\n").length)
    }

    @Test
    fun `spaces are encoded so the browser keeps them`() {
        val url = ArticleReport.url("https://example.com/a", ArticleReport.Problem.TextMissing, "a b", "web", "1")
        assertFalse(url.contains('+'))
        assertFalse(url.contains(' '))
    }
}
