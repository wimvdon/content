package io.github.impca201.rssreader.data

/** "Mark as read on scroll": which articles count as read once the list has scrolled. */
object ScrollRead {
    /**
     * The unread articles above the first one still on screen, given as an index into
     * [articles]. Articles only count once they have scrolled off the top, so the one at the top
     * of the screen is not marked yet.
     */
    fun scrolledPast(
        articles: List<Article>,
        firstVisibleArticle: Int,
    ): Set<String> =
        articles
            .take(firstVisibleArticle.coerceAtLeast(0))
            .filterNot { it.read }
            .mapTo(LinkedHashSet()) { it.key }
}
