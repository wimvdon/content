package be.wimvandonck.content.sync

import be.wimvandonck.content.data.Feed
import be.wimvandonck.content.data.Folder
import be.wimvandonck.content.data.Library

/**
 * Combines this device's changes with the other devices' changes, without clocks: [base] is the
 * sync file as this device last wrote or read it. Whatever differs from [base] on one side is a
 * change on that side and wins; when both sides changed the same thing, this device wins, except
 * that an edit beats a removal (nothing the user just changed disappears).
 */
object SyncMerge {
    fun merge(
        base: SyncDocument?,
        local: SyncDocument,
        remote: SyncDocument,
    ): SyncDocument {
        val b = base ?: SyncDocument()
        val folders = mergeRecords(b.folders, local.folders, remote.folders) { it.id }
        val folderIds = folders.mapTo(HashSet()) { it.id }
        val feeds =
            mergeRecords(b.feeds, local.feeds, remote.feeds) { it.url }
                .map { if (it.folderId != null && it.folderId !in folderIds) it.copy(folderId = null) else it }
        // A device that has no synced settings yet (it never synced, or synced before settings
        // were in the file) takes the file's, so its defaults don't overwrite the user's choices.
        val settings =
            if (b.settings.isEmpty()) local.settings + remote.settings else mergeSettings(b.settings, local.settings, remote.settings)
        return SyncDocument(folders, feeds, settings, legacyRead = remote.legacyRead)
    }

    /** Per setting: a change on this device wins, otherwise the other devices' value (or its removal). */
    private fun mergeSettings(
        base: Map<String, String>,
        local: Map<String, String>,
        remote: Map<String, String>,
    ): Map<String, String> {
        val result = HashMap<String, String>()
        for (k in (base.keys + local.keys + remote.keys)) {
            val value = if (local[k] == base[k]) remote[k] else local[k]
            if (value != null) result[k] = value
        }
        return result
    }

    /** Three-way merge of keyed records, keeping the order of the side that reordered. */
    private fun <T> mergeRecords(
        base: List<T>,
        local: List<T>,
        remote: List<T>,
        key: (T) -> String,
    ): List<T> {
        val b = base.associateBy(key)
        val l = local.associateBy(key)
        val r = remote.associateBy(key)
        val merged = LinkedHashMap<String, T>()
        for (k in (l.keys + r.keys + b.keys)) {
            val pick =
                when {
                    l[k] == b[k] -> r[k]
                    r[k] == b[k] -> l[k]
                    else -> l[k] ?: r[k]
                }
            if (pick != null) merged[k] = pick
        }
        val localOrderChanged = base.map(key).filter { it in l } != local.map(key).filter { it in b }
        val first = if (localOrderChanged) local else remote
        val second = if (localOrderChanged) remote else local
        val order = LinkedHashSet<String>()
        (first.map(key) + second.map(key)).filter { it in merged }.forEach { order.add(it) }
        return order.map { merged.getValue(it) }
    }

    /** What this device sends to [merge] as its side. */
    fun localDocument(library: Library): SyncDocument = SyncDocument.of(library)

    /**
     * Makes [library] match [merged]: folders and feeds (new feeds start without articles until
     * the next refresh; removed ones lose theirs). A version 2 file's read marks ([SyncDocument.legacyRead])
     * are kept for the articles this device doesn't have, so per-item sync takes them over.
     */
    fun apply(
        library: Library,
        merged: SyncDocument,
    ): Library {
        val existing = library.feeds.associateBy { it.url }
        val feeds =
            merged.feeds.map { f ->
                (existing[f.url] ?: Feed(url = f.url, title = f.title)).copy(
                    title = f.title,
                    renamed = f.renamed,
                    folderId = f.folderId,
                    siteUrl = f.siteUrl ?: existing[f.url]?.siteUrl,
                    iconUrl = f.iconUrl ?: existing[f.url]?.iconUrl,
                    tags = f.tags,
                )
            }
        val urls = feeds.mapTo(HashSet()) { it.url }
        val articles = library.articles.filter { it.feedUrl in urls }
        val have = articles.mapTo(HashSet()) { it.key }
        val legacy = merged.legacyRead.filterKeys { it !in have && it !in library.readElsewhere }
        return library.copy(
            feeds = feeds,
            articles = if (articles.size == library.articles.size) library.articles else articles,
            folders = merged.folders.map { Folder(it.id, it.title, it.icon) },
            readElsewhere = if (legacy.isEmpty()) library.readElsewhere else library.readElsewhere + legacy,
        )
    }

    /** True when [merged] has feeds that [library] doesn't, which then need a refresh. */
    fun hasNewFeeds(
        library: Library,
        merged: SyncDocument,
    ): Boolean = merged.feeds.any { library.feed(it.url) == null }
}
