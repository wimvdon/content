package be.wimvandonck.reader.data

import be.wimvandonck.reader.instapaper.InstapaperBookmark

/** Which kind of results Search shows: the chips above the results. */
enum class SearchFilter { All, Feeds, Saved }

/** One search result: a feed article or a saved article, with how well it matches. */
sealed interface SearchHit {
    val relevance: Int

    /** When it appeared or was saved, in milliseconds since 1970. */
    val time: Long

    data class FromFeed(
        val article: Article,
        override val relevance: Int,
    ) : SearchHit {
        override val time: Long get() = article.sortTime
    }

    data class FromSaved(
        val bookmark: InstapaperBookmark,
        override val relevance: Int,
    ) : SearchHit {
        override val time: Long get() = bookmark.time * 1000
    }
}

/**
 * Search's results in one list: the feed articles ([ArticleSearch]) and the saved articles
 * ([be.wimvandonck.reader.instapaper.SavedSearch]) that match, ranked together by how well their
 * title matches, then newest first. Plain Kotlin, unit tested; the web app does the same in
 * web/src/lib/search.ts.
 */
object MixedSearch {
    /**
     * How well a title matches: the whole query in the title counts most, then each word of the
     * query that is in the title. Matches only in the text (all words are somewhere) count 0.
     */
    fun relevance(
        query: String,
        title: String,
    ): Int {
        val words = ArticleSearch.words(query)
        if (words.isEmpty()) return 0
        val normalized = ArticleSearch.normalize(title)
        val phrase = if (words.size > 1 && words.joinToString(" ") in normalized) PHRASE else 0
        return phrase + words.count { it in normalized } * WORD
    }

    /** The matches of both kinds, filtered by [filter], best first, at most [limit]. */
    fun mix(
        query: String,
        feeds: List<Article>,
        saved: List<InstapaperBookmark>,
        filter: SearchFilter = SearchFilter.All,
        limit: Int = ArticleSearch.MAX_RESULTS,
    ): List<SearchHit> {
        val fromFeeds = if (filter == SearchFilter.Saved) emptyList() else feeds.map { SearchHit.FromFeed(it, relevance(query, it.title)) }
        val fromSaved = if (filter == SearchFilter.Feeds) emptyList() else saved.map { SearchHit.FromSaved(it, relevance(query, it.title)) }
        return (fromFeeds + fromSaved)
            .sortedWith(compareByDescending<SearchHit> { it.relevance }.thenByDescending { it.time })
            .take(limit)
    }

    private const val PHRASE = 100
    private const val WORD = 10
}
