package to.wim.content.data

/** Where the save sheet puts an article (#143): To read, one folder, or the Archive. */
sealed interface Place {
    data object ToRead : Place

    data class Folder(
        val name: String,
    ) : Place

    data object Archive : Place
}

/**
 * What the Save and share sheet says about an article (#143, #256): where it goes, whether it is
 * liked, its tags and its note, and who it is sent to with Content. [keep] is false for Don't save:
 * only sent, nothing lands in Saved (offered before the save only).
 */
data class SaveChoice(
    val place: Place = Place.ToRead,
    val liked: Boolean = false,
    val tags: List<String> = emptyList(),
    val note: String = "",
    val keep: Boolean = true,
    val sendTo: List<String> = emptyList(),
)

/**
 * The rules behind the one save and Organise sheet (#143), kept out of the composable so they run on
 * the JVM: which places to offer, what a new folder is, and where a save lands. Plain Kotlin, unit
 * tested.
 */
object SaveChoices {
    /** Where an item is now, and what it has: the sheet starts from this. */
    fun of(item: Item): SaveChoice =
        SaveChoice(
            place =
                when {
                    item.archived -> Place.Archive
                    else -> Saved.folderOf(item)?.let(Place::Folder) ?: Place.ToRead
                },
            liked = item.liked,
            tags = item.tags,
            note = item.note,
        )

    /**
     * The folder chips, in order (#156): To read and Archive, the two places every article can be,
     * then each folder in use and the one being made (not in use yet).
     */
    fun places(
        folders: List<String>,
        current: Place,
    ): List<Place> {
        val inUse = folders.map(Place::Folder)
        val made = (current as? Place.Folder)?.takeIf { c -> folders.none { it.equals(c.name, ignoreCase = true) } }
        return listOf(Place.ToRead, Place.Archive) + inUse + listOfNotNull(made)
    }

    /** A folder typed under New folder: the folder of that name when there is one (case ignored), null when blank. */
    fun newFolder(
        name: String,
        folders: List<String>,
    ): Place.Folder? {
        val clean = name.trim().ifEmpty { return null }
        return Place.Folder(folders.firstOrNull { it.equals(clean, ignoreCase = true) } ?: clean)
    }

    /**
     * Takes back a save that just happened (Remove on the sheet right after Save): the item as it
     * was before ([before]), or gone when it wasn't saved then.
     */
    fun undoSave(
        library: Library,
        key: String,
        before: Item?,
    ): Library =
        when {
            library.item(key) == null -> library
            before == null -> Saved.remove(library, key)
            else -> library.changeItem(key) { before }
        }

    /**
     * The choice with what is still typed in the sheet when Save is tapped: a new folder's name
     * (it goes there) and a tag's (it is added), so nothing typed is lost.
     */
    fun withTyped(
        choice: SaveChoice,
        folder: String,
        tag: String,
        folders: List<String>,
    ): SaveChoice {
        val place = newFolder(folder, folders) ?: choice.place
        val clean = tag.trim()
        val tags = if (clean.isEmpty() || choice.tags.any { it.equals(clean, ignoreCase = true) }) choice.tags else choice.tags + clean
        return if (place == choice.place && tags == choice.tags) choice else choice.copy(place = place, tags = tags)
    }

    /** The Save and Organise sheets' To read / Archive choice: whether it goes into the Archive. */
    fun archived(choice: SaveChoice): Boolean = choice.place == Place.Archive

    /**
     * To read or Archive picked in the sheet: into the Archive, or out of it into To read. An
     * article in an old folder that isn't archived stays in it when To read is picked.
     */
    fun withArchived(
        choice: SaveChoice,
        archived: Boolean,
    ): SaveChoice =
        when {
            archived -> if (choice.place == Place.Archive) choice else choice.copy(place = Place.Archive)
            choice.place == Place.Archive -> choice.copy(place = Place.ToRead)
            else -> choice
        }

    /** The tag on or off (case ignored), as the reader's Tags menu does it (#156). */
    fun withTag(
        choice: SaveChoice,
        tag: String,
    ): SaveChoice {
        val clean = tag.trim().ifEmpty { return choice }
        val has = choice.tags.any { it.equals(clean, ignoreCase = true) }
        return choice.copy(tags = if (has) choice.tags.filterNot { it.equals(clean, ignoreCase = true) } else choice.tags + clean)
    }

