package be.wimvandonck.content.instapaper

import java.net.URI

data class InstapaperTag(
    val id: Long,
    val name: String,
)

/** A highlight on a bookmark (#164): the passage, its note and which occurrence it is (from 0). */
data class InstapaperHighlight(
    val id: Long,
    val text: String,
    val note: String = "",
    val position: Int = 0,
    /** When it was made, in seconds. */
    val time: Long = 0,
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
    /** The article's picture, when Instapaper found one (https only). */
    val imageUrl: String? = null,
    /** How far it was read, 0 to 1. */
    val progress: Float = 0f,
    /** When the progress last changed, in seconds since 1970 (0 when never): Instapaper's only change time. */
    val progressAt: Long = 0,
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

/** One page of a `since` sync: the changed bookmarks, the deleted ids, and how many entries the page held. */
data class InstapaperChanges(
    val bookmarks: List<InstapaperBookmark>,
    val deletedIds: List<Long>,
    val count: Int,
)

data class InstapaperPage(
    val bookmarks: List<InstapaperBookmark>,
    /** The size of the whole list, not of this page. */
    val total: Int,
)

/**
 * The signed-in account. The token never expires. [accountKey] opens the user's Reader account
 * (sync, docs/account.md); it is null when the server has no accounts.
 */
data class InstapaperSession(
    val token: String,
    val userId: Long?,
    val username: String,
    val accountKey: String? = null,
)

enum class InstapaperProblem { SignedOut, NotApproved, RateLimited, Offline, ServerError, Invalid }

class InstapaperException(
    val problem: InstapaperProblem,
    cause: Throwable? = null,
) : Exception(problem.name, cause)
