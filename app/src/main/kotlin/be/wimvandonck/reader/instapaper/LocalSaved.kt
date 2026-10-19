package be.wimvandonck.reader.instapaper

import be.wimvandonck.reader.data.ItemKeys
import be.wimvandonck.reader.sync.Json

/** The articles saved on this phone without an Instapaper account, and their tags. */
data class LocalSavedData(
    val bookmarks: List<InstapaperBookmark> = emptyList(),
    val tags: List<InstapaperTag> = emptyList(),
    /** The id the next saved article or tag gets. */
    val nextId: Long = 1,
)

/** What [LocalSaved.uploadTo] did: how many articles it copied, and why it stopped early (null when all are copied). */
data class LocalUpload(
    val copied: Int,
    val problem: InstapaperProblem?,
)

/** Where [LocalSaved] keeps its data: a DataStore file in the app ([LocalSavedStore]), in memory in tests. */
interface LocalSavedStorage {
    suspend fun read(): LocalSavedData

    /** Changes the data in one step and returns it as stored. */
    suspend fun update(change: (LocalSavedData) -> LocalSavedData): LocalSavedData
}

/**
 * Saved articles without an account: the same lists, likes, archive and tags as Instapaper, kept
 * on this phone and not synced; signing in copies them to Instapaper ([uploadTo]). There are no folders (moving to one is refused). Plain Kotlin,
 * unit tested; the Saved screen uses it through [InstapaperModel] like the real account.
 */
class LocalSaved(
    private val storage: LocalSavedStorage,
    /** The time now, in milliseconds since 1970. */
    private val now: () -> Long = System::currentTimeMillis,
) : InstapaperService {
    override suspend fun list(
        view: InstapaperView,
        offset: Int,
        limit: Int,
    ): InstapaperPage {
        val all = storage.read().bookmarks.filter { InstapaperLists.inView(view, it) }.sortedByDescending { it.time }
        return InstapaperPage(all.drop(offset).take(limit), all.size)
    }

    /** Saving an address that is already saved brings it back to the top of Saved, as Instapaper does. */
    override suspend fun save(
        url: String,
        title: String?,
    ): InstapaperBookmark? {
        val time = now() / 1000
        val key = ItemKeys.of(url)
        val stored =
            storage.update { data ->
                // The same link written another way (http, www., a trailing slash) is the same article.
                val existing = data.bookmarks.firstOrNull { it.url == url || (key != null && ItemKeys.of(it.url) == key) }
                if (existing != null) {
                    val again = existing.copy(time = time, archived = false, title = title?.takeIf { it.isNotBlank() } ?: existing.title)
                    data.copy(bookmarks = data.bookmarks.map { if (it.id == existing.id) again else it })
                } else {
                    val new = InstapaperBookmark(data.nextId, url, title?.takeIf { it.isNotBlank() } ?: url, "", time, liked = false, archived = false, folderId = null, tags = emptyList())
                    data.copy(bookmarks = data.bookmarks + new, nextId = data.nextId + 1)
                }
            }
        // As stored: the storage may give it its own id (ItemsSavedStorage).
        return stored.bookmarks.firstOrNull { it.url == url } ?: stored.bookmarks.firstOrNull { key != null && ItemKeys.of(it.url) == key }
    }

    override suspend fun move(
        id: Long,
        section: String,
    ) {
        val archived =
            when (section) {
                "archive" -> true
                "home" -> false
                else -> throw InstapaperException(InstapaperProblem.Invalid)
            }
        change(id) { it.copy(archived = archived, folderId = null) }
    }

    override suspend fun like(
        id: Long,
        liked: Boolean,
    ): Boolean {
        change(id) { it.copy(liked = liked) }
        return liked
    }

    override suspend fun delete(id: Long) {
        storage.update { data -> data.copy(bookmarks = data.bookmarks.filterNot { it.id == id }).withoutUnusedTags() }
    }

    override suspend fun setTags(
        id: Long,
        add: List<String>,
        removeIds: List<Long>,
    ): List<InstapaperTag> {
        val data =
            storage.update { data ->
                val bookmark = data.bookmarks.firstOrNull { it.id == id } ?: throw InstapaperException(InstapaperProblem.Invalid)
                var tags = data.tags
                var nextId = data.nextId
                val added =
                    add.map(String::trim).filter(String::isNotEmpty).map { name ->
                        tags.firstOrNull { it.name.equals(name, ignoreCase = true) }
                            ?: InstapaperTag(nextId++, name).also { tags = tags + it }
                    }
                val kept = bookmark.tags.filterNot { it.id in removeIds }
                val newTags = kept + added.filter { tag -> kept.none { it.id == tag.id } }
                data
                    .copy(bookmarks = data.bookmarks.map { if (it.id == id) it.copy(tags = newTags) else it }, tags = tags, nextId = nextId)
                    .withoutUnusedTags()
            }
        return data.bookmarks.first { it.id == id }.tags
    }

    override suspend fun folders(): List<InstapaperFolder> = emptyList()

    override suspend fun createFolder(title: String): InstapaperFolder = throw InstapaperException(InstapaperProblem.Invalid)

    override suspend fun deleteFolder(id: Long) = throw InstapaperException(InstapaperProblem.Invalid)

    override suspend fun reorderFolders(order: List<Pair<Long, Int>>): List<InstapaperFolder> = throw InstapaperException(InstapaperProblem.Invalid)

    override suspend fun tags(): List<InstapaperTag> = storage.read().tags.sortedBy { it.name.lowercase() }

    /** Renaming to the name of another tag joins the two. */
    override suspend fun renameTag(
        id: Long,
        name: String,
    ): InstapaperTag {
        val clean = name.trim()
        if (clean.isEmpty()) throw InstapaperException(InstapaperProblem.Invalid)
        var result: InstapaperTag? = null
        val stored = storage.update { data ->
            val tag = data.tags.firstOrNull { it.id == id } ?: throw InstapaperException(InstapaperProblem.Invalid)
            val target = data.tags.firstOrNull { it.id != id && it.name.equals(clean, ignoreCase = true) } ?: tag.copy(name = clean)
            result = target
            data.copy(
                tags = data.tags.filterNot { it.id == id || it.id == target.id } + target,
                bookmarks =
                    data.bookmarks.map { b ->
                        if (b.tags.none { it.id == id }) b else b.copy(tags = (b.tags.filterNot { it.id == id || it.id == target.id } + target))
                    },
            )
        }
        // As stored: the storage may give the renamed tag its own id (ItemsSavedStorage).
        return stored.tags.firstOrNull { it.name.equals(clean, ignoreCase = true) } ?: result!!
    }

    /**
     * Copies the articles saved on this phone to the Instapaper account [remote] after signing in,
     * with their like, archive and tags, oldest first so the newest ends on top. Each one is removed
     * here once it is in the account, so a copy that stops halfway (offline, too many requests)
     * goes on where it left off the next time.
     */
    suspend fun uploadTo(remote: InstapaperService): LocalUpload {
        var copied = 0
        for (local in storage.read().bookmarks.sortedBy { it.time }) {
            try {
                copyOne(local, remote)
            } catch (e: InstapaperException) {
                return LocalUpload(copied, e.problem)
            }
            if (local.url != null) copied++
            storage.update { data -> data.copy(bookmarks = data.bookmarks.filterNot { it.id == local.id }).withoutUnusedTags() }
        }
        return LocalUpload(copied, null)
    }

    private suspend fun copyOne(
        local: InstapaperBookmark,
        remote: InstapaperService,
    ) {
        val url = local.url ?: return
        val saved = remote.save(url, local.title.takeIf { it != url }) ?: return
        if (local.liked && !saved.liked) remote.like(saved.id, true)
        val names = local.tags.map { it.name }.filter { name -> saved.tags.none { it.name.equals(name, ignoreCase = true) } }
        if (names.isNotEmpty()) remote.setTags(saved.id, names, emptyList())
        if (local.archived) remote.move(saved.id, "archive")
    }

    private suspend fun change(
        id: Long,
        patch: (InstapaperBookmark) -> InstapaperBookmark,
    ) {
        storage.update { data ->
            if (data.bookmarks.none { it.id == id }) throw InstapaperException(InstapaperProblem.Invalid)
            data.copy(bookmarks = data.bookmarks.map { if (it.id == id) patch(it) else it })
        }
    }

    private fun LocalSavedData.withoutUnusedTags(): LocalSavedData = copy(tags = tags.filter { tag -> bookmarks.any { b -> b.tags.any { it.id == tag.id } } })
}

