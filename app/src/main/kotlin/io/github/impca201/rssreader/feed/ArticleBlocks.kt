package io.github.impca201.rssreader.feed

import java.net.URI

/** One piece of an article as the reader shows it. */
sealed interface ArticleBlock {
    /** A paragraph with simple formatting (links, bold, italics, line breaks), as cleaned HTML. */
    data class Text(val html: String) : ArticleBlock

    /** A heading inside the article, h1 to h6. */
    data class Heading(
        val level: Int,
        val html: String,
    ) : ArticleBlock

    /** A quote, one or more paragraphs. */
    data class Quote(val paragraphs: List<String>) : ArticleBlock

    /** Preformatted text (code), with its line breaks. */
    data class Code(val text: String) : ArticleBlock

    /** A list; nested lists are items with a larger [ListItem.depth]. */
    data class ListBlock(val items: List<ListItem>) : ArticleBlock

    /** One paragraph of a list item; [marker] is "•", "1." and so on, or empty for its later paragraphs. */
    data class ListItem(
        val html: String,
        val marker: String,
        val depth: Int,
    )

    data class Image(
        val url: String,
        val caption: String?,
    ) : ArticleBlock

    /** Embedded content (a video player, a post) shown as a card that opens [url]. */
    data class Embed(
        val kind: EmbedKind,
        val url: String,
        val thumbnailUrl: String?,
    ) : ArticleBlock
}

enum class EmbedKind { YouTube, Vimeo, Video, Other }

/**
 * Splits article HTML into text, images and embeds, and finds article pictures. Plain Kotlin,
 * unit tested.
 */
