package to.wim.content.data

/**
 * Hide articles with certain words (#277): feed articles that match one of the user's entries are
 * left out of the feed lists, their counts and their tag chips.
 *
 * The entries are added one by one in Settings › Article list, as chips (2026-11-02), and stored one
 * per line (older settings may still use commas or semicolons, which still separate). An entry of
 * several words matches when **every** word is there, in any order, in the article's title or the
 * name of its feed (2026-11-02): "WIRED coupon code" hides WIRED's articles with "coupon code"
 * in their title, and nothing else of WIRED; "wired" alone hides all of WIRED. A word
 * matches as a whole word, or its plural or possessive, ignoring case: "AI" hides "New AI rules" and
 * "ai: a recap", not "said" or "Thailand"; "code" also finds "Coupon Codes". The article's text is not searched through.
 */
class ArticleFilter private constructor(
    /** The entries, in lower case, without duplicates. */
    val words: List<String>,
) {
    val isEmpty: Boolean get() = words.isEmpty()

    /** Each entry's words. */
    private val terms: List<List<String>> = words.map { it.split(' ').filter(String::isNotEmpty) }

    /** Whether [article] is hidden; [source] is the name of its feed (the publication). */
    fun hides(
        article: Article,
        source: String? = null,
    ): Boolean = hidesTitle(article.title, source)

    /**
     * What the setting's dialog shows while typing (2026-11-02): how many of [articles] the words
     * hide, and the newest [limit] of them, so you see what would go before saving.
     */
    fun preview(
        articles: List<Article>,
        limit: Int = PREVIEW_LIMIT,
        /** The name of an article's feed, which the words are looked for in too. */
        sourceOf: (Article) -> String? = { null },
    ): Preview {
        if (words.isEmpty()) return Preview(0, emptyList())
        val hidden = articles.filter { hides(it, sourceOf(it)) }
        return Preview(hidden.size, hidden.sortedByDescending { it.sortTime }.take(limit))
    }

    /** [count] articles hidden; [newest] are the most recent of them. */
    data class Preview(
        val count: Int,
        val newest: List<Article>,
    )

    /** Whether a title (of a feed named [source]) matches an entry: every one of its words is in the one or the other. */
    fun hidesTitle(
        title: String,
        source: String? = null,
    ): Boolean {
        if (terms.isEmpty()) return false
        val text = title.lowercase()
        val from = source?.lowercase().orEmpty()
        if (text.isEmpty() && from.isEmpty()) return false
        return terms.any { entry -> entry.all { word -> containsWord(text, word) || containsWord(from, word) } }
    }

    companion object {
        /** How many hidden articles the preview lists. */
        const val PREVIEW_LIMIT = 5

        /** The filter for the text typed in Settings: words separated by commas or new lines. */
        fun parse(text: String): ArticleFilter = ArticleFilter(words(text))

        /** The words of [text], trimmed, without empty ones or duplicates (case ignored), in lower case. */
        fun words(text: String): List<String> =
            text
                .split(',', '\n', '\r', ';')
                .map { it.trim().replace(Regex("\\s+"), " ").lowercase() }
                .filter { it.isNotEmpty() }
                .distinct()

        /**
         * The entries after adding [typed] (the dialog's field, 2026-11-02): one word or group of
         * words, or several separated by commas, each kept once.
         */
        fun withEntry(
            entries: List<String>,
            typed: String,
        ): List<String> = (entries + words(typed)).distinct()

        /** The entries without [entry] (its chip's X). */
        fun withoutEntry(
            entries: List<String>,
            entry: String,
        ): List<String> = entries - entry

        /** How the entries are stored and synced: one per line, which [parse] and [words] read back. */
        fun text(entries: List<String>): String = entries.joinToString("\n")

        /** What may follow a word and still count as it: nothing, a plural or a possessive. */
        private val ENDINGS = listOf("", "s", "es", "'s", "’s")

        /** [text] (in lower case) has [word] (or its plural or possessive) with no letter or digit right before or after it. */
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
                // The word itself, or its plural or possessive: "code" also finds "Coupon Codes" (2026-11-02).
                val endOk =
                    ENDINGS.any { ending ->
                        text.startsWith(ending, end) && (end + ending.length == text.length || !text[end + ending.length].isLetterOrDigit())
                    }
                if (startOk && endOk) return true
                from = at + 1
            }
        }
    }
}

/** This library without the articles [filter] hides (their feed's name counts too): what the feed lists show (#277). */
fun Library.withoutHidden(filter: ArticleFilter): Library {
    if (filter.isEmpty) return this
    val names = feeds.associate { it.url to it.title }
    return copy(articles = articles.filterNot { filter.hides(it, names[it.feedUrl]) })
}
