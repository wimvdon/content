package be.wimvandonck.content.feed

/** A downloaded document. [url] is the address after redirects. */
class Fetched(
    val url: String,
    val bytes: ByteArray,
)

/** Why a download failed. */
enum class FetchError { Offline, NotFound, ServerError, TooLarge }

class FetchException(
    val error: FetchError,
    cause: Throwable? = null,
) : Exception(error.name, cause)

/** What came out of looking for a feed behind an address the user typed. */
sealed interface FindResult {
    data class Found(
        val url: String,
        val feed: ParsedFeed,
    ) : FindResult

    data object InvalidAddress : FindResult

    data object NoFeed : FindResult

    data class Failed(val error: FetchError) : FindResult
}

/**
 * Finds the feed behind a web address: the address itself when it is a feed, else a feed the
 * page links to, else a feed at a common path such as /feed.
 */
class FeedFinder(
    private val fetch: suspend (String) -> Fetched,
) {
    suspend fun find(input: String): FindResult {
        val typed = FeedDiscovery.normalizeUrl(input) ?: return FindResult.InvalidAddress
        // Any Substack link (a note, post or profile) stands for its publication, which has the feed.
        val url = FeedDiscovery.substackPublication(typed) ?: typed
        val page =
            try {
                fetch(url)
            } catch (e: FetchException) {
                return FindResult.Failed(e.error)
            }
        parseOrNull(page)?.let { return it }

        val html = String(page.bytes, Charsets.UTF_8)
        val linked = FeedDiscovery.findFeedLinks(html, page.url)
        val candidates = (linked + FeedDiscovery.commonFeedUrls(page.url)).distinct().filter { it != page.url }
        for (candidate in candidates.take(MAX_CANDIDATES)) {
            val fetched =
                try {
                    fetch(candidate)
                } catch (_: FetchException) {
                    continue
                }
            parseOrNull(fetched)?.let { return it }
        }
        return FindResult.NoFeed
    }

    private fun parseOrNull(fetched: Fetched): FindResult.Found? =
        try {
            FindResult.Found(fetched.url, FeedParser.parse(fetched.bytes, fetched.url))
        } catch (_: NotAFeedException) {
            null
        }

    private companion object {
        const val MAX_CANDIDATES = 6
    }
}
