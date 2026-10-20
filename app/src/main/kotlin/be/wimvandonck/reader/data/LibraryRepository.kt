package be.wimvandonck.reader.data

import be.wimvandonck.reader.R
import android.content.Context
import be.wimvandonck.reader.data.db.ItemTextRow
import be.wimvandonck.reader.data.db.LibraryDao
import be.wimvandonck.reader.data.db.LibraryDatabase
import be.wimvandonck.reader.data.db.ServiceRowRow
import be.wimvandonck.reader.data.db.SyncRowRow
import be.wimvandonck.reader.feed.FeedFinder
import be.wimvandonck.reader.feed.FeedParser
import be.wimvandonck.reader.feed.FetchError
import be.wimvandonck.reader.feed.FetchException
import be.wimvandonck.reader.feed.Fetched
import be.wimvandonck.reader.feed.FindResult
import be.wimvandonck.reader.feed.NotAFeedException
import be.wimvandonck.reader.feed.Opml
import be.wimvandonck.reader.feed.ParsedFeed
import be.wimvandonck.reader.feed.PageInfo
import be.wimvandonck.reader.feed.SiteIcon
import be.wimvandonck.reader.services.ServiceRows
import be.wimvandonck.reader.services.ServiceState
import be.wimvandonck.reader.sync.ItemRow
import be.wimvandonck.reader.sync.ItemSyncState
import be.wimvandonck.reader.sync.Json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.Collections
import java.util.UUID

/** Why a feed could not be refreshed. */
enum class FeedProblem { Offline, NotFound, ServerError, TooLarge, NotAFeed }

/** Result of adding a feed by address. */
sealed interface AddFeedResult {
    data class Added(val feed: Feed) : AddFeedResult

    data object AlreadyAdded : AddFeedResult

    data object InvalidAddress : AddFeedResult

    data object NoFeed : AddFeedResult

    data class Failed(val problem: FeedProblem) : AddFeedResult
}

/**
 * The user's library (feeds and articles, the reading list and Bookmarks): kept in a database on
 * the device (Room, data/db), held in memory as one [Library] value, refreshed from the network on
 * request. Every change goes through [update], one at a time, and writes only what changed.
 */
