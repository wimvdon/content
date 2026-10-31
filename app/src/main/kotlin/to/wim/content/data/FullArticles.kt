package to.wim.content.data

import to.wim.content.feed.ArticleExtractor
import to.wim.content.feed.FetchException
import to.wim.content.feed.Fetched
import to.wim.content.feed.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Result of loading the full text of an article from its web page. */
sealed interface FullArticle {
    data class Loaded(val html: String) : FullArticle

    /** The page couldn't be downloaded, or no article was found in it. */
    data object Failed : FullArticle
}

/**
 * True when an article's feed text looks like a summary, so the full text is worth loading: it is
 * short, or it links to the article itself ("Read full article", "Continue reading"), which feeds
 * do under a teaser of a few paragraphs.
 */
fun needsFullText(
    feedHtml: String,
    link: String? = null,
): Boolean = Html.toPlainText(feedHtml).length < FULL_TEXT_THRESHOLD || link != null && linksTo(feedHtml, link)

private val hrefs = Regex("""<a\b[^>]*\bhref\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

/** Whether [html] has a link to [link] (ignoring http/https, "www.", a #fragment and a trailing slash). */
private fun linksTo(
    html: String,
    link: String,
): Boolean {
    fun String.normalized() =
        Html.decodeEntities(trim()).substringBefore('#').removeSuffix("/").substringAfter("://").removePrefix("www.").lowercase()
    val target = link.normalized()
    return target.isNotEmpty() && hrefs.findAll(html).any { it.groupValues[1].normalized() == target }
}

/**
 * Whether the reader loads an article's full text: as [choice] in the reader's menu, else as the
 * setting says for a feed's summary. A saved article with no feed text (an added link, or one from
 * a service) has only its description, so it always loads the page (#134). A [saved] article
 * always shows the whole article when its text is short: the feed's short intro is no use there,
 * so the reader offers no switch back to it (#208).
 */
fun wantsFullText(
    choice: Boolean?,
    setting: Boolean,
    hasFeedText: Boolean,
    isSummary: Boolean,
    saved: Boolean = false,
): Boolean = if (saved) isSummary else choice ?: (isSummary && (setting || !hasFeedText))

/** Feed texts shorter than this (in characters of text) count as summaries. */
const val FULL_TEXT_THRESHOLD = 1500

/**
 * Loads the complete text of articles whose feed only has a summary: downloads the article's
 * web page and keeps only the article ([extract], see [ArticleExtractor]). Results are kept in the
 * app's cache folder, which Android may clear and which is not backed up; they are downloaded
 * again when needed.
 */
class FullArticles(
    private val cacheDir: File,
    private val fetch: suspend (String) -> Fetched,
    /** The article of a page's HTML (downloaded from the given address), or null when there is none. */
    private val extract: suspend (html: String, pageUrl: String) -> String?,
) {
    suspend fun load(link: String): FullArticle {
        val file = File(cacheDir, name(link))
        readCached(file)?.let { return FullArticle.Loaded(it) }
        val html =
            try {
                val page = fetch(link)
                extract(withContext(Dispatchers.Default) { ArticleExtractor.decode(page.bytes) }, page.url)
            } catch (_: FetchException) {
                null
            } ?: return FullArticle.Failed
        withContext(Dispatchers.IO) {
            try {
                cacheDir.mkdirs()
                file.writeText(html)
                trim()
            } catch (_: IOException) {
                // Only a cache: the article shows anyway.
            }
        }
        return FullArticle.Loaded(html)
    }

    /** Forgets the stored text of [link], so the next [load] downloads it again (after signing in, #180). */
    suspend fun forget(link: String) {
        withContext(Dispatchers.IO) { File(cacheDir, name(link)).delete() }
    }

    private suspend fun readCached(file: File): String? =
        withContext(Dispatchers.IO) {
            try {
                if (file.exists()) file.readText().also { file.setLastModified(System.currentTimeMillis()) } else null
            } catch (_: IOException) {
                null
            }
        }

    /** Keeps the most recently used [MAX_FILES] articles, and none from an older extractor. */
    private fun trim() {
        val (current, old) = cacheDir.listFiles()?.partition { it.name.startsWith(PREFIX) } ?: return
        old.forEach { it.delete() }
        current.sortedByDescending { it.lastModified() }.drop(MAX_FILES).forEach { it.delete() }
    }

    private fun name(link: String): String =
        PREFIX +
            MessageDigest
                .getInstance("SHA-256")
                .digest(link.toByteArray())
                .joinToString("") { "%02x".format(it) } + ".html"

    private companion object {
        const val MAX_FILES = 300

        /**
         * Starts the names of cached articles. Raise the number when [ArticleExtractor] finds
         * articles differently, so articles kept from before are extracted again.
         */
        const val PREFIX = "v7-"
    }
}
