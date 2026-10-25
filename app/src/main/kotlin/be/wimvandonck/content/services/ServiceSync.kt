package be.wimvandonck.content.services

import be.wimvandonck.content.data.Item
import be.wimvandonck.content.data.ItemKeys
import be.wimvandonck.content.data.Library
import be.wimvandonck.content.data.ReadingListEntry
import be.wimvandonck.content.data.ReadingState
import be.wimvandonck.content.data.Saved
import be.wimvandonck.content.data.changeItem
import be.wimvandonck.content.data.cleanTags
import be.wimvandonck.content.data.item
import be.wimvandonck.content.data.newItem
import be.wimvandonck.content.data.putItem
import be.wimvandonck.content.sync.Json
import be.wimvandonck.content.sync.SyncRows
import kotlin.math.abs

/**
 * What this device last saw at a service: the service's cursor, each article as the service had it
 * by item key (what was last seen or sent: the base for merging, with its id there and whether it is
 * in step with Reader), and where each folder came from there ([ReadingListService.origins]).
 */
data class ServiceState(
    val cursor: String? = null,
    val rows: Map<String, RemoteEntry> = emptyMap(),
    val origins: Map<String, String> = emptyMap(),
    /** What became of each saved article's highlights there (#164), by item key and highlight id. */
    val highlights: Map<String, Map<String, HighlightLink>> = emptyMap(),
)

/** How a service sync ended: the state to keep, and why it stopped early (null when it got through). */
data class ServiceSyncResult(
    val state: ServiceState,
    val problem: ServiceProblem?,
    /** How many articles an import brought in. */
    val imported: Int = 0,
)

/**
 * Keeps Saved and a connected service in step (#109, #145): everything in Saved, both ways. Reader is
 * the library; a service is somewhere it is also kept. Per field: what changed here is sent, what
 * changed there comes in, and when the same field changed on both sides between two rounds the later
 * change wins. A value is never sent just because the two sides differ, and never sent back right
 * after it came in, because each row is what was last seen or sent there. One removed from Reader is
 * removed there. Connecting starts with [import], once. A failed call keeps Reader's data as it is and is tried again on the next
 * sync. Plain Kotlin, tested with a fake service.
 */
object ServiceSync {
    /** How recently an item must have been saved for its text to go with it (#247). */
    const val CONTENT_DAYS = 7L

    private const val DAY_MS = 24 * 60 * 60 * 1000L

    suspend fun run(
        service: ReadingListService,
        state: ServiceState,
        /** False for the quick sync after a change here (see [ReadingListService.changes]). */
        full: Boolean = true,
        /** When each item last changed here, by key, for "the later change wins". */
        editedAt: Map<String, Long> = emptyMap(),
        /** Items other connected services keep in step, by key: a deletion in this one leaves them in Saved. */
        elsewhere: Set<String> = emptySet(),
        /**
         * The text Content shows for a saved item (#247), sent with it when it is new there, or null
         * (then the service fetches the page itself). Only asked for items saved in the last
         * [CONTENT_DAYS] days, so connecting a service doesn't load every article at once.
         */
        textOf: suspend (Item) -> String? = { null },
        now: Long = System.currentTimeMillis(),
        update: suspend ((Library) -> Library) -> Library,
    ): ServiceSyncResult {
        val rows = HashMap(state.rows)
        var cursor = state.cursor
        val progress = ServiceFeature.Progress in service.features
        val notes = ServiceFeature.Notes in service.features
        val content = ServiceFeature.Content in service.features
        if (service.origins.isEmpty()) service.origins = state.origins
        fun result(problem: ServiceProblem?) = ServiceSyncResult(ServiceState(cursor, rows, service.origins.ifEmpty { state.origins }, state.highlights), problem)
        try {
            val changes = service.changes(cursor, full)
            // A first look without an import record here (another device imported): what isn't in
            // Reader yet stays there, as it would after that import.
            val adopt = cursor != null
            update { library -> takeIn(library, rows, changes, progress, adopt, editedAt, elsewhere) }
            cursor = changes.cursor
        } catch (e: ServiceException) {
            return result(e.problem)
        }
        try {
            var library = update { it }
            for (item in library.items) {
                if (item.readingList == null) continue
                val have = rows[item.key]
                when {
                    have == null -> {
                        val recent = now - item.readingList.addedAt <= CONTENT_DAYS * DAY_MS
                        val text = if (content && recent) runCatching { textOf(item) }.getOrNull() else null
                        var added = service.add(item.url, item.title, item.tags, item.archived, Saved.folderOf(item), if (notes) item.note else "", text)
                        var target = entryOf(item, added.remoteId, notes)
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
                        rows[item.key] = added.copy(linked = true)
                    }
                    else -> {
                        // In step (again): only what differs from what was last seen or sent goes.
                        val want = entryOf(item, have.remoteId, notes)
                        rows[item.key] = (if (sameCore(have, want, progress)) have else service.update(have, want)).copy(linked = true)
                    }
                }
            }
            library = update { it }
            val gone = rows.filter { (key, row) -> row.linked && library.item(key)?.readingList == null }.keys
            // A safety net: a library that lost most of its articles at once is more likely damaged
            // than the user's wish, so nothing is removed at the service then.
            val linked = rows.values.count { it.linked }
            if (gone.size > MAX_SAFE_REMOVALS && gone.size * 2 > linked) return result(ServiceProblem.Refused)
            for (key in gone) {
                service.remove(rows.getValue(key).remoteId)
                rows.remove(key)
            }
        } catch (e: ServiceException) {
            return result(e.problem)
        }
        return result(null)
    }

