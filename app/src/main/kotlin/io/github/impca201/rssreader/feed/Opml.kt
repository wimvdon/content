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
                val title = outline.getAttribute("title").ifBlank { outline.getAttribute("text") }.trim()
                Entry(url, title)
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
            for (entry in entries) {
                val text = Html.escape(entry.title.ifBlank { entry.url })
                append("    <outline type=\"rss\" text=\"").append(text)
                append("\" title=\"").append(text)
                append("\" xmlUrl=\"").append(Html.escape(entry.url)).append("\"/>\n")
            }
            append("  </body>\n</opml>\n")
        }
}
