package be.wimvandonck.reader.services

import be.wimvandonck.reader.data.Item
import be.wimvandonck.reader.data.Library
import be.wimvandonck.reader.data.Saved

/** How copying one service's articles to the other goes (#121), for Connected services. */
data class CopyProgress(
    val from: String,
    val to: String,
    val done: Int,
    val total: Int,
    /** Why it stopped early, or null. */
    val problem: ServiceProblem? = null,
    val finished: Boolean = false,
)

/**
 * The opt-in import of #121: copies the articles one service keeps to the other, once, when the
 * user asks. Articles the other already has are skipped; the copies stay the first service's items
 * (the other has a copy, which shares what Settings says). Plain Kotlin, tested with a fake service.
 */
object ServiceCopy {
    /** The saved items [from] has (its rows' keys) that [to] hasn't. */
    fun candidates(
        library: Library,
        from: Set<String>,
        to: Set<String>,
    ): List<Item> = library.items.filter { it.readingList != null && it.key in from && it.key !in to }

    /**
     * Adds [items] to [target] one by one, skipping those [rows] (the target's, as last seen) has, and
     * puts what was added in [rows]. Returns the problem that stopped it, or null.
     */
    suspend fun run(
        target: ReadingListService,
        items: List<Item>,
        rows: MutableMap<String, RemoteEntry>,
        onAdded: () -> Unit = {},
    ): ServiceProblem? {
        val notes = ServiceFeature.Notes in target.features
        for (item in items) {
            if (item.key in rows) continue
            try {
                rows[item.key] = target.add(item.url, item.title, item.tags, item.archived, Saved.folderOf(item), if (notes) item.note else "")
            } catch (e: ServiceException) {
                return e.problem
            }
            onAdded()
        }
        return null
    }
}