    /**
     * Brings in what the service has, once, when it is connected (#145): everything when [everything],
     * else nothing. Each article arrives where it belongs, as it is (archived in the Archive, liked,
     * its tags and note); one Reader has already keeps its own values and gains the service's tags.
     * What isn't imported is remembered as seen, so it stays there until it changes. Afterwards
     * [run] keeps everything in step.
     */
    suspend fun import(
        service: ReadingListService,
        state: ServiceState,
        everything: Boolean,
        update: suspend ((Library) -> Library) -> Library,
    ): ServiceSyncResult {
        val rows = HashMap(state.rows)
        val progress = ServiceFeature.Progress in service.features
        if (service.origins.isEmpty()) service.origins = state.origins
        var count = 0
        try {
            val changes = service.changes(null, full = true)
            update { library ->
                var result = library
                val loose = looseIndex(library, rows)
                for (remote in changes.entries) {
                    val key = keyFor(result, rows, loose, remote.url) ?: continue
                    if (!everything) {
                        if (rows[key]?.linked != true) rows[key] = remote.copy(linked = false)
                        continue
                    }
                    val item = result.item(key)
                    val local = item?.takeIf { it.readingList != null }?.let { entryOf(it, remote.remoteId, remote.note != null) }
                    result = put(result, key, item, if (local != null) merge(null, local, remote, progress) else remote, remote)
                    rows[key] = remote.copy(linked = true)
                    count++
                }
                result
            }
            return ServiceSyncResult(ServiceState(changes.cursor, rows, service.origins, state.highlights), null, count)
        } catch (e: ServiceException) {
            return ServiceSyncResult(state, e.problem)
        }
    }

    /**
     * The state with its keys as the items have them since #253 ([ItemKeys.clean]). A row already
     * under that key keeps it; a second row for the same article (the service has it twice) stays
     * under its old key, which no item has any more, so the next [run] removes that copy there like
     * any article taken out of Saved.
     */
    fun rekey(state: ServiceState): ServiceState {
        if (state.rows.keys.all { ItemKeys.clean(it) == it } && state.highlights.keys.all { ItemKeys.clean(it) == it }) return state
        fun <T> moved(map: Map<String, T>): Map<String, T> {
            val out = LinkedHashMap<String, T>()
            for ((key, value) in map) if (ItemKeys.clean(key) == key) out[key] = value
            for ((key, value) in map) {
                val clean = ItemKeys.clean(key)
                if (clean != key) out[if (clean in out) key else clean] = value
            }
            return out
        }
        return state.copy(rows = moved(state.rows), highlights = moved(state.highlights))
    }

    /** A saved item as a service entry (the core only; its note when the service keeps notes). */
    fun entryOf(
        item: Item,
        remoteId: String,
        notes: Boolean = false,
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
            folder = Saved.folderOf(item),
            note = item.note.takeIf { notes },
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
            a.folder.equals(b.folder, ignoreCase = true) &&
            (a.note == null || b.note == null || a.note == b.note) &&
            sameTags(a.tags, b.tags) &&
            (!progress || abs(a.progress - b.progress) < PROGRESS_STEP)
    }

