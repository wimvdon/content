package be.wimvandonck.reader.raindrop

import be.wimvandonck.reader.services.ReadingListService
import be.wimvandonck.reader.services.RemoteChanges
import be.wimvandonck.reader.services.RemoteEntry
import be.wimvandonck.reader.services.ServiceException
import be.wimvandonck.reader.services.ServiceFeature
import be.wimvandonck.reader.services.ServiceProblem

/**
 * Raindrop.io as Saved's service (#112, #119): Unsorted is To read, collections are folders (nested
 * ones flattened to "Parent / Child"), an "Archive" collection, made on first use, is the archive,
 * and Raindrop's favorite is Liked. Notes sync too. Raindrop has no "changes since", so a sync lists
 * everything (50 a page), at most every few minutes unless it is a full sync; what is gone from the
 * list was deleted there. Only this class knows Raindrop's ways; ServiceSync sees the core.
 */
class RaindropSaved(
    private val client: RaindropClient,
    /** The time now, in milliseconds. */
    private val now: () -> Long = System::currentTimeMillis,
) : ReadingListService {
    override val id = ID

    override val features = setOf(ServiceFeature.Notes)

    /** Each collection's folder name, by id ("Parent / Child" for nested ones). */
    private var folders: Map<Long, String> = emptyMap()

    private var archiveId: Long? = null

    /** When everything was last listed. */
    private var listedAt = 0L

    override suspend fun changes(
        cursor: String?,
        full: Boolean,
    ): RemoteChanges {
        // The quick sync after a change here doesn't list everything again each time.
        if (!full && listedAt > 0 && now() - listedAt < MIN_LIST_INTERVAL_MS) return RemoteChanges(emptyList(), emptyList(), cursor.orEmpty())
        readCollections()
        val entries = ArrayList<RemoteEntry>()
        var page = 0
        while (page < MAX_PAGES) {
            val items = client.raindrops(page)
            items.mapTo(entries, ::entryOf)
            if (items.size < RaindropClient.PAGE) break
            page++
        }
        listedAt = now()
        return RemoteChanges(entries, emptyList(), listedAt.toString(), complete = page < MAX_PAGES)
    }

    private suspend fun readCollections() {
        val all = client.collections() + client.childCollections()
        val byId = all.associateBy { it.id }
        fun path(c: RaindropCollection, depth: Int = 0): String {
            val parent = c.parentId?.let { byId[it] }
            return if (parent == null || depth > MAX_DEPTH) c.title else path(parent, depth + 1) + " / " + c.title
        }
        folders = all.associate { it.id to path(it) }
        archiveId = all.firstOrNull { it.parentId == null && it.title.equals(ARCHIVE, ignoreCase = true) }?.id
    }

    /** A raindrop as an entry: its place (To read, a folder, the archive), favorite, tags and note. */
    fun entryOf(r: Raindrop): RemoteEntry {
        val archived = archiveId != null && r.collectionId == archiveId
        return RemoteEntry(
            remoteId = r.id.toString(),
            url = r.link,
            title = r.title.ifBlank { r.link },
            description = r.excerpt,
            imageUrl = r.cover,
            addedAt = r.created,
            archived = archived,
            liked = r.important,
            tags = r.tags,
            folder = if (archived || r.collectionId < 0) null else folders[r.collectionId],
            note = r.note,
            changedAt = r.lastUpdate,
        )
    }

    override suspend fun add(
        url: String,
        title: String,
        tags: List<String>,
        archived: Boolean,
        folder: String?,
        note: String,
    ): RemoteEntry {
        val fields =
            buildMap<String, Any?> {
                put("link", url)
                if (title.isNotBlank() && title != url) put("title", title)
                if (tags.isNotEmpty()) put("tags", tags)
                if (note.isNotBlank()) put("note", note)
                put("collection", mapOf("\$id" to collectionFor(archived, folder)))
                // Raindrop fills in the cover and description itself.
                put("pleaseParse", emptyMap<String, Any?>())
            }
        return entryOf(client.create(fields))
    }

    override suspend fun update(
        have: RemoteEntry,
        want: RemoteEntry,
    ): RemoteEntry {
        val id = have.remoteId.toLongOrNull() ?: throw ServiceException(ServiceProblem.Refused)
        val fields =
            buildMap<String, Any?> {
                if (want.title != have.title && want.title.isNotBlank()) put("title", want.title)
                if (want.liked != have.liked) put("important", want.liked)
                if (want.tags.map { it.lowercase() }.toSet() != have.tags.map { it.lowercase() }.toSet()) put("tags", want.tags)
                if (want.note != null && want.note != have.note) put("note", want.note)
                if (want.archived != have.archived || !want.folder.equals(have.folder, ignoreCase = true)) {
                    put("collection", mapOf("\$id" to collectionFor(want.archived, want.folder)))
                }
            }
        if (fields.isEmpty()) return want.copy(remoteId = have.remoteId)
        return entryOf(client.update(id, fields))
    }

    override suspend fun remove(remoteId: String) {
        val id = remoteId.toLongOrNull() ?: return
        client.delete(id)
    }

    override suspend fun folders(): List<String> {
        readCollections()
        return folders.filterKeys { it != archiveId }.values.sortedBy { it.lowercase() }
    }

    /**
     * Each folder name's collection (#145), so a nested collection ("Research / Media") is found
     * again before Raindrop is listed in this session, instead of a new top-level one being made.
     */
    override var origins: Map<String, String>
        get() = folders.entries.associate { (id, name) -> name to id.toString() }
        set(value) {
            if (folders.isEmpty()) folders = value.entries.mapNotNull { (name, id) -> id.toLongOrNull()?.let { it to name } }.toMap()
        }


    /** Where an item goes: the Archive collection (made when missing), a folder's collection (made when missing), or Unsorted. */
    private suspend fun collectionFor(
        archived: Boolean,
        folder: String?,
    ): Long {
        if (archived) {
            archiveId?.let { return it }
            val made = client.createCollection(ARCHIVE)
            archiveId = made.id
            folders = folders + (made.id to made.title)
            return made.id
        }
        val name = folder?.trim()?.ifEmpty { null } ?: return UNSORTED
        folders.entries.firstOrNull { it.value.equals(name, ignoreCase = true) }?.let { return it.key }
        val made = client.createCollection(name)
        folders = folders + (made.id to made.title)
        return made.id
    }

    companion object {
        const val ID = "raindrop"

        /** Raindrop's Unsorted: Saved's To read. */
        const val UNSORTED = -1L

        /** The collection that is Saved's archive. */
        const val ARCHIVE = "Archive"

        /** The quick sync after a change lists everything at most this often. */
        const val MIN_LIST_INTERVAL_MS = 3 * 60 * 1000L

        /** 5000 raindrops; a bigger account is read this far, and nothing is taken as deleted then. */
        const val MAX_PAGES = 100

        private const val MAX_DEPTH = 10
    }
}
