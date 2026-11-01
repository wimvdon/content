package to.wim.content.data

import to.wim.content.feed.Opml
import to.wim.content.feed.ParsedFeed

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
    /** Its tags (#187), which its articles inherit. A feed folder becomes its first tag: see [feedFoldersToTags]. */
    val tags: List<String> = emptyList(),
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

/** Feeds' two tabs: the articles still unread, and those already read. */
enum class FeedTab {
    Unread,
    Read,
    ;

    /** Whether [article] belongs in this tab. */
    fun has(article: Article): Boolean = article.read == (this == Read)

    companion object {
        /** The tab stored as Settings.unreadOnly (true: Unread). */
        fun of(unreadOnly: Boolean): FeedTab = if (unreadOnly) Unread else Read
    }
}

/** Which articles the article list shows: every feed, one folder, or one feed. */
sealed interface FeedSelection {
    data object All : FeedSelection

    data class InFolder(val folderId: String) : FeedSelection

    data class Single(val feedUrl: String) : FeedSelection

    /** The feeds with one tag (#187): the Feed tab's chips. */
    data class Tagged(val tag: String) : FeedSelection
}

/**
 * The chip a selection shows as chosen: its tag, or a folder's name, which is its feeds' tag (#201).
 * So a folder picked in ☰ lights up its chip, as picking the chip does.
 */
fun FeedSelection.chipTag(folders: List<Folder>): String? =
    when (this) {
        is FeedSelection.Tagged -> tag
        is FeedSelection.InFolder -> folders.firstOrNull { it.id == folderId }?.title
        else -> null
    }

/** Whether [folder]'s row in ☰ is the one chosen: picked there, or its chip picked (same feeds). */
fun FeedSelection.choosesFolder(folder: Folder): Boolean =
    when (this) {
        is FeedSelection.InFolder -> folderId == folder.id
        is FeedSelection.Tagged -> tag.equals(folder.title, ignoreCase = true)
        else -> false
    }

/**
 * The feeds' tags in the order of the folders they mirror, as Manage and ☰ list them; a tag without
 * a folder (from an older app) comes after them, alphabetically. Case is ignored.
 */
fun feedTagsInOrder(
    feeds: List<Feed>,
    folders: List<Folder>,
): List<String> {
    val position = HashMap<String, Int>()
    folders.forEachIndexed { i, f -> position.putIfAbsent(f.title.trim().lowercase(), i) }
    return feeds
        .flatMap { it.tags }
        .distinctBy { it.lowercase() }
        .sortedWith(compareBy<String> { position[it.lowercase()] ?: Int.MAX_VALUE }.thenBy { it.lowercase() })
}

/**
 * Everything the app knows about the user's reading: the subscriptions and their articles, with
 * read state, and the [items] on the reading list or in Bookmarks (LibraryItems.kt). Immutable;
 * every change returns a new value.
 */
