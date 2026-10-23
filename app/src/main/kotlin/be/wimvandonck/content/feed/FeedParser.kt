package be.wimvandonck.content.feed

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.StringReader
import java.net.URI
import java.nio.charset.Charset
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

/** A downloaded feed, ready to merge into the library. */
data class ParsedFeed(
    val title: String,
    val siteUrl: String?,
    val items: List<ParsedItem>,
)

data class ParsedItem(
    val id: String,
    val title: String,
    val link: String?,
    val author: String?,
    val html: String,
    val publishedAt: Long?,
    /** The article's picture, shown as list thumbnail and reader hero image. */
    val imageUrl: String? = null,
)

/** The document is not an RSS or Atom feed (for example a web page). */
class NotAFeedException(message: String) : Exception(message)

/**
 * Reads RSS 2.0 (and 0.9x), RSS 1.0 (RDF) and Atom feeds. Uses the platform's XML parser, which
 * exists on Android and on the JVM, so it is unit tested without a device.
 */
object FeedParser {
    private const val ATOM_NS = "http://www.w3.org/2005/Atom"
    private const val RSS1_NS = "http://purl.org/rss/1.0/"
    private const val CONTENT_NS = "http://purl.org/rss/1.0/modules/content/"
    private const val DC_NS = "http://purl.org/dc/elements/1.1/"
    private const val XHTML_NS = "http://www.w3.org/1999/xhtml"
    private const val MEDIA_NS = "http://search.yahoo.com/mrss/"
    private const val ITUNES_NS = "http://www.itunes.com/dtds/podcast-1.0.dtd"

    /** Namespaces of plain RSS elements: none, or RSS 1.0's. */
    private val rssNamespaces = setOf(null, "", RSS1_NS)
    private val atomNamespaces = setOf(ATOM_NS, "http://purl.org/atom/ns#", null, "")

    /**
     * Parses [bytes] downloaded from [baseUrl] (used to resolve relative links).
     *
     * @throws NotAFeedException when the bytes are not a feed.
     */
    fun parse(
        bytes: ByteArray,
        baseUrl: String,
    ): ParsedFeed {
        val root = parseXml(bytes) ?: throw NotAFeedException("Not XML")
        return when (root.localName ?: root.nodeName) {
            "rss" -> parseRss(root.child("channel", rssNamespaces) ?: root, root, baseUrl)
            "RDF" -> parseRss(root.child("channel", rssNamespaces) ?: root, root, baseUrl)
            "feed" -> parseAtom(root, baseUrl)
            else -> throw NotAFeedException("Unknown root element ${root.nodeName}")
        }
    }

    private fun parseXml(bytes: ByteArray): Element? {
        val trimmed = trimLeadingWhitespace(bytes)
        return try {
            xmlDocumentBuilder().parse(ByteArrayInputStream(trimmed)).documentElement
        } catch (_: SAXException) {
            // Hand-made feeds often use HTML entities XML doesn't know (&nbsp;, &mdash;, …).
            retryWithFixedEntities(trimmed)
        } catch (_: IOException) {
            null
        }
    }

