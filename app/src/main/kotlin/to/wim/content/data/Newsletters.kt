package to.wim.content.data

/**
 * Newsletters: the emails that reach Saved through Instapaper (Instapaper's "private content",
 * whose address is Instapaper's own page, [InstapaperLinks]). One that comes in after Instapaper is
 * connected waits unread (bold, with a dot) until it is opened, marked as read, archived or removed;
 * the first import brings them in read. Nothing makes one unread again, except Undo right after Mark
 * all as read. Saved's Newsletters chip shows them; with the setting on, a dot on Saved, To read and
 * the chip says one waits. Plain Kotlin, unit tested; the web app's `newsletters.ts` does the same.
 */
object Newsletters {
    fun isNewsletter(url: String?): Boolean = InstapaperLinks.bookmarkId(url) != null

    fun isNewsletter(item: Item): Boolean = isNewsletter(item.url)

    /** The unread ones (always to read: [putItem] sees to that). */
    fun unread(library: Library): List<Item> = library.items.filter { it.unread }

    /** Whether a newsletter waits unread: the dot on Saved, To read and the chip (with the setting on). */
    fun hasUnread(library: Library): Boolean = library.items.any { it.unread }

    /** The library with item [key] read; the same when it wasn't unread. */
    fun markRead(
        library: Library,
        key: String,
    ): Library = library.item(key)?.takeIf { it.unread }?.let { library.putItem(it.copy(unread = false)) } ?: library

    /** The keys of the unread ones among [items] (the list shown): what Mark all as read marks, and Undo brings back. */
    fun unreadKeys(items: List<Item>): List<String> = items.filter { it.unread }.map { it.key }

    /** The library with items [keys] read (Mark all as read). */
    fun markAllRead(
        library: Library,
        keys: Collection<String>,
    ): Library = keys.fold(library, ::markRead)

    /** Undo of Mark all as read: items [keys] unread again, those still to read. */
    fun undoMarkAllRead(
        library: Library,
        keys: Collection<String>,
    ): Library = keys.fold(library) { lib, key -> lib.changeItem(key) { if (it.toRead) it.copy(unread = true) else it } }
}
