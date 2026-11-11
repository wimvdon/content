package to.wim.content.data

/**
 * The two kinds of feed in Feed (2026-11-11): an RSS feed (a website's feed) and a newsletter feed
 * (a newsletter address's, [NewsletterAddresses]). Settings has a large button for each, and each
 * opens Manage feeds with only that kind. Plain Kotlin, unit tested; the web app's `feedKinds.ts`
 * is the same.
 */
enum class FeedKind { Rss, Newsletter }

object FeedKinds {
    fun of(feed: Feed): FeedKind = if (NewsletterAddresses.codeOfFeed(feed.url) != null) FeedKind.Newsletter else FeedKind.Rss

    /** The feeds of one kind, in their order. */
    fun only(
        feeds: List<Feed>,
        kind: FeedKind,
    ): List<Feed> = feeds.filter { of(it) == kind }

    /**
     * A topic's feeds in their new order after one kind's feeds were dragged in Manage feeds, which
     * shows only that kind: those take the places of that kind in the topic ([topicUrls], the topic's
     * feeds as they are), so the other kind's feeds stay where they were. A feed of [kindUrls] new to
     * the topic (dragged in from another one) counts too: the order among the kind is [kindUrls],
     * and what doesn't fit in the kind's places comes at the end.
     */
    fun withOrder(
        topicUrls: List<String>,
        kindUrls: List<String>,
    ): List<String> {
        val shown = kindUrls.toSet()
        val next = kindUrls.iterator()
        val placed = topicUrls.map { if (it in shown && next.hasNext()) next.next() else it }
        return placed + next.asSequence().toList()
    }
}