    /**
     * A topic renamed on the sheet's own Manage topics page (#307): the sheet's choice follows, so
     * Save doesn't bring the old name back as a topic of its own. Renamed to another topic's name,
     * the two are one tag. The same choice when it doesn't have the topic.
     */
    fun withTopicRenamed(
        choice: SaveChoice,
        from: String,
        to: String,
    ): SaveChoice {
        val clean = to.trim()
        if (clean.isEmpty() || choice.tags.none { it.trim().equals(from.trim(), ignoreCase = true) }) return choice
        fun isOld(tag: String) = tag.trim().equals(from.trim(), ignoreCase = true)
        // Joined with a topic the choice has already: that one stays, with its own spelling.
        val joined = choice.tags.any { !isOld(it) && it.trim().equals(clean, ignoreCase = true) }
        val tags = if (joined) choice.tags.filterNot(::isOld) else choice.tags.map { if (isOld(it)) clean else it }
        return choice.copy(tags = tags.distinctBy { it.trim().lowercase() })
    }

    /** A topic removed on the sheet's Manage topics page (#307): off the choice too. */
    fun withoutTopic(
        choice: SaveChoice,
        name: String,
    ): SaveChoice =
        if (choice.tags.none { it.trim().equals(name.trim(), ignoreCase = true) }) {
            choice
        } else {
            choice.copy(tags = choice.tags.filterNot { it.trim().equals(name.trim(), ignoreCase = true) })
        }

    /**
     * The tags the save sheet offers: every tag in use, then the item's own that aren't in use
     * elsewhere yet (a new one goes last), each once (case ignored). The order doesn't change when
     * a tag is turned on or off, so the chips stay put.
     */
    fun tagChoices(
        allTags: List<String>,
        tags: List<String>,
    ): List<String> = (allTags + tags).distinctBy { it.trim().lowercase() }

    /** What the reader's Tags button says (#156): the tags, else Liked, else null (just "Tags"). */
    fun tagsLabel(
        choice: SaveChoice,
        liked: String,
    ): String? =
        when {
            choice.tags.isNotEmpty() -> choice.tags.joinToString(", ")
            choice.liked -> liked
            else -> null
        }

    fun same(
        a: Place,
        b: Place,
    ): Boolean = if (a is Place.Folder && b is Place.Folder) a.name.equals(b.name, ignoreCase = true) else a == b

    /**
     * Saves a link that wasn't saved yet (a share, Add link): where the sheet says, liked or not, with
     * its tags and note. Null for addresses that aren't web links.
     */
    fun saveNew(
        library: Library,
        url: String,
        title: String?,
        now: Long,
        choice: SaveChoice,
    ): Pair<Library, Item?> {
        val folder = (choice.place as? Place.Folder)?.name
        val (saved, item) = Saved.save(library, url, title, now, folder, choice.note, choice.tags)
        item ?: return library to null
        // A link saved before keeps its like; the sheet only adds one.
        val placed = apply(saved, item.key, choice.copy(liked = choice.liked || item.liked, tags = item.tags, note = item.note))
        return placed to placed.item(item.key)
    }

    /**
     * Writes what the sheet says about an item already saved (the reader, a card's menu, Organise): its
     * place, like, tags and note. Nothing else changes, and an unchanged choice changes nothing.
     */
    fun apply(
        library: Library,
        key: String,
        choice: SaveChoice,
    ): Library {
        val item = library.item(key) ?: return library
        if (item.readingList == null) return library
        val placed =
            when (val place = choice.place) {
                Place.Archive -> if (item.archived) library else library.setReadingState(key, ReadingState.Archived)
                Place.ToRead -> if (!item.archived && Saved.folderOf(item) == null) library else Saved.file(library, key, null)
                is Place.Folder -> if (!item.archived && Saved.folderOf(item).equals(place.name, ignoreCase = true)) library else Saved.file(library, key, place.name)
            }
        return placed.changeItem(key) {
            val tags = cleanTags(choice.tags)
            val note = choice.note.trim()
            if (it.liked == choice.liked && it.tags == tags && it.note == note) it else it.copy(liked = choice.liked, tags = tags, note = note)
        }
    }
}
