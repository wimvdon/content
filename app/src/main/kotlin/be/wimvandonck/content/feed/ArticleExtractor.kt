package be.wimvandonck.content.feed

import net.dankito.readability4j.extended.Readability4JExtended
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.net.URI

/**
 * Finds the article in a web page and returns it as simple HTML (paragraphs, headings, lists,
 * quotes, links, text styles, pictures and embedded videos), the way "reader view" in browsers
 * does: menus, headers, sidebars, comments, ads and scripts are left out. Finding the article is
 * Readability4J, a port of Firefox Reader View's Readability, on jsoup, as it is (#236: no changes
 * to the page before it or to what it finds); this object only turns its result into the reader's
 * simple HTML (only the tags below, absolute links, known video players) and leaves out the title,
 * which the reader shows above the article. Plain Kotlin, so it runs in unit tests.
 */
object ArticleExtractor {
    /** Below this many characters of text the page is not treated as an article. */
    const val MIN_TEXT_LENGTH = 250

    /** Tags kept in the result; other elements are replaced by their content. */
    private val keptTags =
        setOf(
            "p", "br", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "blockquote", "pre", "code",
            "em", "i", "strong", "b", "u", "s", "sub", "sup", "a", "hr", "dl", "dt", "dd",
            "figure", "figcaption", "img", "iframe", "video",
        )

    /**
     * What the reader can't show (scripts, drawings, form fields, players without a link), wherever
     * Readability leaves them. Everything else Readability keeps is shown (#251: no other choices).
     */
    private val droppedTags =
        setOf("script", "style", "noscript", "template", "svg", "math", "canvas", "textarea", "select", "input", "button", "object", "audio")

    /** Embedded players worth a card in the reader; other iframes are usually ads or widgets. */
    private val knownEmbeds =
        Regex(
            "youtube\\.com|youtube-nocookie\\.com|youtu\\.be|vimeo\\.com|twitter\\.com|x\\.com|instagram\\.com|" +
                "spotify\\.com|soundcloud\\.com|codepen\\.io|dailymotion\\.com|tiktok\\.com|bsky\\.app",
            RegexOption.IGNORE_CASE,
        )
    private val blockTags = setOf("p", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "blockquote", "pre", "hr", "dl", "dt", "dd", "div", "section", "article", "main", "td", "tr", "table")

    /** The article of [html] (downloaded from [pageUrl]) as simple HTML, or null when none is found. */
    fun extract(
        html: String,
        pageUrl: String,
    ): String? {
        val document = Jsoup.parse(html, pageUrl)
        document.outputSettings().prettyPrint(false)
        val titles = listOfNotNull(document.selectFirst("meta[property=og:title]")?.attr("content"), document.title())
        val article =
            try {
                Readability4JExtended(pageUrl, document).parse()
            } catch (_: Exception) {
                // A page Readability can't handle has no article for the reader.
                null
            }
        val content = article?.articleContent ?: return null
        // The reader shows the title above the article already.
        content.select("h1, h2").firstOrNull { heading -> (titles + listOfNotNull(article.title)).any { isTitle(heading.text(), it) } }?.remove()
        val out = StringBuilder()
        serialize(content, out, pageUrl)
        val result = out.toString().replace(Regex("""(\s*<br>\s*){3,}"""), "<br><br>").trim()
        return result.takeIf { Html.toPlainText(it).length >= MIN_TEXT_LENGTH }
    }

    /** Whether [heading] is the page's [title], which often ends in the site's name ("Title | Site"). */
    private fun isTitle(
        heading: String,
        title: String,
    ): Boolean {
        fun String.normalized() = lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
        val h = heading.normalized()
        val t = title.normalized()
        return h.isNotEmpty() && (h == t || h.length >= MIN_TITLE_LENGTH && t.startsWith("$h "))
    }

    /** Shorter headings only count as the title when they match it exactly. */
    private const val MIN_TITLE_LENGTH = 10

    // ---- Writing the result -------------------------------------------------------------------

    private fun serialize(
        element: Element,
        out: StringBuilder,
        pageUrl: String,
    ) {
        for (child in element.childNodes()) {
            when (child) {
                is TextNode -> out.append(Html.escape(child.wholeText))
                is Element -> serializeElement(child, out, pageUrl)
            }
        }
    }

