package be.wimvandonck.reader.data

/** Which kind of results Search shows: the chips above the results. */
enum class SearchFilter { All, Feeds, Saved }

/** One search result: a feed article or an item of the reading list, with how well it matches. */
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

    data class FromItem(
        val item: Item,
        override val relevance: Int,
    ) : SearchHit {
        override val time: Long get() = item.readingList?.addedAt ?: item.bookmark?.addedAt ?: item.createdAt
    }
}

/**
 * Search's results in one list: the feed articles ([ArticleSearch]) and the items ([ItemSearch])
 * that match, ranked together by how well their title matches, then newest first. Plain Kotlin,
 * unit tested; the web app does the same in web/src/lib/search.ts.
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
        items: List<Item>,
        filter: SearchFilter = SearchFilter.All,
        limit: Int = ArticleSearch.MAX_RESULTS,
    ): List<SearchHit> {
        val fromFeeds = if (filter == SearchFilter.All || filter == SearchFilter.Feeds) feeds.map { SearchHit.FromFeed(it, relevance(query, it.title)) } else emptyList()
        val fromItems =
            items
                .filter {
                    when (filter) {
                        SearchFilter.All -> true
                        SearchFilter.Feeds -> false
                        SearchFilter.Saved -> it.readingList != null
                    }
                }.map { SearchHit.FromItem(it, relevance(query, it.title)) }
        return (fromFeeds + fromItems)
            .sortedWith(compareByDescending<SearchHit> { it.relevance }.thenByDescending { it.time })
            .take(limit)
    }

    private const val PHRASE = 100
    private const val WORD = 10
}

/**
 * Searches Saved (archived items included, #107, #119): title, website, address, description,
 * folder, tags, note and highlights. Every word must appear (case and accents ignored); title matches first,
 * then newest. Plain Kotlin, unit tested.
 */
object ItemSearch {
    fun search(
        items: List<Item>,
        query: String,
        limit: Int = ArticleSearch.MAX_RESULTS,
    ): List<Item> {
        val words = ArticleSearch.words(query)
        if (words.isEmpty()) return emptyList()
        return items
            .asSequence()
            .map { item -> Triple(item, ArticleSearch.normalize(item.title), ArticleSearch.normalize(text(item))) }
            .filter { (_, _, text) -> words.all { it in text } }
            .sortedWith(
                compareByDescending<Triple<Item, String, String>> { (_, title, _) -> words.all { it in title } }
                    .thenByDescending { (item, _, _) -> item.readingList?.addedAt ?: item.bookmark?.addedAt ?: item.createdAt },
            ).take(limit)
            .map { it.first }
            .toList()
    }

    private fun text(i: Item): String =
        (listOf(i.title, i.host, i.url, i.description, i.note, i.folder.orEmpty()) + i.tags + i.highlights.flatMap { listOf(it.quote, it.note) }).joinToString(" ")
}

/**
 * Search's filters (#171): which kind (All, Feeds, Saved), then for feeds a folder or one feed, and
 * for Saved a folder, a tag and a type. Only the chosen kind's filters apply; changing the kind
 * clears them.
 */
data class SearchFilters(
    val scope: SearchFilter = SearchFilter.All,
    /** A folder of feeds (its id). */
    val feedFolderId: String? = null,
    /** One feed (its address). */
    val feedUrl: String? = null,
    /** A folder of Saved (its name, case ignored). */
    val savedFolder: String? = null,
    val tag: String? = null,
    val type: SavedType? = null,
) {
    /** Whether any filter beyond the kind is chosen. */
    val narrowed: Boolean get() = feedFolderId != null || feedUrl != null || savedFolder != null || tag != null || type != null

    /** The kind [scope], without the other kind's filters. */
    fun withScope(scope: SearchFilter): SearchFilters = SearchFilters(scope)
}

/**
 * What the Search tab lists (#171). With words typed, the matches ([feedMatches] and [itemMatches],
 * found by ArticleSearch and ItemSearch) as Search ranks them; without, everything, newest first:
 * all feed articles, all of Saved (archived ones too), or both. Either way only what the filters
 * let through. Plain Kotlin, unit tested; the web app does the same in web/src/lib/search.ts.
 */
object SearchResults {
    /**
     * Whether Search shows its results as notes (cards), like Saved's folders, rather than as rows
     * like the feeds: Feeds is rows, All and Saved are notes.
     */
    fun showsNotes(scope: SearchFilter): Boolean = scope != SearchFilter.Feeds

    /** How many articles the list shows without words typed. */
    const val MAX_BROWSED = 500

    fun of(
        query: String,
        library: Library,
        feedMatches: List<Article>,
        itemMatches: List<Item>,
        filters: SearchFilters,
    ): List<SearchHit> {
        val browsing = ArticleSearch.words(query).isEmpty()
        val articles = if (browsing) library.articles else feedMatches
        val items = if (browsing) library.items.filter { it.readingList != null } else itemMatches
        val folderOfFeed = library.feeds.associate { it.url to it.folderId }
        val feeds =
            when (filters.scope) {
                SearchFilter.Saved -> emptyList()
                else ->
                    articles.filter { a ->
                        (filters.feedUrl == null || a.feedUrl == filters.feedUrl) &&
                            (filters.feedFolderId == null || folderOfFeed[a.feedUrl] == filters.feedFolderId)
                    }
            }
        val saved =
            when (filters.scope) {
                SearchFilter.Feeds -> emptyList()
                else ->
                    items.filter { i ->
                        i.readingList != null &&
                            (filters.savedFolder == null || Saved.folderOf(i)?.equals(filters.savedFolder, ignoreCase = true) == true) &&
                            (filters.tag == null || i.tags.any { it.equals(filters.tag, ignoreCase = true) }) &&
                            (filters.type == null || Saved.typeOf(i.url) == filters.type)
                    }
            }
        // Feed filters are about feeds: with one chosen under All, Saved's matches stay out too.
        val onlyFeeds = filters.scope == SearchFilter.All && (filters.feedUrl != null || filters.feedFolderId != null)
        return MixedSearch.mix(
            if (browsing) "" else query,
            feeds,
            if (onlyFeeds) emptyList() else saved,
            filters.scope,
            if (browsing) MAX_BROWSED else ArticleSearch.MAX_RESULTS,
        )
    }
}
