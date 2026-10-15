package io.github.impca201.rssreader.feed

import org.w3c.dom.Element
import org.xml.sax.SAXException
import java.io.ByteArrayInputStream
import java.io.IOException

/** OPML, the file format feed readers use to move subscriptions between apps. */
object Opml {
    data class Entry(
        val url: String,
        val title: String,
        /** The title of the folder the feed is in, or null. */
        val folder: String? = null,
    )

    /** Every feed in the file, folders included. Empty when the file isn't OPML. */
    fun parse(bytes: ByteArray): List<Entry> {
        val root =
            try {
                xmlDocumentBuilder()
                    .parse(ByteArrayInputStream(bytes))
                    .documentElement
            } catch (_: SAXException) {
                return emptyList()
            } catch (_: IOException) {
                return emptyList()
            }
        if (!root.nodeName.equals("opml", ignoreCase = true)) return emptyList()
        val outlines = root.getElementsByTagNameNS("*", "outline")
        return (0 until outlines.length)
            .map { outlines.item(it) as Element }
            .mapNotNull { outline ->
                val url = FeedDiscovery.normalizeUrl(outline.getAttribute("xmlUrl")) ?: return@mapNotNull null
                val title = outline.title()
                // The nearest enclosing outline without a feed address is the feed's folder.
                var parent = outline.parentNode as? Element
                while (parent != null && (parent.localName ?: parent.nodeName) == "outline" && parent.getAttribute("xmlUrl").isNotBlank()) {
                    parent = parent.parentNode as? Element
                }
                val folder = parent?.takeIf { (it.localName ?: it.nodeName) == "outline" }?.title()?.ifBlank { null }
                Entry(url, title, folder)
            }.distinctBy { it.url }
    }

    fun write(
        title: String,
        entries: List<Entry>,
    ): String =
        buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<opml version=\"2.0\">\n")
            append("  <head>\n    <title>").append(Html.escape(title)).append("</title>\n  </head>\n")
            append("  <body>\n")
            for (entry in entries.filter { it.folder == null }) appendFeed(entry, "    ")
            for ((folder, inFolder) in entries.filter { it.folder != null }.groupBy { it.folder!! }) {
                val text = Html.escape(folder)
                append("    <outline text=\"").append(text).append("\" title=\"").append(text).append("\">\n")
                for (entry in inFolder) appendFeed(entry, "      ")
                append("    </outline>\n")
            }
            append("  </body>\n</opml>\n")
        }

    private fun StringBuilder.appendFeed(
        entry: Entry,
        indent: String,
    ) {
        val text = Html.escape(entry.title.ifBlank { entry.url })
        append(indent).append("<outline type=\"rss\" text=\"").append(text)
        append("\" title=\"").append(text)
        append("\" xmlUrl=\"").append(Html.escape(entry.url)).append("\"/>\n")
    }

    private fun Element.title(): String = getAttribute("title").ifBlank { getAttribute("text") }.trim()
}
