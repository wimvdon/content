package to.wim.content.feed

/**
 * Takes the usual email chrome out of a newsletter's HTML before the reader turns it into blocks
 * (2026-11-15): a "View in browser" link near the top, hidden preheader text, and spacer pictures.
 * Only for articles of a Newsletters feed, never for ordinary RSS articles. Conservative: what is
 * not clearly chrome stays. The same rules as the web app's `emailCleanup.ts`; plain Kotlin, tested
 * on the JVM.
 */
object EmailCleanup {
    private val tag = Regex("""<(/?)([a-zA-Z][a-zA-Z0-9]*)([^>]*)>""")
    private val anchor = Regex("""<a\b[^>]*>([\s\S]*?)</a\s*>""", RegexOption.IGNORE_CASE)
    private val image = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val styleAttr = Regex("""\bstyle\s*=\s*(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE)
    private val srcAttr = Regex("""\bsrc\s*=\s*(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE)
    private val skipped = Regex("""<head\b[\s\S]*?</head\s*>|<style\b[\s\S]*?</style\s*>|<!--[\s\S]*?-->""", RegexOption.IGNORE_CASE)
    private val anyTag = Regex("""<[^>]*>""")
    private val richInner = Regex("""<(?:img|a|table)\b""", RegexOption.IGNORE_CASE)
    private val spaces = Regex("""\s+""")
    private val invisible = Regex("""[\u200B-\u200D\u00A0\u2060]""")

    /** Elements whose hiding is judged; others (tables, lists, links) are left alone. */
    private val hideable = setOf("div", "span", "p", "td", "center", "font")

    /** How much text a weakly hidden element may hold and still count as a preheader. */
    private const val PREHEADER_MAX = 500

    /** A "view in browser" link counts when at most this much text comes before it. */
    private const val TOP_TEXT = 400

    /** Longest link text read as a "view in browser" link. */
    private const val LINK_MAX = 80

    private val weakHidden = Regex("""(?:^|;)(?:max-height:0(?:px)?|opacity:0|font-size:0(?:px)?|mso-hide:all)(?:;|$)""")
    private val pixelStyle = Regex("""(?:^|;)(?:width|height):[01](?:px)?(?:;|$)""")
    private val spacerName = Regex("""(?:^|/)(?:spacer|space|blank|transparent|clear|shim|1x1|pixel|px)[a-z0-9_.-]*\.(?:gif|png)(?:\?|#|$)""")

    /** Whole link texts (lower case, trimmed) that mean "view this email in your browser". */
    private val viewOnlineParts =
        listOf(
            """(?:view|read|open|see|show|display)(?: (?:this|the|our|my))?(?: (?:email|e-mail|newsletter|message|mail|version|post|issue))?""" +
                """ (?:online|on the web|as a web ?page|in (?:your |a |the |my )?(?:web )?browser|in browser)""",
            """(?:view|see|open) (?:the |our )?(?:web|online|browser) version""",
            """(?:web|online|browser) version""",
            """click here to (?:view|read|open|see) (?:this |the )?(?:email |e-mail |newsletter |message )?(?:online|in (?:your |a |the )?browser)""",
            """bekijk(?: (?:deze|de|dit))?(?: (?:e-?mail|nieuwsbrief|mail|bericht))? (?:online|in (?:je|uw|de) browser|in browser)""",
            """(?:online|in (?:je|uw|de) browser) (?:bekijken|lezen|openen)""",
            """(?:lees|open) (?:online|in (?:je|uw|de) browser)""",
            """(?:bekijk (?:de )?)?(?:online|web|browser) ?versie""",
            """(?:voir|lire|afficher|ouvrir)(?: (?:ce|cet|cette|le|la|l'))?(?: (?:mail|e-?mail|message|newsletter|courriel|version))? """ +
                """(?:en ligne|dans (?:votre |le |un )?navigateur|sur (?:le |votre )?(?:web|navigateur))""",
            """version (?:en ligne|web)""",
            """(?:im|in (?:ihrem|deinem|einem)) browser (?:ansehen|anzeigen|öffnen|lesen)""",
            """(?:diese )?(?:e-?mail|nachricht|newsletter) (?:im|in (?:ihrem|deinem)) browser (?:ansehen|anzeigen|öffnen)""",
            """online (?:ansehen|lesen|anzeigen)""",
            """(?:browser|web|online)-?version""",
            """(?:ver|leer|abrir)(?: (?:este|esta|el|la|nuestro))?(?: (?:correo|mensaje|email|e-mail|boletín|newsletter|versión))? """ +
                """(?:en línea|online|en (?:el |tu |su |un )?(?:navegador|explorador))""",
            """versión (?:web|en línea|online)""",
            """(?:visualizza|vedi|leggi|apri)(?: (?:questa|questo|la|il))?(?: (?:email|e-mail|mail|newsletter|messaggio|versione))? """ +
                """(?:online|in linea|(?:nel|sul|nel tuo|sul tuo) browser|sul web)""",
            """versione (?:online|web)""",
        )
    private val viewOnline = Regex("^(?:" + viewOnlineParts.joinToString("|") + ")$")

    /** Words that lead into a "view in browser" link, taken away with it when they stand right before it. */
    private val leadIn =
        Regex(
            """(?:having trouble (?:viewing|reading|seeing) this (?:email|e-mail|message|newsletter)|trouble (?:viewing|reading|seeing) this (?:email|e-mail|message|newsletter)|""" +
                """can['’]?t see this (?:email|e-mail|message|newsletter)(?: properly| correctly)?|is this (?:email|e-mail|message) not displaying (?:properly|correctly)|""" +
                """problemen met (?:het )?(?:bekijken|weergeven|lezen) van (?:deze|dit) (?:e-?mail|bericht|nieuwsbrief)|""" +
                """wordt (?:deze|dit) (?:e-?mail|bericht|nieuwsbrief) niet (?:goed|correct) weergegeven)[?:.!,]?\s*(?:-|–|—)?\s*$""",
            RegexOption.IGNORE_CASE,
        )

    /** True when an inline style gives a picture a width or height of 0 or 1 pixel: a tracking pixel or spacer. */
    fun isPixelStyle(style: String?): Boolean = style != null && pixelStyle.containsMatchIn(normalize(style))

    /** [html] without the email chrome. */
    fun clean(html: String): String = removeSpacers(removeViewInBrowser(removeHidden(html)))

    private fun normalize(style: String): String = style.lowercase().filterNot { it.isWhitespace() }.replace("!important", "")

    private fun styleOf(attrs: String): String? = styleAttr.find(attrs)?.let { normalize(it.groups[1]?.value ?: it.groups[2]?.value.orEmpty()) }

    private fun textLength(html: String): Int = spaces.replace(anyTag.replace(html, ""), " ").trim().length

    /** Hidden preheader text: elements hidden by their inline style, with what is in them. */
    fun removeHidden(html: String): String {
        val tokens = tag.findAll(html).toList()
        val out = StringBuilder()
        var copied = 0
        var i = 0
        while (i < tokens.size) {
            val open = tokens[i]
            val name = open.groupValues[2].lowercase()
            val style =
                if (open.groupValues[1].isEmpty() && name in hideable && !open.value.endsWith("/>")) styleOf(open.groupValues[3]) else null
            val strong = style != null && "display:none" in style
            val weak = style != null && weakHidden.containsMatchIn(style)
            if (!strong && !weak) {
                i++
                continue
            }
            // Its end: the closing tag of the same name that balances this one; none, and it stays.
            var depth = 1
            var j = i + 1
            while (j < tokens.size) {
                val t = tokens[j]
                if (t.groupValues[2].lowercase() == name && !t.value.endsWith("/>")) {
                    depth += if (t.groupValues[1].isEmpty()) 1 else -1
                    if (depth == 0) break
                }
                j++
            }
            if (j >= tokens.size) {
                i++
                continue
            }
            val inner = html.substring(open.range.last + 1, tokens[j].range.first)
            // Display none is hidden for sure; the other signs also hide a wrapper around pictures, so they only count for plain text.
            if (!strong && (richInner.containsMatchIn(inner) || textLength(inner) > PREHEADER_MAX)) {
                i++
                continue
            }
            out.append(html, copied, open.range.first)
            copied = tokens[j].range.last + 1
            i = j + 1
        }
        out.append(html, copied, html.length)
        return out.toString()
    }

    /** A "View in browser" link (and "Having trouble viewing this email?" right before it) near the top. */
    fun removeViewInBrowser(html: String): String {
        // The same text with head, styles and comments blanked out, to measure how far down a link is.
        val scan = skipped.replace(html) { " ".repeat(it.value.length) }
        val found =
            anchor.findAll(scan).filter { m ->
                val label = linkText(m.groupValues[1])
                label.length <= LINK_MAX && viewOnline.matches(label) && textLength(scan.substring(0, m.range.first)) <= TOP_TEXT
            }.toList()
        if (found.isEmpty()) return html
        val out = StringBuilder()
        var copied = 0
        for (m in found) {
            var start = m.range.first
            val before = html.substring(copied, start)
            val textStart = before.lastIndexOf('>') + 1
            val leadMatch = leadIn.find(before.substring(textStart))
            if (leadMatch != null) start = copied + textStart + leadMatch.range.first
            out.append(html, copied, start)
            copied = m.range.last + 1
        }
        out.append(html, copied, html.length)
        return out.toString()
    }

    private fun linkText(inner: String): String =
        spaces
            .replace(
                invisible.replace(
                    anyTag.replace(inner, "").replace("&nbsp;", " ").replace("&#160;", " ").replace("&amp;", "&").replace("&#39;", "'").replace("&rsquo;", "’"),
                    " ",
                ),
                " ",
            ).trim()
            .trim { it in "<>›»«→←-–—|.:!()[]•·" || it.isWhitespace() }
            .lowercase()

    /** Spacer pictures: one named like spacer.gif, or sized 0 or 1 pixel in its style. */
    fun removeSpacers(html: String): String =
        image.replace(html) { m ->
            val text = m.value
            val src = srcAttr.find(text)?.let { it.groups[1]?.value ?: it.groups[2]?.value }?.lowercase().orEmpty()
            val style = styleAttr.find(text)?.let { it.groups[1]?.value ?: it.groups[2]?.value }
            if (isPixelStyle(style) || spacerName.containsMatchIn(src)) "" else text
        }
}
