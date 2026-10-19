package be.wimvandonck.reader.services

import be.wimvandonck.reader.data.Anchors
import be.wimvandonck.reader.data.Highlight
import be.wimvandonck.reader.data.Item
import be.wimvandonck.reader.data.ReadingLists
import be.wimvandonck.reader.data.changeItem
import be.wimvandonck.reader.data.item
import be.wimvandonck.reader.feed.Html
import be.wimvandonck.reader.data.LibraryRepository
import be.wimvandonck.reader.instapaper.InstapaperAccount
import be.wimvandonck.reader.instapaper.InstapaperClient
import be.wimvandonck.reader.instapaper.InstapaperReadingList
import be.wimvandonck.reader.instapaper.InstapaperState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient

/** How the reading list's service is doing, for Settings › Connected services. */
sealed interface ServiceStatus {
    /** No service is connected: the reading list is Reader's own. */
    data object None : ServiceStatus

    data object Idle : ServiceStatus

    data object Syncing : ServiceStatus

    /** The last sync stopped early; what didn't get through goes next time. */
    data class Failed(
        val problem: ServiceProblem,
    ) : ServiceStatus
}

/**
 * Connects the reading list to its service while the app is in use (#109): Instapaper when signed
 * in, nothing otherwise. Syncs when started, a moment after the reading list changes, on refresh and
 * on request; never in the background. Signing in copies the reading list to the account and takes
 * in everything the account has; signing out forgets what was synced and keeps the reading list.
 */
