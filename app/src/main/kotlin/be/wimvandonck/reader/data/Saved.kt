package be.wimvandonck.reader.data

import be.wimvandonck.reader.feed.Html
import java.net.URI

/** Saved's tabs (#119): To read (the inbox), everything, one folder, or the archive. */
sealed interface SavedTab {
    data object ToRead : SavedTab

    data object All : SavedTab

    data class Folder(
        val name: String,
    ) : SavedTab

    data object Archive : SavedTab
}

/** What kind of page a saved link is, for the type filter (#119): read from its link. */
enum class SavedType { Article, Video, Audio }

/** What Saved shows: a tab, and within it only liked ones, only some tags (all of them), one type. */
data class SavedFilter(
    val tab: SavedTab = SavedTab.ToRead,
    val likedOnly: Boolean = false,
    val tags: Set<String> = emptySet(),
    val type: SavedType? = null,
)

/**
 * Saved (#119): one list for everything saved, the same whichever service it is kept in. An item is
 * in exactly one place: To read, one folder, or the archive. Plain Kotlin, unit tested.
 */
/** What a note card shows under its title, see [Saved.cardBody]. */
sealed interface CardBody {
    val text: String

    data class Note(override val text: String) : CardBody

    data class Quote(override val text: String) : CardBody

    data class Description(override val text: String) : CardBody
}

/**
 * One note card (Noter's cards): a saved item, or a feed article shown the same way in Search's
 * notes view. [highlights] is how many; [liked] shows a heart.
 */
data class NoteContent(
    val title: String,
    val imageUrl: String?,
    val body: CardBody?,
    val host: String,
    val tags: List<String> = emptyList(),
    val highlights: Int = 0,
    val liked: Boolean = false,
)

object Saved {
    /**
     * Whether a tab shows its items as notes (cards) rather than as rows like the feeds. Only To
     * read is a list to read through; a folder and the Archive are collections.
     */
    @Suppress("UNUSED_PARAMETER")
    fun showsNotes(tab: SavedTab): Boolean = true

    /** A saved item as a note card. */
    fun note(item: Item): NoteContent = NoteContent(item.title, item.imageUrl, cardBody(item), item.host, item.tags, item.highlights.size, item.liked)

    /** A feed article as a note card (Search's notes view): the start of its text under the title. */
    fun note(article: Article): NoteContent =
        NoteContent(
            title = article.title,
            imageUrl = article.imageUrl,
            body = Html.snippet(article.html).takeIf { it.isNotBlank() }?.let(CardBody::Description),
            host = ItemKeys.hostOf(article.link),
        )

    /** The folder an item is in, or null (To read, archived, or not saved). */
    fun folderOf(item: Item): String? = item.folder?.takeIf { item.readingList?.state == ReadingState.ToRead }

    fun isIn(
        item: Item,
        tab: SavedTab,
    ): Boolean {
        val reading = item.readingList ?: return false
        return when (tab) {
            SavedTab.All -> true
            SavedTab.Archive -> reading.state == ReadingState.Archived
            SavedTab.ToRead -> reading.state == ReadingState.ToRead && item.folder == null
            is SavedTab.Folder -> folderOf(item).equals(tab.name, ignoreCase = true)
        }
    }

    /** The items the filter picks, newest saved first. */
    fun items(
        library: Library,
        filter: SavedFilter,
    ): List<Item> =
        library.items
            .filter { item ->
                isIn(item, filter.tab) &&
                    (!filter.likedOnly || item.liked) &&
                    filter.tags.all { t -> item.tags.any { it.equals(t, ignoreCase = true) } } &&
                    (filter.type == null || typeOf(item.url) == filter.type)
            }.sortedByDescending { it.readingList!!.addedAt }

    /** Every folder in use, sorted (case ignored). */
    /**
     * Folders became tags (#187): an item filed in a folder gets the folder's name as a tag instead,
     * and leaves the folder. The same library when nothing is in a folder. Applied when the library
     * is read and after every change, so a folder synced in from an older app or another device
     * becomes a tag too.
     */
    fun foldersToTags(library: Library): Library {
        if (library.items.none { it.folder != null }) return library
        return library.copy(
            items =
                library.items.map { item ->
                    val folder = item.folder?.trim() ?: return@map item
                    val tags = if (folder.isEmpty() || item.tags.any { it.equals(folder, ignoreCase = true) }) item.tags else item.tags + folder
                    item.copy(folder = null, tags = tags)
                },
        )
    }

    fun folders(library: Library): List<String> = library.items.mapNotNull(::folderOf).distinctBy { it.lowercase() }.sortedBy { it.lowercase() }

    /** How many items each folder has. */
    fun folderCounts(library: Library): Map<String, Int> = folders(library).associateWith { f -> library.items.count { folderOf(it).equals(f, ignoreCase = true) } }

