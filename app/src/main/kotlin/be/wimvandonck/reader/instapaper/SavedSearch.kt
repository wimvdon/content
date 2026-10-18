package be.wimvandonck.reader.instapaper

import be.wimvandonck.reader.data.ArticleSearch

/** The saved articles that Search looks through, and whether some lists could not be read in full. */
data class SavedArticles(
    val bookmarks: List<InstapaperBookmark>,
    /** Instapaper stopped answering part way (offline, rate limited, …): the rest is missing. */
    val incomplete: Boolean = false,
)

/**
 * Search through saved Instapaper articles. Instapaper's API has no search, so the saved articles are
 * read first (Unread, each folder, then Archive; the newest [PER_LIST] of each) and searched on the
 * phone: title, website, address, description and tags. Every word of the query must appear, ignoring
 * case and accents, like [ArticleSearch]; matches in the title come first, then newest saved first.
 * Plain Kotlin, unit tested.
 */
object SavedSearch {
    /** At most this many articles per list, so a long Archive doesn't mean dozens of calls. */
    const val PER_LIST = 100

    /** The lists to read, in order: what is still to be read comes before the Archive. */
    fun views(folders: List<InstapaperFolder>): List<InstapaperView> =
        listOf(InstapaperView.Home) + folders.map { InstapaperView.InFolder(it.id) } + InstapaperView.Archive

    /**
     * Reads every list of [views] a page at a time with [list] (offset, limit), at most [perList]
     * articles each. Stops at the first problem and keeps what it has; a signed-out token is passed on.
     */
    suspend fun load(
        views: List<InstapaperView>,
        pageSize: Int = InstapaperClient.PAGE_SIZE,
        perList: Int = PER_LIST,
        list: suspend (InstapaperView, Int, Int) -> InstapaperPage,
    ): SavedArticles {
        val found = LinkedHashMap<Long, InstapaperBookmark>()
        for (view in views) {
            var offset = 0
            while (offset < perList) {
                val page =
                    try {
                        list(view, offset, minOf(pageSize, perList - offset))
                    } catch (e: InstapaperException) {
                        if (e.problem == InstapaperProblem.SignedOut) throw e
                        return SavedArticles(found.values.toList(), incomplete = true)
                    }
                page.bookmarks.forEach { found.putIfAbsent(it.id, it) }
                offset += page.bookmarks.size
                if (page.bookmarks.isEmpty() || offset >= page.total) break
            }
        }
        return SavedArticles(found.values.toList())
    }

    /** The saved articles that match [query], at most [limit]; none for an empty query. */
    fun search(
        bookmarks: List<InstapaperBookmark>,
        query: String,
        limit: Int = ArticleSearch.MAX_RESULTS,
    ): List<InstapaperBookmark> {
        val words = ArticleSearch.words(query)
        if (words.isEmpty()) return emptyList()
        return bookmarks
            .map { b -> Triple(b, ArticleSearch.normalize(b.title), ArticleSearch.normalize(text(b))) }
            .filter { (_, _, text) -> words.all { it in text } }
            .sortedWith(
                compareByDescending<Triple<InstapaperBookmark, String, String>> { (_, title, _) -> words.all { it in title } }
                    .thenByDescending { it.first.time },
            ).take(limit)
            .map { it.first }
    }

    private fun text(b: InstapaperBookmark): String =
        listOf(b.title, b.host, b.url.orEmpty(), b.description, b.tags.joinToString(" ") { it.name }).joinToString(" ")
}
