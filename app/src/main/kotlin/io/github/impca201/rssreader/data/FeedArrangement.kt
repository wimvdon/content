package io.github.impca201.rssreader.data

/**
 * The Manage feeds and folders page as a flat list that can be reordered by dragging: each
 * folder's header followed by its feeds, then "No folder" and the feeds outside folders. Where a
 * feed ends up decides its folder: the nearest folder header above it.
 */
object FeedArrangement {
    sealed interface Row {
        val key: String

        data class FolderHeader(
            val folder: Folder,
        ) : Row {
            override val key get() = "folder:" + folder.id
        }

        data class FeedItem(
            val feed: Feed,
        ) : Row {
            override val key get() = "feed:" + feed.url
        }

        /** The header above the feeds that are in no folder (only shown when there are folders). */
        data object Unfiled : Row {
            override val key get() = "unfiled"
        }
    }

    /** The rows for [library]; with [foldersOnly], just the folder headers (while a folder is dragged). */
    fun rows(
        library: Library,
        foldersOnly: Boolean = false,
    ): List<Row> =
        buildList {
            val folderIds = library.folders.map { it.id }.toSet()
            library.folders.forEach { folder ->
                add(Row.FolderHeader(folder))
                if (!foldersOnly) library.feeds.filter { it.folderId == folder.id }.forEach { add(Row.FeedItem(it)) }
            }
            if (!foldersOnly) {
                val loose = library.feeds.filter { it.folderId == null || it.folderId !in folderIds }
                if (library.folders.isNotEmpty()) add(Row.Unfiled)
                loose.forEach { add(Row.FeedItem(it)) }
            }
        }

    /**
     * Moves the row at [from] to [to]. Feeds can go anywhere below the first header (so they
     * always land in a folder or under "No folder"); folder headers move among headers only, in
     * the folders-only list; "No folder" doesn't move. Returns [rows] unchanged when the move
     * isn't allowed.
     */
    fun move(
        rows: List<Row>,
        from: Int,
        to: Int,
    ): List<Row> {
        if (from !in rows.indices || to !in rows.indices || from == to) return rows
        val row = rows[from]
        val allowed =
            when (row) {
                Row.Unfiled -> false
                is Row.FolderHeader -> rows.all { it is Row.FolderHeader }
                // With folders, the first row is a header and a feed can't go above it.
                is Row.FeedItem -> to > 0 || rows.all { it is Row.FeedItem }
            }
        if (!allowed) return rows
        return rows.toMutableList().apply { add(to, removeAt(from)) }
    }

    /**
     * The library arranged like [rows]: folders in header order, and when the rows hold feeds,
     * feeds in row order, each in the folder above it. Feeds missing from the rows keep their
     * place at the end.
     */
    fun apply(
        library: Library,
        rows: List<Row>,
    ): Library {
        val folders = rows.filterIsInstance<Row.FolderHeader>().map { header -> library.folder(header.folder.id) ?: header.folder }
        val orderedFolders = folders + library.folders.filter { f -> folders.none { it.id == f.id } }
        if (rows.none { it is Row.FeedItem }) return library.copy(folders = orderedFolders)
        var folderId: String? = null
        val placed =
            rows.mapNotNull { row ->
                when (row) {
                    is Row.FolderHeader -> {
                        folderId = row.folder.id
                        null
                    }

                    Row.Unfiled -> {
                        folderId = null
                        null
                    }

                    is Row.FeedItem -> library.feed(row.feed.url)?.copy(folderId = folderId)
                }
            }
        val rest = library.feeds.filter { feed -> placed.none { it.url == feed.url } }
        return library.copy(folders = orderedFolders, feeds = placed + rest)
    }
}
