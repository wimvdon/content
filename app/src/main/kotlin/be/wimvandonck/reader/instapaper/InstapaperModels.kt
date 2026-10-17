package be.wimvandonck.reader.instapaper

import java.net.URI

data class InstapaperTag(
    val id: Long,
    val name: String,
)

data class InstapaperFolder(
    val id: Long,
    val title: String,
    val position: Int,
)

data class InstapaperBookmark(
    val id: Long,
    val url: String?,
    val title: String,
    val description: String,
    /** When it was saved, in seconds since 1970. */
    val time: Long,
    val liked: Boolean,
    val archived: Boolean,
    val folderId: Long?,
    val tags: List<InstapaperTag>,
) {
    /** The address to open, only when it is a web address (so a saved item can't carry a script). */
    val link: String? get() = url?.takeIf { it.startsWith("https://") || it.startsWith("http://") }

    /** The site's name without "www.", or empty. */
    val host: String get() = url?.let { address -> runCatching { URI(address).host.orEmpty().removePrefix("www.") }.getOrDefault("") }.orEmpty()
}

/** Which list of bookmarks: Unread (Instapaper calls it home), the archive, the liked ones, one folder or one tag. */
sealed interface InstapaperView {
    data object Home : InstapaperView

    data object Archive : InstapaperView

    data object Liked : InstapaperView

    data class InFolder(
        val folderId: Long,
    ) : InstapaperView

    data class WithTag(
        val name: String,
    ) : InstapaperView
}

data class InstapaperPage(
    val bookmarks: List<InstapaperBookmark>,
    /** The size of the whole list, not of this page. */
    val total: Int,
)

/** The signed-in account. The token never expires; it stays on this phone only. */
data class InstapaperSession(
    val token: String,
    val userId: Long?,
    val username: String,
)

enum class InstapaperProblem { SignedOut, NotApproved, RateLimited, Offline, ServerError, Invalid }

class InstapaperException(
    val problem: InstapaperProblem,
    cause: Throwable? = null,
) : Exception(problem.name, cause)

/** What the Instapaper screen needs from the account. [InstapaperClient] is the real one; tests use a fake. */
interface InstapaperService {
    suspend fun list(
        view: InstapaperView,
        offset: Int,
        limit: Int,
    ): InstapaperPage

    suspend fun save(
        url: String,
        title: String?,
    )

    /** [section] is "archive", "home" or a folder id. */
    suspend fun move(
        id: Long,
        section: String,
    )

    /** Returns whether the bookmark is liked afterwards. */
    suspend fun like(
        id: Long,
        liked: Boolean,
    ): Boolean

    suspend fun delete(id: Long)

    /** Adds tags by name (created when new) and removes tags by id. Returns the bookmark's tags afterwards. */
    suspend fun setTags(
        id: Long,
        add: List<String>,
        removeIds: List<Long>,
    ): List<InstapaperTag>

    suspend fun folders(): List<InstapaperFolder>

    suspend fun createFolder(title: String): InstapaperFolder

    suspend fun deleteFolder(id: Long)

    /** Positions start at 1; folders left out keep theirs. Returns every folder in the new order. */
    suspend fun reorderFolders(order: List<Pair<Long, Int>>): List<InstapaperFolder>

    suspend fun tags(): List<InstapaperTag>

    suspend fun renameTag(
        id: Long,
        name: String,
    ): InstapaperTag
}