    /** The tags in a tab, most used first, for its chips. */
    fun tags(
        library: Library,
        tab: SavedTab,
    ): List<String> =
        library.items
            .filter { isIn(it, tab) }
            .flatMap { it.tags }
            .groupBy { it.lowercase() }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, List<String>>> { it.value.size }.thenBy { it.key })
            .map { it.value.first() }

    /**
     * The states Saved's state chip offers (#205): To read, Archive or everything. A tag stays
     * chosen when the state changes, as a folder does with Unread and All in Feeds.
     */
    val states: List<SavedTab> = listOf(SavedTab.ToRead, SavedTab.Archive, SavedTab.All)

    /** How many liked items are still to read, for ☰'s Liked row (#206). */
    fun likedToReadCount(library: Library): Int = library.items.count { it.liked && isIn(it, SavedTab.ToRead) }

    /** How many items still to read carry each tag, for ☰ (as Feeds counts unread articles). */
    fun toReadTagCounts(library: Library): Map<String, Int> =
        tags(library, SavedTab.All).associateWith { tag -> library.items.count { item -> isIn(item, SavedTab.ToRead) && item.tags.any { it.equals(tag, ignoreCase = true) } } }

    /** The filter without a folder or tags that are gone (from all of Saved, not just the state shown). */
    fun shown(
        library: Library,
        filter: SavedFilter,
    ): SavedFilter {
        val tab = filter.tab
        val withTab = if (tab is SavedTab.Folder && folders(library).none { it.equals(tab.name, ignoreCase = true) }) filter.copy(tab = SavedTab.ToRead) else filter
        val tags = tags(library, SavedTab.All)
        val kept = withTab.tags.filterTo(LinkedHashSet()) { t -> tags.any { it.equals(t, ignoreCase = true) } }
        return if (kept.size == withTab.tags.size) withTab else withTab.copy(tags = kept)
    }

    /**
     * Saves a link from the save sheet (#119): To read, or straight into [folder], with a note and
     * tags. A link already saved moves there and keeps what it had. Null for addresses that aren't
     * web links.
     */
    fun save(
        library: Library,
        url: String,
        title: String?,
        now: Long,
        folder: String? = null,
        note: String = "",
        tags: List<String> = emptyList(),
    ): Pair<Library, Item?> {
        val (added, item) = library.addToReadingList(url, title, now)
        item ?: return library to null
        val updated =
            item.copy(
                folder = folder?.trim()?.ifEmpty { null },
                note = if (note.isBlank()) item.note else note.trim(),
                tags = cleanTags(item.tags + tags),
            )
        return added.putItem(updated) to updated
    }

    /** Puts an item in a folder, or To read with null (it stays saved; archived ones come back to read). */
    fun file(
        library: Library,
        key: String,
        folder: String?,
    ): Library =
        library.changeItem(key) { item ->
            val reading = item.readingList ?: return@changeItem item
            item.copy(readingList = reading.copy(state = ReadingState.ToRead), folder = folder?.trim()?.ifEmpty { null })
        }

    /** Removes an item from Saved, with its note, tags and highlights (the dialog says so). */
    fun remove(
        library: Library,
        key: String,
    ): Library = if (library.item(key) == null) library else library.copy(items = library.items.filter { it.key != key })

    /** Renames a folder on every item; renaming it to another folder's name joins the two. */
    fun renameFolder(
        library: Library,
        from: String,
        to: String,
    ): Library {
        val clean = to.trim()
        if (clean.isEmpty()) return library
        return library.copy(items = library.items.map { if (it.folder.equals(from, ignoreCase = true)) it.copy(folder = clean) else it })
    }

    /** Takes a folder away: its items go to the Archive. */
    fun removeFolder(
        library: Library,
        name: String,
    ): Library =
        library.copy(
            items =
                library.items.map {
                    // Its articles go to the Archive: filed away, they don't come back as unread work.
                    if (it.folder.equals(name, ignoreCase = true)) it.copy(folder = null, readingList = it.readingList?.copy(state = ReadingState.Archived)) else it
                },
        )

    /**
     * Moves bookmarks from before #119 into folders, once: a bookmark's collection becomes its
     * folder ([defaultFolder] without one), and it is saved to read there. Nothing else changes.
     */
    fun settle(
        library: Library,
        defaultFolder: String,
    ): Library {
        if (library.items.none { it.bookmark != null }) return library
        return library.copy(
            items =
                library.items.map { item ->
                    val bookmark = item.bookmark ?: return@map item
                    item.copy(
                        bookmark = null,
                        readingList = ReadingListEntry(ReadingState.ToRead, item.readingList?.addedAt ?: bookmark.addedAt),
                        folder = bookmark.collection?.trim()?.ifEmpty { null } ?: defaultFolder,
                    )
                },
        )
    }

    /**
     * The body of an item's note card (#133): your note, else the first highlight (shown
     * quoted, as the author's words), else the website's description; null when it has none.
     */
    fun cardBody(item: Item): CardBody? =
        when {
            item.note.isNotBlank() -> CardBody.Note(item.note.trim())
            item.highlights.any { it.quote.isNotBlank() } -> CardBody.Quote(item.highlights.first { it.quote.isNotBlank() }.quote.trim())
            item.description.isNotBlank() -> CardBody.Description(item.description.trim())
            else -> null
        }

    /** A link's type: a video site, a podcast or audio site, else an article. */
    fun typeOf(url: String): SavedType {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return SavedType.Article
        val host = uri.host.orEmpty().lowercase().removePrefix("www.").removePrefix("m.")
        val path = uri.path.orEmpty()
        return when {
            host in VIDEO_HOSTS || (host == "youtube.com" && (path.startsWith("/watch") || path.startsWith("/shorts") || path.startsWith("/live"))) -> SavedType.Video
            host in AUDIO_HOSTS || (host == "open.spotify.com" && (path.startsWith("/episode") || path.startsWith("/show"))) -> SavedType.Audio
            else -> SavedType.Article
        }
    }

    private val VIDEO_HOSTS = setOf("youtu.be", "vimeo.com", "dailymotion.com", "twitch.tv", "ted.com", "tiktok.com")
    private val AUDIO_HOSTS = setOf("podcasts.apple.com", "overcast.fm", "pca.st", "castro.fm", "soundcloud.com", "podcasts.google.com", "pod.link", "podbean.com", "anchor.fm")
}
