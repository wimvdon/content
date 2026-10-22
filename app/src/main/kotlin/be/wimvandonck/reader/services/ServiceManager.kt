package be.wimvandonck.reader.services

import be.wimvandonck.reader.data.Item
import be.wimvandonck.reader.data.LibraryRepository
import be.wimvandonck.reader.data.ServiceImport
import be.wimvandonck.reader.data.ServiceScope
import be.wimvandonck.reader.data.item
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

    /** The last sync stopped early; what didn't get through goes next time. */
    data class Failed(
        val problem: ServiceProblem,
    ) : ServiceStatus
}

/**
 * Connects Saved to its services while the app is in use (#109, #145): Instapaper, when
 * connected. Connecting waits for an import ([import]), done once; from then
 * on each service syncs its scope both ways, so an article reaches every service whose scope covers
 * it. Syncs when started, a moment after Saved changes, on refresh and on request; never in the
 * background. Disconnecting forgets what was synced and keeps Saved.
 */
class ServiceManager(
    private val scope: CoroutineScope,
    private val library: LibraryRepository,
    private val instapaper: InstapaperAccount,
    private val http: OkHttpClient,
    /** What each service syncs (a setting, #145). */
    private val scopeOf: (String) -> ServiceScope = { ServiceScope.Everything },
    /** What was imported from each service, or null before its import (a setting, #145). */
    private val importOf: (String) -> ServiceImport? = { null },
    /** Records an import, or forgets it (null) when the service is disconnected. */
    private val recordImport: suspend (String, ServiceImport?) -> Unit = { _, _ -> },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _statuses = MutableStateFlow<Map<String, ServiceStatus>>(emptyMap())

    /** Each connected service's status, by id. */
    val statuses: StateFlow<Map<String, ServiceStatus>> = _statuses.asStateFlow()

    private val _presence = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    /** Which connected services keep each saved item in step, by item key (removing asks which). */
    val presence: StateFlow<Map<String, Set<String>>> = _presence.asStateFlow()

    private val _waiting = MutableStateFlow<Set<String>>(emptySet())

    /** The connected services waiting for their import: nothing syncs with them until then. */
    val waiting: StateFlow<Set<String>> = _waiting.asStateFlow()

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
            // Raindrop.io is no longer a service (#179): what was synced with it, and its import, are forgotten.
            lock.withLock { library.saveServiceState(RAINDROP_ID, library.serviceState(RAINDROP_ID), null) }
            if (importOf(RAINDROP_ID) != null) recordImport(RAINDROP_ID, null)
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
        lock.withLock { refreshPresence() }
    }

    private suspend fun refreshPresence() {
        val map = HashMap<String, MutableSet<String>>()
        val waiting = HashSet<String>()
        for (id in services.keys) {
            val rows = library.serviceState(id).rows
            for ((key, row) in rows) if (row.linked) map.getOrPut(key) { LinkedHashSet() } += id
            if (importOf(id) == null && rows.isEmpty()) waiting += id
        }
        _presence.value = map
        _waiting.value = waiting
    }

    /** The connected services' ids, Instapaper first. */
    val connected: List<String> get() = services.keys.toList()

    /** Syncs every connected service that has had its import; [full] is false for the quick sync after a change. */
    suspend fun sync(full: Boolean = true) {
        for ((id, service) in services) {
            lock.withLock {
                val state = library.serviceState(id)
                // Connected before #145: everything was already in step, so it needs no import.
                if (importOf(id) == null && state.rows.isEmpty()) return@withLock
                _statuses.update { it + (id to ServiceStatus.Syncing) }
                val elsewhere = HashSet<String>()
                for (other in services.keys) {
                    if (other != id) library.serviceState(other).rows.filterValues { it.linked }.keys.forEach { elsewhere += it }
                }
                val result =
                    ServiceSync.run(service, state, scopeOf(id), full = full, editedAt = HashMap(editedAt), elsewhere = elsewhere) { change ->
                        library.edit(change)
                    }
                finish(id, state, result)
            }
        }
        lock.withLock { refreshPresence() }
    }

    /**
     * Brings in what [id] has, once (#145): everything, or the folders in [what] (and its archive
     * when asked). Then syncs. False when it didn't get through.
     */
    suspend fun import(
        id: String,
        what: ServiceImport,
    ): Boolean {
        val service = services[id] ?: return false
        val result =
            lock.withLock {
                _statuses.update { it + (id to ServiceStatus.Syncing) }
                val state = library.serviceState(id)
                val result = ServiceSync.import(service, state, what) { change -> library.edit(change) }
                finish(id, state, result)
                if (result.problem == null) recordImport(id, what.copy(at = clock(), count = result.imported))
                refreshPresence()
                result
            }
        if (result.problem != null) return false
        sync()
        return true
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

    /** The folders [id] has, for choosing what to import; null when it couldn't be asked. */
    suspend fun folders(id: String): List<String>? {
        val service = services[id] ?: return null
        return try {
            service.folders()
        } catch (e: ServiceException) {
            null
        }
    }

    /** How many articles in step with [id] a scope of [next] would take out (they are archived there). */
    suspend fun leaving(
        id: String,
        next: ServiceScope,
    ): Int {
        val lib = library.library.filterNotNull().first()
        val rows = lock.withLock { library.serviceState(id).rows }
        return rows.count { (key, row) -> row.linked && lib.item(key)?.let { it.readingList != null && !next.covers(it) } == true }
    }

    /**
     * Removes an item from one service only (an article two services keep); it stays in Saved and in
     * the other, and leaves this one's step. False when that didn't work (offline, or it isn't there).
     */
    suspend fun removeFrom(
        key: String,
        id: String,
    ): Boolean =
        lock.withLock {
            val service = services[id] ?: return@withLock false
            val state = library.serviceState(id)
            val row = state.rows[key] ?: return@withLock false
            try {
                service.remove(row.remoteId)
            } catch (e: ServiceException) {
                return@withLock false
            }
            library.saveServiceState(id, state, state.copy(rows = state.rows - key))
            refreshPresence()
            true
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
    )

    private fun core(item: Item) = Core(item.key, item.readingList?.state, item.folder, item.liked, item.tags, item.title, item.note, (item.progress * 100).toInt())

    private companion object {
        /** A row of changes (archiving several, tagging) goes as one sync. */
        const val CHANGE_DELAY_MS = 2_000L

        val ORDER = listOf(InstapaperReadingList.ID)

        /** The id Raindrop.io had as a service, until #179 removed it. */
        const val RAINDROP_ID = "raindrop"
    }
}
