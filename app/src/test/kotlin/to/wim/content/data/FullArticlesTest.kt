package to.wim.content.data

import to.wim.content.feed.FetchError
import to.wim.content.feed.FetchException
import to.wim.content.feed.Fetched
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
        FullArticles(
            dir,
            fetch = { url ->
                downloads++
                if (url == link) Fetched(url, page.toByteArray()) else throw FetchException(FetchError.NotFound)
            },
            // The WebView's extractor, standing in: the article is what is inside <article>.
            extract = { html, _ -> html.substringAfter("<article>", "").substringBefore("</article>").ifEmpty { null } },
        )

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

    /** A site with a paywall: its preview without the sign-in, the whole article with it. */
    private fun paywalled(
        dir: File,
        signedIn: () -> Boolean,
    ) = FullArticles(
        dir,
        fetch = { url ->
            downloads++
            val text = if (signedIn()) "Whole article" else "Preview"
            Fetched(url, "<article>$text</article>".toByteArray(), signedIn = signedIn())
        },
        extract = { html, _ -> html.substringAfter("<article>", "").substringBefore("</article>").ifEmpty { null } },
        signedIn = { signedIn() },
    )

    @Test
    fun `a preview kept before the sign-ins were known is not shown once signed in`() {
        val dir = folder.newFolder("articles")
        var signedIn = false
        // Shared from another app, downloaded before the sign-ins were read: the site's preview.
        assertEquals(FullArticle.Loaded("Preview"), runBlocking { paywalled(dir) { signedIn }.load(link) })
        signedIn = true
        assertEquals(FullArticle.Loaded("Whole article"), runBlocking { paywalled(dir) { signedIn }.load(link) })
        assertEquals(2, downloads)
        // The whole article is kept from then on.
        assertEquals(FullArticle.Loaded("Whole article"), runBlocking { paywalled(dir) { signedIn }.load(link) })
        assertEquals(2, downloads)
    }

    @Test
    fun `forgetting an article forgets it with and without the sign-in`() {
        val dir = folder.newFolder("articles")
        var signedIn = false
        runBlocking { paywalled(dir) { signedIn }.load(link) }
        signedIn = true
        runBlocking { paywalled(dir) { signedIn }.load(link) }
        assertEquals(2, dir.listFiles()?.size)
        runBlocking { paywalled(dir) { signedIn }.forget(link) }
        assertTrue(dir.listFiles().isNullOrEmpty())
    }

    @Test
    fun `a page without an article fails and is not kept`() {
        val dir = folder.newFolder("articles")
        val articles = FullArticles(dir, fetch = { url -> Fetched(url, "<p>Menu</p>".toByteArray()) }, extract = { _, _ -> null })
        assertEquals(FullArticle.Failed, runBlocking { articles.load(link) })
        assertTrue(dir.listFiles().isNullOrEmpty())
    }

    @Test
    fun `a page that can't be downloaded fails`() {
        val result = runBlocking { articles(folder.newFolder("articles")).load("https://example.com/missing") }
        assertEquals(FullArticle.Failed, result)
    }

    @Test
    fun `a saved article without feed text always loads its page`() {
        // A feed's summary follows the setting.
        assertTrue(wantsFullText(choice = null, setting = true, hasFeedText = true, isSummary = true))
        assertFalse(wantsFullText(choice = null, setting = false, hasFeedText = true, isSummary = true))
        // An added link, or one from a service: only its description, so the page, whatever the setting.
        assertTrue(wantsFullText(choice = null, setting = false, hasFeedText = false, isSummary = true))
        // A complete text needs nothing more, and the reader's menu always wins.
        assertFalse(wantsFullText(choice = null, setting = true, hasFeedText = true, isSummary = false))
        assertFalse(wantsFullText(choice = false, setting = false, hasFeedText = false, isSummary = true))
        assertTrue(wantsFullText(choice = true, setting = false, hasFeedText = true, isSummary = false))
        // Saved: the whole article whenever the text is short, whatever the setting or an old choice.
        assertTrue(wantsFullText(choice = false, setting = false, hasFeedText = true, isSummary = true, saved = true))
        assertFalse(wantsFullText(choice = true, setting = true, hasFeedText = true, isSummary = false, saved = true))
    }
}
