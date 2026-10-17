package io.github.impca201.rssreader.feed

import java.net.URI

/**
 * Finds the article in a web page and returns it as simple HTML (paragraphs, headings, lists,
 * quotes, links, text styles, pictures and embedded videos), the way "reader view" in browsers
 * does: menus, headers, sidebars, comments, ads and scripts are left out. Plain Kotlin with its own forgiving HTML
 * reader, so it needs no library and runs in unit tests.
 */
object ArticleExtractor {
    /** Below this many characters of text the page is not treated as an article. */
    const val MIN_TEXT_LENGTH = 250

    /** How many levels above a paragraph its score counts. */
    private const val ANCESTOR_LEVELS = 5

    /** A look-alike container counts as part of the article from this share of the best one's score. */
    private const val SAME_KIND_SHARE = 0.2

    private val voidTags = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr")
    private val rawTextTags = setOf("script", "style", "noscript", "template", "svg", "math", "textarea", "iframe", "object", "select", "canvas")
    private val droppedTags =
        (rawTextTags - "iframe") + setOf("nav", "header", "footer", "aside", "form", "button", "audio", "dialog", "menu")

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
            "figure", "figcaption", "img", "iframe", "video",
        )

    /** Embedded players worth a card in the reader; other iframes are usually ads or widgets. */
    private val knownEmbeds =
        Regex(
            "youtube\\.com|youtube-nocookie\\.com|youtu\\.be|vimeo\\.com|twitter\\.com|x\\.com|instagram\\.com|" +
                "spotify\\.com|soundcloud\\.com|codepen\\.io|dailymotion\\.com|tiktok\\.com|bsky\\.app",
            RegexOption.IGNORE_CASE,
        )
    private val blockTags = setOf("p", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "blockquote", "pre", "hr", "dl", "dt", "dd", "div", "section", "article", "main", "td", "tr", "table")

    /**
     * Words in class names and ids that mark page parts outside the article. They are matched as
     * whole parts of a name ("share-buttons", "sidebar_left"), so utility classes such as
     * "overflow-hidden", "shadow" or "text-lg" don't count.
     */
    private val unlikely =
        Regex(
            "(?:^|[-_])(comments?|sidebar|footer|footnotes?|masthead|menu|nav|navbar|navigation|share|sharing|social|" +
                "related|recommended|recommendations?|promo|sponsor(?:ed)?|advert(?:isement)?|ads?|banner|subscribe|" +
                "newsletter|signup|cookies?|consent|popup|modal|breadcrumbs?|pagination|author-bio|byline|tags|toolbar|" +
                "skip|print-only|outbrain|taboola|disqus|sr-only|visually-hidden)(?:$|[-_])",
            RegexOption.IGNORE_CASE,
        )
    private val likely =
        Regex(
            // "text" only after another word ("article-text"): Tailwind's text-lg, text-gray-500, … aren't articles.
            "(?:^|[-_])(article|content|entry|post|story|main|body|prose|blog)(?:$|[-_])|[-_]text(?:$|[-_])",
            RegexOption.IGNORE_CASE,
        )

    /** The class names and id of an element, one by one; responsive variants ("md:hidden") are left out. */
    private fun names(element: Element): List<String> =
        element.classAndId.split(Regex("""\s+""")).filter { it.isNotEmpty() && ':' !in it }

    private class Element(
        val tag: String,
        val attributes: Map<String, String>,
        val parent: Element?,
    ) {
        val children = mutableListOf<Any>() // Element or String
        var score = 0.0

        /** Characters of paragraph text anywhere inside. */
        var paragraphText = 0
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

    /** Elements whose content is running text: a button in there is a word of it (a glossary term). */
    private val textTags = setOf("p", "li", "dd", "td", "h1", "h2", "h3", "h4", "h5", "h6")

    /** Removes what is never part of an article. */
    private fun prune(
        element: Element,
        inText: Boolean = false,
    ) {
        element.children.removeAll { child ->
            child is Element && !(child.tag == "button" && inText) && (child.tag in droppedTags || isUnlikely(child))
        }
        for (child in element.children.filterIsInstance<Element>()) prune(child, inText || child.tag in textTags)
    }

    private fun isUnlikely(element: Element): Boolean {
        if (element.tag in setOf("body", "article", "main", "#root")) return false
        if (element.attributes["hidden"] != null || element.attributes["aria-hidden"] == "true") return true
        val role = element.attributes["role"].orEmpty()
        if (role in setOf("navigation", "complementary", "banner", "contentinfo", "dialog", "menu")) return true
        val names = names(element)
        // Only "hidden" on its own hides an element everywhere (Tailwind, Bootstrap's d-none).
        if (names.any { it == "hidden" || it == "d-none" }) return true
        return names.any { unlikely.containsMatchIn(it) } && names.none { likely.containsMatchIn(it) }
    }

    private fun bestCandidate(root: Element): Element? {
        val paragraphs = mutableListOf<Element>()
        collect(root) { if (it.tag in setOf("p", "pre", "td", "blockquote") || it.tag == "div" && hasOwnText(it)) paragraphs += it }
        for (p in paragraphs) {
            val text = textOf(p)
            if (text.length < 25) continue
            generateSequence(p.parent) { it.parent }.forEach { it.paragraphText += text.length }
            val score = 1 + text.count { it == ',' } + minOf(text.length / 100, 3)
            // Up to five levels up, less the further away (as Readability does): sites that wrap
            // every paragraph in boxes of its own still have one container that collects them all.
            var ancestor = p.parent
            var level = 0
            while (ancestor != null && ancestor.tag != "#root" && level < ANCESTOR_LEVELS) {
                ancestor.score += score / if (level == 0) 1.0 else if (level == 1) 2.0 else level * 3.0
                ancestor = ancestor.parent
                level++
            }
        }
        val candidates = mutableListOf<Element>()
        collect(root) { if (it.score > 0) candidates += it }
        var best =
            candidates.maxByOrNull { candidate ->
                val weight =
                    (if (names(candidate).any { likely.containsMatchIn(it) }) 25 else 0) +
                        (if (candidate.tag in setOf("article", "main")) 25 else 0)
                (candidate.score + weight) * (1 - linkDensity(candidate))
            } ?: return null
        // A small box with an article-like class name (often just the lead paragraph) can outscore
        // the container of the whole text: use the nearest ancestor that holds clearly more of it.
        val small = best
        generateSequence(small.parent) { it.parent }
            .take(ANCESTOR_LEVELS)
            .firstOrNull { it.tag != "#root" && it.paragraphText >= small.paragraphText * 3 && linkDensity(it) < 0.5 }
            ?.let { best = it }
        best = withSameKindParts(root, best)
        // Text split over sibling containers (e.g. one <div> per section): use the parent instead.
        val parent = best.parent
        if (parent != null && parent.tag != "#root") {
            val siblingsWithText =
                parent.children.filterIsInstance<Element>().count { it !== best && it.score >= best.score * 0.3 }
            if (siblingsWithText > 0) return parent
        }
        return best
    }

    /**
     * Articles split into several identical containers with ads or videos in between (several
     * `<div class="post-content">` on WordPress sites such as Ars Technica): the smallest element
     * holding [best] and its look-alikes that also hold text, or [best] itself when there are none.
     */
    private fun withSameKindParts(
        root: Element,
        best: Element,
    ): Element {
        val className = best.attributes["class"]?.trim().orEmpty()
        if (className.isEmpty()) return best
        val parts = mutableListOf<Element>()
        collect(root) {
            if (it !== best && it.tag == best.tag && it.attributes["class"]?.trim() == className &&
                it.score >= best.score * SAME_KIND_SHARE && linkDensity(it) < 0.5
            ) {
                parts += it
            }
        }
        var common = best
        for (part in parts) {
            while (!common.contains(part)) common = common.parent ?: return best
        }
        return if (common.tag == "#root") best else common
    }

    private fun Element.contains(other: Element): Boolean {
        var e: Element? = other
        while (e != null) {
            if (e === this) return true
            e = e.parent
        }
        return false
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
                    element.attributes[name]?.let { resolve(pageUrl, it) }?.let { out.attr(name, it) }
                }
                for (name in listOf("srcset", "data-srcset")) {
                    element.attributes[name]?.let { resolveSrcset(pageUrl, it) }?.let { out.attr(name, it) }
                }
                for (name in listOf("width", "height", "alt")) element.attributes[name]?.let { out.attr(name, it) }
                out.append('>')
            }

            tag == "iframe" -> {
                val src = (element.attributes["src"] ?: element.attributes["data-src"])?.let { resolve(pageUrl, it) }
                if (src != null && knownEmbeds.containsMatchIn(src)) out.append("<iframe").attr("src", src).append("></iframe>")
            }

            tag == "video" -> {
                val src =
                    (element.attributes["src"] ?: findDescendant(element, "source")?.attributes?.get("src"))
                        ?.let { resolve(pageUrl, it) }
                if (src != null) {
                    out.append("<video").attr("src", src)
                    element.attributes["poster"]?.let { resolve(pageUrl, it) }?.let { out.attr("poster", it) }
                    out.append("></video>")
                }
            }

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

    /** Elements that make their own block in the result. */
    private val blockLike = blockTags + setOf("figure", "figcaption")

    private fun hasBlockDescendant(element: Element): Boolean =
        element.children.any { child ->
            child is Element && (child.tag in blockLike || hasBlockDescendant(child))
        }

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

    private fun findDescendant(
        element: Element,
        tag: String,
    ): Element? {
        for (child in element.children) {
            if (child is Element) {
                if (child.tag == tag) return child
                findDescendant(child, tag)?.let { return it }
            }
        }
        return null
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
