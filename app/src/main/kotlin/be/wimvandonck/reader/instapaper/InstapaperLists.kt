package be.wimvandonck.reader.instapaper

import be.wimvandonck.reader.data.Article
import be.wimvandonck.reader.data.Feed
import be.wimvandonck.reader.data.Library
import be.wimvandonck.reader.feed.Html
import java.net.URI

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

    /**
     * A saved article as the list and the reader show it, the same way as a feed article. When
     * [library] still has the feed article with the same address, its text, author and picture are
     * used (so the reader shows the feed's text, or the full article, like for a feed); otherwise
     * Instapaper's description and picture. The reader loads the page for the full text.
     */
    fun article(
        bookmark: InstapaperBookmark,
        library: Library? = null,
    ): Article {
        val fromFeed = library?.let { feedArticle(bookmark, it) }
        return Article(
            feedUrl = FEED,
            id = bookmark.id.toString(),
            title = bookmark.title.ifEmpty { fromFeed?.title ?: bookmark.url.orEmpty() },
            link = bookmark.link,
            author = fromFeed?.author,
            html = fromFeed?.html ?: if (bookmark.description.isBlank()) "" else "<p>${Html.escape(bookmark.description)}</p>",
            publishedAt = bookmark.time * 1000,
            fetchedAt = bookmark.time * 1000,
            read = false,
            imageUrl = fromFeed?.imageUrl ?: bookmark.imageUrl,
        )
    }

    /** The feed article with the same address as a saved article, while the library still has it. */
    fun feedArticle(
        bookmark: InstapaperBookmark,
        library: Library,
    ): Article? {
        val address = addressKey(bookmark.url) ?: return null
        return library.articles.firstOrNull { addressKey(it.link) == address }
    }

    /**
     * Where a saved article comes from, shown above its title like a feed: the feed it was saved
     * from, else a feed of the same website, else the website itself with its icon (favicon).
     */
    fun source(
        bookmark: InstapaperBookmark,
        library: Library?,
    ): Feed? {
        val host = bookmark.host.ifEmpty { return null }
        if (library != null) {
            feedArticle(bookmark, library)?.let { article -> library.feed(article.feedUrl)?.let { return it } }
            library.feeds.firstOrNull { hostOf(it.siteUrl) == host || hostOf(it.url) == host }?.let { return it }
        }
        return Feed(url = host, title = host, iconUrl = "https://${bookmark.url?.let(::hostWithWww) ?: host}/favicon.ico")
    }

    /** An address without what doesn't change the page (http(s), www., a trailing slash, the #part). */
    fun addressKey(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return url
            .trim()
            .substringBefore('#')
            .replaceFirst(Regex("^https?://", RegexOption.IGNORE_CASE), "")
            .replaceFirst(Regex("^www\\.", RegexOption.IGNORE_CASE), "")
            .trimEnd('/')
            .lowercase()
    }

    private fun hostOf(url: String?): String = url?.let { runCatching { URI(it).host.orEmpty().removePrefix("www.") }.getOrDefault("") }.orEmpty()

    private fun hostWithWww(url: String): String? = runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotEmpty() }

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
