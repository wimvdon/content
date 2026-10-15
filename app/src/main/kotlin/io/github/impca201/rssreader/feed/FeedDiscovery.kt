package io.github.impca201.rssreader.feed

import java.net.URI

/** Turns what the user typed into a feed address, and finds feeds linked from web pages. */
object FeedDiscovery {
    private val linkTag = Regex("""<link\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val attribute = Regex("""([a-zA-Z_:][-a-zA-Z0-9_:.]*)\s*=\s*("([^"]*)"|'([^']*)'|([^\s"'>]+))""")
    private val feedTypes = setOf("application/rss+xml", "application/atom+xml", "application/rdf+xml", "application/xml", "text/xml")

    /** Paths that often hold a site's feed, tried when a page links to none. */
    val commonPaths = listOf("/feed", "/rss", "/feed.xml", "/rss.xml", "/atom.xml", "/index.xml")

    /**
     * A web address from user input: "example.com" becomes "https://example.com", feed:// and
     * http:// become https:// (Android blocks unencrypted connections). Null when it can't be
     * a web address.
     */
    fun normalizeUrl(input: String): String? {
        var text = input.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        text =
            when {
                text.startsWith("feed:", ignoreCase = true) -> text.substring(5).removePrefix("//").let(::withScheme)
                else -> withScheme(text)
            }
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        val host = uri.host ?: return null
        if (uri.scheme?.lowercase() != "https" || !host.contains('.') && host != "localhost") return null
        return uri.toString()
    }

    private fun withScheme(text: String): String =
        when {
            text.startsWith("https://", ignoreCase = true) -> "https://" + text.substring(8)
            text.startsWith("http://", ignoreCase = true) -> "https://" + text.substring(7)
            text.contains("://") -> text
            else -> "https://$text"
        }

    /** Feed addresses a web page announces with `<link rel="alternate" type="application/rss+xml">`. */
    fun findFeedLinks(
        html: String,
        pageUrl: String,
    ): List<String> =
        linkTag
            .findAll(html)
            .mapNotNull { tag ->
                val attributes =
                    attribute.findAll(tag.value).associate { m ->
                        val value = m.groups[3]?.value ?: m.groups[4]?.value ?: m.groups[5]?.value.orEmpty()
                        m.groupValues[1].lowercase() to Html.decodeEntities(value.trim())
                    }
                val rel = attributes["rel"].orEmpty().lowercase().split(Regex("""\s+"""))
                val type = attributes["type"].orEmpty().lowercase().substringBefore(';').trim()
                val href = attributes["href"].orEmpty()
                if ("alternate" in rel && type in feedTypes && href.isNotEmpty()) resolve(pageUrl, href) else null
            }.mapNotNull(::normalizeUrl)
            .distinct()
            .toList()

    /** [commonPaths] on the page's site. */
    fun commonFeedUrls(pageUrl: String): List<String> {
        val uri = runCatching { URI(pageUrl) }.getOrNull() ?: return emptyList()
        val root = "${uri.scheme}://${uri.rawAuthority}"
        return commonPaths.map { root + it }
    }

    private fun resolve(
        base: String,
        href: String,
    ): String? = runCatching { URI(base).resolve(URI(href)).toString() }.getOrNull()
}
