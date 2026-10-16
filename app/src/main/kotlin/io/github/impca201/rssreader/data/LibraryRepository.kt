package io.github.impca201.rssreader.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import io.github.impca201.rssreader.feed.FeedFinder
import io.github.impca201.rssreader.feed.FeedParser
import io.github.impca201.rssreader.feed.FetchError
import io.github.impca201.rssreader.feed.FetchException
import io.github.impca201.rssreader.feed.Fetched
import io.github.impca201.rssreader.feed.FindResult
import io.github.impca201.rssreader.feed.NotAFeedException
import io.github.impca201.rssreader.feed.Opml
import io.github.impca201.rssreader.feed.ParsedFeed
import io.github.impca201.rssreader.feed.SiteIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
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
 * The user's feeds and articles: stored on the device with DataStore, refreshed from the
 * network on request.
 */
class LibraryRepository(
    context: Context,
    private val scope: CoroutineScope,
    private val fetch: suspend (String) -> Fetched,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val store: DataStore<Library> =
        DataStoreFactory.create(
            serializer = LibrarySerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { Library() },
            scope = scope,
            produceFile = { context.applicationContext.dataStoreFile("library.txt") },
        )

    /** `null` until read from storage. */
    val library: StateFlow<Library?> = store.data.stateIn(scope, SharingStarted.Eagerly, null)

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** Feeds whose last refresh failed, by address. Kept in memory only. */
    private val _problems = MutableStateFlow<Map<String, FeedProblem>>(emptyMap())
    val problems: StateFlow<Map<String, FeedProblem>> = _problems.asStateFlow()

    private val refreshLock = Mutex()

    /** Articles read on other devices (see sync/): when they arrive here, they arrive as read. */
    @Volatile
    var knownRead: Set<String> = emptySet()

    /**
     * Changes the stored library in one step and returns a value computed with it, for sync:
     * nothing else can change the library in between.
     */
    suspend fun <T> update(change: (Library) -> Pair<Library, T>): T {
        var result: T? = null
        store.updateData { library ->
            val (updated, value) = change(library)
            result = value
            updated
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
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
            val feeds = store.data.first().feeds
            val permits = Semaphore(PARALLEL_DOWNLOADS)
            val results =
                coroutineScope {
                    feeds
                        .map { feed -> async { permits.withPermit { feed.url to download(feed.url) } } }
                        .awaitAll()
                }
            val downloads = results.mapNotNull { (url, result) -> result.getOrNull()?.let { url to it } }.toMap()
            if (downloads.isNotEmpty()) store.updateData { it.mergeAll(downloads, clock(), knownRead) }
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
        val feeds = store.data.first().feeds.filter { it.iconUrl == null && iconsTried.add(it.url) }
        if (feeds.isEmpty()) return
        val permits = Semaphore(PARALLEL_DOWNLOADS)
        val found =
            coroutineScope {
                feeds
                    .map { feed -> async { permits.withPermit { feed.url to findIcon(feed) } } }
                    .awaitAll()
            }.mapNotNull { (url, icon) -> icon?.let { url to it } }
        if (found.isEmpty()) return
        store.updateData { library -> found.fold(library) { lib, (url, icon) -> lib.setFeedIcon(url, icon) } }
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
                store.updateData { library ->
                    if (library.feed(feed.url) != null) {
                        library
                    } else {
                        added = true
                        library.addFeed(feed.copy(folderId = folderId?.takeIf { library.folder(it) != null })).merge(feed.url, result.feed, clock(), knownRead)
                    }
                }
                // In the background: the dialog shouldn't wait for the website.
                if (added) scope.launch { findMissingIcons() }
                if (added) AddFeedResult.Added(feed) else AddFeedResult.AlreadyAdded
            }
        }

    suspend fun removeFeed(url: String) {
        store.updateData { it.removeFeed(url) }
        _problems.update { it - url }
    }

    suspend fun setRead(
        key: String,
        read: Boolean,
    ) {
        store.updateData { it.setRead(key, read) }
    }

    /** Adds the feeds of an OPML file, with their folders. Returns how many were new. */
    suspend fun importOpml(bytes: ByteArray): Int {
        val entries = Opml.parse(bytes)
        var added = 0
        store.updateData { library ->
            val (updated, count) = library.import(entries) { newFolderId() }
            added = count
            updated
        }
        return added
    }

    suspend fun exportOpml(title: String): String = Opml.write(title, store.data.first().toOpmlEntries())

    /** Creates a folder and returns its id. */
    suspend fun addFolder(
        title: String,
        icon: String,
    ): String {
        val id = newFolderId()
        store.updateData { it.addFolder(Folder(id, title, icon)) }
        return id
    }

    suspend fun updateFolder(
        id: String,
        title: String,
        icon: String,
    ) {
        store.updateData { it.updateFolder(id, title, icon) }
    }

    suspend fun removeFolder(id: String) {
        store.updateData { it.removeFolder(id) }
    }

    suspend fun moveFeed(
        url: String,
        folderId: String?,
    ) {
        store.updateData { it.moveFeed(url, folderId) }
    }

    suspend fun renameFeed(
        url: String,
        title: String,
    ) {
        store.updateData { it.renameFeed(url, title) }
    }

    /** Stores the order (and folders) the user dragged feeds and folders into. */
    suspend fun arrange(rows: List<FeedArrangement.Row>) {
        store.updateData { FeedArrangement.apply(it, rows) }
    }

    /** Marks the selection as read and returns what was unread, for Undo. */
    suspend fun markAllRead(selection: FeedSelection): Set<String> {
        var keys: Set<String> = emptySet()
        store.updateData { library ->
            keys = library.unreadKeys(selection)
            library.markAllRead(selection)
        }
        return keys
    }

    suspend fun setRead(
        keys: Set<String>,
        read: Boolean,
    ) {
        store.updateData { it.setRead(keys, read) }
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
    }
}

private object LibrarySerializer : Serializer<Library> {
    override val defaultValue = Library()

    override suspend fun readFrom(input: InputStream): Library =
        try {
            LibraryCodec.decode(input.readBytes().decodeToString())
        } catch (e: IOException) {
            throw CorruptionException("Unreadable library", e)
        }

    override suspend fun writeTo(
        t: Library,
        output: OutputStream,
    ) {
        output.write(LibraryCodec.encode(t).encodeToByteArray())
    }
}
