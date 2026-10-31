package to.wim.content.data

import to.wim.content.feed.Html

/**
 * Who shared an item with you (#167): their email address and name (the name may be empty), when,
 * whether it is in the inbox ([inInbox], until you remove it there; saving it doesn't) and whether
 * you opened it there ([read]).
 */
data class SharedBy(
    val email: String,
    val name: String = "",
    val at: Long = 0,
    val inInbox: Boolean = false,
    val read: Boolean = false,
) {
    /** How the app names them: the name, else the address. */
    val label: String get() = name.ifBlank { email }
}

/** One article as it is shared. */
data class SharedArticle(
    val url: String,
    val title: String,
    val imageUrl: String? = null,
    val description: String = "",
)

/** Something another Content user shared with you, as the account's inbox holds it. */
data class InboxMessage(
    val id: String,
    val from: SharedBy,
    val items: List<SharedArticle>,
)

/** What taking in the inbox brought: from whom, and how many articles. */
data class Received(
    val from: List<SharedBy>,
    val count: Int,
)

/**
 * Sharing with other Content users (#167): one article at a time. Receiving puts each shared article
 * in the inbox, unread, marked with who shared it, whether or not it is in Saved too. A one-time
 * copy: later changes on either side don't follow. Plain Kotlin, unit tested; the web app's
 * `sharing.ts` does the same.
 */
object Sharing {
    fun article(item: Item) = SharedArticle(item.url, item.title, item.imageUrl, item.description)

    /**
     * What is sent for [url]: with the picture and intro the library knows (a saved item, else a feed
     * article with that link), so the receiver's notification can show them; else just the link and
     * [title].
     */
    fun articleFor(
        library: Library,
        url: String,
        title: String,
    ): SharedArticle {
        library.items.firstOrNull { it.url == url }?.let { return article(it).copy(title = title.ifBlank { it.title }) }
        val feedArticle = library.articles.firstOrNull { it.link == url } ?: return SharedArticle(url, title)
        return SharedArticle(url, title, feedArticle.imageUrl?.takeIf { it.startsWith("https://") }, Html.snippet(feedArticle.html, INTRO_LENGTH))
    }

    /** How much of a feed article's text goes along as its intro. */
    private const val INTRO_LENGTH = 300

    /**
     * The library with [messages] taken in: each article is in the inbox, unread, with who shared it.
     * One already in Saved stays there as well; one shared again comes back unread with its latest
     * sender. Returns what arrived.
     */
    fun takeIn(
        library: Library,
        messages: List<InboxMessage>,
        now: Long,
    ): Pair<Library, Received> {
        var lib = library
        var count = 0
        val from = LinkedHashSet<SharedBy>()
        for (message in messages) {
            for (shared in message.items) {
                val key = ItemKeys.of(shared.url) ?: continue
                val existing = lib.item(key)
                val base = existing ?: lib.newItem(shared.url, shared.title, now, shared.description, shared.imageUrl) ?: continue
                val updated = base.copy(sharedBy = message.from.copy(at = now, inInbox = true, read = false))
                if (updated != existing) {
                    lib = lib.putItem(updated)
                    count++
                }
                from += message.from
            }
        }
        return lib to Received(from.toList(), count)
    }
}

/**
 * The inbox (#246): what other Content users shared with you, newest first, read or unread, until you
 * remove it. Saving an article from it keeps it there, as a feed article stays in its feed. Plain
 * Kotlin, unit tested; the web app's `inbox.ts` does the same.
 */
object Inbox {
    fun items(library: Library): List<Item> = library.items.filter { it.inInbox }.sortedByDescending { it.sharedBy!!.at }

    /** How many are unread: the bell's dot shows while there are any. */
    fun unread(library: Library): Int = library.items.count { it.inInbox && !it.sharedBy!!.read }

    /** The library with item [key] marked read (or unread) in the inbox; the same when it isn't there. */
    fun markRead(
        library: Library,
        key: String,
        read: Boolean = true,
    ): Library {
        val item = library.item(key)?.takeIf { it.inInbox && it.sharedBy!!.read != read } ?: return library
        return library.putItem(item.copy(sharedBy = item.sharedBy!!.copy(read = read)))
    }

    /** The library with item [key] out of the inbox; it stays in Saved if it is saved. */
    fun remove(
        library: Library,
        key: String,
    ): Library {
        val item = library.item(key)?.takeIf { it.inInbox } ?: return library
        return library.putItem(item.copy(sharedBy = item.sharedBy!!.copy(inInbox = false)))
    }
}
