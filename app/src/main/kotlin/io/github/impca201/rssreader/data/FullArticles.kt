package io.github.impca201.rssreader.data

import io.github.impca201.rssreader.feed.ArticleExtractor
import io.github.impca201.rssreader.feed.FetchException
import io.github.impca201.rssreader.feed.Fetched
import io.github.impca201.rssreader.feed.Html
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

/** True when an article's feed text looks like a summary, so the full text is worth loading. */
fun needsFullText(feedHtml: String): Boolean = Html.toPlainText(feedHtml).length < FULL_TEXT_THRESHOLD

/** Feed texts shorter than this (in characters of text) count as summaries. */
const val FULL_TEXT_THRESHOLD = 1500

/**
 * Loads the complete text of articles whose feed only has a summary: downloads the article's
 * web page and keeps only the article (see [ArticleExtractor]). Results are kept in the app's
 * cache folder, which Android may clear and which is not backed up; they are downloaded again
 * when needed.
 */
class FullArticles(
    private val cacheDir: File,
    private val fetch: suspend (String) -> Fetched,
) {
    suspend fun load(link: String): FullArticle {
        val file = File(cacheDir, name(link))
        readCached(file)?.let { return FullArticle.Loaded(it) }
        val html =
            try {
                val page = fetch(link)
                withContext(Dispatchers.Default) { ArticleExtractor.extract(ArticleExtractor.decode(page.bytes), page.url) }
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

    private suspend fun readCached(file: File): String? =
        withContext(Dispatchers.IO) {
            try {
                if (file.exists()) file.readText().also { file.setLastModified(System.currentTimeMillis()) } else null
            } catch (_: IOException) {
                null
            }
        }

    /** Keeps the most recently used [MAX_FILES] articles. */
    private fun trim() {
        val files = cacheDir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(MAX_FILES).forEach { it.delete() }
    }

    private fun name(link: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(link.toByteArray())
            .joinToString("") { "%02x".format(it) } + ".html"

    private companion object {
        const val MAX_FILES = 300
    }
}
