package be.wimvandonck.content.feed

import net.dankito.readability4j.extended.Readability4JExtended
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.net.URI

/**
 * Finds the article in a web page and returns it as simple HTML (paragraphs, headings, lists,
 * quotes, links, text styles, pictures and embedded videos), the way "reader view" in browsers
 * does: menus, headers, sidebars, comments, ads and scripts are left out. Finding the article is
 * Readability4J, a port of Firefox Reader View's Readability, on jsoup; this object prepares the
 * page for it and turns its result into the reader's simple HTML (only the tags below, absolute
 * links, known video players). Plain Kotlin, so it runs in unit tests.
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

    /** Never part of what the reader shows, wherever Readability leaves them. */
    private val droppedTags =
        setOf(
            "script", "style", "noscript", "template", "svg", "math", "textarea", "object", "select", "canvas",
            "nav", "header", "footer", "aside", "form", "button", "input", "audio", "dialog", "menu",
        )

    /** Embedded players worth a card in the reader; other iframes are usually ads or widgets. */
    private val knownEmbeds =
        Regex(
            "youtube\\.com|youtube-nocookie\\.com|youtu\\.be|vimeo\\.com|twitter\\.com|x\\.com|instagram\\.com|" +
                "spotify\\.com|soundcloud\\.com|codepen\\.io|dailymotion\\.com|tiktok\\.com|bsky\\.app",
            RegexOption.IGNORE_CASE,
        )
    private val blockTags = setOf("p", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "blockquote", "pre", "hr", "dl", "dt", "dd", "div", "section", "article", "main", "td", "tr", "table")

    /** Elements whose content is running text: a button in there is a word of it (a glossary term). */
    private val textTags = listOf("p", "li", "dd", "td", "h1", "h2", "h3", "h4", "h5", "h6")

    /** The article of [html] (downloaded from [pageUrl]) as simple HTML, or null when none is found. */
    fun extract(
        html: String,
        pageUrl: String,
    ): String? {
        val document = Jsoup.parse(html, pageUrl)
        document.outputSettings().prettyPrint(false)
        val titles = listOfNotNull(document.selectFirst("meta[property=og:title]")?.attr("content"), document.title())
        // Readability drops buttons, also the ones some sites put around words of the text.
        document.select(textTags.joinToString(", ") { "$it button" }).forEach { it.unwrap() }
        // What the page hides is not part of what a reader sees.
        document.select("[hidden], [aria-hidden=true], [style~=(?i)display:\\s*none], .hidden, .d-none").remove()
        divsToParagraphs(document)
        joinSplitText(document)
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
        dropPageDetails(content, pageUrl)
        val out = StringBuilder()
        serialize(content, out, pageUrl)
        val result = out.toString().replace(Regex("""(\s*<br>\s*){3,}"""), "<br><br>").trim()
        return result.takeIf { Html.toPlainText(it).length >= MIN_TEXT_LENGTH }
    }

    /** Elements that start a block of their own; a `<div>` without any inside is a paragraph. */
    private val blockSelector =
        "p, div, section, article, main, header, footer, aside, nav, ul, ol, dl, table, blockquote, pre, figure, form, hr, " +
            "h1, h2, h3, h4, h5, h6"

    /**
     * Sites that write a `<div>` per paragraph: such a div becomes a `<p>` here, so that Readability
     * keeps its links and bold words inside the paragraph instead of splitting it around them.
     */
    private fun divsToParagraphs(document: Document) {
        for (div in document.body().select("div")) {
            if (div.select(blockSelector).size == 1 && div.text().length >= PARAGRAPH_LENGTH) div.tagName("p")
        }
    }

    /**
     * Articles split into look-alike containers with ads or videos in between (several
     * `<div class="post-content">` on some WordPress sites): Readability keeps only one of them,
     * so the text of the others moves into the first. Only containers that each hold several real
     * paragraphs count, so a list of teaser cards stays as it is.
     */
    private fun joinSplitText(document: Document) {
        val parts =
            document.body().select("div[class], section[class]").filter { element ->
                element.children().count { it.normalName() == "p" && it.text().length >= PARAGRAPH_LENGTH } >= 2
            }
        val group =
            parts
                .groupBy { it.normalName() + " " + it.className().trim() }
                .values
                .filter { it.size >= 2 && it.none { a -> it.any { b -> a !== b && b.parents().contains(a) } } }
                .maxByOrNull { group -> group.sumOf { it.text().length } }
                ?: return
        val first = group.first()
        for (part in group.drop(1)) {
            first.appendChildren(part.childNodes().toList())
            part.remove()
        }
    }

    /** From this many characters a paragraph counts as real text. */
    private const val PARAGRAPH_LENGTH = 80

    /**
     * Short lines about the page rather than the article: a byline with its date (a `<time>`) or a
     * link to the page itself ("54 comments").
     */
    private fun dropPageDetails(
        content: Element,
        pageUrl: String,
    ) {
        val page = pageUrl.substringBefore('#')
        val details =
            content.select("time") +
                content.select("a[href]").filter { link ->
                    val href = link.absUrl("href")
                    href.substringBefore('#') == page && '#' in href
                }
        for (detail in details) {
            val block = (listOf(detail) + detail.parents()).firstOrNull { it.normalName() in detailBlocks } ?: detail
            if (block !== content && block.text().length < PARAGRAPH_LENGTH) block.remove() else if (block === content) detail.remove()
        }
    }

    private val detailBlocks = setOf("p", "div", "li", "ul", "section", "header", "footer", "span")

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
