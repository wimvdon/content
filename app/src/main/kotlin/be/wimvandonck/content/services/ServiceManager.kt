package be.wimvandonck.content.services

import be.wimvandonck.content.data.Item
import be.wimvandonck.content.data.LibraryRepository
import be.wimvandonck.content.data.changeItem
import be.wimvandonck.content.data.ServiceImport
import be.wimvandonck.content.data.item
import be.wimvandonck.content.instapaper.InstapaperAccount
import be.wimvandonck.content.instapaper.InstapaperClient
import be.wimvandonck.content.instapaper.InstapaperReadingList
import be.wimvandonck.content.instapaper.InstapaperState
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient

/** How a connected service is doing, for Settings › Connected services. */
sealed interface ServiceStatus {
    /** Not connected. */
    data object None : ServiceStatus

    data object Idle : ServiceStatus

    data object Syncing : ServiceStatus

    /** Just connected: waits for the user to say whether to import what the service has (#251). */
    data object ImportChoice : ServiceStatus

    /** The last sync stopped early; what didn't get through goes next time. */
    data class Failed(
        val problem: ServiceProblem,
    ) : ServiceStatus
}

/**
 * Connects Saved to its services while the app is in use (#109, #145): Instapaper, when connected.
 * Connecting asks once whether to import what the service has ([chooseImport], #251); from then on
 * everything in Saved syncs both ways, highlights included (#164). Syncs when started, a moment
 * after Saved changes, on refresh and on request; never in the background. Disconnecting forgets
 * what was synced and keeps Saved.
 */
