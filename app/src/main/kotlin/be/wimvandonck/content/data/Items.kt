package be.wimvandonck.content.data

import java.net.URI

/** Where an item is on the reading list: still to read, or done with it (archived, but kept and searchable). */
enum class ReadingState { ToRead, Archived }

/** An item's place on the reading list. */
data class ReadingListEntry(
    val state: ReadingState,
    /** When it was added (or added again), in milliseconds; the list is newest first. */
    val addedAt: Long,
)

/** An item's place in Bookmarks: one flat collection (or none). */
data class BookmarkEntry(
    val addedAt: Long,
    val collection: String? = null,
)

/**
 * A highlighted passage, anchored by its text rather than its position (#102): the quote, a few
 * dozen characters before and after it, and which occurrence of the quote it is (from 0), so it
 * can be found again in another version of the text.
 */
data class Highlight(
    val id: String,
    val quote: String,
    val prefix: String = "",
    val suffix: String = "",
    val occurrence: Int = 0,
    val note: String = "",
    val createdAt: Long,
)

/**
 * One link and everything the user did with it (#106). A feed article that is only in its feed has
 * no item; it gets one as soon as it is saved, tagged or annotated, under the same [key] as the feed
 * article's link, so the reading list and Bookmarks never hold a copy of it. Memberships are
 * independent: an item can be on the reading list and in Bookmarks at once, and adding or removing
 * one never changes the other. Notes, tags and highlights belong to the item, so they stay when it
 * moves between sections.
 */
data class Item(
    /** [ItemKeys.of] the link: links that only differ in http(s), www., a trailing slash or #part are one item. */
    val key: String,
    val url: String,
    val title: String,
    /** A short description (Instapaper's, or the start of the feed text). */
    val description: String = "",
    val imageUrl: String? = null,
    /** The feed article it came from, while the user follows that feed: its feed and article id. */
    val feedUrl: String? = null,
    val articleId: String? = null,
    val createdAt: Long,
    val readingList: ReadingListEntry? = null,
    /**
     * The folder it is filed in (#119): one at most, only while it is to read. To read without a
     * folder is the inbox; archiving takes it out of its folder.
     */
    val folder: String? = null,
    /**
     * The service it was kept in before #145 (#112, #121). No longer used: an item reaches every
     * service whose scope covers it. Kept so the sync file stays the same on both platforms.
     */
    val service: String? = null,
    /** Bookmarks before #119; moved into folders when read (see [Saved.settle]). */
    val bookmark: BookmarkEntry? = null,
    /** Liked is one flag on the item (#107), not a list. */
    val liked: Boolean = false,
    val tags: List<String> = emptyList(),
    val note: String = "",
    val highlights: List<Highlight> = emptyList(),
    /** How far it has been read, 0 to 1. */
    val progress: Float = 0f,
    /** Who shared it with you (#167), or null. */
    val sharedBy: SharedBy? = null,
) {
    /** The site's name without "www.", or empty. */
    val host: String get() = ItemKeys.hostOf(url)

    /** False when nothing of the user's is left on it, so it can be dropped. */
    val kept: Boolean
        get() = readingList != null || bookmark != null || liked || tags.isNotEmpty() || note.isNotBlank() || highlights.isNotEmpty() || inInbox

    /** In the inbox (#246): shared with you and not removed there, whether or not it is in Saved. */
    val inInbox: Boolean get() = sharedBy?.inInbox == true

    val onReadingList: Boolean get() = readingList != null

    val toRead: Boolean get() = readingList?.state == ReadingState.ToRead

    val archived: Boolean get() = readingList?.state == ReadingState.Archived

    val bookmarked: Boolean get() = bookmark != null
}

/** Item keys: one per link, whatever way the link is written. Plain Kotlin, the same as the web app's. */
object ItemKeys {
    /** An address without what doesn't change the page (http(s), www., a trailing slash, the #part), lower case. */
    fun of(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return url
            .trim()
            .substringBefore('#')
            .replaceFirst(Regex("^https?://", RegexOption.IGNORE_CASE), "")
            .replaceFirst(Regex("^www\\.", RegexOption.IGNORE_CASE), "")
            .trimEnd('/')
            .lowercase()
            .ifEmpty { null }
    }

    /**
     * [of] without tracking parameters (utm_… and the like), for recognising the same article
     * when a service stores its link without them. Never used as an item's key: only to find
     * one that already exists (ServiceSync.keyFor).
     */
    fun loose(url: String?): String? {
        val key = of(url) ?: return null
        val q = key.indexOf('?')
        if (q < 0) return key
        val base = key.substring(0, q).trimEnd('/')
        val kept = key.substring(q + 1).split('&').filter { it.isNotEmpty() && !TRACKING.matches(it.substringBefore('=')) }.sorted()
        return if (kept.isEmpty()) base else base + "?" + kept.joinToString("&")
    }

    private val TRACKING = Regex("utm_.*|fbclid|gclid|dclid|msclkid|mc_cid|mc_eid|igshid|_hsenc|_hsmi|mkt_tok|ref_src|cmpid|ocid")

    /** True for addresses an item may have: web addresses only, so a saved item can't carry a script. */
    fun isWebLink(url: String?): Boolean = url != null && (url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true))

    fun hostOf(url: String?): String = url?.let { runCatching { URI(it.trim()).host.orEmpty().removePrefix("www.") }.getOrDefault("") }.orEmpty()
}
