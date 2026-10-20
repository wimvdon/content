package be.wimvandonck.reader.sync

import be.wimvandonck.reader.data.Article
import be.wimvandonck.reader.data.BookmarkEntry
import be.wimvandonck.reader.data.Highlight
import be.wimvandonck.reader.data.Item
import be.wimvandonck.reader.data.ItemKeys
import be.wimvandonck.reader.data.Library
import be.wimvandonck.reader.data.LibraryRows
import be.wimvandonck.reader.data.ReadingListEntry
import be.wimvandonck.reader.data.ReadingState
import be.wimvandonck.reader.data.cleanTags
import be.wimvandonck.reader.data.item
import be.wimvandonck.reader.data.putItem

/**
 * The library's rows for per-item sync (#106, docs/sync.md): one row per item ("item:<item key>")
 * and one per read feed article ("read:<feed url> <article id>"), each with its JSON data, or null
 * when it is gone. No article text: an item row holds the link, title, picture address, memberships,
 * tags, note and highlights; a read row only the article's date. The web app does the same in
 * web/src/lib/syncRows.ts. Plain Kotlin, unit tested.
 */
object SyncRows {
    const val ITEM = "item:"
    const val READ = "read:"

    /** Read marks older than this are dropped from the account (feeds rarely go back further). */
    const val KEEP_READ_MS = SyncDocument.KEEP_READ_MS

    fun itemData(i: Item): Map<String, Any?> =
        linkedMapOf(
            "url" to i.url,
            "title" to i.title,
            "imageUrl" to i.imageUrl,
            "feedUrl" to i.feedUrl,
            "articleId" to i.articleId,
            "createdAt" to i.createdAt,
            "readingList" to
                i.readingList?.let {
                    linkedMapOf("state" to if (it.state == ReadingState.Archived) "archived" else "toread", "addedAt" to it.addedAt)
                },
            "folder" to i.folder,
            "service" to i.service,
            "bookmark" to i.bookmark?.let { linkedMapOf("addedAt" to it.addedAt, "collection" to it.collection) },
            "liked" to i.liked,
            "tags" to i.tags,
            "note" to i.note,
            "highlights" to i.highlights.map(LibraryRows::highlightMap),
            // In thousandths, so both apps write the same number.
            "progress" to Math.round(i.progress * 1000).toLong(),
        )

    /**
     * The item an item row describes, or null when the data isn't one. [local] gives what isn't
     * synced (the description).
     */
    fun itemOf(
        key: String,
        data: Map<*, *>,
        local: Item?,
    ): Item? {
        val url = (data["url"] as? String)?.takeIf { ItemKeys.isWebLink(it) } ?: return null
        val reading = data["readingList"] as? Map<*, *>
        val bookmark = data["bookmark"] as? Map<*, *>
        val createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L
        return Item(
            key = key,
            url = url,
            title = (data["title"] as? String)?.ifBlank { null } ?: url,
            description = local?.description.orEmpty(),
            imageUrl = (data["imageUrl"] as? String)?.takeIf { it.startsWith("https://") },
            feedUrl = data["feedUrl"] as? String,
            articleId = data["articleId"] as? String,
            createdAt = createdAt,
            readingList =
                reading?.let {
                    val state =
                        when (it["state"]) {
                            "toread" -> ReadingState.ToRead
                            "archived" -> ReadingState.Archived
                            else -> null
                        }
                    state?.let { s -> ReadingListEntry(s, (it["addedAt"] as? Number)?.toLong() ?: createdAt) }
                },
            folder = (data["folder"] as? String)?.trim()?.ifEmpty { null },
            service = (data["service"] as? String)?.ifEmpty { null },
            bookmark = bookmark?.let { BookmarkEntry((it["addedAt"] as? Number)?.toLong() ?: createdAt, (it["collection"] as? String)?.ifBlank { null }) },
            liked = data["liked"] == true,
            tags = cleanTags((data["tags"] as? List<*>).orEmpty().filterIsInstance<String>()),
            note = data["note"] as? String ?: "",
            highlights = (data["highlights"] as? List<*>).orEmpty().mapNotNull(LibraryRows::highlightOf).distinctBy { it.id },
            progress = (((data["progress"] as? Number)?.toDouble() ?: 0.0) / 1000).toFloat().coerceIn(0f, 1f),
        )
    }