class LibraryRepository(
    context: Context,
    private val scope: CoroutineScope,
    private val fetch: suspend (String) -> Fetched,
    private val clock: () -> Long = System::currentTimeMillis,
    /** The library from before #106 (feeds and articles, and Saved without an account), moved in on first start. */
    private val legacy: LegacyLibrary = LegacyLibrary(context),
) {
    private val database = LibraryDatabase.open(context)

    /** Where bookmarks without a collection go when they move into Saved's folders (#119). */
    private val bookmarksFolder = context.getString(R.string.bookmarks_title)
    private val dao = database.dao()

    private val state = MutableStateFlow<Library?>(null)

    /** `null` until read from storage. */
    val library: StateFlow<Library?> = state.asStateFlow()

    /** One change at a time, so a change always starts from the library the previous one left. */
    private val writeLock = Mutex()

    init {
        scope.launch {
            state.value = writeLock.withLock { load() }
            dao.deleteServiceTexts()
            // A rough word count from each kept text's length, for reading times; exact once a text is read again.
            _textWords.value = dao.textLengths().associate { it.itemKey to it.length / HTML_CHARS_PER_WORD }
        }
    }

    private suspend fun load(): Library {
        if (dao.meta(MIGRATED) == null) {
            val moved = legacy.read()
            dao.replaceAll(LibraryChanges.all(moved), MIGRATED)
            legacy.delete()
            return moved
        }
        legacy.delete()
        val read = LibraryRows.library(dao.read())
        // Bookmarks from before #119 move into folders once.
        val settled = Saved.settle(read, bookmarksFolder)
        if (settled !== read) dao.write(LibraryChanges.between(read, settled))
        return settled
    }

    /** The library once read from storage. */
    suspend fun current(): Library = state.filterNotNull().first()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** Feeds whose last refresh failed, by address. Kept in memory only. */
    private val _problems = MutableStateFlow<Map<String, FeedProblem>>(emptyMap())
    val problems: StateFlow<Map<String, FeedProblem>> = _problems.asStateFlow()

    private val refreshLock = Mutex()


    /**
     * Changes the stored library in one step and returns a value computed with it, for sync:
     * nothing else can change the library in between. Only what changed is written.
     */
    suspend fun <T> update(change: (Library) -> Pair<Library, T>): T =
        writeLock.withLock {
            val old = current()
            val (changed, value) = change(old)
            // A bookmark synced in from a device that hasn't moved to folders yet moves in too.
            val updated = Saved.settle(changed, bookmarksFolder)
            if (updated !== old) {
                val changes = LibraryChanges.between(old, updated)
                if (!changes.isEmpty) dao.write(changes)
                state.value = updated
            }
            value
        }

    /** Changes the stored library in one step and returns it. */
    suspend fun edit(change: (Library) -> Library): Library = update { library -> change(library).let { it to it } }

    /**
     * Fills in a link saved without a title (an added or shared link): the page's title, description
     * and picture, read from the page itself. Leaves the item alone when the page can't be read.
     */
    suspend fun describe(itemKey: String) {
        val item = current().item(itemKey) ?: return
        if (item.title != item.url) return
        val info =
            try {
                val page = fetch(item.url)
                PageInfo.parse(page.bytes.decodeToString(), page.url)
            } catch (_: FetchException) {
                return
            }
        edit { library ->
            library.changeItem(itemKey) { i ->
                if (i.title != i.url) {
                    i
                } else {
                    i.copy(
                        title = info.title ?: i.title,
                        description = i.description.ifBlank { info.description.orEmpty() },
                        imageUrl = i.imageUrl ?: info.imageUrl,
                    )
                }
            }
        }
    }

    /** About how many words the longest text kept for each item has, by item key, for reading times. */
    private val _textWords = MutableStateFlow<Map<String, Int>>(emptyMap())
    val textWords: StateFlow<Map<String, Int>> = _textWords.asStateFlow()

    /** What this device last synced of the account's items (sync/ItemSync.kt). */
    suspend fun itemSyncState(): ItemSyncState {
        val rows =
            dao.syncRows().mapNotNull { row ->
                @Suppress("UNCHECKED_CAST")
                val data = runCatching { Json.parse(row.data) }.getOrNull() as? Map<String, Any?> ?: return@mapNotNull null
                row.key to ItemRow(row.key, row.version, data)
            }.toMap()
        return ItemSyncState(dao.meta(LibraryDao.ITEMS_VERSION)?.toLongOrNull() ?: 0, rows)
    }

    /** Stores [next] after a sync that started from [previous], writing only the rows that changed. */
    suspend fun saveItemSyncState(
        previous: ItemSyncState,
        next: ItemSyncState,
    ) {
        // Deleted rows aren't kept: saving that key again later costs one conflict, which brings the deletion back.
        val put = next.rows.values.filter { it.data != null && previous.rows[it.key] != it }.map { SyncRowRow(it.key, it.version, Json.write(it.data)) }
        val delete = previous.rows.keys.filter { next.rows[it]?.data == null }
        dao.writeSyncRows(put, delete, next.version)
    }

    /** What this device last saw of the reading list at [service] (services/ServiceSync.kt). */
    suspend fun serviceState(service: String): ServiceState {
        val rows = dao.serviceRows(service).mapNotNull { row -> ServiceRows.decode(row.data)?.let { row.itemKey to it } }.toMap()
        return ServiceState(dao.meta("$service-cursor"), rows, ServiceRows.decodeOrigins(dao.meta("$service-origins")))
    }

    /** Stores [next] after a service sync that started from [previous]; null forgets the service (signed out). */
    suspend fun saveServiceState(
        service: String,
        previous: ServiceState,
        next: ServiceState?,
    ) {
        if (next == null) {
            dao.writeServiceRows(service, emptyList(), emptyList(), null)
            return
        }
        val put = next.rows.filter { (key, entry) -> previous.rows[key] != entry }.map { (key, entry) -> ServiceRowRow(service, key, ServiceRows.encode(entry)) }
        val delete = previous.rows.keys.filter { it !in next.rows }
        dao.writeServiceRows(service, put, delete, next.cursor ?: "", ServiceRows.encodeOrigins(next.origins).takeIf { next.origins != previous.origins })
    }

    /** Forgets what was synced of the items (signed out, or another account). */
    suspend fun forgetItemSync() {
        dao.writeSyncRows(emptyList(), emptyList(), 0, clear = true)
    }

    suspend fun putText(
        itemKey: String,
        source: String,
        html: String,
    ) {
        dao.putText(ItemTextRow(itemKey, source, html, clock()))
        val words = ReadingLists.words(html)
        _textWords.update { if ((it[itemKey] ?: 0) >= words) it else it + (itemKey to words) }
    }

    /**
     * Downloads every feed (a few at a time) and then merges all results at once, so the list
     * changes in one step, in date order. Returns the number of feeds that failed. A refresh that
     * is already running is not started twice.
     */
    suspend fun refreshAll(): Int {
        if (!refreshLock.tryLock()) return 0
        try {
            _refreshing.value = true
            val feeds = current().feeds
            val permits = Semaphore(PARALLEL_DOWNLOADS)
            val results =
                coroutineScope {
                    feeds
                        .map { feed -> async { permits.withPermit { feed.url to download(feed.url) } } }
                        .awaitAll()
                }
            val downloads = results.mapNotNull { (url, result) -> result.getOrNull()?.let { url to it } }.toMap()
            if (downloads.isNotEmpty()) edit { it.mergeAll(downloads, clock()) }
            findMissingIcons()
            val problems = results.mapNotNull { (url, result) -> result.exceptionOrNull()?.let { url to (it as ProblemException).problem } }.toMap()
            _problems.value = problems
            return problems.size
        } finally {
            _refreshing.value = false
            refreshLock.unlock()
        }
    }

    /** Feeds whose icon was looked for in this app session, so a site without one isn't asked every time. */
    private val iconsTried: MutableSet<String> = Collections.synchronizedSet(HashSet())

    /**
     * Looks up the website icon of feeds that have none yet (a few at a time): the icon the home
     * page announces, or else the site's /favicon.ico.
     */
    private suspend fun findMissingIcons() {
        val feeds = current().feeds.filter { it.iconUrl == null && iconsTried.add(it.url) }
        if (feeds.isEmpty()) return
        val permits = Semaphore(PARALLEL_DOWNLOADS)
        val found =
            coroutineScope {
                feeds
                    .map { feed -> async { permits.withPermit { feed.url to findIcon(feed) } } }
                    .awaitAll()
            }.mapNotNull { (url, icon) -> icon?.let { url to it } }
        if (found.isEmpty()) return
        edit { library -> found.fold(library) { lib, (url, icon) -> lib.setFeedIcon(url, icon) } }
    }

    private suspend fun findIcon(feed: Feed): String? {
        val home = SiteIcon.homePage(feed.siteUrl, feed.url) ?: return null
        return try {
            val page = fetch(home)
            SiteIcon.fromPage(page.bytes.decodeToString(), page.url) ?: SiteIcon.fallback(page.url)
        } catch (_: FetchException) {
            null
        }
    }

    private class ProblemException(
        val problem: FeedProblem,
    ) : Exception()

    private suspend fun download(url: String): Result<ParsedFeed> =
        try {
            val fetched = fetch(url)
            Result.success(FeedParser.parse(fetched.bytes, fetched.url))
        } catch (e: FetchException) {
            Result.failure(ProblemException(e.error.toProblem()))
        } catch (_: NotAFeedException) {
            Result.failure(ProblemException(FeedProblem.NotAFeed))
        }

    /** Finds and adds the feed behind [input], in [folderId] when given (the folder being viewed). */
    suspend fun addFeed(
        input: String,
        folderId: String? = null,
    ): AddFeedResult =
        when (val result = FeedFinder(fetch).find(input)) {
            FindResult.InvalidAddress -> AddFeedResult.InvalidAddress

            FindResult.NoFeed -> AddFeedResult.NoFeed

            is FindResult.Failed -> AddFeedResult.Failed(result.error.toProblem())

            is FindResult.Found -> {
                val feed = Feed(url = result.url, title = result.feed.title.ifBlank { result.url }, siteUrl = result.feed.siteUrl)
                var added = false
                edit { library ->
                    if (library.feed(feed.url) != null) {
                        library
                    } else {
                        added = true
                        library.addFeed(feed.copy(folderId = folderId?.takeIf { library.folder(it) != null })).merge(feed.url, result.feed, clock())
                    }
                }
                // In the background: the dialog shouldn't wait for the website.
                if (added) scope.launch { findMissingIcons() }
                if (added) AddFeedResult.Added(feed) else AddFeedResult.AlreadyAdded
            }
        }

    suspend fun removeFeed(url: String) {
        edit { it.removeFeed(url) }
        _problems.update { it - url }
    }

    suspend fun setRead(
        key: String,
        read: Boolean,
    ) {
        edit { it.setRead(key, read) }
    }

    /** Adds the feeds of an OPML file, with their folders. Returns how many were new. */
    suspend fun importOpml(bytes: ByteArray): Int {
        val entries = Opml.parse(bytes)
        var added = 0
        edit { library ->
            val (updated, count) = library.import(entries) { newFolderId() }
            added = count
            updated
        }
        return added
    }

    suspend fun exportOpml(title: String): String = Opml.write(title, current().toOpmlEntries())

    /** Creates a folder and returns its id. */
    suspend fun addFolder(
        title: String,
        icon: String,
    ): String {
        val id = newFolderId()
        edit { it.addFolder(Folder(id, title, icon)) }
        return id
    }

    suspend fun updateFolder(
        id: String,
        title: String,
        icon: String,
    ) {
        edit { it.updateFolder(id, title, icon) }
    }

    suspend fun removeFolder(id: String) {
        edit { it.removeFolder(id) }
    }

    suspend fun moveFeed(
        url: String,
        folderId: String?,
    ) {
        edit { it.moveFeed(url, folderId) }
    }

    suspend fun renameFeed(
        url: String,
        title: String,
    ) {
        edit { it.renameFeed(url, title) }
    }

    /** Stores the order (and folders) the user dragged feeds and folders into. */
    suspend fun arrange(rows: List<FeedArrangement.Row>) {
        edit { FeedArrangement.apply(it, rows) }
    }

    /** Marks the selection as read and returns what was unread, for Undo. */
    suspend fun markAllRead(selection: FeedSelection): Set<String> {
        var keys: Set<String> = emptySet()
        edit { library ->
            keys = library.unreadKeys(selection)
            library.markAllRead(selection)
        }
        return keys
    }

    suspend fun setRead(
        keys: Set<String>,
        read: Boolean,
    ) {
        edit { it.setRead(keys, read) }
    }

    private fun newFolderId(): String = UUID.randomUUID().toString()

    private fun FetchError.toProblem(): FeedProblem =
        when (this) {
            FetchError.Offline -> FeedProblem.Offline
            FetchError.NotFound -> FeedProblem.NotFound
            FetchError.ServerError -> FeedProblem.ServerError
            FetchError.TooLarge -> FeedProblem.TooLarge
        }

    private companion object {
        const val PARALLEL_DOWNLOADS = 4

        /** Article HTML has about this many characters per word, tags included. */
        const val HTML_CHARS_PER_WORD = 7
    }
}

private const val MIGRATED = "migrated-from-library-txt"
