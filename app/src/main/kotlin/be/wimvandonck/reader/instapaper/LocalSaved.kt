package be.wimvandonck.reader.instapaper

import be.wimvandonck.reader.data.ItemKeys
import be.wimvandonck.reader.sync.Json

/**
 * The articles saved on this phone without an Instapaper account before #106, and their tags. They
 * move into the library's reading list once (SavedItems.migrate); only reading them is left.
 */
data class LocalSavedData(
    val bookmarks: List<InstapaperBookmark> = emptyList(),
    val tags: List<InstapaperTag> = emptyList(),
    /** The id the next saved article or tag gets. */
    val nextId: Long = 1,
)

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
