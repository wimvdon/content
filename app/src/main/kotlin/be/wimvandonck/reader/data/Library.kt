package be.wimvandonck.reader.data

import be.wimvandonck.reader.feed.Opml
import be.wimvandonck.reader.feed.ParsedFeed

/** A subscribed feed. [url] is the feed's own address and identifies it. */
data class Feed(
    val url: String,
    val title: String,
    val siteUrl: String? = null,
    /** The [Folder] it is in, or null. */
    val folderId: String? = null,
    /** The website's icon (favicon), once found. */
    val iconUrl: String? = null,
    /** True when the user gave the feed its own name, which refreshes then keep. */
    val renamed: Boolean = false,
)

/** A group of feeds with its own title and icon; it gets a tab in the bottom bar. */
data class Folder(
    val id: String,
    val title: String,
    /** A Material Symbols name from [IconCatalog], such as "sports-soccer". */
    val icon: String = IconCatalog.DEFAULT,
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

/** Which articles the article list shows: every feed, one folder, or one feed. */
sealed interface FeedSelection {
    data object All : FeedSelection

    data class InFolder(val folderId: String) : FeedSelection

    data class Single(val feedUrl: String) : FeedSelection
}

/**
 * Everything the app knows about the user's feeds: the subscriptions and their articles, with
 * read state. Immutable; every change returns a new value.
 */
data class Library(
    val feeds: List<Feed> = emptyList(),
    val articles: List<Article> = emptyList(),
    val folders: List<Folder> = emptyList(),
) {
    fun feed(url: String): Feed? = feeds.firstOrNull { it.url == url }

    fun folder(id: String): Folder? = folders.firstOrNull { it.id == id }

    /** Adds a folder at the end (ignored when the id is taken or the title is blank). */
    fun addFolder(folder: Folder): Library =
        if (folder(folder.id) != null || folder.title.isBlank()) this else copy(folders = folders + folder.copy(title = folder.title.trim()))

    fun updateFolder(
        id: String,
        title: String,
        icon: String,
    ): Library =
        if (title.isBlank()) this else copy(folders = folders.map { if (it.id == id) it.copy(title = title.trim(), icon = icon) else it })

    /** Removes a folder; its feeds stay, without a folder. */
    fun removeFolder(id: String): Library =
        copy(
            folders = folders.filterNot { it.id == id },
            feeds = feeds.map { if (it.folderId == id) it.copy(folderId = null) else it },
        )

    /**
     * Gives a feed the user's own name. A blank name goes back to the feed's own title, which
     * the next refresh brings back.
     */
    fun renameFeed(
        url: String,
        title: String,
    ): Library =
        copy(
            feeds =
                feeds.map {
                    when {
                        it.url != url -> it
                        title.isBlank() -> it.copy(renamed = false)
                        else -> it.copy(title = title.trim(), renamed = true)
                    }
                },
        )

    /** Remembers the icon found for a feed's website. */
    fun setFeedIcon(
        url: String,
        iconUrl: String,
    ): Library = copy(feeds = feeds.map { if (it.url == url) it.copy(iconUrl = iconUrl) else it })

    /** Puts a feed in a folder, or takes it out with null. Unknown folders count as none. */
    fun moveFeed(
        url: String,
        folderId: String?,
    ): Library {
        val target = folderId?.takeIf { folder(it) != null }
        return copy(feeds = feeds.map { if (it.url == url) it.copy(folderId = target) else it })
    }

    /**
     * Adds the feeds of an OPML file with their folders: folders are matched by title (new ones
     * get an id from [newId]); feeds already followed are left as they are. Returns the new
     * library and how many feeds were added.
     */
    fun import(
        entries: List<Opml.Entry>,
        newId: () -> String,
    ): Pair<Library, Int> {
        var library = this
        var added = 0
        for (entry in entries) {
            if (library.feed(entry.url) != null) continue
            val folderId =
                entry.folder?.let { title ->
                    library.folders.firstOrNull { it.title.equals(title, ignoreCase = true) }?.id
                        ?: newId().also { library = library.addFolder(Folder(it, title)) }
                }
            library = library.addFeed(Feed(url = entry.url, title = entry.title.ifBlank { entry.url }, folderId = folderId))
            added++
        }
        return library to added
    }

    /** The feeds as OPML entries, with their folder titles. */
    fun toOpmlEntries(): List<Opml.Entry> = feeds.map { Opml.Entry(it.url, it.title, it.folderId?.let { id -> folder(id)?.title }) }

    /** The feeds of a folder, or the feeds in no folder for null. */
    fun feedsIn(folderId: String?): List<Feed> = feeds.filter { it.folderId == folderId }

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
     * kept, up to [MAX_ARTICLES_PER_FEED] per feed (the newest ones). New articles whose key is in
     * [knownRead] (read on another device, see sync/) arrive as read.
     */
    fun merge(
        feedUrl: String,
        parsed: ParsedFeed,
        now: Long,
        knownRead: Set<String> = emptySet(),
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
                    // A date in the future (a wrong clock or time zone) would stick to the top.
                    publishedAt = (item.publishedAt ?: old?.publishedAt)?.coerceAtMost(now),
                    fetchedAt = old?.fetchedAt ?: now,
                    read = old?.read ?: (Article.keyOf(feedUrl, item.id) in knownRead),
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
                title = if (feed.renamed) feed.title else parsed.title.ifBlank { feed.title },
                siteUrl = parsed.siteUrl ?: feed.siteUrl,
            )
        return copy(
            feeds = feeds.map { if (it.url == feedUrl) updatedFeed else it },
            articles = articles.filterNot { it.feedUrl == feedUrl } + feedArticles,
        )
    }

    /**
     * Merges the downloads of a whole refresh at once, so the list changes in one step and new
     * articles take their place by date instead of appearing feed by feed.
     */
    fun mergeAll(
        downloads: Map<String, ParsedFeed>,
        now: Long,
        knownRead: Set<String> = emptySet(),
    ): Library = downloads.entries.fold(this) { library, (url, parsed) -> library.merge(url, parsed, now, knownRead) }

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

    /** The unread articles of the selection, to undo [markAllRead]. */
    fun unreadKeys(selection: FeedSelection): Set<String> =
        articles.filter { selection.includes(it) && !it.read }.mapTo(HashSet()) { it.key }

    /** Sets the read state of many articles at once. */
    fun setRead(
        keys: Set<String>,
        read: Boolean,
    ): Library = copy(articles = articles.map { if (it.key in keys && it.read != read) it.copy(read = read) else it })

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

    /** [selection] if its feed or folder still exists, else All (it was removed meanwhile). */
    fun shown(selection: FeedSelection): FeedSelection =
        when (selection) {
            FeedSelection.All -> selection
            is FeedSelection.InFolder -> if (folder(selection.folderId) != null) selection else FeedSelection.All
            is FeedSelection.Single -> if (feed(selection.feedUrl) != null) selection else FeedSelection.All
        }

    /** Unread counts for All, every folder and every feed, for the drawer. */
    fun unreadCounts(): Map<FeedSelection, Int> =
        buildMap {
            put(FeedSelection.All, unreadCount(FeedSelection.All))
            folders.forEach { put(FeedSelection.InFolder(it.id), unreadCount(FeedSelection.InFolder(it.id))) }
            feeds.forEach { put(FeedSelection.Single(it.url), unreadCount(FeedSelection.Single(it.url))) }
        }

    fun unreadCount(selection: FeedSelection): Int = articles.count { selection.includes(it) && !it.read }

    private fun FeedSelection.includes(article: Article): Boolean =
        when (this) {
            FeedSelection.All -> feed(article.feedUrl) != null
            is FeedSelection.InFolder -> feed(article.feedUrl)?.folderId == folderId
            is FeedSelection.Single -> article.feedUrl == feedUrl
        }

    companion object {
        /** Bounds storage: feeds rarely list more than 50 articles at a time. */
        const val MAX_ARTICLES_PER_FEED = 200

        /** Bounds storage for feeds that put entire books in one article. */
        const val MAX_HTML_LENGTH = 200_000
    }
}
