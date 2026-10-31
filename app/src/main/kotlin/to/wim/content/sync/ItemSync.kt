package to.wim.content.sync

import to.wim.content.data.Library

/** A row as the account has it: its key, the items version that last changed it, and its data (null: deleted). */
data class ItemRow(
    val key: String,
    val version: Long,
    val data: Map<String, Any?>?,
)

/** One page of the rows changed since a version; ask again from [version] while [more]. */
data class ItemsPage(
    val version: Long,
    val rows: List<ItemRow>,
    val more: Boolean,
)

/** A row this device saves: what it was based on (0 for new) and its new data (null to delete). */
data class ItemChange(
    val key: String,
    val baseVersion: Long,
    val data: Map<String, Any?>?,
)

data class ItemsWritten(
    val version: Long,
    /** The rows saved, with their new version. */
    val saved: Map<String, Long>,
    /** Rows another device changed first: what is there now. */
    val conflicts: List<ItemRow>,
)

/** The account's items (worker/src/account.ts). [AccountClient] is the real one; tests use a fake. */
interface ItemsTarget {
    suspend fun readItems(since: Long): ItemsPage

    suspend fun writeItems(rows: List<ItemChange>): ItemsWritten
}

/**
 * What this device knows of the account's items: the version it synced to and every row as it last
 * synced it (the base for merging). Deleted rows aren't kept.
 */
data class ItemSyncState(
    val version: Long = 0,
    val rows: Map<String, ItemRow> = emptyMap(),
)

/**
 * One per-item sync (#106, docs/sync.md), the same as the web app's syncItems: reads the rows
 * changed since the last sync and merges them into the library ([SyncRows.merge], three ways, with
 * the row as last synced), then saves the rows this device changed. A row another device changed
 * first comes back as a conflict; it is merged the same way and saved again. Plain Kotlin, unit
 * tested.
 */
object ItemSync {
    /** Saves after other devices' changes are tried again this often, then given up until next time. */
    const val MAX_ATTEMPTS = 4

    /** At most this many rows per save (the account's limit). */
    const val BATCH = 500

    /**
     * [update] changes the library in one step (nothing else changes it meanwhile) and returns it.
     * Returns the new state, to keep for the next sync.
     */
    suspend fun run(
        target: ItemsTarget,
        state: ItemSyncState,
        now: Long,
        update: suspend ((Library) -> Library) -> Library,
    ): ItemSyncState {
        val keepSince = now - SyncRows.KEEP_READ_MS
        val base = HashMap(state.rows)
        var version = state.version

        // The other devices' changes first.
        while (true) {
            val page = target.readItems(version)
            if (page.rows.isNotEmpty()) update { library -> takeIn(library, base, page.rows, keepSince) }
            version = page.version
            if (!page.more) break
        }

        // Then this device's changes.
        var attempt = 0
        while (true) {
            var changes: List<ItemChange> = emptyList()
            update { library ->
                changes = changes(library, base, keepSince)
                library
            }
            if (changes.isEmpty()) break
            if (attempt++ >= MAX_ATTEMPTS) throw SyncException(SyncProblem.ServerError)
            for (batch in changes.chunked(BATCH)) {
                val written = target.writeItems(batch)
                for (change in batch) {
                    val saved = written.saved[change.key] ?: continue
                    if (change.data == null) base.remove(change.key) else base[change.key] = ItemRow(change.key, saved, change.data)
                }
                if (written.conflicts.isNotEmpty()) update { library -> takeIn(library, base, written.conflicts, keepSince, keepDeleted = true) }
            }
        }
        return ItemSyncState(version, base)
    }

    /**
     * The library after merging [rows] from the account into it; [base] then holds them as synced.
     * Rows this device already has at that version (its own saves coming back) are skipped. A
     * deleted row is kept in [base] only with [keepDeleted] (a conflict): saving that key again
     * must name the deletion's version.
     */
    fun takeIn(
        library: Library,
        base: MutableMap<String, ItemRow>,
        rows: List<ItemRow>,
        keepSince: Long,
        keepDeleted: Boolean = false,
    ): Library {
        var result = library
        val articles = library.articles.associateBy { it.key }
        // Read marks are applied together at the end: one at a time would be slow with thousands.
        val reads = HashMap<String, Long?>()
        for (row in rows) {
            val known = base[row.key]
            if (known != null && known.version >= row.version) continue
            // Data this version can't read is left alone: neither applied nor taken as the base.
            if (row.data != null && row.key.startsWith(SyncRows.ITEM) && SyncRows.itemOf(row.key.removePrefix(SyncRows.ITEM), row.data, null) == null) continue
            // Taking a row in only changes that row's article, so the articles as they were still answer for the others.
            val local = SyncRows.localRow(result, row.key, keepSince, known?.data, articles)
            val merged = SyncRows.merge(row.key, known?.data, local, row.data)
            if (!SyncRows.same(row.key, merged, local)) {
                if (row.key.startsWith(SyncRows.READ)) reads[row.key.removePrefix(SyncRows.READ)] = SyncRows.readTimeOf(merged) else result = SyncRows.apply(result, row.key, merged, keepSince)
            }
            if (row.data == null && !keepDeleted) base.remove(row.key) else base[row.key] = row
        }
        return SyncRows.applyReads(result, reads, keepSince)
    }

    /** The rows whose data on this device differs from what was last synced, to save. */
    fun changes(
        library: Library,
        base: Map<String, ItemRow>,
        keepSince: Long,
    ): List<ItemChange> {
        val local = SyncRows.localRows(library, keepSince)
        val out = ArrayList<ItemChange>()
        for ((key, data) in local) {
            val known = base[key]
            if (!SyncRows.same(key, data, known?.data)) out.add(ItemChange(key, known?.version ?: 0, data))
        }
        // Rows this device had that are gone here now (unknown kinds stay as they are).
        for ((key, known) in base) {
            if (key in local || !(key.startsWith(SyncRows.ITEM) || key.startsWith(SyncRows.READ))) continue
            if (known.data != null) out.add(ItemChange(key, known.version, null))
        }
        return out
    }
}