    private fun serializeElement(
        element: Element,
        out: StringBuilder,
        pageUrl: String,
    ) {
        val tag = element.normalName()
        when {
            tag in droppedTags -> return

            tag !in keptTags -> {
                // A container holding only text and inline elements is a paragraph (sites that
                // use a <div> per paragraph); one holding blocks just passes them on.
                if (tag in blockTags && !hasBlockDescendant(element)) {
                    val inner = StringBuilder()
                    serialize(element, inner, pageUrl)
                    if (Html.toPlainText(inner.toString()).isNotEmpty()) out.append("<p>").append(inner.trim()).append("</p>")
                } else {
                    serialize(element, out, pageUrl)
                }
            }

            tag == "br" || tag == "hr" -> out.append("<$tag>")

            tag == "img" -> {
                out.append("<img")
                for (name in listOf("src", "data-src", "data-lazy-src")) {
                    element.attrOrNull(name)?.let { resolve(pageUrl, it) }?.let { out.attr(name, it) }
                }
                for (name in listOf("srcset", "data-srcset")) {
                    element.attrOrNull(name)?.let { resolveSrcset(pageUrl, it) }?.let { out.attr(name, it) }
                }
                for (name in listOf("width", "height", "alt")) element.attrOrNull(name)?.let { out.attr(name, it) }
                out.append('>')
            }

            tag == "iframe" -> {
                val src = (element.attrOrNull("src") ?: element.attrOrNull("data-src"))?.let { resolve(pageUrl, it) }
                if (src != null && knownEmbeds.containsMatchIn(src)) out.append("<iframe").attr("src", src).append("></iframe>")
            }

            tag == "video" -> {
                val src =
                    (element.attrOrNull("src") ?: element.selectFirst("source[src]")?.attr("src"))
                        ?.let { resolve(pageUrl, it) }
                if (src != null) {
                    out.append("<video").attr("src", src)
                    element.attrOrNull("poster")?.let { resolve(pageUrl, it) }?.let { out.attr("poster", it) }
                    out.append("></video>")
                }
            }

            tag == "a" -> {
                val href = element.attrOrNull("href")?.let { resolve(pageUrl, it) }
                if (href == null) {
                    serialize(element, out, pageUrl)
                } else {
                    out.append("<a href=\"").append(Html.escape(href)).append("\">")
                    serialize(element, out, pageUrl)
                    out.append("</a>")
                }
            }

            else -> {
                val inner = StringBuilder()
                serialize(element, inner, pageUrl)
                if (tag in blockTags && inner.isBlank()) return
                out.append('<').append(tag).append('>').append(inner).append("</").append(tag).append('>')
            }
        }
    }

    private fun Element.attrOrNull(name: String): String? = if (hasAttr(name)) attr(name) else null

    /** Elements that make their own block in the result. */
    private val blockLike = blockTags + setOf("figure", "figcaption")

    private fun hasBlockDescendant(element: Element): Boolean =
        element.children().any { child -> child.normalName() in blockLike || hasBlockDescendant(child) }

    private fun StringBuilder.attr(
        name: String,
        value: String,
    ): StringBuilder = append(' ').append(name).append("=\"").append(Html.escape(value)).append('"')

    private fun resolveSrcset(
        base: String,
        srcset: String,
    ): String =
        srcset.split(',').mapNotNull { part ->
            val pieces = part.trim().split(Regex("""\s+"""), limit = 2)
            val url = pieces.firstOrNull()?.let { resolve(base, it) } ?: return@mapNotNull null
            if (pieces.size > 1) "$url ${pieces[1]}" else url
        }.joinToString(", ")

    private fun resolve(
        base: String,
        href: String,
    ): String? {
        val trimmed = href.trim()
        if (trimmed.startsWith("#") || trimmed.startsWith("javascript:", ignoreCase = true)) return null
        val resolved = runCatching { URI(base).resolve(URI(trimmed.replace(" ", "%20"))).toString() }.getOrNull() ?: return null
        val scheme = resolved.substringBefore(':').lowercase()
        return resolved.takeIf { scheme == "http" || scheme == "https" || scheme == "mailto" }
    }

    /** The text of a downloaded page, using the charset its <meta> declares (UTF-8 otherwise). */
    fun decode(bytes: ByteArray): String {
        val head = String(bytes, 0, minOf(bytes.size, 4096), Charsets.ISO_8859_1)
        val name = Regex("""<meta[^>]+charset\s*=\s*["']?([A-Za-z0-9._-]+)""", RegexOption.IGNORE_CASE).find(head)?.groupValues?.get(1)
        val charset = runCatching { charset(name ?: "UTF-8") }.getOrDefault(Charsets.UTF_8)
        return String(bytes, charset)
    }
}
