package be.wimvandonck.reader.data

import be.wimvandonck.reader.feed.Html

// The reading list and Bookmarks inside the library (#106): one item per link, with memberships.
// Every change returns a new Library, like the rest of it. Plain Kotlin, unit tested.

fun Library.item(key: String): Item? = items.firstOrNull { it.key == key }

/** The item of a link, whatever way the link is written. */
fun Library.itemForLink(url: String?): Item? = ItemKeys.of(url)?.let(::item)

/** The item a feed article has once it is saved, tagged or annotated; null while it is only in its feed. */
fun Library.itemFor(article: Article): Item? = itemForLink(article.link)

/** The feed article with this link, while the library still has it. */
fun Library.feedArticleFor(url: String?): Article? {
    val key = ItemKeys.of(url) ?: return null
    return articles.firstOrNull { ItemKeys.of(it.link) == key }
}

/**
 * The item for [url], made from the feed article with that link when there is one (its title,
 * picture, start of the text and where it came from), else from [title]. Not added to the library:
 * give it a membership and [putItem] it. Null for addresses that aren't web links.
 */
fun Library.newItem(
    url: String,
    title: String?,
    now: Long,
    description: String = "",
    imageUrl: String? = null,
): Item? {
    if (!ItemKeys.isWebLink(url)) return null
    val key = ItemKeys.of(url) ?: return null
    val article = feedArticleFor(url)
    return Item(
        key = key,
        url = url.trim(),
        title = title?.trim()?.takeIf { it.isNotEmpty() } ?: article?.title?.takeIf { it.isNotBlank() } ?: url.trim(),
        description = description.ifBlank { article?.html?.let { Html.snippet(it) }.orEmpty() },
        imageUrl = imageUrl ?: article?.imageUrl,
        feedUrl = article?.feedUrl,
        articleId = article?.id,
        createdAt = now,
    )
}

/** Adds or replaces an item; an item with nothing of the user's left on it is dropped. */
fun Library.putItem(item: Item): Library {
    val index = items.indexOfFirst { it.key == item.key }
    return when {
        !item.kept -> if (index < 0) this else copy(items = items.filterIndexed { i, _ -> i != index })
        index < 0 -> copy(items = items + item)
        items[index] == item -> this
        else -> copy(items = items.toMutableList().also { it[index] = item })
    }
}

/** Changes the item with [key], if there is one. */
fun Library.changeItem(
    key: String,
    change: (Item) -> Item,
): Library = item(key)?.let { putItem(change(it)) } ?: this

/**
 * Puts a link on the reading list, to read. A link already there (archived or not) comes back to
 * the top, as Instapaper does; an item already in Bookmarks keeps its bookmark. Returns the item,
 * or null for addresses that aren't web links.
 */
fun Library.addToReadingList(
    url: String,
    title: String?,
    now: Long,
    description: String = "",
    imageUrl: String? = null,
): Pair<Library, Item?> {
    val existing = itemForLink(url)
    val base = existing ?: newItem(url, title, now, description, imageUrl) ?: return this to null
    val item =
        base.copy(
            title = title?.trim()?.takeIf { it.isNotEmpty() } ?: base.title,
            readingList = ReadingListEntry(ReadingState.ToRead, now),
        )
    return putItem(item) to item
}

/** Archives an item or puts it back to read; null takes it off the reading list (Bookmarks keeps it). */
fun Library.setReadingState(
    key: String,
    state: ReadingState?,
): Library =
    changeItem(key) { item ->
        when {
            state == null -> item.copy(readingList = null)
            item.readingList == null -> item
            else -> item.copy(readingList = item.readingList.copy(state = state))
        }
    }

fun Library.setLiked(
    key: String,
    liked: Boolean,
): Library = changeItem(key) { it.copy(liked = liked) }

/** The item's tags, trimmed, without empty ones and without duplicates (case ignored). */
fun Library.setTags(
    key: String,
    tags: List<String>,
): Library = changeItem(key) { it.copy(tags = cleanTags(tags)) }

/** Every tag on any item, sorted (case ignored). */
fun Library.itemTags(): List<String> = items.flatMap { it.tags }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }

/** Renames a tag on every item; renaming to the name of another tag joins the two. */
fun Library.renameTag(
    from: String,
    to: String,
): Library {
    val clean = to.trim()
    if (clean.isEmpty()) return this
    return copy(
        items =
            items.map { item ->
                if (item.tags.none { it.equals(from, ignoreCase = true) }) {
                    item
                } else {
                    item.copy(tags = cleanTags(item.tags.map { if (it.equals(from, ignoreCase = true)) clean else it }))
                }
            },
    )
}

/** Keeps an item in Bookmarks, in [collection] (blank for none). Its reading list place stays as it is. */
fun Library.keepAsBookmark(
    key: String,
    collection: String?,
    now: Long,
): Library =
    changeItem(key) { item ->
        item.copy(bookmark = BookmarkEntry(addedAt = item.bookmark?.addedAt ?: now, collection = collection?.trim()?.ifEmpty { null }))
    }

/** Takes an item out of Bookmarks; its reading list place stays as it is. */
fun Library.removeBookmark(key: String): Library = changeItem(key) { it.copy(bookmark = null) }

/** The items on the reading list in [state], newest first. */
fun Library.readingList(state: ReadingState): List<Item> = items.filter { it.readingList?.state == state }.sortedByDescending { it.readingList!!.addedAt }

/** The items in Bookmarks, newest first. */
fun Library.bookmarks(): List<Item> = items.filter { it.bookmark != null }.sortedByDescending { it.bookmark!!.addedAt }

internal fun cleanTags(tags: List<String>): List<String> = tags.map(String::trim).filter(String::isNotEmpty).distinctBy { it.lowercase() }
