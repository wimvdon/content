package to.wim.content.data

/**
 * Hide articles with certain words (#277): feed articles whose title contains one of the user's
 * words are left out of the feed lists, their counts and their topic chips.
 *
 * The words are typed in Settings › List, separated by commas or new lines. A word matches as a
 * whole word or phrase, ignoring case: "AI" hides "New AI rules" and "ai: a recap", not "said"
 * or "Thailand"; "apple watch" hides "The Apple Watch review". Only the title counts: it is what
 * the list shows, and articles are not searched through.
 */
class ArticleFilter private constructor(
    /** The words and phrases, in lower case, without duplicates. */
    val words: List<String>,
) {
    val isEmpty: Boolean get() = words.isEmpty()

    /** Whether [article] is hidden. */
    fun hides(article: Article): Boolean = hidesTitle(article.title)

    /** Whether a title with one of the words is hidden. */
    fun hidesTitle(title: String): Boolean {
        if (words.isEmpty() || title.isEmpty()) return false
        val text = title.lowercase()
        return words.any { containsWord(text, it) }
    }

    companion object {
        /** The filter for the text typed in Settings: words separated by commas or new lines. */
        fun parse(text: String): ArticleFilter = ArticleFilter(words(text))

        /** The words of [text], trimmed, without empty ones or duplicates (case ignored), in lower case. */
        fun words(text: String): List<String> =
            text
                .split(',', '\n', '\r', ';')
                .map { it.trim().replace(Regex("\\s+"), " ").lowercase() }
                .filter { it.isNotEmpty() }
                .distinct()

        /** [text] (in lower case) has [word] with no letter or digit right before or after it. */
        private fun containsWord(
            text: String,
            word: String,
        ): Boolean {
            var from = 0
            while (true) {
                val at = text.indexOf(word, from)
                if (at < 0) return false
                val end = at + word.length
                val startOk = at == 0 || !text[at - 1].isLetterOrDigit()
                val endOk = end == text.length || !text[end].isLetterOrDigit()
                if (startOk && endOk) return true
                from = at + 1
            }
        }
    }
}

/** This library without the articles [filter] hides: what the feed lists show (#277). */
fun Library.withoutHidden(filter: ArticleFilter): Library = if (filter.isEmpty) this else copy(articles = articles.filterNot(filter::hides))