    fun readData(time: Long): Map<String, Any?> = linkedMapOf("time" to time)

    fun readTimeOf(data: Map<*, *>?): Long? = (data?.get("time") as? Number)?.toLong()

    /**
     * This device's data for a row: what the library holds now. A read mark older than [keepSince]
     * counts as gone (that is how old marks leave the account). Rows of kinds this version doesn't
     * know stay as they were ([base]).
     */
    fun localRow(
        library: Library,
        key: String,
        keepSince: Long,
        base: Map<String, Any?>?,
        articles: Map<String, Article> = library.articles.associateBy { it.key },
    ): Map<String, Any?>? =
        when {
            key.startsWith(ITEM) -> library.item(key.removePrefix(ITEM))?.let(::itemData)

            key.startsWith(READ) -> {
                val articleKey = key.removePrefix(READ)
                val article = articles[articleKey]
                val time = if (article != null) article.sortTime.takeIf { article.read } else library.readElsewhere[articleKey]
                time?.takeIf { it >= keepSince }?.let(::readData)
            }

            else -> base
        }

    /** Every row the library has now (item and read rows), by key. */
    fun localRows(
        library: Library,
        keepSince: Long,
    ): Map<String, Map<String, Any?>> {
        val rows = LinkedHashMap<String, Map<String, Any?>>()
        for (item in library.items) rows[ITEM + item.key] = itemData(item)
        val have = HashSet<String>()
        for (article in library.articles) {
            have.add(article.key)
            if (article.read && article.sortTime >= keepSince) rows[READ + article.key] = readData(article.sortTime)
        }
        for ((key, time) in library.readElsewhere) if (key !in have && time >= keepSince) rows[READ + key] = readData(time)
        return rows
    }

    /** Two rows' data mean the same (each app may write its JSON a little differently). */
    fun same(
        key: String,
        a: Map<String, Any?>?,
        b: Map<String, Any?>?,
    ): Boolean {
        if (a == null || b == null) return a == null && b == null
        return when {
            key.startsWith(ITEM) -> {
                val k = key.removePrefix(ITEM)
                itemOf(k, a, null)?.let(::itemData) == itemOf(k, b, null)?.let(::itemData)
            }
            key.startsWith(READ) -> readTimeOf(a) == readTimeOf(b)
            else -> a == b
        }
    }

    /**
     * Three-way merge of one row: whatever differs from [base] on one side is a change on that side
     * and wins; both changed, this device wins, except that an edit beats a removal. Items merge per
     * field, tags as a set and highlights per highlight, so changes to different parts both stay.
     */
    fun merge(
        key: String,
        base: Map<String, Any?>?,
        local: Map<String, Any?>?,
        remote: Map<String, Any?>?,
    ): Map<String, Any?>? {
        if (same(key, local, base)) return remote
        if (same(key, remote, base) || same(key, local, remote)) return local
        if (local == null || remote == null) return local ?: remote
        if (!key.startsWith(ITEM)) return local
        val k = key.removePrefix(ITEM)
        val b = base?.let { itemOf(k, it, null) }
        val l = itemOf(k, local, null) ?: return remote
        val r = itemOf(k, remote, null) ?: return local
        return itemData(mergeItems(b, l, r))
    }

