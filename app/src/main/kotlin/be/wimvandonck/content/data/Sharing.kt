package be.wimvandonck.content.data

/** Who shared an item with you (#167): their email address and name (the name may be empty). */
data class SharedBy(
    val email: String,
    val name: String = "",
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
 * articles); receiving puts each shared article in Saved, To read, marked with who shared it (and
 * the tag it was shared as). A one-time copy: later changes on either side don't follow. Plain
 * Kotlin, unit tested; the web app's `sharing.ts` does the same.
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
     * The library with [messages] taken in: each article in Saved (To read, unless it is there
     * already), with who shared it (an article already shared keeps its first sender) and the tag it
     * was shared as. Returns what arrived.
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
                        readingList = base.readingList ?: ReadingListEntry(ReadingState.ToRead, now),
                        sharedBy = base.sharedBy ?: message.from,
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