/** The saved articles as JSON text for storage, and back. Plain Kotlin, unit tested. */
object LocalSavedCodec {
    fun encode(data: LocalSavedData): String =
        Json.write(
            mapOf(
                "version" to 1,
                "nextId" to data.nextId,
                "tags" to data.tags.map { mapOf("id" to it.id, "name" to it.name) },
                "articles" to
                    data.bookmarks.map { b ->
                        mapOf(
                            "id" to b.id,
                            "url" to b.url,
                            "title" to b.title,
                            "description" to b.description,
                            "time" to b.time,
                            "liked" to b.liked,
                            "archived" to b.archived,
                            "tags" to b.tags.map { it.id },
                        )
                    },
            ),
        )

    /** Empty when there is nothing stored yet, or it can't be read. */
    fun decode(text: String?): LocalSavedData {
        if (text.isNullOrBlank()) return LocalSavedData()
        val root = runCatching { Json.parse(text) }.getOrNull() as? Map<*, *> ?: return LocalSavedData()
        val tags =
            (root["tags"] as? List<*>).orEmpty().mapNotNull { t ->
                val m = t as? Map<*, *> ?: return@mapNotNull null
                val id = (m["id"] as? Number)?.toLong() ?: return@mapNotNull null
                InstapaperTag(id, m["name"] as? String ?: return@mapNotNull null)
            }
        val byId = tags.associateBy { it.id }
        val bookmarks =
            (root["articles"] as? List<*>).orEmpty().mapNotNull { a ->
                val m = a as? Map<*, *> ?: return@mapNotNull null
                val id = (m["id"] as? Number)?.toLong() ?: return@mapNotNull null
                InstapaperBookmark(
                    id = id,
                    url = m["url"] as? String,
                    title = m["title"] as? String ?: "",
                    description = m["description"] as? String ?: "",
                    time = (m["time"] as? Number)?.toLong() ?: 0L,
                    liked = m["liked"] == true,
                    archived = m["archived"] == true,
                    folderId = null,
                    tags = (m["tags"] as? List<*>).orEmpty().mapNotNull { (it as? Number)?.toLong()?.let(byId::get) },
                )
            }
        val highest = (bookmarks.map { it.id } + tags.map { it.id }).maxOrNull() ?: 0L
        val nextId = ((root["nextId"] as? Number)?.toLong() ?: 1L).coerceAtLeast(highest + 1)
        return LocalSavedData(bookmarks, tags, nextId)
    }
}
