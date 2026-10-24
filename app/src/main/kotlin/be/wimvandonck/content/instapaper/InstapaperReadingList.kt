package be.wimvandonck.content.instapaper

import be.wimvandonck.content.services.HighlightService
import be.wimvandonck.content.services.ReadingListService
import be.wimvandonck.content.services.RemoteHighlight
import be.wimvandonck.content.services.RemoteChanges
import be.wimvandonck.content.services.RemoteEntry
import be.wimvandonck.content.services.ServiceException
import be.wimvandonck.content.services.ServiceFeature
import be.wimvandonck.content.services.ServiceProblem

/**
 * Instapaper as Saved's service (#107, #109, #119), on API v2: `since` keeps a full copy of the
 * account on the device. Home is To read and the archive is the archive. Folders became tags (#187):
 * Content neither reads nor changes Instapaper's folders; an article in one there is in To read here. Only this class
 * knows Instapaper's ways; ServiceSync sees the core.
 */
class InstapaperReadingList(
    private val client: InstapaperClient,
    /** The time now, in milliseconds. */
    private val now: () -> Long = System::currentTimeMillis,
) : ReadingListService,
    HighlightService {
    override val id = ID

    override val features = setOf(ServiceFeature.Progress, ServiceFeature.Highlights, ServiceFeature.Content)

    /** The account's folders by id, read with the changes. */
    private var folders: Map<Long, String> = emptyMap()

    /** The account's tags by name (lower case), to remove one by id. */
    private var tagIds: Map<String, Long> = emptyMap()

    override suspend fun changes(
        cursor: String?,
        full: Boolean,
    ): RemoteChanges =
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
            // Folders became tags (#187): Content reads no Instapaper folder and leaves them alone.
            folder = null,
            progress = b.progress,
            changedAt = b.progressAt * 1000,
        )
    }

    override suspend fun add(
        url: String,
        title: String,
        tags: List<String>,
        archived: Boolean,
        folder: String?,
        note: String,
        content: String?,
    ): RemoteEntry =
        guarded {
            val named = title.takeIf { it.isNotBlank() && it != url }
            // With Content's text (#247), so Instapaper shows the same article; should Instapaper
            // refuse the text (too large, say), the link alone, as before.
            var saved =
                content?.let {
                    try {
                        client.save(url, named, it)
                    } catch (e: InstapaperException) {
                        if (e.problem != InstapaperProblem.Invalid && e.problem != InstapaperProblem.ServerError) throw e
                        null
                    }
                } ?: client.save(url, named) ?: throw ServiceException(ServiceProblem.Refused)
            if (tags.isNotEmpty()) saved = saved.copy(tags = client.setTags(saved.id, tags, emptyList()))
            if (archived && !saved.archived) {
                client.move(saved.id, "archive")
                saved = saved.copy(archived = true, folderId = null)
            }
            entryOf(saved) ?: throw ServiceException(ServiceProblem.Refused)
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
            // The archive or home (To read). Folders became tags (#187): an article in a folder there stays in it.
            when {
                want.archived -> if (!have.archived) client.move(id, "archive")
                have.archived -> client.move(id, "home")
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
            want.copy(remoteId = have.remoteId, addedAt = have.addedAt, folder = null)
        }

    override suspend fun folders(): List<String> = guarded { client.folders().map { it.title }.sortedBy { it.lowercase() } }

    /** Instapaper's folders are flat: a name is enough to find one again. */
    override var origins: Map<String, String> = emptyMap()

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

    override suspend fun highlights(remoteId: String): List<RemoteHighlight> =
        guarded {
            val id = remoteId.toLongOrNull() ?: throw ServiceException(ServiceProblem.Refused)
            client.highlights(id).map { RemoteHighlight(it.id.toString(), it.text, it.note, it.position, it.time * 1000) }
        }

    override suspend fun addHighlight(
        remoteId: String,
        text: String,
        note: String,
        position: Int,
    ): RemoteHighlight =
        guarded {
            val id = remoteId.toLongOrNull() ?: throw ServiceException(ServiceProblem.Refused)
            val made =
                try {
                    client.addHighlight(id, text, note, position)
                } catch (e: InstapaperException) {
                    // Instapaper answers 403 when a free account used its highlights for the month.
                    if (e.problem == InstapaperProblem.NotApproved) throw ServiceException(ServiceProblem.LimitReached, e)
                    throw e
                }
            RemoteHighlight(made.id.toString(), made.text, made.note)
        }

    override suspend fun removeHighlight(highlightId: String) =
        guarded {
            val id = highlightId.toLongOrNull() ?: return@guarded
            try {
                client.deleteHighlight(id)
            } catch (e: InstapaperException) {
                // Already gone at Instapaper: that is what was wanted.
                if (e.problem != InstapaperProblem.Invalid) throw e
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