class ServiceManager(
    private val scope: CoroutineScope,
    private val library: LibraryRepository,
    private val instapaper: InstapaperAccount,
    private val http: OkHttpClient,
    /** Copy highlights to the service (#102; a setting, off by default). */
    private val copyHighlights: () -> Boolean = { false },
) {
    private val _status = MutableStateFlow<ServiceStatus>(ServiceStatus.None)
    val status: StateFlow<ServiceStatus> = _status.asStateFlow()

    private val lock = Mutex()

    @Volatile
    private var service: ReadingListService? = null

    private var started = false

    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            instapaper.state
                .filter { it != InstapaperState.Loading }
                .map { (it as? InstapaperState.SignedIn)?.session?.token }
                .distinctUntilChanged()
                .collect { token ->
                    val before = service
                    service = token?.let { InstapaperReadingList(InstapaperClient(http, it)) }
                    // Signed out (or another account): what was synced belongs to the old sign-in.
                    if (before != null) lock.withLock { library.saveServiceState(before.id, library.serviceState(before.id), null) }
                    _status.value = if (service == null) ServiceStatus.None else ServiceStatus.Idle
                    if (service != null) sync()
                }
        }
        scope.launch {
            // A change to the reading list: its items' core, progress and highlights.
            library.library
                .filterNotNull()
                .map { lib -> lib.items.filter { it.readingList != null }.map(::core) }
                .distinctUntilChanged()
                .drop(1)
                .debounce(CHANGE_DELAY_MS)
                .collect { sync() }
        }
    }

    /** Syncs now, if a service is connected. */
    suspend fun sync() {
        val current = service ?: return
        lock.withLock {
            _status.value = ServiceStatus.Syncing
            val state = library.serviceState(current.id)
            val result =
                ServiceSync.run(
                    current,
                    state,
                    copyHighlights = copyHighlights(),
                    positionOf = { item, highlight -> positionIn(current, item, highlight) },
                ) { change -> library.edit(change) }
            library.saveServiceState(current.id, state, result.state)
            _status.value = result.problem?.let { ServiceStatus.Failed(it) } ?: ServiceStatus.Idle
            if (result.problem == ServiceProblem.SignedOut) instapaper.signOut()
        }
    }

    /**
     * The service's own text of an item (Instapaper's parsed text, own account only), kept on the
     * device as the item's "instapaper" text; null when the service has none.
     */
    suspend fun text(item: Item): String? {
        val current = service ?: return null
        if (ServiceFeature.Text !in current.features) return null
        library.texts(item.key)[current.id]?.let { return it }
        val remoteId = library.serviceState(current.id).rows[item.key]?.remoteId ?: return null
        val text = runCatching { current.text(remoteId) }.getOrNull() ?: return null
        library.putText(item.key, current.id, text)
        return text
    }

    /** Which occurrence of its text a highlight is, counted in the service's own text when it is kept here. */
    private suspend fun positionIn(
        service: ReadingListService,
        item: Item,
        highlight: Highlight,
    ): Int {
        val text = library.texts(item.key)[service.id] ?: return highlight.occurrence
        return Anchors.occurrenceIn(highlight, Html.toPlainText(text))
    }

    /**
     * Takes in an item's highlights made in the service's own apps (#102), when it is opened: one
     * request per article, so not for the whole list at every sync.
     */
    suspend fun takeInHighlights(item: Item) {
        val current = service ?: return
        if (ServiceFeature.Highlights !in current.features) return
        val remoteId = library.serviceState(current.id).rows[item.key]?.remoteId ?: return
        val remote = runCatching { current.highlights(remoteId) }.getOrNull() ?: return
        lock.withLock {
            val state = library.serviceState(current.id)
            val row = state.rows[item.key] ?: return
            var links = row.highlights
            library.edit { lib ->
                val now = lib.item(item.key) ?: return@edit lib
                val (highlights, newLinks) = HighlightSync.takeIn(current.id, now, remote, row.highlights)
                links = newLinks
                lib.changeItem(item.key) { it.copy(highlights = highlights) }
            }
            if (links != row.highlights) library.saveServiceState(current.id, state, state.copy(rows = state.rows + (item.key to row.copy(highlights = links))))
        }
    }

    /**
     * The matching test of #102 (test builds): for up to [limit] articles with highlights made in the
     * service, how each version of the text finds them: the service's text, the feed's, and Reader's
     * own extraction ([extract]). Newest articles first; at most [MAX_SCANNED] are asked for highlights.
     */
    suspend fun matchTest(
        limit: Int = 50,
        extract: suspend (String) -> String?,
        onProgress: (Int) -> Unit,
    ): MatchReport? {
        val current = service ?: return null
        if (ServiceFeature.Highlights !in current.features) return null
        val rows = library.serviceState(current.id).rows
        val lib = library.library.value ?: return null
        var report = MatchReport()
        var scanned = 0
        for ((key, row) in rows.entries.sortedByDescending { it.value.addedAt }) {
            if (report.articles >= limit || scanned >= MAX_SCANNED) break
            scanned++
            val remote = runCatching { current.highlights(row.remoteId) }.getOrNull().orEmpty()
            if (remote.isEmpty()) continue
            val texts = LinkedHashMap<String, String>()
            runCatching { current.text(row.remoteId) }.getOrNull()?.let { texts[current.id] = Html.toPlainText(it) }
            lib.item(key)?.let { ReadingLists.feedArticle(it, lib) }?.html?.takeIf { it.isNotBlank() }?.let { texts["feed"] = Html.toPlainText(it) }
            extract(row.url)?.let { texts["reader"] = Html.toPlainText(it) }
            report = report.add(remote.map(MatchReport::highlightOf), texts)
            onProgress(report.articles)
        }
        return report
    }

    /** The ids of an item's highlights the service refused (a plan's limit): shown as not there. */
    suspend fun highlightsNotCopied(itemKey: String): Set<String> {
        val current = service ?: return emptySet()
        return HighlightSync.notCopied(library.serviceState(current.id).rows[itemKey]?.highlights.orEmpty())
    }

    /** The connected service's name for the reader's text choice, or null. */
    val textSource: String? get() = service?.takeIf { ServiceFeature.Text in it.features }?.id

    private data class Core(
        val key: String,
        val state: Any?,
        val liked: Boolean,
        val tags: List<String>,
        val title: String,
        val progress: Int,
        val highlights: List<Pair<String, String>>,
    )

    private fun core(item: Item) = Core(item.key, item.readingList?.state, item.liked, item.tags, item.title, (item.progress * 100).toInt(), item.highlights.map { it.id to it.note })

    private companion object {
        /** A row of changes (archiving several, tagging) goes as one sync. */
        const val CHANGE_DELAY_MS = 2_000L

        /** The matching test asks at most this many articles for their highlights. */
        const val MAX_SCANNED = 300
    }
}
