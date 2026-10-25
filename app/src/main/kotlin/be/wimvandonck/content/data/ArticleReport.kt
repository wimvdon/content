package be.wimvandonck.content.data

import be.wimvandonck.content.feed.Html
import java.net.URLEncoder

/**
 * Reporting an article the reader cleaned up badly (#181): a pre-filled "new issue" page in this
 * repository, opened in the browser, where the user sends it with their GitHub account. It holds
 * the article's link, what is wrong, the comment, the app and its version, and the text Content
 * shows (#251), so it can be compared with the page itself. A browser takes only so long an address:
 * a long text is cut short there ([Report.cut]) and the app puts all of it on the clipboard to paste.
 * Nothing is sent until the user submits it there. Plain Kotlin, unit tested; the web app's
 * `articleReport.ts` builds the same page.
 */
object ArticleReport {
    /** What is wrong, as the issue says it (in English, for the repository). */
    enum class Problem(val label: String) {
        TextMissing("Text missing"),
        JunkLeft("Junk left in"),
        PicturesWrong("Pictures wrong"),
        Other("Other"),
    }

    const val NEW_ISSUE = "https://github.com/wimvdon/content/issues/new"

    /** The label the issue gets, so reports can be found together. */
    const val LABEL = "parser"

    /** The longest comment kept. */
    const val MAX_COMMENT = 1500

    /** The longest address made: GitHub and browsers refuse much longer ones. */
    const val MAX_URL = 7500

    /** Said where the text is cut short; the whole text is on the clipboard then. */
    const val CUT_NOTE = "… (cut short here: the whole text is on the clipboard, paste it below)"

    /** The "new issue" page, and whether the article's text had to be cut short to fit. */
    data class Report(
        val url: String,
        val cut: Boolean,
    )

    /** The pre-filled "new issue" page for [link], with [text] (the article as Content shows it). */
    fun report(
        link: String,
        problem: Problem,
        comment: String,
        app: String,
        version: String,
        text: String = "",
    ): Report {
        val host = runCatching { java.net.URI(link).host }.getOrNull()?.removePrefix("www.") ?: link
        val title = "Article cleanup: ${problem.label.lowercase()} on $host"
        val head =
            buildString {
                appendLine("**Article:** $link")
                appendLine("**Problem:** ${problem.label}")
                appendLine("**App:** $app $version")
                val note = comment.trim().take(MAX_COMMENT)
                if (note.isNotEmpty()) {
                    appendLine()
                    appendLine(note)
                }
            }.trimEnd()
        fun url(body: String) = "$NEW_ISSUE?labels=$LABEL&title=${encode(title)}&body=${encode(body)}"
        fun withText(shown: String) = "$head\n\n**Text in Content:**\n\n````\n$shown\n````"
        val whole = text.trim()
        if (whole.isEmpty()) return Report(url(head), cut = false)
        val full = url(withText(whole))
        if (full.length <= MAX_URL) return Report(full, cut = false)
        // As much of the text as fits, cut at a word.
        var low = 0
        var high = whole.length
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (url(withText(whole.take(mid) + CUT_NOTE)).length <= MAX_URL) low = mid else high = mid - 1
        }
        val start = whole.take(low).let { s -> s.lastIndexOf(' ').takeIf { it > s.length / 2 }?.let { s.take(it) } ?: s }
        return Report(url(withText(start.trimEnd() + CUT_NOTE)), cut = true)
    }

    /** The article's text from the HTML the reader shows: one paragraph per block, blank lines between. */
    fun textOf(html: String): String =
        html
            .split(blockEnds)
            .map(Html::toPlainText)
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")

    private val blockEnds = Regex("""(?i)</(p|h[1-6]|li|blockquote|pre|figcaption|dd|dt)>|<br\s*/?>|<hr\s*/?>""")

    private fun encode(text: String) = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
}
