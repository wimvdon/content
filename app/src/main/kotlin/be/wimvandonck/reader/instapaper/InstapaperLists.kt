package be.wimvandonck.reader.instapaper

import be.wimvandonck.reader.data.Article
import be.wimvandonck.reader.feed.Html

/** What happened to a saved article. */
sealed interface BookmarkChange {
    data class Archived(val archived: Boolean) : BookmarkChange

    data class Liked(val liked: Boolean) : BookmarkChange

    /** Into a folder, or back to Unread when null. */
    data class Moved(val folderId: Long?) : BookmarkChange

    data class Tagged(val tags: List<InstapaperTag>) : BookmarkChange

    data object Deleted : BookmarkChange
}

/**
 * Instapaper inside the reader: saved articles show in the same list and reader as feed articles.
 * They have no read state (archiving is a separate action) and are not in All articles. Plain
 * Kotlin, unit tested; the web app does the same in web/src/lib/instapaperLibrary.ts.
 */
object InstapaperLists {
    /** The "feed" of every saved article, so its key ("instapaper <id>") never clashes with a feed's. */
    const val FEED = "instapaper"

    fun isSaved(article: Article): Boolean = article.feedUrl == FEED

    /** A saved article as the list and the reader show it. The reader loads the page for the full text. */
    fun article(bookmark: InstapaperBookmark): Article =
        Article(
            feedUrl = FEED,
            id = bookmark.id.toString(),
            title = bookmark.title.ifEmpty { bookmark.url.orEmpty() },
            link = bookmark.link,
            author = null,
            html = if (bookmark.description.isBlank()) "" else "<p>${Html.escape(bookmark.description)}</p>",
            publishedAt = bookmark.time * 1000,
            fetchedAt = bookmark.time * 1000,
            read = false,
        )

    /** Whether a saved article belongs in a list: Unread holds what is in no folder and not archived. */
    fun inView(
        view: InstapaperView,
        bookmark: InstapaperBookmark,
    ): Boolean =
        when (view) {
            InstapaperView.Home -> !bookmark.archived && bookmark.folderId == null
            InstapaperView.Archive -> bookmark.archived
            InstapaperView.Liked -> bookmark.liked
            is InstapaperView.InFolder -> !bookmark.archived && bookmark.folderId == view.folderId
            is InstapaperView.WithTag -> bookmark.tags.any { it.name == view.name }
        }

    /** A saved article after a change, or null when it was deleted. Back to Unread leaves its folder. */
    fun patch(
        bookmark: InstapaperBookmark,
        change: BookmarkChange,
    ): InstapaperBookmark? =
        when (change) {
            BookmarkChange.Deleted -> null
            is BookmarkChange.Archived -> bookmark.copy(archived = change.archived, folderId = if (change.archived) bookmark.folderId else null)
            is BookmarkChange.Liked -> bookmark.copy(liked = change.liked)
            is BookmarkChange.Moved -> bookmark.copy(folderId = change.folderId, archived = false)
            is BookmarkChange.Tagged -> bookmark.copy(tags = change.tags)
        }

    /** The list after a change to one of its articles: changed in place, or gone when it no longer belongs. */
    fun apply(
        view: InstapaperView,
        items: List<InstapaperBookmark>,
        id: Long,
        change: BookmarkChange,
    ): List<InstapaperBookmark> =
        items.mapNotNull { b ->
            if (b.id != id) b else patch(b, change)?.takeIf { inView(view, it) }
        }
}
