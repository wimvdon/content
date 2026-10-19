package be.wimvandonck.reader.instapaper

import be.wimvandonck.reader.services.ReadingListService
import be.wimvandonck.reader.services.RemoteChanges
import be.wimvandonck.reader.services.RemoteEntry
import be.wimvandonck.reader.services.ServiceException
import be.wimvandonck.reader.services.ServiceFeature
import be.wimvandonck.reader.services.ServiceProblem

/**
 * Instapaper as Saved's service (#107, #109, #119), on API v2: `since` keeps a full copy of the
 * account on the device. Home is To read, Instapaper's folders are Saved's folders (a folder Reader
 * files into is made there when it doesn't exist yet), the archive is the archive. Only this class
 * knows Instapaper's ways; ServiceSync sees the core.
 */
class InstapaperReadingList(
    private val client: InstapaperClient,
    /** The time now, in milliseconds. */
    private val now: () -> Long = System::currentTimeMillis,
) : ReadingListService {
    override val id = ID

    override val features = setOf(ServiceFeature.Text, ServiceFeature.Progress)

    /** The account's folders by id, read with the changes. */
    private var folders: Map<Long, String> = emptyMap()

    /** The account's tags by name (lower case), to remove one by id. */
    private var tagIds: Map<String, Long> = emptyMap()

    /** Instapaper refused its parsed text for this account (it isn't the developer's own): don't ask again. */
    @Volatile
    private var textRefused = false

    override suspend fun changes(cursor: String?): RemoteChanges =
        guarded {
            // Recorded before asking, a little early, so nothing changed meanwhile is missed (taking a change twice is harmless).
            val asked = now() / 1000 - CURSOR_MARGIN_S
            folders = client.folders().associate { it.id to it.title }
            tagIds = client.tags().associate { it.name.lowercase() to it.id }
            val since = cursor?.toLongOrNull() ?: 1L
            val entries = ArrayList<RemoteEntry>()
            val deleted = ArrayList<String>()
            var offset = 0
            while (true) {
                val page = client.changesSince(since, offset, PAGE)
                page.bookmarks.mapNotNullTo(entries, ::entryOf)
                page.deletedIds.mapTo(deleted) { it.toString() }
                offset += page.count
                if (page.count < PAGE) break
            }
            RemoteChanges(entries, deleted, asked.coerceAtLeast(1).toString())
        }

    /** A bookmark as an entry, with its folder. Articles without a web address are left out. */
    fun entryOf(b: InstapaperBookmark): RemoteEntry? {
        val url = b.link ?: return null
        val folder = b.folderId?.let { folders[it] }?.takeIf { !b.archived }
        val tags = b.tags.map { it.name }
        return RemoteEntry(
            remoteId = b.id.toString(),
            url = url,
            title = b.title.ifBlank { url },
            description = b.description,
            imageUrl = b.imageUrl,
            addedAt = b.time * 1000,
            archived = b.archived,
            liked = b.liked,
            tags = tags,
            folder = folder,
            progress = b.progress,
        )
    }

    override suspend fun add(
        url: String,
        title: String,
        tags: List<String>,
        archived: Boolean,
        folder: String?,
    ): RemoteEntry =
        guarded {
            var saved = client.save(url, title.takeIf { it.isNotBlank() && it != url }) ?: throw ServiceException(ServiceProblem.Refused)
            if (tags.isNotEmpty()) saved = saved.copy(tags = client.setTags(saved.id, tags, emptyList()))
            if (archived && !saved.archived) {
                client.move(saved.id, "archive")
                saved = saved.copy(archived = true, folderId = null)
            } else if (!archived && folder != null) {
                val id = folderId(folder)
                if (saved.folderId != id) client.move(saved.id, id.toString())
                saved = saved.copy(folderId = id)
            }
            entryOf(saved) ?: throw ServiceException(ServiceProblem.Refused)
        }

    /** The id of the folder called [title] (case ignored), made at Instapaper when there is none. */
    private suspend fun folderId(title: String): Long {
        folders.entries.firstOrNull { it.value.equals(title, ignoreCase = true) }?.let { return it.key }
        val made = client.createFolder(title)
        folders = folders + (made.id to made.title)
        return made.id
    }

    override suspend fun update(
        have: RemoteEntry,
        want: RemoteEntry,
    ): RemoteEntry =
        guarded {
            val id = have.remoteId.toLongOrNull() ?: throw ServiceException(ServiceProblem.Refused)
            fun List<String>.has(tag: String) = any { it.equals(tag, ignoreCase = true) }
            if (want.liked != have.liked) client.like(id, want.liked)
            val removed = have.tags.filter { !want.tags.has(it) }
            val added = want.tags.filter { !have.tags.has(it) }
            val removeIds = removed.mapNotNull { tagIds[it.lowercase()] }
            if (added.isNotEmpty() || removeIds.isNotEmpty()) client.setTags(id, added, removeIds)
            // One place: the archive, a folder, or home (To read).
            val folderChanged = !want.folder.equals(have.folder, ignoreCase = true)
            when {
                want.archived -> if (!have.archived) client.move(id, "archive")
                want.folder != null -> if (have.archived || folderChanged) client.move(id, folderId(want.folder).toString())
                have.archived || have.folder != null -> client.move(id, "home")
            }
            val progressChanged = kotlin.math.abs(want.progress - have.progress) >= 0.01f
            if (want.title != have.title || progressChanged) {
                client.updateBookmark(
                    id,
                    title = want.title.takeIf { it != have.title && it.isNotBlank() },
                    progress = want.progress.takeIf { progressChanged },
                    progressAt = now() / 1000,
                )
            }
            want.copy(remoteId = have.remoteId, addedAt = have.addedAt, folder = want.folder.takeIf { !want.archived })
        }

    override suspend fun remove(remoteId: String) =
        guarded {
            val id = remoteId.toLongOrNull() ?: return@guarded
            try {
                client.delete(id)
            } catch (e: InstapaperException) {
                // Already gone at Instapaper: that is what was wanted.
                if (e.problem != InstapaperProblem.Invalid) throw e
            }
        }

    override suspend fun text(remoteId: String): String? {
        if (textRefused) return null
        val id = remoteId.toLongOrNull() ?: return null
        return try {
            client.parse(id)
        } catch (e: InstapaperException) {
            when (e.problem) {
                // Not the developer's own account (Reader has no Instaparser key, #113).
                InstapaperProblem.NotApproved -> textRefused = true
                else -> Unit
            }
            null
        }
    }

    private suspend fun <T> guarded(block: suspend () -> T): T =
        try {
            block()
        } catch (e: InstapaperException) {
            throw ServiceException(problemOf(e.problem), e)
        }

    companion object {
        const val ID = "instapaper"

        /** Instapaper's largest page. */
        const val PAGE = 500

        /** How much earlier than the request the next `since` starts, for clocks that differ. */
        const val CURSOR_MARGIN_S = 300L

        fun problemOf(problem: InstapaperProblem): ServiceProblem =
            when (problem) {
                InstapaperProblem.SignedOut -> ServiceProblem.SignedOut
                InstapaperProblem.RateLimited -> ServiceProblem.RateLimited
                InstapaperProblem.Offline -> ServiceProblem.Offline
                InstapaperProblem.NotApproved, InstapaperProblem.ServerError, InstapaperProblem.Invalid -> ServiceProblem.Refused
            }
    }
}
