package be.wimvandonck.content.data

import be.wimvandonck.content.feed.Html

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
fun Library.putItem(given: Item): Library {
    // Put in Saved, a shared item no longer waits in Shared with me (#167): removing it later drops it.
    val item = if (given.readingList != null && given.sharedBy?.waits == true) given.copy(sharedBy = given.sharedBy.copy(waits = false)) else given
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
            // Saved again after it was taken out: it goes to the default service, like a new save (#121).
            service = if (base.readingList != null) base.service else null,
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
            // Archiving takes it out of its folder (#119).
            state == ReadingState.Archived -> item.copy(readingList = item.readingList.copy(state = state), folder = null)
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
fun Library.itemTags(): List<String> = items.recentTags()

/**
 * The tags of [this], most recently used first (#220): the tag of the article saved last comes
 * first (when it was put in Saved, else when the item was made). Equal times go alphabetically.
 */
fun List<Item>.recentTags(): List<String> {
    val latest = LinkedHashMap<String, Pair<String, Long>>()
    for (item in this) {
        val time = item.readingList?.addedAt ?: item.createdAt
        for (tag in item.tags) {
            val key = tag.lowercase()
            val seen = latest[key]
            if (seen == null || time > seen.second) latest[key] = (seen?.first ?: tag) to time
        }
    }
    return latest.entries.sortedWith(compareByDescending<Map.Entry<String, Pair<String, Long>>> { it.value.second }.thenBy { it.key }).map { it.value.first }
}

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

/** The items on the reading list in [state], newest first. */
fun Library.readingList(state: ReadingState): List<Item> = items.filter { it.readingList?.state == state }.sortedByDescending { it.readingList!!.addedAt }

internal fun cleanTags(tags: List<String>): List<String> = tags.map(String::trim).filter(String::isNotEmpty).distinctBy { it.lowercase() }

/** Takes a tag off every item; an item with nothing left on it goes. */
fun Library.removeTag(name: String): Library =
    items.filter { item -> item.tags.any { it.equals(name, ignoreCase = true) } }.fold(this) { lib, item ->
        lib.putItem(item.copy(tags = item.tags.filterNot { it.equals(name, ignoreCase = true) }))
    }

/** How many items have each tag, by tag (as [itemTags] names them). */
fun Library.tagCounts(): Map<String, Int> = itemTags().associateWith { tag -> items.count { item -> item.tags.any { it.equals(tag, ignoreCase = true) } } }

/**
 * Tags a saved item with the tags of the feed it came from (#120, #187), each in the spelling of a
 * tag already in use; a feed without tags gives its folder's name. Nothing for a link that isn't a
 * feed article. Later changes to the feed don't touch tags already given.
 */
fun Library.tagWithFeedFolder(key: String): Library {
    val item = item(key) ?: return this
    val article = ReadingLists.feedArticle(item, this) ?: return this
    val feed = feed(article.feedUrl) ?: return this
    val folderTitle = feed.folderId?.let { id -> folders.firstOrNull { it.id == id }?.title?.trim() }?.takeIf { it.isNotEmpty() }
    val given = feed.tags.ifEmpty { listOfNotNull(folderTitle) }
    if (given.isEmpty()) return this
    val inUse = itemTags()
    val add = given.map { t -> inUse.firstOrNull { it.equals(t, ignoreCase = true) } ?: t }.filter { t -> item.tags.none { it.equals(t, ignoreCase = true) } }
    return if (add.isEmpty()) this else setTags(key, item.tags + add)
}

/**
 * Adds a highlight to the item of [url] (#102). A feed article that isn't saved yet goes to To read
 * first: annotating it keeps it.
 */
fun Library.addHighlight(
    url: String,
    title: String?,
    highlight: Highlight,
    now: Long,
): Library {
    val existing = itemForLink(url)?.takeIf { it.readingList != null }
    val (library, item) = if (existing != null) this to existing else addToReadingList(url, title, now)
    item ?: return this
    return library.changeItem(item.key) { it.copy(highlights = it.highlights + highlight) }
}

/** Changes a highlight's note. */
fun Library.setHighlightNote(
    key: String,
    highlightId: String,
    note: String,
): Library = changeItem(key) { item -> item.copy(highlights = item.highlights.map { if (it.id == highlightId) it.copy(note = note.trim()) else it }) }

/** Removes a highlight. */
fun Library.removeHighlight(
    key: String,
    highlightId: String,
): Library = changeItem(key) { item -> item.copy(highlights = item.highlights.filterNot { it.id == highlightId }) }
