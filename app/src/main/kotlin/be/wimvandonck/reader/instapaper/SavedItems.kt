package be.wimvandonck.reader.instapaper

import be.wimvandonck.reader.data.Item
import be.wimvandonck.reader.data.ItemKeys
import be.wimvandonck.reader.data.Library
import be.wimvandonck.reader.data.ReadingListEntry
import be.wimvandonck.reader.data.ReadingState
import be.wimvandonck.reader.data.cleanTags
import be.wimvandonck.reader.data.item
import be.wimvandonck.reader.data.newItem
import be.wimvandonck.reader.data.putItem

/**
 * Saved without an account is the library's reading list (#106): [LocalSaved] sees the items on
 * the reading list as its saved articles, and its changes become changes to those items. Ids are
 * worked out from the item's key and the tag's name, so they stay the same between calls. Plain
 * Kotlin, unit tested.
 */
object SavedItems {
    /** The reading list as [LocalSaved]'s data. */
    fun data(library: Library): LocalSavedData {
        val bookmarks = library.items.filter { it.readingList != null }.map(::bookmark)
        val tags = bookmarks.flatMap { it.tags }.distinctBy { it.id }
        return LocalSavedData(bookmarks, tags, (bookmarks.map { it.id } + tags.map { it.id }).maxOrNull()?.let { it + 1 } ?: 1)
    }

    fun bookmark(item: Item): InstapaperBookmark =
        InstapaperBookmark(
            id = idOf(item.key),
            url = item.url,
            title = item.title,
            description = item.description,
            time = (item.readingList?.addedAt ?: item.createdAt) / 1000,
            liked = item.liked,
            archived = item.archived,
            folderId = null,
            tags = item.tags.map { InstapaperTag(tagIdOf(it), it) },
            imageUrl = item.imageUrl,
        )

    /**
     * Applies what [LocalSaved] changed ([before] → [after]) to the library's items: new saved
     * articles join the item of their link (no copy), changed ones change their item, and removed
     * ones leave the reading list (Bookmarks keeps them).
     */
    fun apply(
        library: Library,
        before: LocalSavedData,
        after: LocalSavedData,
        now: Long,
    ): Library {
        val old = before.bookmarks.associateBy { it.id }
        var result = library
        for (b in after.bookmarks) {
            val previous = old[b.id]
            if (previous == b) continue
            val existing = (if (previous != null) result.items.firstOrNull { idOf(it.key) == b.id } else null) ?: result.item(ItemKeys.of(b.url) ?: continue)
            val base = existing ?: result.newItem(b.url ?: continue, b.title.takeIf { it != b.url }, now, b.description, b.imageUrl) ?: continue
            // Saving again sets a new time; otherwise the stored milliseconds stay.
            val addedAt = if (previous != null && previous.time == b.time) base.readingList?.addedAt ?: (b.time * 1000) else b.time * 1000
            result =
                result.putItem(
                    base.copy(
                        // A link saved without a title is titled with its address; the feed article's title is better.
                        title = b.title.takeIf { it.isNotBlank() && it != b.url } ?: base.title,
                        readingList = ReadingListEntry(if (b.archived) ReadingState.Archived else ReadingState.ToRead, addedAt),
                        liked = b.liked,
                        tags = cleanTags(b.tags.map { it.name }),
                    ),
                )
        }
        val kept = after.bookmarks.mapTo(HashSet()) { it.id }
        for (gone in before.bookmarks.filter { it.id !in kept }) {
            val item = result.items.firstOrNull { idOf(it.key) == gone.id } ?: continue
            // Off the reading list; an item that is also a bookmark stays, with its tags and like.
            result = result.putItem(if (item.bookmark != null) item.copy(readingList = null) else item.copy(readingList = null, liked = false, tags = emptyList()))
        }
        return result
    }

    /**
     * The library after moving in the articles saved on this phone before #106 (the "saved"
     * DataStore file): each becomes the item of its link on the reading list, with its title,
     * time, like, archive and tags. Links that turn out to be the same keep the newest.
     */
    fun migrate(
        library: Library,
        saved: LocalSavedData,
    ): Library =
        saved.bookmarks.filter { ItemKeys.isWebLink(it.url) }.sortedBy { it.time }.fold(library) { lib, b ->
            val time = b.time * 1000
            val key = ItemKeys.of(b.url) ?: return@fold lib
            val base = lib.item(key) ?: lib.newItem(b.url!!, b.title, time, b.description, b.imageUrl) ?: return@fold lib
            lib.putItem(
                base.copy(
                    title = b.title.ifBlank { base.title },
                    readingList = ReadingListEntry(if (b.archived) ReadingState.Archived else ReadingState.ToRead, time),
                    liked = base.liked || b.liked,
                    tags = cleanTags(base.tags + b.tags.map { it.name }),
                ),
            )
        }

    /** A saved article's id: the same for the same item every time (64-bit FNV-1a of the key, positive). */
    fun idOf(key: String): Long = hash("item:$key")

    fun tagIdOf(name: String): Long = hash("tag:" + name.lowercase())

    private fun hash(text: String): Long {
        var h = -0x340d631b7bdddcdbL
        for (c in text) {
            h = h xor c.code.toLong()
            h *= 0x100000001b3L
        }
        // Never 0, never negative.
        return (h and Long.MAX_VALUE).coerceAtLeast(1)
    }
}

/** [LocalSaved]'s storage on the library's items, through [update] (LibraryRepository.update). */
class ItemsSavedStorage(
    private val read: suspend () -> Library,
    private val update: suspend ((Library) -> Library) -> Library,
    private val now: () -> Long = System::currentTimeMillis,
) : LocalSavedStorage {
    override suspend fun read(): LocalSavedData = SavedItems.data(read.invoke())

    override suspend fun update(change: (LocalSavedData) -> LocalSavedData): LocalSavedData {
        val updated =
            update.invoke { library ->
                val before = SavedItems.data(library)
                SavedItems.apply(library, before, change(before), now())
            }
        return SavedItems.data(updated)
    }
}
