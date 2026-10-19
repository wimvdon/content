package be.wimvandonck.reader.data

import java.net.URI

/** What a bookmark is, for its card and the type chips (#108): read from its link. */
enum class BookmarkKind { Article, Tool, Repo, Video }

/** What Bookmarks shows: one collection or all, one type or all. */
data class BookmarkFilter(
    val collection: String? = null,
    val kind: BookmarkKind? = null,
)

/** Where the save sheet puts a link: on the reading list, or in Bookmarks. */
enum class SaveTo { ReadingList, Bookmark }

/** Bookmarks as the screens show them (#108). Plain Kotlin, unit tested. */
object Bookmarks {
    /** Articles go to the reading list, other links (tools, repositories, videos) to Bookmarks. */
    fun suggested(url: String): SaveTo = if (kindOf(url) == BookmarkKind.Article) SaveTo.ReadingList else SaveTo.Bookmark

    /**
     * Saves a link from the save sheet: on the reading list or in Bookmarks (with its collection),
     * with a note and tags. Null for addresses that aren't web links.
     */
    fun save(
        library: Library,
        url: String,
        title: String?,
        to: SaveTo,
        now: Long,
        collection: String? = null,
        note: String = "",
        tags: List<String> = emptyList(),
    ): Pair<Library, Item?> {
        if (to == SaveTo.Bookmark) return add(library, url, title, now, collection, note.takeIf { it.isNotBlank() }, tags)
        val (added, item) = library.addToReadingList(url, title, now)
        item ?: return library to null
        val noted = if (note.isBlank()) item else item.copy(note = note.trim())
        val updated = noted.copy(tags = cleanTags(item.tags + tags))
        return added.putItem(updated) to updated
    }

    /**
     * Read again: back on the reading list to read, from the start, at the top. It stays in
     * Bookmarks.
     */
    fun readAgain(
        library: Library,
        key: String,
        now: Long,
    ): Library =
        library.changeItem(key) { item ->
            if (item.readingList?.state == ReadingState.ToRead) item else item.copy(readingList = ReadingListEntry(ReadingState.ToRead, now), progress = 0f)
        }

    /**
     * Removes a bookmark. An item on the reading list stays there with its note, tags and highlights;
     * otherwise the whole item goes, so nothing hidden is left behind.
     */
    fun remove(
        library: Library,
        key: String,
    ): Library {
        val item = library.item(key) ?: return library
        return if (item.readingList != null) library.removeBookmark(key) else library.copy(items = library.items.filter { it.key != key })
    }

    /** Changes a bookmark's collection (blank: none), note and tags. */
    fun edit(
        library: Library,
        key: String,
        collection: String,
        note: String,
        tags: List<String>,
    ): Library =
        library.changeItem(key) { item ->
            val b = item.bookmark ?: return@changeItem item
            item.copy(bookmark = b.copy(collection = collection.trim().ifEmpty { null }), note = note.trim(), tags = cleanTags(tags))
        }

    /**
     * A link's kind: a video (YouTube, Vimeo), a repository (GitHub, GitLab, Codeberg, Bitbucket),
     * a tool (a site's own home page, where there is no article to read), else an article.
     */
    fun kindOf(url: String): BookmarkKind {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return BookmarkKind.Article
        val host = uri.host.orEmpty().lowercase().removePrefix("www.").removePrefix("m.")
        val path = uri.path.orEmpty().trim('/')
        val parts = path.split('/').filter { it.isNotEmpty() }
        return when {
            host == "youtu.be" || (host == "youtube.com" && (path == "watch" || parts.firstOrNull() in setOf("shorts", "live"))) -> BookmarkKind.Video
            host == "vimeo.com" && parts.firstOrNull()?.all(Char::isDigit) == true -> BookmarkKind.Video
            host in REPO_HOSTS && parts.size >= 2 && parts[0] !in NOT_REPOS -> BookmarkKind.Repo
            parts.isEmpty() && uri.query.isNullOrEmpty() -> BookmarkKind.Tool
            else -> BookmarkKind.Article
        }
    }

    private val REPO_HOSTS = setOf("github.com", "gitlab.com", "codeberg.org", "bitbucket.org", "sr.ht", "git.sr.ht")

    /** First path parts on code hosts that are pages of the host, not repositories. */
    private val NOT_REPOS = setOf("about", "features", "pricing", "explore", "topics", "marketplace", "settings", "login", "orgs", "sponsors", "blog")

    /** The bookmarks the filter picks, newest kept first. */
    fun items(
        library: Library,
        filter: BookmarkFilter,
    ): List<Item> =
        library.bookmarks().filter {
            (filter.collection == null || it.bookmark?.collection.equals(filter.collection, ignoreCase = true)) &&
                (filter.kind == null || kindOf(it.url) == filter.kind)
        }

    /** Every collection in use, sorted (case ignored). */
    fun collections(library: Library): List<String> =
        library.items.mapNotNull { it.bookmark?.collection }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }

    /** How many bookmarks each collection has. */
    fun collectionCounts(library: Library): Map<String, Int> =
        collections(library).associateWith { c -> library.items.count { it.bookmark?.collection.equals(c, ignoreCase = true) } }

    /** The filter without a collection that no longer exists. */
    fun shown(
        library: Library,
        filter: BookmarkFilter,
    ): BookmarkFilter =
        if (filter.collection != null && collections(library).none { it.equals(filter.collection, ignoreCase = true) }) filter.copy(collection = null) else filter

    /**
     * Keeps a link in Bookmarks (a quick save from Feeds, the share sheet, Add bookmark): the item of
     * its link, made from the feed article when there is one, with a collection, note and tags. A link
     * already on the reading list stays there. Null for addresses that aren't web links.
     */
    fun add(
        library: Library,
        url: String,
        title: String?,
        now: Long,
        collection: String? = null,
        note: String? = null,
        tags: List<String> = emptyList(),
    ): Pair<Library, Item?> {
        val base = library.itemForLink(url) ?: library.newItem(url, title, now) ?: return library to null
        val item =
            base.copy(
                title = title?.trim()?.takeIf { it.isNotEmpty() } ?: base.title,
                bookmark = BookmarkEntry(base.bookmark?.addedAt ?: now, collection?.trim()?.ifEmpty { null } ?: base.bookmark?.collection),
                note = note?.trim() ?: base.note,
                tags = cleanTags(base.tags + tags),
            )
        return library.putItem(item) to item
    }

    /** Renames a collection on every bookmark; renaming to another collection's name joins the two. */
    fun renameCollection(
        library: Library,
        from: String,
        to: String,
    ): Library {
        val clean = to.trim()
        if (clean.isEmpty()) return library
        return library.copy(
            items =
                library.items.map { item ->
                    val b = item.bookmark
                    if (b?.collection.equals(from, ignoreCase = true)) item.copy(bookmark = b!!.copy(collection = clean)) else item
                },
        )
    }

    /** Takes a collection away: its bookmarks stay, in no collection. */
    fun removeCollection(
        library: Library,
        name: String,
    ): Library =
        library.copy(
            items =
                library.items.map { item ->
                    val b = item.bookmark
                    if (b?.collection.equals(name, ignoreCase = true)) item.copy(bookmark = b!!.copy(collection = null)) else item
                },
        )
}
