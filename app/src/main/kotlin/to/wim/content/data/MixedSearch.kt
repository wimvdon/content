package to.wim.content.data

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
 * Search's filters: one row of chips. **Feeds** (a menu: all feeds, or some of them, picked with
 * check boxes) and **Saved** pick the kind; neither shows both. Then a **topic**, one at a time,
 * which narrows both kinds: feeds with that topic and saved articles with it. Picking feeds means
 * only feeds; Saved lets go of the picked feeds. A topic keeps only the picked feeds that have it.
 */
data class SearchFilters(
    val scope: SearchFilter = SearchFilter.All,
    /** Some feeds (their addresses), with [scope] Feeds; empty: every feed. */
    val feedUrls: Set<String> = emptySet(),
    /** A topic's name (case ignored). */
    val topic: String? = null,
) {
    /** Whether anything beyond the kind is chosen. */
    val narrowed: Boolean get() = feedUrls.isNotEmpty() || topic != null

    /** The kind [scope]; the picked feeds go, the topic stays (it is about both kinds). */
    fun withScope(scope: SearchFilter): SearchFilters = copy(scope = scope, feedUrls = emptySet())

    /** The Feeds menu's check box for one feed: picks it (only feeds then) or lets go of it. */
    fun toggleFeed(url: String): SearchFilters {
        val urls = if (url in feedUrls) feedUrls - url else feedUrls + url
        return copy(scope = SearchFilter.Feeds, feedUrls = urls)
    }

    /** A topic chip: picks it, or with null lets go; picked feeds without it are let go too. */
    fun withTopic(
        topic: String?,
        library: Library,
    ): SearchFilters {
        if (topic == null) return copy(topic = null)
        val kept = feedUrls.filterTo(LinkedHashSet()) { url -> library.feed(url)?.let { SearchResults.hasTopic(it, topic, library) } == true }
        return copy(topic = topic, feedUrls = kept)
    }
}

/**
 * What the Search tab lists (#171). With words typed, the matches ([feedMatches] and [itemMatches],
 * found by ArticleSearch and ItemSearch) as Search ranks them; without, everything, newest first:
 * all feed articles, all of Saved (archived ones too), or both. Either way only what the filters
 * let through. Plain Kotlin, unit tested.
 */
object SearchResults {
    /**
     * Whether Search shows its results as notes (cards), like Saved's folders, rather than as rows
     * like the feeds: Feeds is rows, All and Saved are notes.
     */
    fun showsNotes(scope: SearchFilter): Boolean = scope != SearchFilter.Feeds

    /** How many articles the list shows without words typed. */
    const val MAX_BROWSED = 500

    /** Whether a feed has [topic]: as one of its tags, or as its folder. */
    fun hasTopic(
        feed: Feed,
        topic: String,
        library: Library,
    ): Boolean =
        feed.tags.any { it.equals(topic, ignoreCase = true) } ||
            feed.folderId?.let { library.folder(it) }?.title?.equals(topic, ignoreCase = true) == true

    /**
     * The topic chips for [scope]: the topics that some feed (unless only Saved is searched) or some
     * saved article (unless only feeds) has, in the user's order; the chosen one always stays.
     */
    fun topics(
        library: Library,
        scope: SearchFilter,
        chosen: String?,
    ): List<String> =
        library.topicNames().filter { topic ->
            topic.equals(chosen, ignoreCase = true) ||
                (scope != SearchFilter.Saved && library.feeds.any { hasTopic(it, topic, library) }) ||
                (scope != SearchFilter.Feeds && library.items.any { i -> i.readingList != null && i.tags.any { it.equals(topic, ignoreCase = true) } })
        }

    /** The feeds the Feeds menu offers: those with the chosen topic, else all of them. */
    fun menuFeeds(
        library: Library,
        topic: String?,
    ): List<Feed> = if (topic == null) library.feeds else library.feeds.filter { hasTopic(it, topic, library) }

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
        val topic = filters.topic
        val feedsByUrl = library.feeds.associateBy { it.url }
        val feeds =
            when (filters.scope) {
                SearchFilter.Saved -> emptyList()
                else ->
                    articles.filter { a ->
                        (filters.feedUrls.isEmpty() || a.feedUrl in filters.feedUrls) &&
                            (topic == null || feedsByUrl[a.feedUrl]?.let { hasTopic(it, topic, library) } == true)
                    }
            }
        val saved =
            when (filters.scope) {
                SearchFilter.Feeds -> emptyList()
                else ->
                    items.filter { i ->
                        i.readingList != null && (topic == null || i.tags.any { it.equals(topic, ignoreCase = true) })
                    }
            }
        return MixedSearch.mix(
            if (browsing) "" else query,
            feeds,
            saved,
            filters.scope,
            if (browsing) MAX_BROWSED else ArticleSearch.MAX_RESULTS,
        )
    }
}
