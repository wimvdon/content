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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    scope: CoroutineScope,
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

    /**
     * Downloads every feed (a few at a time) and merges the results. Returns the number of
     * feeds that failed. A refresh that is already running is not started twice.
     */
    suspend fun refreshAll(): Int {
        if (!refreshLock.tryLock()) return 0
        try {
            _refreshing.value = true
            val feeds = store.data.first().feeds
            val permits = Semaphore(PARALLEL_DOWNLOADS)
            val failures =
                coroutineScope {
                    feeds
                        .map { feed -> async { permits.withPermit { refresh(feed.url) } } }
                        .awaitAll()
                }
            return failures.count { !it }
        } finally {
            _refreshing.value = false
            refreshLock.unlock()
        }
    }

    /** Returns true when the feed was refreshed. */
    private suspend fun refresh(url: String): Boolean {
        val problem =
            try {
                val fetched = fetch(url)
                val parsed = FeedParser.parse(fetched.bytes, fetched.url)
                store.updateData { it.merge(url, parsed, clock()) }
                null
            } catch (e: FetchException) {
                e.error.toProblem()
            } catch (_: NotAFeedException) {
                FeedProblem.NotAFeed
            }
        _problems.update { if (problem == null) it - url else it + (url to problem) }
        return problem == null
    }

    suspend fun addFeed(input: String): AddFeedResult =
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
                        library.addFeed(feed).merge(feed.url, result.feed, clock())
                    }
                }
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

    suspend fun markAllRead(selection: FeedSelection) {
        store.updateData { it.markAllRead(selection) }
    }

    /** Adds the feeds of an OPML file and downloads them. Returns how many were new. */
    suspend fun importOpml(bytes: ByteArray): Int {
        val entries = Opml.parse(bytes)
        var added = 0
        store.updateData { library ->
            entries.fold(library) { current, entry ->
                if (current.feed(entry.url) != null) {
                    current
                } else {
                    added++
                    current.addFeed(Feed(url = entry.url, title = entry.title.ifBlank { entry.url }))
                }
            }
        }
        return added
    }

    suspend fun exportOpml(title: String): String =
        Opml.write(title, store.data.first().feeds.map { Opml.Entry(it.url, it.title) })

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
