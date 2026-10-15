package io.github.impca201.rssreader.data

import io.github.impca201.rssreader.feed.ParsedFeed

/** A subscribed feed. [url] is the feed's own address and identifies it. */
data class Feed(
    val url: String,
    val title: String,
    val siteUrl: String? = null,
)

/**
 * One article of a [Feed]. [id] is unique within its feed (the feed's guid, or else the link);
 * [key] is unique across all feeds.
 */
data class Article(
    val feedUrl: String,
    val id: String,
    val title: String,
    val link: String?,
    val author: String?,
    val html: String,
    val publishedAt: Long?,
    val fetchedAt: Long,
    val read: Boolean = false,
    /** The article's picture, if the feed has one. */
    val imageUrl: String? = null,
) {
    val key: String get() = keyOf(feedUrl, id)

    /** When the article appeared: its publication date, or when it was first fetched. */
    val sortTime: Long get() = publishedAt ?: fetchedAt

    companion object {
        fun keyOf(
            feedUrl: String,
            id: String,
        ): String = "$feedUrl $id"
    }
}

/** Which articles the article list shows: every feed, or one. */
sealed interface FeedSelection {
    data object All : FeedSelection

    data class Single(val feedUrl: String) : FeedSelection
}

/**
 * Everything the app knows about the user's feeds: the subscriptions and their articles, with
 * read state. Immutable; every change returns a new value.
 */
data class Library(
    val feeds: List<Feed> = emptyList(),
    val articles: List<Article> = emptyList(),
) {
    fun feed(url: String): Feed? = feeds.firstOrNull { it.url == url }

    fun article(key: String): Article? = articles.firstOrNull { it.key == key }

    /** Adds [feed] at the end, unless a feed with the same address is already there. */
    fun addFeed(feed: Feed): Library = if (feed(feed.url) != null) this else copy(feeds = feeds + feed)

    /** Removes a feed together with its articles. */
    fun removeFeed(url: String): Library =
        copy(
            feeds = feeds.filterNot { it.url == url },
            articles = articles.filterNot { it.feedUrl == url },
        )

    /**
     * Applies a freshly downloaded copy of a feed: new articles are added as unread, known ones
     * get the new title and text but keep their read state. Articles that left the feed are
     * kept, up to [MAX_ARTICLES_PER_FEED] per feed (the newest ones).
     */
    fun merge(
        feedUrl: String,
        parsed: ParsedFeed,
        now: Long,
    ): Library {
        val feed = feed(feedUrl) ?: return this
        val existing = articles.filter { it.feedUrl == feedUrl }.associateBy { it.id }
        val seen = HashSet<String>()
        val incoming =
            parsed.items.mapNotNull { item ->
                if (!seen.add(item.id)) return@mapNotNull null
                val old = existing[item.id]
                Article(
                    feedUrl = feedUrl,
                    id = item.id,
                    title = item.title,
                    link = item.link,
                    author = item.author,
                    html = item.html.take(MAX_HTML_LENGTH),
                    publishedAt = item.publishedAt ?: old?.publishedAt,
                    fetchedAt = old?.fetchedAt ?: now,
                    read = old?.read ?: false,
                    imageUrl = item.imageUrl ?: old?.imageUrl,
                )
            }
        val kept = existing.values.filter { it.id !in seen }
        val feedArticles =
            (incoming + kept)
                .sortedByDescending { it.sortTime }
                .take(MAX_ARTICLES_PER_FEED)
        val updatedFeed =
            feed.copy(
                title = parsed.title.ifBlank { feed.title },
                siteUrl = parsed.siteUrl ?: feed.siteUrl,
            )
        return copy(
            feeds = feeds.map { if (it.url == feedUrl) updatedFeed else it },
            articles = articles.filterNot { it.feedUrl == feedUrl } + feedArticles,
        )
    }

    fun setRead(
        key: String,
        read: Boolean,
    ): Library =
        if (article(key)?.read == read) {
            this
        } else {
            copy(articles = articles.map { if (it.key == key) it.copy(read = read) else it })
        }

    /** Marks every article of the selection as read. */
    fun markAllRead(selection: FeedSelection): Library =
        copy(articles = articles.map { if (selection.includes(it) && !it.read) it.copy(read = true) else it })

    /**
     * The articles to list, newest first. With [unreadOnly], read articles are left out, except
     * the ones in [keep] (articles read while the list was open, so they don't jump away).
     */
    fun articlesFor(
        selection: FeedSelection,
        unreadOnly: Boolean,
        keep: Set<String> = emptySet(),
    ): List<Article> =
        articles
            .filter { selection.includes(it) && (!unreadOnly || !it.read || it.key in keep) }
            .sortedByDescending { it.sortTime }

    fun unreadCount(selection: FeedSelection): Int = articles.count { selection.includes(it) && !it.read }

    private fun FeedSelection.includes(article: Article): Boolean =
        when (this) {
            FeedSelection.All -> feed(article.feedUrl) != null
            is FeedSelection.Single -> article.feedUrl == feedUrl
        }

    companion object {
        /** Bounds storage: feeds rarely list more than 50 articles at a time. */
        const val MAX_ARTICLES_PER_FEED = 200

        /** Bounds storage for feeds that put entire books in one article. */
        const val MAX_HTML_LENGTH = 200_000
    }
}
