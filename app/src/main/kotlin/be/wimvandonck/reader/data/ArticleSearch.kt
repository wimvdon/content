package be.wimvandonck.reader.data

import be.wimvandonck.reader.feed.Html
import java.text.Normalizer

/**
 * Searches the stored articles of every feed: their title, feed name, author and text. Every word
 * of the query must appear, ignoring case and accents ("cafe" finds "Café"). Articles with all the
 * words in their title come first, then the rest, newest first within each.
 *
 * Built once per library (it turns every article's HTML into searchable text), then searched as
 * often as the query changes.
 */
class ArticleSearch(
    library: Library,
) {
    private class Entry(
        val article: Article,
        val title: String,
        val text: String,
    )

    private val feedsByUrl = library.feeds.associateBy { it.url }

    private val entries: List<Entry> =
        library.articles.mapNotNull { article ->
            val feed = feedsByUrl[article.feedUrl] ?: return@mapNotNull null
            val title = normalize(article.title)
            Entry(article, title, normalize("${article.title} ${feed.title} ${article.author.orEmpty()} ${Html.toPlainText(article.html)}"))
        }

    /** The articles that match [query], at most [limit]; none for an empty query. */
    fun search(
        query: String,
        limit: Int = MAX_RESULTS,
    ): List<Article> {
        val words = words(query)
        if (words.isEmpty()) return emptyList()
        return entries
            .filter { entry -> words.all { it in entry.text } }
            .sortedWith(compareByDescending<Entry> { entry -> words.all { it in entry.title } }.thenByDescending { it.article.sortTime })
            .take(limit)
            .map { it.article }
    }

    companion object {
        const val MAX_RESULTS = 200

        private val marks = Regex("""\p{Mn}+""")

        /** Lower case without accents, so "Café" and "cafe" match. */
        fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD).replace(marks, "").lowercase()

        /** The query's words, normalized. */
        fun words(query: String): List<String> = normalize(query).split(Regex("""\s+""")).filter { it.isNotEmpty() }
    }
}