class ServiceManager(
    private val scope: CoroutineScope,
    private val library: LibraryRepository,
    private val instapaper: InstapaperAccount,
    private val http: OkHttpClient,
    /** What was imported from each service, or null before its import (a setting, #145). */
    private val importOf: (String) -> ServiceImport? = { null },
    /** Records an import, or forgets it (null) when the service is disconnected. */
    private val recordImport: suspend (String, ServiceImport?) -> Unit = { _, _ -> },
    /** The text Content shows for a saved item, sent with it to a service that takes it (#247). */
    private val textOf: suspend (Item) -> String? = { null },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _statuses = MutableStateFlow<Map<String, ServiceStatus>>(emptyMap())

    /** Each connected service's status, by id. */
    val statuses: StateFlow<Map<String, ServiceStatus>> = _statuses.asStateFlow()

    private val _notCopied = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    /** The highlights a service refused for its plan's limit (#164), by item key: "Not in Instapaper". */
    val notCopied: StateFlow<Map<String, Set<String>>> = _notCopied.asStateFlow()

    private val lock = Mutex()

    /** The connected services, by id, in a fixed order (Instapaper first). */
    @Volatile
    private var services: Map<String, ReadingListService> = emptyMap()

    /** When each saved item last changed here, by key, for "the later change wins" (in memory). */
    private val editedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

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
                    connect(InstapaperReadingList.ID, token?.let { InstapaperReadingList(InstapaperClient(http, it)) })
                    if (services.isNotEmpty()) sync()
                }
        }
        scope.launch {
            // A change to Saved: its items' core, progress and note. Each changed item is stamped.
            var before: Map<String, Core>? = null
            library.library
                .filterNotNull()
                .map { lib -> lib.items.filter { it.readingList != null }.associate { it.key to core(it) } }
                .distinctUntilChanged()
                .onEach { now ->
                    val previous = before
                    if (previous != null) {
                        val time = clock()
                        for ((key, c) in now) if (previous[key] != c) editedAt[key] = time
                    }
                    before = now
                }.drop(1)
                .debounce(CHANGE_DELAY_MS)
                .collect { sync(full = false) }
        }
    }

    private suspend fun connect(
        id: String,
        service: ReadingListService?,
    ) {
        val before = services[id]
        services = (services - id).let { if (service != null) it + (id to service) else it }.toSortedMap(compareBy { ORDER.indexOf(it) }).toMap()
        // Disconnected (or another account): what was synced, and the import, belong to the old connection.
        if (before != null) {
            lock.withLock { library.saveServiceState(id, library.serviceState(id), null) }
            if (service == null) recordImport(id, null)
        }
        _statuses.update { if (service == null) it - id else it + (id to ServiceStatus.Idle) }
        lock.withLock { refreshNotCopied() }
    }

    private suspend fun refreshNotCopied() {
        val notCopied = HashMap<String, MutableSet<String>>()
        for (id in services.keys) {
            val state = library.serviceState(id)
            for ((key, links) in state.highlights) {
                val refused = HighlightCopy.notCopied(links)
                if (refused.isNotEmpty()) notCopied.getOrPut(key) { HashSet() } += refused
            }
        }
        _notCopied.value = notCopied
    }

    /** The connected services' ids, Instapaper first. */
    val connected: List<String> get() = services.keys.toList()

    /**
     * Syncs every connected service; [full] is false for the quick sync after a change. A service
     * just connected waits until [chooseImport] says whether to bring in what it has.
     */
    suspend fun sync(full: Boolean = true) {
        for ((id, service) in services) {
            lock.withLock {
                val state = library.serviceState(id)
                if (importOf(id) == null && state.rows.isEmpty()) {
                    _statuses.update { it + (id to ServiceStatus.ImportChoice) }
                    return@withLock
                }
                _statuses.update { it + (id to ServiceStatus.Syncing) }
                val elsewhere = HashSet<String>()
                for (other in services.keys) {
                    if (other != id) library.serviceState(other).rows.filterValues { it.linked }.keys.forEach { elsewhere += it }
                }
                var result =
                    ServiceSync.run(service, state, full = full, editedAt = HashMap(editedAt), elsewhere = elsewhere, textOf = textOf, now = clock()) { change ->
                        library.edit(change)
                    }
                if (result.problem == null && service is HighlightService) result = copyHighlights(service, result)
                finish(id, state, result)
            }
        }
        lock.withLock { refreshNotCopied() }
    }

    /**
     * The answer to the question asked when [id] was connected (#251): bring in everything it has
     * now ([everything]), or nothing. Either way, from then on everything in Saved syncs both ways.
     */
    suspend fun chooseImport(
        id: String,
        everything: Boolean,
    ) {
        val service = services[id] ?: return
        lock.withLock {
            val state = library.serviceState(id)
            if (importOf(id) != null || state.rows.isNotEmpty()) return@withLock
            _statuses.update { it + (id to ServiceStatus.Syncing) }
            val imported = ServiceSync.import(service, state, everything) { change -> library.edit(change) }
            finish(id, state, imported)
            if (imported.problem == null) recordImport(id, ServiceImport(clock(), imported.imported, everything))
        }
        sync()
    }

    private suspend fun finish(
        id: String,
        state: ServiceState,
        result: ServiceSyncResult,
    ) {
        library.saveServiceState(id, state, result.state)
        _statuses.update { if (id in services) it + (id to (result.problem?.let { p -> ServiceStatus.Failed(p) } ?: ServiceStatus.Idle)) else it - id }
        if (result.problem == ServiceProblem.SignedOut) {
            if (id == InstapaperReadingList.ID) instapaper.signOut()
        }
    }

    /**
     * Copies the highlights of each article in step with the service there (#164). Only articles
     * with something to do cost a call; it stops at the first problem and goes on next time.
     */
    private suspend fun copyHighlights(
        service: HighlightService,
        result: ServiceSyncResult,
    ): ServiceSyncResult {
        val lib = library.library.filterNotNull().first()
        val state = result.state
        val links = state.highlights.filterKeys { state.rows[it]?.linked == true }.toMutableMap()
        val now = clock()
        val look = lookAt.toSet()
        lookAt.removeAll(look)
        for ((key, row) in state.rows) {
            if (!row.linked) continue
            val item = lib.item(key)?.takeIf { it.readingList != null } ?: continue
            val synced = HighlightCopy.sync(service, (service as ReadingListService).id, row.remoteId, item.highlights, links[key].orEmpty(), now, look = key in look)
            links[key] = synced.links
            if (synced.highlights != item.highlights) {
                library.edit { l -> l.changeItem(key) { i -> i.copy(highlights = HighlightCopy.apply(i.highlights, item.highlights, synced.highlights)) } }
            }
            if (synced.problem != null) return result.copy(state = state.copy(highlights = links), problem = synced.problem)
        }
        return result.copy(state = state.copy(highlights = links))
    }

    /** Saved items whose highlights are looked up at the service on the next sync (opened in the reader). */
    private val lookAt: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    /**
     * An article opened in the reader (#164): its highlights at the service are looked up now, so
     * ones made in the service's own apps come in, even when nothing here changed.
     */
    fun lookAtHighlights(key: String) {
        if (services.isEmpty()) return
        lookAt += key
        scope.launch { sync(full = false) }
    }

    private data class Core(
        val key: String,
        val state: Any?,
        val folder: String?,
        val liked: Boolean,
        val tags: List<String>,
        val title: String,
        val note: String,
        val progress: Int,
        /** Each highlight's id and note: a new, changed or deleted one is copied (#164). */
        val highlights: List<Pair<String, String>>,
    )

    private fun core(item: Item) =
        Core(item.key, item.readingList?.state, item.folder, item.liked, item.tags, item.title, item.note, (item.progress * 100).toInt(), item.highlights.map { it.id to it.note })

    private companion object {
        /** A row of changes (archiving several, tagging) goes as one sync. */
        const val CHANGE_DELAY_MS = 2_000L

        val ORDER = listOf(InstapaperReadingList.ID)
    }
}
