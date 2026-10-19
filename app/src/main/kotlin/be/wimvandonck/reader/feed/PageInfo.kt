package be.wimvandonck.reader.feed

import org.jsoup.Jsoup

/** What a web page says about itself: its title, description and picture (Open Graph, else the page's own). */
data class PageInfo(
    val title: String?,
    val description: String?,
    val imageUrl: String?,
) {
    companion object {
        /**
         * Reads [html] of the page at [url]. Only https pictures are kept (the app blocks plain
         * http). Plain Kotlin with jsoup, unit tested.
         */
        fun parse(
            html: String,
            url: String,
        ): PageInfo {
            val doc = Jsoup.parse(html, url)
            fun meta(vararg names: String): String? =
                names.firstNotNullOfOrNull { name ->
                    doc.selectFirst("meta[property=$name], meta[name=$name]")?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }
                }
            val image =
                doc.selectFirst("meta[property=og:image], meta[name=twitter:image]")?.absUrl("content")?.takeIf { it.startsWith("https://") }
            return PageInfo(
                title = meta("og:title", "twitter:title") ?: doc.title().trim().takeIf { it.isNotEmpty() },
                description = meta("og:description", "description", "twitter:description")?.take(MAX_DESCRIPTION),
                imageUrl = image,
            )
        }

        private const val MAX_DESCRIPTION = 400
    }
}
