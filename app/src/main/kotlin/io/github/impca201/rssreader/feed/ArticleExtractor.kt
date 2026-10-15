package io.github.impca201.rssreader.feed

import java.net.URI

/**
 * Finds the article in a web page and returns it as simple HTML (paragraphs, headings, lists,
 * quotes, links and text styles), the way "reader view" in browsers does: menus, headers,
 * sidebars, comments, ads and scripts are left out. Plain Kotlin with its own forgiving HTML
 * reader, so it needs no library and runs in unit tests.
 */
object ArticleExtractor {
    /** Below this many characters of text the page is not treated as an article. */
    const val MIN_TEXT_LENGTH = 250

    private val voidTags = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr")
    private val rawTextTags = setOf("script", "style", "noscript", "template", "svg", "math", "textarea", "iframe", "object", "select", "canvas")
    private val droppedTags =
        rawTextTags + setOf("nav", "header", "footer", "aside", "form", "button", "figure", "picture", "video", "audio", "dialog", "menu")

    /** Tags whose start closes an open <p> (HTML's implied end tags). */
    private val closesParagraph =
        setOf(
            "p", "div", "ul", "ol", "dl", "table", "blockquote", "pre", "h1", "h2", "h3", "h4", "h5", "h6",
            "section", "article", "header", "footer", "aside", "nav", "figure", "hr", "form", "main",
        )

    /** Tags kept in the result; other elements are replaced by their content. */
    private val keptTags =
        setOf(
            "p", "br", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "blockquote", "pre", "code",
            "em", "i", "strong", "b", "u", "s", "sub", "sup", "a", "hr", "dl", "dt", "dd",
        )
    private val blockTags = setOf("p", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "blockquote", "pre", "hr", "dl", "dt", "dd", "div", "section", "article", "main", "td", "tr", "table")

    private val unlikely =
        Regex(
            "comment|sidebar|footer|footnote|masthead|menu|nav|share|social|related|recommend|promo|sponsor|advert|" +
                "\\bad\\b|\\bads\\b|banner|subscribe|newsletter|signup|cookie|consent|popup|modal|breadcrumb|pagination|" +
                "author-bio|byline|tags|toolbar|skip|hidden|print-only|outbrain|taboola|disqus",
            RegexOption.IGNORE_CASE,
        )
    private val likely = Regex("article|content|entry|post|story|main|body|text|prose|blog", RegexOption.IGNORE_CASE)

    private class Element(
        val tag: String,
        val attributes: Map<String, String>,
        val parent: Element?,
    ) {
        val children = mutableListOf<Any>() // Element or String
        var score = 0.0
        val classAndId: String get() = (attributes["class"].orEmpty() + " " + attributes["id"].orEmpty())
    }

    /** The article of [html] (downloaded from [pageUrl]) as simple HTML, or null when none is found. */
    fun extract(
        html: String,
        pageUrl: String,
    ): String? {
        val root = parse(html)
        prune(root)
        val best = bestCandidate(root) ?: return null
        val out = StringBuilder()
        serialize(best, out, pageUrl)
        val result = out.toString().replace(Regex("""(\s*<br>\s*){3,}"""), "<br><br>").trim()
        return result.takeIf { Html.toPlainText(it).length >= MIN_TEXT_LENGTH }
    }

    // ---- Reading HTML into a tree -------------------------------------------------------------

    private val tagPattern = Regex("""<(/?)([a-zA-Z][a-zA-Z0-9:-]*)((?:\s+[^\s/>"'=]+(?:\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+))?)*)\s*(/?)>""")
    private val attributePattern = Regex("""([^\s/>"'=]+)(?:\s*=\s*("([^"]*)"|'([^']*)'|([^\s>]+)))?""")

