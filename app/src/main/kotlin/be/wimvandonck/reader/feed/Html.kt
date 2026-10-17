package be.wimvandonck.reader.feed

/** Small, dependency-free helpers for the HTML found in feeds. */
object Html {
    private val removedBlocks =
        Regex(
            """<(script|style|iframe|object|embed|noscript|form|svg|video|audio|picture|button|select|textarea)\b[^>]*>.*?</\1\s*>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
    private val removedTags =
        Regex(
            """</?(img|source|input|font|link|meta|base|track|wbr|iframe|script|style|embed|object)\b[^>]*>""",
            RegexOption.IGNORE_CASE,
        )
    private val presentationAttributes =
        Regex(
            """\s(style|color|bgcolor|face|size|width|height|align|class|id)\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)""",
            RegexOption.IGNORE_CASE,
        )
    private val comments = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)
    private val tags = Regex("""<[^>]*>""")
    private val blockBreaks = Regex("""<(br|/p|/div|/li|/h[1-6]|/blockquote|/tr)\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val entity = Regex("""&(#[xX][0-9a-fA-F]{1,6}|#\d{1,7}|[a-zA-Z][a-zA-Z0-9]{1,31});""")

    /** The HTML entities found in feeds besides XML's own five, as Unicode code points. */
    val namedEntities: Map<String, Int> =
        mapOf(
            "amp" to 38, "lt" to 60, "gt" to 62, "quot" to 34, "apos" to 39,
            "nbsp" to 160, "iexcl" to 161, "cent" to 162, "pound" to 163, "euro" to 8364, "yen" to 165,
            "copy" to 169, "reg" to 174, "trade" to 8482, "deg" to 176, "plusmn" to 177, "times" to 215,
            "divide" to 247, "micro" to 181, "para" to 182, "middot" to 183, "sect" to 167, "laquo" to 171,
            "raquo" to 187, "iquest" to 191, "frac12" to 189, "frac14" to 188, "frac34" to 190,
            "ndash" to 8211, "mdash" to 8212, "lsquo" to 8216, "rsquo" to 8217, "sbquo" to 8218,
            "ldquo" to 8220, "rdquo" to 8221, "bdquo" to 8222, "hellip" to 8230, "bull" to 8226,
            "prime" to 8242, "dagger" to 8224, "permil" to 8240, "larr" to 8592, "rarr" to 8594,
            "uarr" to 8593, "darr" to 8595, "thinsp" to 8201, "ensp" to 8194, "emsp" to 8195,
            "zwj" to 8205, "zwnj" to 8204, "shy" to 173,
            "Agrave" to 192, "Aacute" to 193, "Acirc" to 194, "Atilde" to 195, "Auml" to 196, "Aring" to 197,
            "AElig" to 198, "Ccedil" to 199, "Egrave" to 200, "Eacute" to 201, "Ecirc" to 202, "Euml" to 203,
            "Igrave" to 204, "Iacute" to 205, "Icirc" to 206, "Iuml" to 207, "Ntilde" to 209, "Ograve" to 210,
            "Oacute" to 211, "Ocirc" to 212, "Otilde" to 213, "Ouml" to 214, "Oslash" to 216, "Ugrave" to 217,
            "Uacute" to 218, "Ucirc" to 219, "Uuml" to 220, "Yacute" to 221, "szlig" to 223, "agrave" to 224,
            "aacute" to 225, "acirc" to 226, "atilde" to 227, "auml" to 228, "aring" to 229, "aelig" to 230,
            "ccedil" to 231, "egrave" to 232, "eacute" to 233, "ecirc" to 234, "euml" to 235, "igrave" to 236,
            "iacute" to 237, "icirc" to 238, "iuml" to 239, "ntilde" to 241, "ograve" to 242, "oacute" to 243,
            "ocirc" to 244, "otilde" to 245, "ouml" to 246, "oslash" to 248, "ugrave" to 249, "uacute" to 250,
            "ucirc" to 251, "uuml" to 252, "yacute" to 253, "yuml" to 255, "OElig" to 338, "oelig" to 339,
        )

    /**
     * Article HTML ready for display: scripts, embeds, images and forms removed, and colours,
     * fonts and sizes stripped so the reader's own style (and dark or sepia pages) apply.
     */
    fun clean(html: String): String =
        html
            .replace(comments, "")
            .replace(removedBlocks, "")
            .replace(removedTags, "")
            .replace(presentationAttributes, "")
            .trim()

    /** The text of some HTML, on one line: tags removed, entities decoded, whitespace collapsed. */
    fun toPlainText(html: String): String =
        decodeEntities(
            html
                .replace(comments, "")
                .replace(removedBlocks, " ")
                .replace(blockBreaks, " ")
                .replace(tags, ""),
        ).replace(' ', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()

    /** A short plain-text preview for the article list. */
    fun snippet(
        html: String,
        maxLength: Int = 240,
    ): String {
        // Long articles: only the start is needed, cut before a tag that would be left half open.
        var start = if (html.length > maxLength * SNIPPET_SOURCE_FACTOR) html.take(maxLength * SNIPPET_SOURCE_FACTOR) else html
        if (start.lastIndexOf('<') > start.lastIndexOf('>')) start = start.substringBeforeLast('<')
        val text = toPlainText(start)
        if (text.length <= maxLength) return text
        val cut = text.lastIndexOf(' ', maxLength).takeIf { it > maxLength / 2 } ?: maxLength
        return text.take(cut).trimEnd() + "…"
    }

    private const val SNIPPET_SOURCE_FACTOR = 20

    fun decodeEntities(text: String): String {
        if ('&' !in text) return text
        return entity.replace(text) { m ->
            val name = m.groupValues[1]
            val codePoint =
                when {
                    name.startsWith("#x") || name.startsWith("#X") -> name.drop(2).toIntOrNull(16)
                    name.startsWith("#") -> name.drop(1).toIntOrNull()
                    else -> namedEntities[name]
                }
            if (codePoint == null || !Character.isValidCodePoint(codePoint) || codePoint == 0) {
                m.value
            } else {
                String(Character.toChars(codePoint))
            }
        }
    }

    /** Escapes text for use inside HTML or XML, attributes included. */
    fun escape(text: String): String =
        buildString(text.length) {
            for (c in text) {
                when (c) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&#39;")
                    else -> append(c)
                }
            }
        }
}
