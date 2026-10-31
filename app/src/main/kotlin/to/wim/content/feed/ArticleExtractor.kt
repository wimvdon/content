package to.wim.content.feed

import to.wim.content.sync.Json

/**
 * Finds the article in a web page and returns it as simple HTML, the way "reader view" in browsers
 * does. The work is done by the web app's extractor (`web/src/lib/articleExtractor.ts`, with
 * Defuddle), bundled into the app's assets as [ASSET] and run in a hidden WebView by
 * [WebArticleExtractor], so both apps show the same article. This object holds the plain parts
 * around it, so they run in unit tests.
 */
object ArticleExtractor {
    /** The bundled extractor in the app's assets, built by `npm run build:android` in `web/`. */
    const val ASSET = "article-extractor.js"

    /** The JavaScript that asks the extractor for the article of [html], downloaded from [pageUrl]. */
    fun script(
        html: String,
        pageUrl: String,
    ): String = "extractArticle(${Json.write(html)}, ${Json.write(pageUrl)})"

    /** The article in what the WebView gives back for [script] (its result as JSON), or null when there is none. */
    fun result(json: String?): String? =
        runCatching { Json.parse(json ?: "null") as? String }.getOrNull()?.takeIf { it.isNotBlank() }

    /** The text of a downloaded page, using the charset its <meta> declares (UTF-8 otherwise). */
    fun decode(bytes: ByteArray): String {
        val head = String(bytes, 0, minOf(bytes.size, 4096), Charsets.ISO_8859_1)
        val name = Regex("""<meta[^>]+charset\s*=\s*["']?([A-Za-z0-9._-]+)""", RegexOption.IGNORE_CASE).find(head)?.groupValues?.get(1)
        val charset = runCatching { charset(name ?: "UTF-8") }.getOrDefault(Charsets.UTF_8)
        return String(bytes, charset)
    }
}