    private fun sameTags(
        a: List<String>,
        b: List<String>,
    ): Boolean = a.mapTo(HashSet()) { it.lowercase() } == b.mapTo(HashSet()) { it.lowercase() }

    /** Removing more than this many articles at once, and more than half, is held back (see [run]). */
    const val MAX_SAFE_REMOVALS = 20

    /**
     * Saved items not yet in step with this service, by [ItemKeys.loose] link: where an article
     * the service has but this device hasn't linked can belong. Another device may have sent it,
     * and the service may keep its link without tracking parameters; matching only the exact link
     * then made a second item (a duplicate here, not at the service).
     */
    fun looseIndex(
        library: Library,
        rows: Map<String, RemoteEntry>,
    ): Map<String, String> {
        val index = HashMap<String, String>()
        for (item in library.items) {
            if (item.readingList == null || rows[item.key]?.linked == true) continue
            val loose = ItemKeys.loose(item.url) ?: continue
            index.putIfAbsent(loose, item.key)
        }
        return index
    }

    /** The item a service's link belongs to: the same link, else a saved item from [loose] not yet linked, else a new one. */
    fun keyFor(
        library: Library,
        rows: Map<String, RemoteEntry>,
        loose: Map<String, String>,
        url: String,
    ): String? {
        val exact = ItemKeys.of(url) ?: return null
        if (library.item(exact) != null || rows.containsKey(exact)) return exact
        val match = ItemKeys.loose(url)?.let { loose[it] } ?: return exact
        return match.takeIf { rows[it]?.linked != true } ?: exact
    }

    /** Progress changes smaller than this aren't worth a call. */
    private const val PROGRESS_STEP = 0.01f

    /**
     * The library after taking in what changed at the service; [rows] then holds the service's
     * articles as seen now. Only what changed there comes in; a new article there is taken in when
     * [adopt].
     */
    fun takeIn(
        library: Library,
        rows: MutableMap<String, RemoteEntry>,
        changes: RemoteChanges,
        progress: Boolean,
        adopt: Boolean = true,
        editedAt: Map<String, Long> = emptyMap(),
        elsewhere: Set<String> = emptySet(),
    ): Library {
        var result = library
        val byRemoteId = rows.entries.associate { (key, entry) -> entry.remoteId to key }
        val loose = looseIndex(library, rows)
        // A complete list says what was deleted there: what it had before and hasn't now.
        val listed = changes.entries.mapTo(HashSet()) { it.remoteId }
        val deleted = if (changes.complete) changes.deleted + rows.values.map { it.remoteId }.filter { it !in listed } else changes.deleted
        for (remote in changes.entries) {
            val key = byRemoteId[remote.remoteId] ?: keyFor(result, rows, loose, remote.url) ?: continue
            val base = rows[key]
            val item = result.item(key)
            val saved = item?.takeIf { it.readingList != null }
            val linked = base?.linked == true
            val changedThere = base == null || !sameCore(base, remote, progress)
            var link = linked
            when {
                // In step: per field, what changed there comes in.
                saved != null && linked -> {
                    val local = entryOf(saved, remote.remoteId, remote.note != null)
                    val remoteLater = remote.changedAt > 0 && remote.changedAt > (editedAt[key] ?: 0L)
                    result = put(result, key, saved, merge(base, local, remote, progress, remoteLater), remote)
                }
                // The same link in both, not in step yet: it joins.
                saved != null -> {
                    result = put(result, key, saved, merge(null, entryOf(saved, remote.remoteId, remote.note != null), remote, progress), remote)
                    link = true
                }
                // Taken out of Saved here and not sent yet: it stays out, unless it changed there since.
                linked -> if (changedThere) result = put(result, key, item, remote, remote) else Unit
                // Seen there before but not in step (not imported): only a change there brings it in.
                base != null -> {
                    if (changedThere) {
                        result = put(result, key, item, remote, remote)
                        link = true
                    }
                }
                // New there.
                else -> {
                    if (adopt) {
                        result = put(result, key, item, remote, remote)
                        link = true
                    }
                }
            }
            rows[key] = remote.copy(linked = link)
        }
        for (gone in deleted.distinct()) {
            val key = byRemoteId[gone] ?: continue
            val base = rows.remove(key) ?: continue
            if (!base.linked) continue
            val item = result.item(key) ?: continue
            if (item.readingList == null) continue
            // Changed here since: the edit stays (and is saved again); otherwise it leaves Saved.
            if (!sameCore(base, entryOf(item, base.remoteId, base.note != null), progress)) continue
            // Another service keeps it in step: it stays in Saved, and nothing is deleted there.
            if (key in elsewhere) continue
            result = result.putItem(if (item.bookmark != null) item.copy(readingList = null) else item.copy(readingList = null, liked = false, tags = emptyList()))
        }
        return result
    }

