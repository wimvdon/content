package be.wimvandonck.content.feed

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

    private val SUBSTACK_NAME = Regex("^[a-z0-9-]+$")

    /**
     * The publication behind any Substack link, where its feed is found: a note, post or profile on
     * substack.com (`substack.com/@name/...`), an `open.substack.com/pub/name/...` link, or a post on
     * `name.substack.com` all give `https://name.substack.com/`. Null for any other address (a
     * publication on its own domain is found as it is).
     */
    fun substackPublication(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        val name =
            when {
                host == "substack.com" || host == "www.substack.com" -> segments.firstOrNull()?.takeIf { it.startsWith("@") }?.drop(1)
                host == "open.substack.com" -> segments.takeIf { it.firstOrNull() == "pub" }?.getOrNull(1)
                host.endsWith(".substack.com") -> host.removeSuffix(".substack.com").takeIf { !it.contains('.') }
                else -> null
            }?.lowercase()
        return name?.takeIf { SUBSTACK_NAME.matches(it) }?.let { "https://$it.substack.com/" }
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
        linkTags(html)
            .mapNotNull { attributes ->
                val rel = attributes.rel()
                val type = attributes["type"].orEmpty().lowercase().substringBefore(';').trim()
                val href = attributes["href"].orEmpty()
                if ("alternate" in rel && type in feedTypes && href.isNotEmpty()) resolve(pageUrl, href) else null
            }.mapNotNull(::normalizeUrl)
            .distinct()

    /** The attributes of every `<link>` tag in a page (names in lower case, values decoded). */
    internal fun linkTags(html: String): List<Map<String, String>> =
        linkTag
            .findAll(html)
            .map { tag ->
                attribute.findAll(tag.value).associate { m ->
                    val value = m.groups[3]?.value ?: m.groups[4]?.value ?: m.groups[5]?.value.orEmpty()
                    m.groupValues[1].lowercase() to Html.decodeEntities(value.trim())
                }
            }.toList()

    internal fun Map<String, String>.rel(): List<String> = this["rel"].orEmpty().lowercase().split(Regex("""\s+"""))

    /** [commonPaths] on the page's site. */
    fun commonFeedUrls(pageUrl: String): List<String> {
        val uri = runCatching { URI(pageUrl) }.getOrNull() ?: return emptyList()
        val root = "${uri.scheme}://${uri.rawAuthority}"
        return commonPaths.map { root + it }
    }

    internal fun resolve(
        base: String,
        href: String,
    ): String? = runCatching { URI(base).resolve(URI(href)).toString() }.getOrNull()
}
