package be.wimvandonck.reader.services

import be.wimvandonck.reader.data.Highlight
import be.wimvandonck.reader.data.Item
import be.wimvandonck.reader.data.ItemKeys
import be.wimvandonck.reader.data.Library
import be.wimvandonck.reader.data.ReadingListEntry
import be.wimvandonck.reader.data.ReadingState
import be.wimvandonck.reader.data.changeItem
import be.wimvandonck.reader.data.cleanTags
import be.wimvandonck.reader.data.item
import be.wimvandonck.reader.data.newItem
import be.wimvandonck.reader.data.putItem
import be.wimvandonck.reader.sync.Json
import be.wimvandonck.reader.sync.SyncRows
import kotlin.math.abs

/**
 * What this device last saw of the reading list at a service: the service's cursor and each
 * article as the service had it, by item key (the base for merging, with its id there).
 */
data class ServiceState(
    val cursor: String? = null,
    val rows: Map<String, RemoteEntry> = emptyMap(),
)

/** How a service sync ended: the state to keep, and why it stopped early (null when it got through). */
data class ServiceSyncResult(
    val state: ServiceState,
    val problem: ServiceProblem?,
)

/**
 * Keeps the reading list and a connected service the same (#109): reads what changed at the service
 * since last time and merges it three ways with the article as last seen there and Reader's own
 * (the same rules as per-item sync: a change on one side wins, both changed, Reader wins, an edit
 * beats a removal; tags as a set), then sends the service what changed in Reader. A membership
 * change only calls this section's service. A failed call keeps Reader's data as it is and is tried
 * again on the next sync. Plain Kotlin, tested with a fake service.
 */
object ServiceSync {
    suspend fun run(
        service: ReadingListService,
        state: ServiceState,
        /** Copy Reader's highlights to the service (#102, off by default). */
        copyHighlights: Boolean = false,
        now: Long = System.currentTimeMillis(),
        /** Which occurrence of its text a highlight is in the service's text of the item. */
        positionOf: suspend (Item, Highlight) -> Int = { _, h -> h.occurrence },
        update: suspend ((Library) -> Library) -> Library,
    ): ServiceSyncResult {
        val rows = HashMap(state.rows)
        var cursor = state.cursor
        val progress = ServiceFeature.Progress in service.features
        try {
            val changes = service.changes(cursor)
            update { library -> takeIn(library, rows, changes, progress) }
            cursor = changes.cursor
        } catch (e: ServiceException) {
            return ServiceSyncResult(ServiceState(cursor, rows), e.problem)
        }
        var library = update { it }
        try {
            for (item in library.items) {
                if (item.readingList == null) continue
                val have = rows[item.key]
                val want = entryOf(item, have?.remoteId.orEmpty())
                if (have == null) {
                    var added = service.add(item.url, item.title, item.tags, item.archived)
                    var target = want.copy(remoteId = added.remoteId)
                    // Saved without a title (its address stands in): the service's title is better.
                    if (item.title == item.url && added.title.isNotBlank() && added.title != item.url) {
                        target = target.copy(title = added.title)
                        library =
                            update { lib ->
                                lib.changeItem(item.key) { i ->
                                    if (i.title != i.url) i else i.copy(title = added.title, description = i.description.ifBlank { added.description }, imageUrl = i.imageUrl ?: added.imageUrl)
                                }
                            }
                    }
                    if (!sameCore(added, target, progress)) added = service.update(added, target)
                    rows[item.key] = added
                } else if (!sameCore(have, want, progress)) {
                    rows[item.key] = service.update(have, want).copy(highlights = have.highlights)
                }
            }
            if (copyHighlights && ServiceFeature.Highlights in service.features) {
                library = update { it }
                for (item in library.items) {
                    val row = rows[item.key] ?: continue
                    if (item.highlights.isEmpty() && row.highlights.isEmpty()) continue
                    val positions = item.highlights.associate { it.id to positionOf(item, it) }
                    val pushed = HighlightSync.push(service, row.remoteId, item, row.highlights, now) { positions[it.id] ?: it.occurrence }
                    rows[item.key] = row.copy(highlights = pushed.links)
                    pushed.problem?.let { return ServiceSyncResult(ServiceState(cursor, rows), it) }
                }
            }
            library = update { it }
            val gone = rows.keys.filter { library.item(it)?.readingList == null }
            // A safety net: a reading list that lost most of its articles at once is more likely a
            // damaged library than the user's wish, so nothing is removed at the service then.
            if (gone.size > MAX_SAFE_REMOVALS && gone.size * 2 > rows.size) return ServiceSyncResult(ServiceState(cursor, rows), ServiceProblem.Refused)
            for (key in gone) {
                service.remove(rows.getValue(key).remoteId)
                rows.remove(key)
            }
        } catch (e: ServiceException) {
            return ServiceSyncResult(ServiceState(cursor, rows), e.problem)
        }
        return ServiceSyncResult(ServiceState(cursor, rows), null)
    }

    /** An item on the reading list as a service entry (the core only). */
    fun entryOf(
        item: Item,
        remoteId: String,
    ): RemoteEntry =
        RemoteEntry(
            remoteId = remoteId,
            url = item.url,
            title = item.title,
            description = item.description,
            imageUrl = item.imageUrl,
            addedAt = item.readingList?.addedAt ?: item.createdAt,
            archived = item.archived,
            liked = item.liked,
            tags = item.tags,
            progress = item.progress,
        )

    /** The same core: archived, liked, title, tags (case ignored) and, when synced, progress. */
    fun sameCore(
        a: RemoteEntry?,
        b: RemoteEntry?,
        progress: Boolean,
    ): Boolean {
        if (a == null || b == null) return a == null && b == null
        return a.archived == b.archived &&
            a.liked == b.liked &&
            a.title == b.title &&
            sameTags(a.tags, b.tags) &&
            (!progress || abs(a.progress - b.progress) < PROGRESS_STEP)
    }