    private fun parse(html: String): Element {
        val root = Element("#root", emptyMap(), null)
        var current = root
        var i = 0
        val text = html.replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "")
        while (i < text.length) {
            val lt = text.indexOf('<', i)
            if (lt < 0) {
                current.children += text.substring(i)
                break
            }
            if (lt > i) current.children += text.substring(i, lt)
            val match = tagPattern.matchAt(text, lt)
            if (match == null) {
                // A "<" that doesn't start a tag (or a doctype / processing instruction).
                val end = text.indexOf('>', lt)
                if (text.startsWith("<!", lt) || text.startsWith("<?", lt)) {
                    i = if (end < 0) text.length else end + 1
                } else {
                    current.children += "<"
                    i = lt + 1
                }
                continue
            }
            i = match.range.last + 1
            val closing = match.groupValues[1] == "/"
            val tag = match.groupValues[2].lowercase()
            if (closing) {
                var e: Element? = current
                while (e != null && e.tag != tag) e = e.parent
                if (e != null && e.parent != null) current = e.parent
                continue
            }
            if (tag in closesParagraph) current = closeOpen(current, "p")
            if (tag == "li") current = closeOpen(current, "li", stopAt = setOf("ul", "ol"))
            val element = Element(tag, attributes(match.groupValues[3]), current)
            current.children += element
            if (tag in rawTextTags) {
                // Skip everything up to the matching end tag without reading it as HTML.
                val end = text.indexOf("</$tag", i, ignoreCase = true)
                i = if (end < 0) text.length else (text.indexOf('>', end).takeIf { it >= 0 } ?: text.length - 1) + 1
            } else if (tag !in voidTags && match.groupValues[4] != "/") {
                current = element
            }
        }
        return root
    }

    /** Closes an open [tag] element (and anything inside it) if one is open below [stopAt]. */
    private fun closeOpen(
        current: Element,
        tag: String,
        stopAt: Set<String> = emptySet(),
    ): Element {
        var e: Element? = current
        while (e != null && e.tag != tag) {
            if (e.tag in stopAt || e.tag in setOf("div", "td", "blockquote", "#root")) return current
            e = e.parent
        }
        return e?.parent ?: current
    }

    private fun attributes(text: String): Map<String, String> =
        attributePattern.findAll(text).associate { m ->
            m.groupValues[1].lowercase() to Html.decodeEntities(m.groups[3]?.value ?: m.groups[4]?.value ?: m.groups[5]?.value.orEmpty())
        }

    // ---- Finding the article ------------------------------------------------------------------

    /** Removes what is never part of an article. */
    private fun prune(element: Element) {
        element.children.removeAll { child ->
            child is Element && (child.tag in droppedTags || isUnlikely(child))
        }
        element.children.filterIsInstance<Element>().forEach(::prune)
    }

    private fun isUnlikely(element: Element): Boolean {
        if (element.tag in setOf("body", "article", "main", "#root")) return false
        if (element.attributes["hidden"] != null || element.attributes["aria-hidden"] == "true") return true
        val role = element.attributes["role"].orEmpty()
        if (role in setOf("navigation", "complementary", "banner", "contentinfo", "dialog", "menu")) return true
        val names = element.classAndId
        return unlikely.containsMatchIn(names) && !likely.containsMatchIn(names)
    }

    private fun bestCandidate(root: Element): Element? {
        val paragraphs = mutableListOf<Element>()
        collect(root) { if (it.tag in setOf("p", "pre", "td", "li", "blockquote") || it.tag == "div" && hasOwnText(it)) paragraphs += it }
        for (p in paragraphs) {
            val text = textOf(p)
            if (text.length < 25) continue
            val score = 1 + text.count { it == ',' } + minOf(text.length / 100, 3)
            p.parent?.let { it.score += score }
            p.parent?.parent?.let { it.score += score / 2.0 }
        }
        val candidates = mutableListOf<Element>()
        collect(root) { if (it.score > 0) candidates += it }
        val best =
            candidates.maxByOrNull { candidate ->
                val weight =
                    (if (likely.containsMatchIn(candidate.classAndId)) 25 else 0) +
                        (if (candidate.tag in setOf("article", "main")) 25 else 0)
                (candidate.score + weight) * (1 - linkDensity(candidate))
            } ?: return null
        // Text split over sibling containers (e.g. one <div> per section): use the parent instead.
        val parent = best.parent
        if (parent != null && parent.tag != "#root") {
            val siblingsWithText =
                parent.children.filterIsInstance<Element>().count { it !== best && it.score >= best.score * 0.3 }
            if (siblingsWithText > 0) return parent
        }
        return best
    }

    private fun hasOwnText(element: Element): Boolean = element.children.any { it is String && it.isNotBlank() && it.trim().length > 25 }

    private fun collect(
        element: Element,
        action: (Element) -> Unit,
    ) {
        for (child in element.children) {
            if (child is Element) {
                action(child)
                collect(child, action)
            }
        }
    }

    private fun textOf(element: Element): String =
        buildString { appendText(element, this) }.let { Html.decodeEntities(it).replace(Regex("""\s+"""), " ").trim() }

    private fun appendText(
        element: Element,
        out: StringBuilder,
    ) {
        for (child in element.children) {
            when (child) {
                is String -> out.append(child)
                is Element -> appendText(child, out.append(' '))
            }
        }
    }

    private fun linkDensity(element: Element): Double {
        val total = textOf(element).length
        if (total == 0) return 1.0
        var links = 0
        collect(element) { if (it.tag == "a") links += textOf(it).length }
        return (links.toDouble() / total).coerceAtMost(1.0)
    }

    // ---- Writing the result -------------------------------------------------------------------

    private fun serialize(
        element: Element,
        out: StringBuilder,
        pageUrl: String,
    ) {
        for (child in element.children) {
            when (child) {
                is String -> out.append(Html.escape(Html.decodeEntities(child)))
                is Element -> serializeElement(child, out, pageUrl)
            }
        }
    }

    private fun serializeElement(
        element: Element,
        out: StringBuilder,
        pageUrl: String,
    ) {
        val tag = element.tag
        when {
            tag !in keptTags -> {
                serialize(element, out, pageUrl)
                if (tag in blockTags) out.append("<br>")
            }

            tag == "br" || tag == "hr" -> out.append("<$tag>")

            tag == "a" -> {
                val href = element.attributes["href"]?.let { resolve(pageUrl, it) }
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