    /**
     * Three-way merge of the core: a field changed on one side takes that side's value; changed on
     * both, the later change wins ([remoteLater]: the service's; otherwise Reader's). Tags as a set.
     */
    fun merge(
        base: RemoteEntry?,
        local: RemoteEntry,
        remote: RemoteEntry,
        progress: Boolean,
        remoteLater: Boolean = false,
    ): RemoteEntry {
        fun <T> pick(
            b: T?,
            l: T,
            r: T,
        ): T =
            when {
                l == b -> r
                r == b || b == null -> l
                remoteLater -> r
                else -> l
            }
        val folder =
            when {
                local.folder.equals(base?.folder, ignoreCase = true) -> remote.folder
                remote.folder.equals(base?.folder, ignoreCase = true) || base == null -> local.folder
                remoteLater -> remote.folder
                else -> local.folder
            }
        return local.copy(
            title = pick(base?.title, local.title, remote.title),
            archived = pick(base?.archived, local.archived, remote.archived),
            liked = pick(base?.liked, local.liked, remote.liked),
            tags = SyncRows.mergeTags(base?.tags.orEmpty(), local.tags, remote.tags),
            folder = folder,
            note = if (local.note == null || remote.note == null) local.note else pick(base?.note, local.note, remote.note),
            progress = if (progress) pick(base?.progress, local.progress, remote.progress) else local.progress,
        ).let { if (it.archived) it.copy(folder = null) else it }
    }

    private fun put(
        library: Library,
        key: String,
        item: Item?,
        entry: RemoteEntry,
        remote: RemoteEntry,
    ): Library {
        val base = item ?: library.newItem(remote.url, remote.title, remote.addedAt, remote.description, remote.imageUrl)?.copy(key = key) ?: return library
        val updated =
            base.copy(
                title = entry.title.ifBlank { base.title },
                description = base.description.ifBlank { remote.description },
                imageUrl = base.imageUrl ?: remote.imageUrl,
                readingList = ReadingListEntry(if (entry.archived) ReadingState.Archived else ReadingState.ToRead, base.readingList?.addedAt ?: remote.addedAt),
                liked = entry.liked,
                tags = cleanTags(entry.tags),
                folder = entry.folder.takeIf { !entry.archived },
                note = entry.note ?: base.note,
                progress = entry.progress.coerceIn(0f, 1f),
            )
        return if (updated == item) library else library.putItem(updated)
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
                "folder" to e.folder,
                "note" to e.note,
                "extra" to e.extra,
                "changedAt" to e.changedAt,
                "linked" to e.linked,
            ),
        )

    /** Where each folder came from at the service ([ServiceState.origins]), as stored. */
    fun encodeOrigins(origins: Map<String, String>): String = Json.write(origins)

    fun decodeOrigins(text: String?): Map<String, String> {
        val m = text?.takeIf { it.isNotEmpty() }?.let { runCatching { Json.parse(it) }.getOrNull() } as? Map<*, *> ?: return emptyMap()
        return m.entries.mapNotNull { (k, v) -> (k as? String)?.let { name -> (v as? String)?.let { name to it } } }.toMap()
    }

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
            folder = (m["folder"] as? String)?.ifEmpty { null },
            note = m["note"] as? String,
            extra = m["extra"] as? String,
            changedAt = (m["changedAt"] as? Number)?.toLong() ?: 0L,
            linked = m["linked"] != false,
        )
    }
}