object ArticleBlocks {
    private val figure = Regex("""<figure\b[^>]*>(.*?)</figure\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val img = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val iframe = Regex("""<iframe\b[^>]*>(?:.*?</iframe\s*>)?""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val video = Regex("""<video\b[^>]*>(.*?)</video\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val source = Regex("""<source\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val figcaption = Regex("""<figcaption\b[^>]*>(.*?)</figcaption\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val special = Regex("""${figure.pattern}|${iframe.pattern}|${video.pattern}|${img.pattern}""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val attribute = Regex("""([a-zA-Z_:][-a-zA-Z0-9_:.]*)\s*=\s*("([^"]*)"|'([^']*)'|([^\s"'>]+))""")
    private val youTubeId = Regex("""(?:youtube(?:-nocookie)?\.com/(?:embed/|watch\?v=|v/|shorts/)|youtu\.be/)([A-Za-z0-9_-]{6,20})""")
    private val vimeoId = Regex("""vimeo\.com/(?:video/)?(\d+)""")

    /** The reader's blocks for [html] downloaded from [baseUrl], in order. */
    fun split(
        html: String,
        baseUrl: String,
    ): List<ArticleBlock> {
        val blocks = mutableListOf<ArticleBlock>()
        // Text between pictures that turn out to be nothing (tracking pixels) is read as one run.
        val text = StringBuilder()
        fun addText(segment: String) {
            text.append(segment)
        }
        fun add(block: ArticleBlock) {
            blocks += TextLayout.blocks(Html.clean(text.toString()))
            text.setLength(0)
            blocks += block
        }
        var last = 0
        for (m in special.findAll(html)) {
            addText(html.substring(last, m.range.first))
            last = m.range.last + 1
            val tag = m.value.substringAfter('<').takeWhile { it.isLetter() }.lowercase()
            when (tag) {
                "figure" -> figureBlocks(m.value, baseUrl).forEach(::add)
                "iframe" -> embedFor(attributes(m.value)["src"], baseUrl)?.let(::add)
                "video" -> videoBlock(m.value, baseUrl)?.let(::add)
                else -> imageFor(m.value, baseUrl)?.let { add(ArticleBlock.Image(it, null)) }
            }
        }
        addText(html.substring(last))
        blocks += TextLayout.blocks(Html.clean(text.toString()))
        // Pages that write a caption twice (once for a hidden picture viewer) show it once.
        return blocks.filterIndexed { i, block -> i == 0 || block !is ArticleBlock.Text || !sameText(block, blocks[i - 1]) }
    }

    /** The article's picture: the first real image in [html]. */
    fun firstImage(
        html: String,
        baseUrl: String,
    ): String? = img.findAll(html).firstNotNullOfOrNull { imageFor(it.value, baseUrl) }

    /**
     * True when two addresses show the same picture, also in another size (sites serve
     * "photo-1024x768.jpg" in the feed and "photo.jpg?w=2000" on the page).
     */
    fun isSameImage(
        a: String?,
        b: String?,
    ): Boolean {
        if (a == null || b == null) return false
        fun key(url: String) =
            url
                .substringBefore('?')
                .substringBefore('#')
                .substringAfterLast('/')
                .lowercase()
                .replace(Regex("""-\d+x\d+(?=\.[a-z]+$)"""), "")
                .replace(Regex("""@\dx(?=\.[a-z]+$)"""), "")
        return key(a) == key(b) && key(a).isNotEmpty()
    }

    /** A picture address fit to load: absolute, https, not a data: URI. */
    fun imageUrl(
        raw: String?,
        baseUrl: String,
    ): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("data:", ignoreCase = true) } ?: return null
        val resolved = runCatching { URI(baseUrl).resolve(URI(value.replace(" ", "%20"))).toString() }.getOrNull() ?: return null
        return when {
            resolved.startsWith("https://", ignoreCase = true) -> resolved
            // Android blocks unencrypted connections; most image hosts also serve https.
            resolved.startsWith("http://", ignoreCase = true) -> "https://" + resolved.substring(7)
            resolved.startsWith("//") -> "https:$resolved"
            else -> null
        }
    }

    private fun figureBlocks(
        figureHtml: String,
        baseUrl: String,
    ): List<ArticleBlock> {
        val caption =
            figcaption
                .find(figureHtml)
                ?.groupValues
                ?.get(1)
                ?.let(Html::toPlainText)
                ?.ifEmpty { null }
        iframe.find(figureHtml)?.let { m -> embedFor(attributes(m.value)["src"], baseUrl)?.let { return listOf(it) } }
        video.find(figureHtml)?.let { m -> videoBlock(m.value, baseUrl)?.let { return listOf(it) } }
        val image = img.findAll(figureHtml).firstNotNullOfOrNull { imageFor(it.value, baseUrl) }
        return when {
            image != null -> listOf(ArticleBlock.Image(image, caption))
            caption != null -> listOf(ArticleBlock.Text("<p>${Html.escape(caption)}</p>"))
            else -> emptyList()
        }
    }

    private fun videoBlock(
        videoHtml: String,
        baseUrl: String,
    ): ArticleBlock? {
        val attrs = attributes(videoHtml)
        val src = attrs["src"] ?: source.findAll(videoHtml).firstNotNullOfOrNull { attributes(it.value)["src"] }
        val url = src?.let { resolve(baseUrl, it) } ?: return null
        return ArticleBlock.Embed(EmbedKind.Video, url, attrs["poster"]?.let { imageUrl(it, baseUrl) })
    }

    /** The embed card for an iframe or video address, or null when there's nothing to open. */
    fun embedFor(
        src: String?,
        baseUrl: String,
    ): ArticleBlock.Embed? {
        val url = src?.let { resolve(baseUrl, it) } ?: return null
        youTubeId.find(url)?.let {
            val id = it.groupValues[1]
            return ArticleBlock.Embed(EmbedKind.YouTube, "https://www.youtube.com/watch?v=$id", "https://i.ytimg.com/vi/$id/hqdefault.jpg")
        }
        vimeoId.find(url)?.let { return ArticleBlock.Embed(EmbedKind.Vimeo, "https://vimeo.com/${it.groupValues[1]}", null) }
        return ArticleBlock.Embed(EmbedKind.Other, url, null)
    }

    /** The address of an <img> tag, or null for tracking pixels and unusable sources. */
    private fun imageFor(
        tag: String,
        baseUrl: String,
    ): String? {
        val attrs = attributes(tag)
        val width = attrs["width"]?.toIntOrNull()
        val height = attrs["height"]?.toIntOrNull()
        if ((width != null && width <= 2) || (height != null && height <= 2)) return null
        val candidate =
            largestFromSrcset(attrs["srcset"] ?: attrs["data-srcset"])
                ?: attrs["data-src"]
                ?: attrs["data-lazy-src"]
                ?: attrs["src"]
        val url = imageUrl(candidate, baseUrl) ?: return null
        val lower = url.lowercase()
        if (TRACKERS.any { it in lower }) return null
        return url
    }

    private fun largestFromSrcset(srcset: String?): String? =
        srcset
            ?.split(',')
            ?.mapNotNull { part ->
                val pieces = part.trim().split(Regex("""\s+"""))
                val url = pieces.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val size = pieces.getOrNull(1)?.dropLast(1)?.toFloatOrNull() ?: 1f
                url to size
            }?.maxByOrNull { it.second }
            ?.first

    private fun attributes(tag: String): Map<String, String> {
        val start = tag.substringBefore('>')
        return attribute.findAll(start).associate { m ->
            m.groupValues[1].lowercase() to Html.decodeEntities(m.groups[3]?.value ?: m.groups[4]?.value ?: m.groups[5]?.value.orEmpty())
        }
    }

    private fun resolve(
        base: String,
        href: String,
    ): String? {
        val resolved = runCatching { URI(base).resolve(URI(href.trim().replace(" ", "%20"))).toString() }.getOrNull() ?: return null
        val withScheme = if (resolved.startsWith("//")) "https:$resolved" else resolved
        val scheme = withScheme.substringBefore(':').lowercase()
        return withScheme.takeIf { scheme == "http" || scheme == "https" }
    }

    /** Parts of addresses that belong to tracking pixels and share buttons, not pictures. */
    private val TRACKERS = listOf("feeds.feedburner.com/~r/", "/~ff/", "stats.wordpress.com", "pixel.wp.com", "/pixel.", "doubleclick.net")

    private fun sameText(
        block: ArticleBlock.Text,
        previous: ArticleBlock,
    ): Boolean = previous is ArticleBlock.Text && Html.toPlainText(previous.html) == Html.toPlainText(block.html)
}
