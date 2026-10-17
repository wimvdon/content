package io.github.impca201.rssreader.data

import io.github.impca201.rssreader.feed.FetchError
import io.github.impca201.rssreader.feed.FetchException
import io.github.impca201.rssreader.feed.Fetched
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FullArticlesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val link = "https://example.com/story"
    private val paragraph = "<p>" + "A sentence of the article, long enough to count. ".repeat(4) + "</p>"
    private val page = "<html><body><article>${paragraph.repeat(4)}</article></body></html>"

    private var downloads = 0

    private fun articles(dir: File) =
        FullArticles(dir) { url ->
            downloads++
            if (url == link) Fetched(url, page.toByteArray()) else throw FetchException(FetchError.NotFound)
        }

    @Test
    fun `a loaded article is kept and not downloaded again`() {
        val dir = folder.newFolder("articles")
        val first = runBlocking { articles(dir).load(link) }
        val second = runBlocking { articles(dir).load(link) }
        assertTrue(first is FullArticle.Loaded)
        assertEquals(first, second)
        assertEquals(1, downloads)
    }

    @Test
    fun `articles kept by an older version of the extractor are extracted again and removed`() {
        val dir = folder.newFolder("articles")
        // How the cache named articles before the extractor got a version, and with an older version.
        val unversioned = File(dir, "0123456789abcdef.html").apply { writeText("<p>Cut-off text</p>") }
        val old = File(dir, "v2-0123456789abcdef.html").apply { writeText("<p>Cut-off text</p>") }
        val result = runBlocking { articles(dir).load(link) }
        assertEquals(1, downloads)
        assertTrue((result as FullArticle.Loaded).html.contains("A sentence of the article"))
        assertFalse(unversioned.exists())
        assertFalse(old.exists())
    }

    @Test
    fun `a page that can't be downloaded fails`() {
        val result = runBlocking { articles(folder.newFolder("articles")).load("https://example.com/missing") }
        assertEquals(FullArticle.Failed, result)
    }
}
