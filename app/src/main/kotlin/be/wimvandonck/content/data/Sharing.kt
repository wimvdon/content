package be.wimvandonck.content.data

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
    /** The tag it was shared as, or null for single articles. */
    val tag: String? = null,
)

/** What taking in the inbox brought: from whom, and how many articles. */
data class Received(
    val from: List<SharedBy>,
    val count: Int,
)

/**
 * Sharing with other Content users (#167). Sending picks what goes (one article, or a tag's
 * articles); receiving puts each shared article in the inbox, unread, marked with who shared it (and
 * the tag it was shared as), whether or not it is in Saved too. A one-time copy: later changes on
 * either side don't follow. Plain Kotlin, unit tested; the web app's `sharing.ts` does the same.
 */
object Sharing {
    /** The most articles one share may hold. */
    const val MAX_ITEMS = 500

    fun article(item: Item) = SharedArticle(item.url, item.title, item.imageUrl, item.description)

    /** A tag's saved articles, newest first, at most [MAX_ITEMS]. */
    fun tagArticles(
        library: Library,
        tag: String,
    ): List<SharedArticle> =
        library.items
            .filter { it.readingList != null && it.tags.any { t -> t.equals(tag, ignoreCase = true) } }
            .sortedByDescending { it.readingList!!.addedAt }
            .take(MAX_ITEMS)
            .map(::article)

    /**
     * The library with [messages] taken in: each article is in the inbox, unread, with who shared it
     * and the tag it was shared as. One already in Saved stays there as well; one shared again comes
     * back unread with its latest sender. Returns what arrived.
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
                val tags = message.tag?.takeIf { t -> base.tags.none { it.equals(t, ignoreCase = true) } }?.let { base.tags + it } ?: base.tags
                val updated =
                    base.copy(
                        sharedBy = message.from.copy(at = now, inInbox = true, read = false),
                        tags = cleanTags(tags),
                    )
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
