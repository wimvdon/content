package to.wim.content.data

/** "Mark as read on scroll": which articles count as read once the list has scrolled. */
object ScrollRead {
    /**
     * The unread articles above the first one still on screen, given as an index into
     * [articles], that were on screen before ([seen], #157). Articles only count once they have
     * scrolled off the top, so the one at the top of the screen is not marked yet, and new ones
     * that arrive above it (a refresh, another device) are never marked unseen.
     */
    fun scrolledPast(
        articles: List<Article>,
        firstVisibleArticle: Int,
        seen: Set<String>,
    ): Set<String> =
        articles
            .take(firstVisibleArticle.coerceAtLeast(0))
            .filter { !it.read && it.key in seen }
            .mapTo(LinkedHashSet()) { it.key }
}
