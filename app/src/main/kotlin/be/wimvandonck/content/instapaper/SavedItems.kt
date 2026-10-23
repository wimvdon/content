package be.wimvandonck.content.instapaper

import be.wimvandonck.content.data.Item
import be.wimvandonck.content.data.ItemKeys
import be.wimvandonck.content.data.Library
import be.wimvandonck.content.data.ReadingListEntry
import be.wimvandonck.content.data.ReadingState
import be.wimvandonck.content.data.cleanTags
import be.wimvandonck.content.data.item
import be.wimvandonck.content.data.newItem
import be.wimvandonck.content.data.putItem

/** Moves the articles saved on this phone before #106 into the reading list. Plain Kotlin, unit tested. */
object SavedItems {
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
}