data class Library(
    val feeds: List<Feed> = emptyList(),
    val articles: List<Article> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val items: List<Item> = emptyList(),
    /**
     * Articles read on another device that this one doesn't have (yet): article key → the article's
     * date. They arrive read, and their marks stay in sync until they do (see sync/SyncRows.kt).
     */
    val readElsewhere: Map<String, Long> = emptyMap(),
) {
    fun feed(url: String): Feed? = feeds.firstOrNull { it.url == url }

    /**
     * A feed's tag mirrors its folder (#186): feeds are grouped in folders, as in OPML, and the Feed
     * tab's chips filter on that tag. A feed in a folder has the folder's name as its only tag, a
     * feed outside folders none, so moving a feed or renaming a folder moves its chip too. The same
     * library when everything already matches. Applied when the library is read and after every
     * change, so a feed synced in from an older app gets it too.
     */
    fun feedFoldersToTags(): Library {
        val titles = folders.associate { it.id to it.title.trim() }
        fun mirror(f: Feed): List<String> = listOfNotNull(titles[f.folderId]?.takeIf { it.isNotEmpty() })
        if (feeds.all { it.tags == mirror(it) }) return this
        return copy(feeds = feeds.map { f -> mirror(f).let { t -> if (f.tags == t) f else f.copy(tags = t) } })
    }

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

    /**
     * Removes a feed together with its articles; items saved from it stay. The read ones keep their
     * mark in [readElsewhere], so other devices don't see them turn unread and adding the feed
     * again brings them back read (#157).
     */
    fun removeFeed(url: String): Library {
        val (gone, stay) = articles.partition { it.feedUrl == url }
        val marks = gone.filter { it.read }.associate { it.key to it.sortTime }
        return copy(
            feeds = feeds.filterNot { it.url == url },
            articles = stay,
            readElsewhere = if (marks.isEmpty()) readElsewhere else readElsewhere + marks,
        )
    }

    /**
     * Applies a freshly downloaded copy of a feed: new articles are added as unread, known ones
     * get the new title and text but keep their read state. Articles that left the feed are
     * kept, up to [MAX_ARTICLES_PER_FEED] per feed (the newest ones). New articles in
     * [readElsewhere] (read on another device) arrive as read, and read ones that no longer fit
     * keep their mark there (#157): leaving this device doesn't make them unread elsewhere. Marks
     * older than [READ_MARKS_KEPT_MS] go, and articles that old the feed lists are read (#317).
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
                // A date in the future (a wrong clock or time zone) would stick to the top.
                val published = (item.publishedAt ?: old?.publishedAt)?.coerceAtMost(now)
                Article(
                    feedUrl = feedUrl,
                    id = item.id,
                    title = item.title,
                    link = item.link,
                    author = item.author,
                    html = item.html.take(MAX_HTML_LENGTH),
                    publishedAt = published,
                    fetchedAt = old?.fetchedAt ?: now,
                    read = (old?.read ?: (Article.keyOf(feedUrl, item.id) in readElsewhere)) || tooOld(published, now),
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
        // Arrived: the article itself carries its read state now.
        val arrived = feedArticles.mapNotNullTo(HashSet()) { it.key.takeIf { key -> key in readElsewhere } }
        val stays = feedArticles.mapTo(HashSet()) { it.key }
        val left = existing.values.filter { it.read && it.key !in stays }.associate { it.key to it.sortTime }
        val oldest = now - READ_MARKS_KEPT_MS
        val stale = readElsewhere.any { it.value < oldest }
        return copy(
            feeds = feeds.map { if (it.url == feedUrl) updatedFeed else it },
            articles = articles.filterNot { it.feedUrl == feedUrl } + feedArticles,
            readElsewhere =
                if (arrived.isEmpty() && left.isEmpty() && !stale) {
                    readElsewhere
                } else {
                    (readElsewhere - arrived + left).filterValues { it >= oldest }
                },
        )
    }

    /**
     * Whether a feed article is read because it is too old (#317): read marks of articles older than
     * [READ_MARKS_KEPT_MS] aren't kept or synced, so such an article read before would otherwise come
     * back unread on every new device, after a reinstall or once its mark went. It also clears the
     * ones that already came back.
     */
    private fun tooOld(
        publishedAt: Long?,
        now: Long,
    ): Boolean = publishedAt != null && publishedAt < now - READ_MARKS_KEPT_MS

    /**
     * Merges the downloads of a whole refresh at once, so the list changes in one step and new
     * articles take their place by date instead of appearing feed by feed.
     */
    fun mergeAll(
        downloads: Map<String, ParsedFeed>,
        now: Long,
    ): Library = downloads.entries.fold(this) { library, (url, parsed) -> library.merge(url, parsed, now) }

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

    /**
     * The articles of one of Feeds' tabs, newest first: the unread ones, or the ones already read.
     * Articles in [keep] (read or marked unread during this visit) stay where they were, so rows
     * don't jump away under your finger.
     */
    fun articlesFor(
        selection: FeedSelection,
        tab: FeedTab,
        keep: Set<String> = emptySet(),
    ): List<Article> =
        articles
            .filter { selection.includes(it) && (tab.has(it) || it.key in keep) }
            .sortedByDescending { it.sortTime }

    /** [selection] if its feed or folder still exists, else All (it was removed meanwhile). */
    fun shown(selection: FeedSelection): FeedSelection =
        when (selection) {
            FeedSelection.All -> selection
            is FeedSelection.InFolder -> if (folder(selection.folderId) != null) selection else FeedSelection.All
            is FeedSelection.Single -> if (feed(selection.feedUrl) != null) selection else FeedSelection.All
            is FeedSelection.Tagged -> if (feedTags().any { it.equals(selection.tag, ignoreCase = true) }) selection else FeedSelection.All
        }

    /** Every feed tag (#187), in the folders' order: the Feed tab's chips. See [feedTagsInOrder]. */
    fun feedTags(): List<String> = feedTagsInOrder(feeds, folders)

    /** Unread counts for All, every folder and every feed, for the drawer. */
    fun unreadCounts(): Map<FeedSelection, Int> =
        buildMap {
            put(FeedSelection.All, unreadCount(FeedSelection.All))
            folders.forEach { put(FeedSelection.InFolder(it.id), unreadCount(FeedSelection.InFolder(it.id))) }
            feeds.forEach { put(FeedSelection.Single(it.url), unreadCount(FeedSelection.Single(it.url))) }
            feedTags().forEach { put(FeedSelection.Tagged(it), unreadCount(FeedSelection.Tagged(it))) }
        }

    fun unreadCount(selection: FeedSelection): Int = articles.count { selection.includes(it) && !it.read }

    private fun FeedSelection.includes(article: Article): Boolean =
        when (this) {
            FeedSelection.All -> feed(article.feedUrl) != null
            is FeedSelection.InFolder -> feed(article.feedUrl)?.folderId == folderId
            is FeedSelection.Single -> article.feedUrl == feedUrl
            is FeedSelection.Tagged -> feed(article.feedUrl)?.tags.orEmpty().any { it.equals(tag, ignoreCase = true) }
        }

    companion object {
        /** Bounds storage: feeds rarely list more than 50 articles at a time. */
        const val MAX_ARTICLES_PER_FEED = 200

        /**
         * Read marks of articles older than this are dropped; sync uses the same. Two years, not 120
         * days: a feed that posts rarely still lists articles from months back (#245).
         */
        const val READ_MARKS_KEPT_MS = 730L * 24 * 60 * 60 * 1000

        /** Bounds storage for feeds that put entire books in one article. */
        const val MAX_HTML_LENGTH = 200_000
    }
}
