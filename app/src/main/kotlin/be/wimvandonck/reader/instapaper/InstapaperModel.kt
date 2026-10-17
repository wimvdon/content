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
) {
    val canLoadMore: Boolean get() = !loading && !loadingMore && items.size < total
}

/**
 * Everything the Instapaper screen does: show one list at a time, and change bookmarks, folders and
 * tags. Plain Kotlin on top of [InstapaperService], so it runs in unit tests. After a change the
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

    fun toggleArchive(bookmark: InstapaperBookmark) =
        act {
            api.move(bookmark.id, if (bookmark.archived) "home" else "archive")
            when (mutable.value.view) {
                InstapaperView.Home, InstapaperView.Archive, is InstapaperView.InFolder -> drop(bookmark.id)
                else -> patch(bookmark.id) { it.copy(archived = !bookmark.archived) }
            }
            refreshSides()
        }

    fun toggleLike(bookmark: InstapaperBookmark) =
        act {
            val liked = api.like(bookmark.id, !bookmark.liked)
            if (mutable.value.view == InstapaperView.Liked && !liked) drop(bookmark.id) else patch(bookmark.id) { it.copy(liked = liked) }
        }

    /** Moves to a folder, or back to Unread when [folderId] is null. */
    fun moveTo(
        bookmark: InstapaperBookmark,
        folderId: Long?,
    ) = act {
        api.move(bookmark.id, folderId?.toString() ?: "home")
        when (mutable.value.view) {
            InstapaperView.Home, is InstapaperView.InFolder -> drop(bookmark.id)
            else -> patch(bookmark.id) { it.copy(folderId = folderId, archived = false) }
        }
        refreshSides()
    }

    fun delete(bookmark: InstapaperBookmark) =
        act {
            api.delete(bookmark.id)
            drop(bookmark.id)
            refreshSides()
        }

    fun addTag(
        bookmark: InstapaperBookmark,
        name: String,
    ) = act {
        val clean = name.trim()
        if (clean.isEmpty() || bookmark.tags.any { it.name.equals(clean, ignoreCase = true) }) return@act
        setTags(bookmark, api.setTags(bookmark.id, listOf(clean), emptyList()))
    }

    fun removeTag(
        bookmark: InstapaperBookmark,
        tag: InstapaperTag,
    ) = act { setTags(bookmark, api.setTags(bookmark.id, emptyList(), listOf(tag.id))) }

    fun createFolder(title: String) =
        act {
            val clean = title.trim()
            if (clean.isEmpty()) return@act
            val folder = api.createFolder(clean)
            mutable.update { it.copy(folders = (it.folders + folder).sortedBy(InstapaperFolder::position)) }
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

    private fun drop(id: Long) = mutable.update { it.copy(items = it.items.filterNot { b -> b.id == id }, total = (it.total - 1).coerceAtLeast(0)) }

    private fun patch(
        id: Long,
        change: (InstapaperBookmark) -> InstapaperBookmark,
    ) = mutable.update { it.copy(items = it.items.map { b -> if (b.id == id) change(b) else b }) }

    private fun setTags(
        bookmark: InstapaperBookmark,
        tags: List<InstapaperTag>,
    ) {
        val view = mutable.value.view
        if (view is InstapaperView.WithTag && tags.none { it.name == view.name }) drop(bookmark.id) else patch(bookmark.id) { it.copy(tags = tags) }
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
