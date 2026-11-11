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

    /**
     * Whether articles arrived above the ones on screen (#394): a refresh on coming back to the
     * app, or another device. The list keeps its place, so they would be out of sight above it,
     * and Mark all as read would mark them without them ever being seen; the list goes back to
     * the top instead. [before] and [after] are the list's article keys, [visible] the keys on
     * screen from the top (day headers and the like are skipped). Nothing before a first list.
     */
    fun arrivedAbove(
        before: List<String>,
        after: List<String>,
        visible: List<String>,
    ): Boolean {
        if (before.isEmpty()) return false
        val known = before.toHashSet()
        val anchor = visible.firstOrNull { it in known } ?: return false
        val at = after.indexOf(anchor)
        return at > 0 && after.subList(0, at).any { it !in known }
    }
}
