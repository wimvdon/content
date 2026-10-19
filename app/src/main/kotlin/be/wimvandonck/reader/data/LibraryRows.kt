package be.wimvandonck.reader.data

import be.wimvandonck.reader.data.db.ArticleRow
import be.wimvandonck.reader.data.db.FeedRow
import be.wimvandonck.reader.data.db.FolderRow
import be.wimvandonck.reader.data.db.ItemRow
import be.wimvandonck.reader.data.db.LibraryTables
import be.wimvandonck.reader.sync.Json

/** The library as database rows and back (data/db). Plain Kotlin, unit tested. */
object LibraryRows {
    fun folder(
        position: Int,
        folder: Folder,
    ) = FolderRow(folder.id, position, folder.title, folder.icon)

    fun feed(
        position: Int,
        feed: Feed,
    ) = FeedRow(feed.url, position, feed.title, feed.siteUrl, feed.folderId, feed.iconUrl, feed.renamed)

    fun article(a: Article) = ArticleRow(a.key, a.feedUrl, a.id, a.title, a.link, a.author, a.html, a.publishedAt, a.fetchedAt, a.read, a.imageUrl)

    fun item(i: Item) =
        ItemRow(
            key = i.key,
            url = i.url,
            title = i.title,
            description = i.description,
            imageUrl = i.imageUrl,
            feedUrl = i.feedUrl,
            articleId = i.articleId,
            createdAt = i.createdAt,
            readingState =
                when (i.readingList?.state) {
                    ReadingState.ToRead -> TO_READ
                    ReadingState.Archived -> ARCHIVED
                    null -> null
                },
            readingAddedAt = i.readingList?.addedAt,
            bookmarkAddedAt = i.bookmark?.addedAt,
            collection = i.bookmark?.collection,
            liked = i.liked,
            tags = Json.write(i.tags),
            note = i.note,
            highlights = Json.write(i.highlights.map(::highlightMap)),
            progress = i.progress,
        )

    fun library(tables: LibraryTables): Library {
        val folders = tables.folders.sortedBy { it.position }.map { Folder(it.id, it.title, IconCatalog.fromStored(it.icon)) }
        val folderIds = folders.mapTo(HashSet()) { it.id }
        val feeds =
            tables.feeds.sortedBy { it.position }.map {
                Feed(it.url, it.title, it.siteUrl, it.folderId?.takeIf { id -> id in folderIds }, it.iconUrl, it.renamed)
            }
        val feedUrls = feeds.mapTo(HashSet()) { it.url }
        val articles =
            tables.articles.filter { it.feedUrl in feedUrls }.map {
                Article(it.feedUrl, it.articleId, it.title, it.link, it.author, it.html, it.publishedAt, it.fetchedAt, it.read, it.imageUrl)
            }
        return Library(
            feeds = feeds,
            articles = articles,
            folders = folders,
            items = tables.items.map(::item),
            readElsewhere = tables.readElsewhere.associate { it.key to it.time },
        )
    }

    fun item(row: ItemRow): Item =
        Item(
            key = row.key,
            url = row.url,
            title = row.title,
            description = row.description,
            imageUrl = row.imageUrl,
            feedUrl = row.feedUrl,
            articleId = row.articleId,
            createdAt = row.createdAt,
            readingList =
                when (row.readingState) {
                    TO_READ -> ReadingListEntry(ReadingState.ToRead, row.readingAddedAt ?: row.createdAt)
                    ARCHIVED -> ReadingListEntry(ReadingState.Archived, row.readingAddedAt ?: row.createdAt)
                    else -> null
                },
            bookmark = row.bookmarkAddedAt?.let { BookmarkEntry(it, row.collection) },
            liked = row.liked,
            tags = (runCatching { Json.parse(row.tags) }.getOrNull() as? List<*>).orEmpty().filterIsInstance<String>(),
            note = row.note,
            highlights = (runCatching { Json.parse(row.highlights) }.getOrNull() as? List<*>).orEmpty().mapNotNull(::highlightOf),
            progress = row.progress.coerceIn(0f, 1f),
        )

    fun highlightMap(h: Highlight): Map<String, Any?> =
        linkedMapOf(
            "id" to h.id,
            "quote" to h.quote,
            "prefix" to h.prefix,
            "suffix" to h.suffix,
            "occurrence" to h.occurrence.toLong(),
            "note" to h.note,
            "createdAt" to h.createdAt,
        )

    fun highlightOf(value: Any?): Highlight? {
        val m = value as? Map<*, *> ?: return null
        val id = (m["id"] as? String)?.takeIf { it.isNotEmpty() } ?: return null
        val quote = (m["quote"] as? String)?.takeIf { it.isNotEmpty() } ?: return null
        return Highlight(
            id = id,
            quote = quote,
            prefix = m["prefix"] as? String ?: "",
            suffix = m["suffix"] as? String ?: "",
            occurrence = ((m["occurrence"] as? Number)?.toInt() ?: 0).coerceAtLeast(0),
            note = m["note"] as? String ?: "",
            createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L,
        )
    }

    private const val TO_READ = "toread"
    private const val ARCHIVED = "archived"
}

/**
 * What to write to the database after a change: the folders and feeds in full when they changed
 * (null when not; they are few and their order matters), and only the articles and items that
 * changed or went away.
 */
data class LibraryChanges(
    val folders: List<Folder>? = null,
    val feeds: List<Feed>? = null,
    val articles: List<Article> = emptyList(),
    val deletedArticles: List<String> = emptyList(),
    val items: List<Item> = emptyList(),
    val deletedItems: List<String> = emptyList(),
    val readElsewhere: Map<String, Long> = emptyMap(),
    val deletedReadElsewhere: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() =
            folders == null && feeds == null && articles.isEmpty() && deletedArticles.isEmpty() && items.isEmpty() && deletedItems.isEmpty() &&
                readElsewhere.isEmpty() && deletedReadElsewhere.isEmpty()

    companion object {
        fun between(
            old: Library,
            new: Library,
        ): LibraryChanges {
            val (articles, deletedArticles) = diff(old.articles, new.articles) { it.key }
            val (items, deletedItems) = diff(old.items, new.items) { it.key }
            return LibraryChanges(
                folders = new.folders.takeIf { it != old.folders },
                feeds = new.feeds.takeIf { it != old.feeds },
                articles = articles,
                deletedArticles = deletedArticles,
                items = items,
                deletedItems = deletedItems,
                readElsewhere = if (old.readElsewhere === new.readElsewhere) emptyMap() else new.readElsewhere.filter { (k, v) -> old.readElsewhere[k] != v },
                deletedReadElsewhere = if (old.readElsewhere === new.readElsewhere) emptyList() else old.readElsewhere.keys.filter { it !in new.readElsewhere },
            )
        }

        /** Everything in [library], to fill an empty database. */
        fun all(library: Library) = LibraryChanges(library.folders, library.feeds, library.articles, emptyList(), library.items, emptyList(), library.readElsewhere)

        private fun <T> diff(
            old: List<T>,
            new: List<T>,
            key: (T) -> String,
        ): Pair<List<T>, List<String>> {
            if (old === new) return emptyList<T>() to emptyList()
            val before = old.associateBy(key)
            val changed = new.filter { value -> before[key(value)].let { it == null || (it !== value && it != value) } }
            val kept = new.mapTo(HashSet(), key)
            return changed to before.keys.filter { it !in kept }
        }
    }
}