    private fun sameTags(
        a: List<String>,
        b: List<String>,
    ): Boolean = a.mapTo(HashSet()) { it.lowercase() } == b.mapTo(HashSet()) { it.lowercase() }

    /** Removing more than this many articles at once, and more than half, is held back (see [run]). */
    const val MAX_SAFE_REMOVALS = 20

    /** Progress changes smaller than this aren't worth a call. */
    private const val PROGRESS_STEP = 0.01f

    /**
     * The library after taking in what changed at the service; [rows] then holds the service's
     * articles as seen now.
     */
    fun takeIn(
        library: Library,
        rows: MutableMap<String, RemoteEntry>,
        changes: RemoteChanges,
        progress: Boolean,
    ): Library {
        var result = library
        val byRemoteId = rows.entries.associate { (key, entry) -> entry.remoteId to key }
        for (remote in changes.entries) {
            val key = byRemoteId[remote.remoteId] ?: ItemKeys.of(remote.url) ?: continue
            val base = rows[key]
            val item = result.item(key)
            val local = item?.takeIf { it.readingList != null }?.let { entryOf(it, remote.remoteId) }
            val merged: RemoteEntry? =
                when {
                    local != null -> merge(base, local, remote, progress)
                    // Taken off the reading list here and not sent yet: it stays off, unless the service changed it since.
                    base != null && sameCore(base, remote, progress) -> null
                    else -> remote
                }
            if (merged != null) result = put(result, key, item, merged, remote)
            rows[key] = remote.copy(highlights = base?.highlights.orEmpty())
        }
        for (gone in changes.deleted) {
            val key = byRemoteId[gone] ?: continue
            val base = rows.remove(key) ?: continue
            val item = result.item(key) ?: continue
            if (item.readingList == null) continue
            // Changed here since: the edit stays (and is saved again); otherwise it leaves the reading list.
            if (!sameCore(base, entryOf(item, base.remoteId), progress)) continue
            result = result.putItem(if (item.bookmark != null) item.copy(readingList = null) else item.copy(readingList = null, liked = false, tags = emptyList()))
        }
        return result
    }

    /** Three-way merge of the core: a change on one side wins; both changed, Reader's. */
    fun merge(
        base: RemoteEntry?,
        local: RemoteEntry,
        remote: RemoteEntry,
        progress: Boolean,
    ): RemoteEntry {
        fun <T> pick(
            b: T?,
            l: T,
            r: T,
        ): T = if (l == b) r else l
        return local.copy(
            title = pick(base?.title, local.title, remote.title),
            archived = pick(base?.archived, local.archived, remote.archived),
            liked = pick(base?.liked, local.liked, remote.liked),
            tags = SyncRows.mergeTags(base?.tags.orEmpty(), local.tags, remote.tags),
            progress = if (progress) pick(base?.progress, local.progress, remote.progress) else local.progress,
        )
    }

    private fun put(
        library: Library,
        key: String,
        item: Item?,
        entry: RemoteEntry,
        remote: RemoteEntry,
    ): Library {
        val base = item ?: library.newItem(remote.url, remote.title, remote.addedAt, remote.description, remote.imageUrl)?.copy(key = key) ?: return library
        return library.putItem(
            base.copy(
                title = entry.title.ifBlank { base.title },
                description = base.description.ifBlank { remote.description },
                imageUrl = base.imageUrl ?: remote.imageUrl,
                readingList = ReadingListEntry(if (entry.archived) ReadingState.Archived else ReadingState.ToRead, base.readingList?.addedAt ?: remote.addedAt),
                liked = entry.liked,
                tags = cleanTags(entry.tags),
                progress = entry.progress.coerceIn(0f, 1f),
            ),
        )
    }
}

/** A service entry as stored text and back (the service_rows table). Plain Kotlin, unit tested. */
object ServiceRows {
    fun encode(e: RemoteEntry): String =
        Json.write(
            linkedMapOf(
                "id" to e.remoteId,
                "url" to e.url,
                "title" to e.title,
                "addedAt" to e.addedAt,
                "archived" to e.archived,
                "liked" to e.liked,
                "tags" to e.tags,
                "progress" to Math.round(e.progress * 1000).toLong(),
                "extra" to e.extra,
                "highlights" to
                    e.highlights.mapValues { (_, link) ->
                        linkedMapOf<String, Any?>("id" to link.remoteId, "note" to link.note, "refusedAt" to link.refusedAt)
                    },
            ),
        )

    fun decode(text: String): RemoteEntry? {
        val m = runCatching { Json.parse(text) }.getOrNull() as? Map<*, *> ?: return null
        return RemoteEntry(
            remoteId = m["id"] as? String ?: return null,
            url = m["url"] as? String ?: return null,
            title = m["title"] as? String ?: "",
            addedAt = (m["addedAt"] as? Number)?.toLong() ?: 0L,
            archived = m["archived"] == true,
            liked = m["liked"] == true,
            tags = (m["tags"] as? List<*>).orEmpty().filterIsInstance<String>(),
            progress = (((m["progress"] as? Number)?.toDouble() ?: 0.0) / 1000).toFloat(),
            extra = m["extra"] as? String,
            highlights =
                (m["highlights"] as? Map<*, *>).orEmpty().entries.mapNotNull { (k, v) ->
                    val link = v as? Map<*, *> ?: return@mapNotNull null
                    (k as? String ?: return@mapNotNull null) to
                        HighlightLink(link["id"] as? String, link["note"] as? String ?: "", (link["refusedAt"] as? Number)?.toLong())
                }.toMap(),
        )
    }
}
