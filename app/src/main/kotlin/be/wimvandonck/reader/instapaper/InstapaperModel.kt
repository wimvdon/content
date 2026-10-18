package be.wimvandonck.reader.instapaper

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InstapaperUiState(
    val view: InstapaperView = InstapaperView.Home,
    val items: List<InstapaperBookmark> = emptyList(),
    val total: Int = 0,
    /** The chosen list is being loaded. */
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val folders: List<InstapaperFolder> = emptyList(),
    val tags: List<InstapaperTag> = emptyList(),
    val problem: InstapaperProblem? = null,
    /** The saved article open in the reader. */
    val open: InstapaperBookmark? = null,
    /** The article just saved from a feed, while the sheet to file it (folder and tags) is open. */
    val justSaved: InstapaperBookmark? = null,
) {
    val canLoadMore: Boolean get() = !loading && !loadingMore && items.size < total
}

/**
 * Instapaper in the app: one list at a time (shown like a feed's articles), the folders and tags for
 * the drawer and Manage, and the changes to saved articles, folders and tags. Plain Kotlin on top of [InstapaperService], so it runs in unit tests. After a change the
 * list is updated in place, without reading it again.
 */
class InstapaperModel(
    private val api: InstapaperService,
    private val scope: CoroutineScope,
    /** Called when Instapaper refuses the token, so the account can sign out. */
    private val onSignedOut: () -> Unit,
) {
    private val mutable = MutableStateFlow(InstapaperUiState())
    val state: StateFlow<InstapaperUiState> = mutable.asStateFlow()
    private var loading: Job? = null

    fun start() {
        refreshSides()
        load()
    }

    /**
     * Reads the folders, tags and the shown list again (pull to refresh). The list stays on screen
     * and counts as loading until the new one is in, so the refresh indicator stays put meanwhile.
     */
    fun refresh() {
        mutable.update { it.copy(loading = true, problem = null) }
        start()
    }

    fun select(view: InstapaperView) {
        mutable.update { it.copy(view = view, items = emptyList(), total = 0, loading = true, loadingMore = false, problem = null) }
        load()
    }

    fun loadMore() {
        val current = mutable.value
        if (!current.canLoadMore) return
        mutable.update { it.copy(loadingMore = true) }
        scope.launch {
            val page = guarded { api.list(current.view, current.items.size, InstapaperClient.PAGE_SIZE) } ?: return@launch
            mutable.update { now ->
                if (now.view != current.view) {
                    now
                } else {
                    now.copy(
                        items = now.items + page.bookmarks.filter { b -> now.items.none { it.id == b.id } },
                        total = page.total,
                        loadingMore = false,
                    )
                }
            }
        }
    }

    /**
     * Saves a feed article and keeps it as [InstapaperUiState.justSaved], so it can be put in a folder
     * and tagged at once. Throws when Instapaper refuses (the caller says why); returns whether it was saved.
     */
    suspend fun save(
        url: String,
        title: String?,
    ): Boolean {
        val bookmark =
            try {
                api.save(url, title)
            } catch (e: InstapaperException) {
                if (e.problem == InstapaperProblem.SignedOut) onSignedOut()
                throw e
            }
        if (bookmark != null) mutable.update { it.copy(justSaved = bookmark) }
        refreshSides()
        return true
    }

    /** The sheet for the article just saved was closed. */
    fun closeJustSaved() = mutable.update { it.copy(justSaved = null) }

    /** The saved article opened in the reader; it stays there when a change takes it out of the list. */
    fun open(bookmark: InstapaperBookmark) = mutable.update { it.copy(open = bookmark) }

    fun toggleArchive(bookmark: InstapaperBookmark) =
        change(bookmark) {
            api.move(bookmark.id, if (bookmark.archived) "home" else "archive")
            BookmarkChange.Archived(!bookmark.archived)
        }

    fun toggleLike(bookmark: InstapaperBookmark) = change(bookmark) { BookmarkChange.Liked(api.like(bookmark.id, !bookmark.liked)) }

    /** Moves to a folder, or back to Unread when [folderId] is null. */
    fun moveTo(
        bookmark: InstapaperBookmark,
        folderId: Long?,
    ) = change(bookmark) {
        api.move(bookmark.id, folderId?.toString() ?: "home")
        BookmarkChange.Moved(folderId)
    }

    fun delete(bookmark: InstapaperBookmark) =
        change(bookmark) {
            api.delete(bookmark.id)
            BookmarkChange.Deleted
        }

    fun addTag(
        bookmark: InstapaperBookmark,
        name: String,
    ) {
        val clean = name.trim()
        if (clean.isEmpty() || bookmark.tags.any { it.name.equals(clean, ignoreCase = true) }) return
        change(bookmark) { BookmarkChange.Tagged(api.setTags(bookmark.id, listOf(clean), emptyList())) }
    }

    fun removeTag(
        bookmark: InstapaperBookmark,
        tag: InstapaperTag,
    ) = change(bookmark) { BookmarkChange.Tagged(api.setTags(bookmark.id, emptyList(), listOf(tag.id))) }

    fun createFolder(title: String) =
        act {
            val clean = title.trim()
            if (clean.isEmpty()) return@act
            val folder = api.createFolder(clean)
            mutable.update { it.copy(folders = (it.folders + folder).sortedBy(InstapaperFolder::position)) }
        }

    /** Makes folder [title] and moves [bookmark] into it, from Move to folder (or the sheet after saving). */
    fun moveToNewFolder(
        bookmark: InstapaperBookmark,
        title: String,
    ) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        change(bookmark) {
            val folder = api.createFolder(clean)
            mutable.update { it.copy(folders = (it.folders + folder).sortedBy(InstapaperFolder::position)) }
            api.move(bookmark.id, folder.id.toString())
            BookmarkChange.Moved(folder.id)
        }
    }

    fun deleteFolder(folder: InstapaperFolder) =
        act {
            api.deleteFolder(folder.id)
            mutable.update { it.copy(folders = it.folders.filterNot { f -> f.id == folder.id }) }
            if ((mutable.value.view as? InstapaperView.InFolder)?.folderId == folder.id) select(InstapaperView.Home)
            refreshSides()
        }

    fun moveFolder(
        folder: InstapaperFolder,
        delta: Int,
    ) = act {
        val folders = mutable.value.folders
        val order = reorderedPositions(folders, folders.indexOfFirst { it.id == folder.id }, delta) ?: return@act
        val reordered = api.reorderFolders(order)
        mutable.update { it.copy(folders = reordered) }
    }

    fun renameTag(
        tag: InstapaperTag,
        name: String,
    ) = act {
        val clean = name.trim()
        if (clean.isEmpty() || clean == tag.name) return@act
        val renamed = api.renameTag(tag.id, clean)
        mutable.update { it.copy(tags = it.tags.map { t -> if (t.id == tag.id) renamed else t }) }
        if ((mutable.value.view as? InstapaperView.WithTag)?.name == tag.name) select(InstapaperView.WithTag(renamed.name)) else refreshSides()
    }

    private fun load() {
        loading?.cancel()
        val view = mutable.value.view
        loading =
            scope.launch {
                val page = guarded { api.list(view, 0, InstapaperClient.PAGE_SIZE) } ?: return@launch
                mutable.update { if (it.view == view) it.copy(items = page.bookmarks, total = page.total, loading = false) else it }
            }
    }

    /** Folders and tags change with moves and tagging, so they are read again after each change. */
    private fun refreshSides() {
        scope.launch {
            guarded {
                val folders = api.folders()
                val tags = api.tags()
                mutable.update { it.copy(folders = folders, tags = tags) }
            }
        }
    }

    private fun act(block: suspend () -> Unit) {
        mutable.update { it.copy(problem = null) }
        scope.launch { guarded(block) }
    }

    private suspend fun <T> guarded(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: InstapaperException) {
            mutable.update { it.copy(problem = e.problem, loading = false, loadingMore = false) }
            if (e.problem == InstapaperProblem.SignedOut) onSignedOut()
            null
        }

    /** Runs one change and updates the list, the open article and (counts change) the folders and tags. */
    private fun change(
        bookmark: InstapaperBookmark,
        call: suspend () -> BookmarkChange,
    ) = act {
        val change = call()
        mutable.update { s ->
            val items = InstapaperLists.apply(s.view, s.items, bookmark.id, change)
            s.copy(
                items = items,
                total = (s.total - (s.items.size - items.size)).coerceAtLeast(0),
                open = if (s.open?.id == bookmark.id) InstapaperLists.patch(s.open, change) else s.open,
                justSaved = if (s.justSaved?.id == bookmark.id) InstapaperLists.patch(s.justSaved, change) else s.justSaved,
            )
        }
        refreshSides()
    }
}

/** The positions (from 1) after moving the folder at [index] by [delta], or null at the ends. */
fun reorderedPositions(
    folders: List<InstapaperFolder>,
    index: Int,
    delta: Int,
): List<Pair<Long, Int>>? {
    val target = index + delta
    if (index !in folders.indices || target !in folders.indices) return null
    val next = folders.toMutableList()
    next.add(target, next.removeAt(index))
    return next.mapIndexed { i, folder -> folder.id to i + 1 }
}