    private fun retryWithFixedEntities(bytes: ByteArray): Element? {
        val text = String(bytes, declaredCharset(bytes))
        val fixed = replaceHtmlEntities(text).replaceFirst(Regex("""^<\?xml[^>]*\?>"""), "")
        return try {
            xmlDocumentBuilder().parse(InputSource(StringReader(fixed))).documentElement
        } catch (_: SAXException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    /** Replaces HTML-only named entities with numeric references, which XML always accepts. */
    internal fun replaceHtmlEntities(text: String): String =
        Regex("""&([a-zA-Z][a-zA-Z0-9]{1,31});""").replace(text) { m ->
            val name = m.groupValues[1]
            if (name in setOf("amp", "lt", "gt", "quot", "apos")) {
                m.value
            } else {
                Html.namedEntities[name]?.let { "&#$it;" } ?: "&amp;$name;"
            }
        }

    private fun declaredCharset(bytes: ByteArray): Charset {
        val head = String(bytes, 0, minOf(bytes.size, 200), Charsets.ISO_8859_1)
        val name = Regex("""encoding\s*=\s*["']([A-Za-z0-9._-]+)["']""").find(head)?.groupValues?.get(1)
        return runCatching { Charset.forName(name ?: "UTF-8") }.getOrDefault(Charsets.UTF_8)
    }

    private fun trimLeadingWhitespace(bytes: ByteArray): ByteArray {
        var start = 0
        // UTF-8 byte order mark.
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) start = 3
        while (start < bytes.size && bytes[start].toInt().toChar() in " \t\r\n") start++
        return if (start == 0) bytes else bytes.copyOfRange(start, bytes.size)
    }

    private fun parseRss(
        channel: Element,
        root: Element,
        baseUrl: String,
    ): ParsedFeed {
        val siteUrl = channel.childText("link", rssNamespaces)?.let { resolve(baseUrl, it) }
        val linkBase = siteUrl ?: baseUrl
        // RSS 2.0 puts items inside the channel, RSS 1.0 next to it.
        val itemElements = channel.children("item", rssNamespaces).ifEmpty { root.children("item", rssNamespaces) }
        val items =
            itemElements.map { item ->
                val guidElement = item.child("guid", rssNamespaces)
                val guid = guidElement?.textContent?.trim()?.takeIf { it.isNotEmpty() }
                val guidIsLink = guidElement?.getAttribute("isPermaLink")?.equals("false", ignoreCase = true) != true
                val link =
                    item.childText("link", rssNamespaces)?.let { resolve(linkBase, it) }
                        ?: guid?.takeIf { guidIsLink && it.startsWith("http") }
                val description = item.childText("description", rssNamespaces).orEmpty()
                val content = item.childText("encoded", setOf(CONTENT_NS)).orEmpty()
                val html = if (content.length > description.length) content else description
                val title = item.childText("title", rssNamespaces)?.let(Html::toPlainText).orEmpty()
                val date = FeedDates.parse(item.childText("pubDate", rssNamespaces) ?: item.childText("date", setOf(DC_NS)))
                ParsedItem(
                    id = guid ?: link ?: fallbackId(title, date, html),
                    title = title.ifEmpty { Html.snippet(html, 80) },
                    link = link,
                    author =
                        (item.childText("creator", setOf(DC_NS)) ?: item.childText("author", rssNamespaces))
                            ?.let(Html::toPlainText)
                            ?.ifEmpty { null },
                    html = html,
                    publishedAt = date,
                    imageUrl = itemImage(item, html, link ?: linkBase),
                )
            }
        return ParsedFeed(
            title = channel.childText("title", rssNamespaces)?.let(Html::toPlainText).orEmpty(),
            siteUrl = siteUrl,
            items = items,
        )
    }

    private fun parseAtom(
        feed: Element,
        baseUrl: String,
    ): ParsedFeed {
        val feedBase = xmlBase(feed, baseUrl)
        val feedAuthor = feed.child("author", atomNamespaces)?.childText("name", atomNamespaces)
        val items =
            feed.children("entry", atomNamespaces).map { entry ->
                val entryBase = xmlBase(entry, feedBase)
                val link = alternateLink(entry)?.let { resolve(entryBase, it) }
                val content = entry.child("content", atomNamespaces)?.let(::atomText).orEmpty()
                val summary = entry.child("summary", atomNamespaces)?.let(::atomText).orEmpty()
                val html = content.ifEmpty { summary }
                val title = entry.child("title", atomNamespaces)?.let(::atomText)?.let(Html::toPlainText).orEmpty()
                val date =
                    FeedDates.parse(
                        entry.childText("published", atomNamespaces)
                            ?: entry.childText("updated", atomNamespaces)
                            ?: entry.childText("issued", atomNamespaces),
                    )
                ParsedItem(
                    id = entry.childText("id", atomNamespaces) ?: link ?: fallbackId(title, date, html),
                    title = title.ifEmpty { Html.snippet(html, 80) },
                    link = link,
                    author = entry.child("author", atomNamespaces)?.childText("name", atomNamespaces) ?: feedAuthor,
                    html = html,
                    publishedAt = date,
                    imageUrl = itemImage(entry, html, link ?: entryBase),
                )
            }
        return ParsedFeed(
            title = feed.child("title", atomNamespaces)?.let(::atomText)?.let(Html::toPlainText).orEmpty(),
            siteUrl = alternateLink(feed)?.let { resolve(feedBase, it) },
            items = items,
        )
    }

    /**
     * The item's picture: Media RSS (media:content, media:thumbnail, also inside media:group), an
     * image enclosure, an iTunes image, or else the first picture in its text.
     */
    private fun itemImage(
        item: Element,
        html: String,
        base: String,
    ): String? {
        val media = item.children("group", setOf(MEDIA_NS)).flatMap { it.childElements() } + item.childElements()
        val candidates =
            media.filter { it.namespaceURI == MEDIA_NS && (it.localName == "content" || it.localName == "thumbnail") }
                .filter { el ->
                    el.localName == "thumbnail" ||
                        el.getAttribute("medium") == "image" ||
                        el.getAttribute("type").startsWith("image/") ||
                        IMAGE_FILE.containsMatchIn(el.getAttribute("url"))
                }.sortedByDescending { it.getAttribute("width").toIntOrNull() ?: 0 }
                .map { it.getAttribute("url") } +
                item.childElements()
                    .filter { (it.localName == "enclosure" || (it.localName == "link" && it.getAttribute("rel") == "enclosure")) }
                    .filter { it.getAttribute("type").startsWith("image/") || IMAGE_FILE.containsMatchIn(it.getAttribute("url") + it.getAttribute("href")) }
                    .map { it.getAttribute("url").ifEmpty { it.getAttribute("href") } } +
                item.children("image", setOf(ITUNES_NS)).map { it.getAttribute("href") }
        return candidates.firstNotNullOfOrNull { ArticleBlocks.imageUrl(it, base) } ?: ArticleBlocks.firstImage(html, base)
    }

    private val IMAGE_FILE = Regex("""\.(jpe?g|png|webp|gif|avif)(\?|$)""", RegexOption.IGNORE_CASE)

    private fun Element.childElements(): List<Element> {
        val nodes = childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    /** The text of an Atom text construct (type text, html or xhtml), as HTML. */
    private fun atomText(element: Element): String =
        when (element.getAttribute("type").lowercase()) {
            "xhtml" -> {
                val div = element.child("div", setOf(XHTML_NS, null, ""))
                (div ?: element).childNodes.let { nodes ->
                    buildString { for (i in 0 until nodes.length) serialize(nodes.item(i), this) }
                }.trim()
            }

            "html", "text/html" -> element.textContent.trim()

            else -> Html.escape(element.textContent.trim()).replace("\n", "<br>")
        }

    private fun alternateLink(element: Element): String? {
        val links = element.children("link", atomNamespaces)
        val alternate =
            links.firstOrNull { it.getAttribute("rel").let { rel -> rel.isEmpty() || rel == "alternate" } && it.hasAttribute("href") }
        return alternate?.getAttribute("href")?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun xmlBase(
        element: Element,
        parentBase: String,
    ): String {
        val base = element.getAttributeNS("http://www.w3.org/XML/1998/namespace", "base")
        return if (base.isNullOrBlank()) parentBase else resolve(parentBase, base)
    }

    private fun resolve(
        base: String,
        href: String,
    ): String {
        val trimmed = href.trim()
        return try {
            URI(base).resolve(URI(trimmed)).toString()
        } catch (_: Exception) {
            trimmed
        }
    }

    private fun fallbackId(
        title: String,
        date: Long?,
        html: String,
    ): String = "hash:" + (title + "|" + date + "|" + html.take(500)).hashCode().toUInt().toString(16)

    /** Writes an XHTML DOM node back out as HTML. */
    private fun serialize(
        node: Node,
        out: StringBuilder,
    ) {
        when (node.nodeType) {
            Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> out.append(Html.escape(node.nodeValue))

            Node.ELEMENT_NODE -> {
                val name = node.localName ?: node.nodeName
                out.append('<').append(name)
                val attributes = node.attributes
                for (i in 0 until attributes.length) {
                    val attribute = attributes.item(i)
                    val attributeName = attribute.localName ?: attribute.nodeName
                    if (attribute.nodeName.startsWith("xmlns")) continue
                    out.append(' ').append(attributeName).append("=\"").append(Html.escape(attribute.nodeValue)).append('"')
                }
                out.append('>')
                val children = node.childNodes
                for (i in 0 until children.length) serialize(children.item(i), out)
                out.append("</").append(name).append('>')
            }
        }
    }

    private fun Element.children(
        localName: String,
        namespaces: Set<String?>,
    ): List<Element> {
        val result = mutableListOf<Element>()
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element && (node.localName ?: node.nodeName) == localName && node.namespaceURI in namespaces) {
                result += node
            }
        }
        return result
    }

    private fun Element.child(
        localName: String,
        namespaces: Set<String?>,
    ): Element? = children(localName, namespaces).firstOrNull()

    private fun Element.childText(
        localName: String,
        namespaces: Set<String?>,
    ): String? =
        child(localName, namespaces)
            ?.textContent
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
}

/**
 * A namespace-aware XML parser that never reads files or URLs named in the document and keeps
 * quiet about recoverable errors. Not every platform parser supports every safety feature, so
 * each one is optional.
 */
internal fun xmlDocumentBuilder(): DocumentBuilder =
    DocumentBuilderFactory
        .newInstance()
        .apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            isCoalescing = true
            listOf(
                "http://xml.org/sax/features/external-general-entities",
                "http://xml.org/sax/features/external-parameter-entities",
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
            ).forEach { feature ->
                try {
                    setFeature(feature, false)
                } catch (_: ParserConfigurationException) {
                    // Not supported here.
                }
            }
        }.newDocumentBuilder()
        .apply { setErrorHandler(QuietErrorHandler) }

/** Fails on fatal errors without printing warnings to the log. */
private object QuietErrorHandler : ErrorHandler {
    override fun warning(exception: SAXParseException) = Unit

    override fun error(exception: SAXParseException) = Unit

    override fun fatalError(exception: SAXParseException): Unit = throw exception
}
