package io.github.impca201.rssreader.feed

import io.github.impca201.rssreader.feed.FeedDiscovery.rel
import java.net.URI

/** Finds the small picture that stands for a website (its favicon), to show next to its feed. */
object SiteIcon {
    /**
     * The best icon a home page announces: the largest of its `apple-touch-icon` and `icon` links
     * (Apple touch icons are 180 px and look sharp), skipping SVG, which the app can't draw.
     * Null when the page announces none.
     */
    fun fromPage(
        html: String,
        pageUrl: String,
    ): String? =
        FeedDiscovery
            .linkTags(html)
            .mapNotNull { attributes ->
                val rel = attributes.rel()
                val href = attributes["href"].orEmpty()
                val isTouch = rel.any { it.startsWith("apple-touch-icon") }
                if (!isTouch && "icon" !in rel || href.isEmpty() || href.startsWith("data:")) return@mapNotNull null
                val type = attributes["type"].orEmpty().lowercase()
                if (type == "image/svg+xml" || href.substringBefore('?').lowercase().endsWith(".svg")) return@mapNotNull null
                val url = FeedDiscovery.resolve(pageUrl, href)?.let(FeedDiscovery::normalizeUrl) ?: return@mapNotNull null
                val size = attributes["sizes"].orEmpty().let(::largestSize) ?: if (isTouch) TOUCH_ICON_SIZE else 0
                url to size
            }.maxByOrNull { it.second }
            ?.first

    /** The classic `/favicon.ico` of the site that [pageUrl] belongs to. */
    fun fallback(pageUrl: String): String? {
        val uri = runCatching { URI(pageUrl) }.getOrNull() ?: return null
        val host = uri.host ?: return null
        return "https://$host/favicon.ico"
    }

    /** The home page to look for the icon on: the feed's website, or else its own site. */
    fun homePage(
        siteUrl: String?,
        feedUrl: String,
    ): String? {
        val uri = runCatching { URI(siteUrl ?: feedUrl) }.getOrNull() ?: return null
        val host = uri.host ?: return null
        return FeedDiscovery.normalizeUrl("https://$host/")
    }

    /** "16x16 32x32" → 32; "any" or nothing readable → null. */
    private fun largestSize(sizes: String): Int? =
        sizes
            .lowercase()
            .split(' ')
            .mapNotNull { it.substringBefore('x').toIntOrNull() }
            .maxOrNull()

    private const val TOUCH_ICON_SIZE = 180
}
