package be.wimvandonck.content.feed

/**
 * Splits a run of article HTML into the reader's text blocks: paragraphs, headings, lists,
 * quotes and code. The reader spaces and styles each block itself, so paragraphs stay apart and
 * lists and quotes keep their shape whatever the page's markup (a <div> per paragraph, text
 * separated by two <br>s, …). Plain Kotlin, unit tested.
 */
object TextLayout {
    private val token = Regex("""<(/?)([a-zA-Z][a-zA-Z0-9]*)\b[^>]*>|[^<]+|<""")

    /** Elements that start and end a paragraph. */
    private val paragraphTags =
        setOf(
            "p", "div", "section", "article", "main", "header", "footer", "aside", "center", "address",
            "dl", "dt", "dd", "table", "thead", "tbody", "tfoot", "tr", "td", "th", "caption",
            "figure", "figcaption", "details", "summary", "hr",
        )

    private class OpenList(val ordered: Boolean, var count: Int = 0)

    private class Builder {
        val blocks = mutableListOf<ArticleBlock>()
        val buffer = StringBuilder()
        val lists = ArrayDeque<OpenList>()
        var quoteDepth = 0
        var inPre = false
        var heading = 0
        var marker: String? = null
        val listItems = mutableListOf<ArticleBlock.ListItem>()
        val quoteParagraphs = mutableListOf<String>()

        fun flush() {
            val raw = buffer.toString()
            buffer.setLength(0)
            if (inPre) {
                val text = Html.decodeEntities(raw.replace(Regex("""<[^>]*>"""), "")).trim('\n', '\r')
                if (text.isNotBlank()) {
                    endGroups()
                    blocks += ArticleBlock.Code(text.trimEnd())
                }
                return
            }
            val html = raw.trim().removeEdgeBreaks()
            if (Html.toPlainText(html).isEmpty()) return
            when {
                heading > 0 -> {
                    endGroups()
                    blocks += ArticleBlock.Heading(heading, html)
                }
                lists.isNotEmpty() -> {
                    endQuote()
                    val depth = lists.size
                    val itemMarker = marker ?: if (lists.last().ordered) "${lists.last().count}." else bullet(depth)
                    // Later paragraphs of the same item get no marker of their own.
                    marker = ""
                    listItems += ArticleBlock.ListItem(html, itemMarker, depth)
                }
                quoteDepth > 0 -> {
                    endList()
                    quoteParagraphs += html
                }
                else -> {
                    endGroups()
                    blocks += ArticleBlock.Text(html)
                }
            }
        }

        fun endList() {
            if (listItems.isNotEmpty()) blocks += ArticleBlock.ListBlock(listItems.toList())
            listItems.clear()
        }

        fun endQuote() {
            if (quoteParagraphs.isNotEmpty()) blocks += ArticleBlock.Quote(quoteParagraphs.toList())
            quoteParagraphs.clear()
        }

        fun endGroups() {
            endList()
            endQuote()
        }
    }

    fun blocks(html: String): List<ArticleBlock> {
        val b = Builder()
        val tokens = token.findAll(html).toList()
        var index = 0
        while (index < tokens.size) {
            val m = tokens[index]
            index++
            val name = m.groupValues[2].lowercase()
            if (name.isEmpty()) {
                // Text (or a lone "<").
                b.buffer.append(m.value)
                continue
            }
            val closing = m.groupValues[1] == "/"
            when {
                b.inPre && name != "pre" -> if (name == "br") b.buffer.append('\n') else b.buffer.append(m.value)

                name == "br" -> {
                    // Two line breaks in a row separate paragraphs; one stays a line break.
                    var next = index
                    while (next < tokens.size && tokens[next].groupValues[2].isEmpty() && tokens[next].value.isBlank()) next++
                    if (next < tokens.size && tokens[next].groupValues[2].equals("br", ignoreCase = true)) {
                        b.flush()
                        index = next + 1
                    } else {
                        b.buffer.append("<br>")
                    }
                }

                name.length == 2 && name[0] == 'h' && name[1] in '1'..'6' -> {
                    b.flush()
                    b.heading = if (closing) 0 else name[1] - '0'
                }

                name == "ul" || name == "ol" -> {
                    b.flush()
                    if (closing) {
                        b.lists.removeLastOrNull()
                        if (b.lists.isEmpty()) b.endList()
                    } else {
                        b.lists.addLast(OpenList(ordered = name == "ol"))
                    }
                }

                name == "li" -> {
                    b.flush()
                    if (!closing) {
                        val list = b.lists.lastOrNull()
                        if (list != null) {
                            list.count++
                            b.marker = if (list.ordered) "${list.count}." else bullet(b.lists.size)
                        }
                    }
                }

                name == "blockquote" -> {
                    b.flush()
                    if (closing) {
                        b.quoteDepth = (b.quoteDepth - 1).coerceAtLeast(0)
                        if (b.quoteDepth == 0) b.endQuote()
                    } else {
                        b.endList()
                        b.quoteDepth++
                    }
                }

                name == "pre" -> {
                    b.flush()
                    b.inPre = !closing
                }

                name in paragraphTags -> b.flush()

                else -> b.buffer.append(m.value)
            }
        }
        b.flush()
        b.endGroups()
        return b.blocks
    }

    private fun bullet(depth: Int): String = if (depth % 2 == 1) "•" else "◦"

    private val edgeBreaks = Regex("""^(\s*<br\s*/?>\s*)+|(\s*<br\s*/?>\s*)+$""", RegexOption.IGNORE_CASE)

    private fun String.removeEdgeBreaks(): String = replace(edgeBreaks, "")
}