    fun mergeItems(
        b: Item?,
        l: Item,
        r: Item,
    ): Item {
        fun <T> pick(
            base: T?,
            local: T,
            remote: T,
        ): T = if (local == base) remote else local
        return l.copy(
            url = pick(b?.url, l.url, r.url),
            title = pick(b?.title, l.title, r.title),
            imageUrl = pick(b?.imageUrl, l.imageUrl, r.imageUrl),
            feedUrl = pick(b?.feedUrl, l.feedUrl, r.feedUrl),
            articleId = pick(b?.articleId, l.articleId, r.articleId),
            createdAt = minOf(l.createdAt, r.createdAt),
            readingList = pick(b?.readingList, l.readingList, r.readingList),
            folder = pick(b?.folder, l.folder, r.folder),
            // Once kept in a service it stays there: a device that set it wins over one that didn't.
            service = pick(b?.service, l.service, r.service) ?: l.service ?: r.service,
            bookmark = pick(b?.bookmark, l.bookmark, r.bookmark),
            liked = pick(b?.liked, l.liked, r.liked),
            tags = mergeTags(b?.tags.orEmpty(), l.tags, r.tags),
            note = pick(b?.note, l.note, r.note),
            highlights = mergeHighlights(b?.highlights.orEmpty(), l.highlights, r.highlights),
            progress = pick(b?.progress, l.progress, r.progress),
        )
    }

    /** Tags as a set: each side's additions and removals both count (case ignored). */
    fun mergeTags(
        base: List<String>,
        local: List<String>,
        remote: List<String>,
    ): List<String> {
        fun List<String>.has(tag: String) = any { it.equals(tag, ignoreCase = true) }
        val kept = local.filter { remote.has(it) || !base.has(it) }
        val added = remote.filter { !local.has(it) && !base.has(it) }
        return cleanTags(kept + added)
    }

    /** Highlights per id: a change on one side wins; both changed, this device; an edit beats a removal. */
    fun mergeHighlights(
        base: List<Highlight>,
        local: List<Highlight>,
        remote: List<Highlight>,
    ): List<Highlight> {
        val b = base.associateBy { it.id }
        val l = local.associateBy { it.id }
        val r = remote.associateBy { it.id }
        return (local.map { it.id } + remote.map { it.id } + base.map { it.id }).distinct().mapNotNull { id ->
            when {
                l[id] == b[id] -> r[id]
                r[id] == b[id] -> l[id]
                else -> l[id] ?: r[id]
            }
        }.sortedBy { it.createdAt }
    }

    /**
     * The library after taking a row's merged data: an item row puts or removes the item; a read row
     * marks the article read or unread, or, for an article this device doesn't have (yet), remembers
     * it in [Library.readElsewhere] so it arrives read. An article older than [keepSince] whose mark
     * was dropped stays as it is (dropping old marks doesn't make anything unread).
     */
    fun apply(
        library: Library,
        key: String,
        data: Map<String, Any?>?,
        keepSince: Long,
    ): Library =
        when {
            key.startsWith(ITEM) -> {
                val itemKey = key.removePrefix(ITEM)
                val local = library.item(itemKey)
                val item = data?.let { itemOf(itemKey, it, local) }
                when {
                    item != null -> library.putItem(item)
                    data != null -> library
                    local == null -> library
                    else -> library.copy(items = library.items.filterNot { it.key == itemKey })
                }
            }

            key.startsWith(READ) -> applyReads(library, mapOf(key.removePrefix(READ) to readTimeOf(data)), keepSince)

            else -> library
        }

    /** Read marks (article key → date, null: unread) in one step, so thousands of them stay quick. */
    fun applyReads(
        library: Library,
        reads: Map<String, Long?>,
        keepSince: Long,
    ): Library {
        if (reads.isEmpty()) return library
        val read = HashSet<String>()
        val unread = HashSet<String>()
        val elsewhere = HashMap(library.readElsewhere)
        val articles = library.articles.associateBy { it.key }
        for ((key, time) in reads) {
            val article = articles[key]
            when {
                article != null && time == null && article.sortTime < keepSince -> Unit
                article != null -> (if (time != null) read else unread).add(key)
                time == null -> elsewhere.remove(key)
                else -> elsewhere[key] = time
            }
        }
        var result = library
        if (read.isNotEmpty()) result = result.setRead(read, true)
        if (unread.isNotEmpty()) result = result.setRead(unread, false)
        return if (elsewhere == library.readElsewhere) result else result.copy(readElsewhere = elsewhere)
    }
}
