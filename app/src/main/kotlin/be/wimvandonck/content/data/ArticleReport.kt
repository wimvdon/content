package be.wimvandonck.content.data

import java.net.URLEncoder

/**
 * Reporting an article the reader cleaned up badly (#181): a pre-filled "new issue" page in this
 * repository, opened in the browser, where the user sends it with their GitHub account. It holds
 * the article's link, what is wrong, the comment, the app and its version; never the article's text.
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

    /** The longest comment kept, so the address stays short enough for every browser. */
    const val MAX_COMMENT = 1500

    /** The pre-filled "new issue" page for [link]. */
    fun url(
        link: String,
        problem: Problem,
        comment: String,
        app: String,
        version: String,
    ): String {
        val host = runCatching { java.net.URI(link).host }.getOrNull()?.removePrefix("www.") ?: link
        val title = "Article cleanup: ${problem.label.lowercase()} on $host"
        val body =
            buildString {
                appendLine("**Article:** $link")
                appendLine("**Problem:** ${problem.label}")
                appendLine("**App:** $app $version")
                val text = comment.trim().take(MAX_COMMENT)
                if (text.isNotEmpty()) {
                    appendLine()
                    appendLine(text)
                }
            }.trimEnd()
        return "$NEW_ISSUE?labels=$LABEL&title=${encode(title)}&body=${encode(body)}"
    }

    private fun encode(text: String) = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
}
