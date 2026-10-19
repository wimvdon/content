package be.wimvandonck.reader.services

import be.wimvandonck.reader.data.DuplicateSync
import be.wimvandonck.reader.data.Item
import be.wimvandonck.reader.data.changeItem
import be.wimvandonck.reader.data.LibraryRepository
import be.wimvandonck.reader.instapaper.InstapaperAccount
import be.wimvandonck.reader.instapaper.InstapaperClient
import be.wimvandonck.reader.instapaper.InstapaperReadingList
import be.wimvandonck.reader.instapaper.InstapaperState
import be.wimvandonck.reader.raindrop.RaindropAccount
import be.wimvandonck.reader.raindrop.RaindropClient
import be.wimvandonck.reader.raindrop.RaindropSaved
import be.wimvandonck.reader.raindrop.RaindropState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
 * Connects Saved to its services while the app is in use (#109, #112): Instapaper and Raindrop.io,
 * each when connected. Each service keeps its own items; new saves go to the default one, and
 * nothing is copied from one to the other (#121). Syncs when started, a moment after Saved changes,
 * on refresh and on request; never in the background. Connecting copies what is to go there and
 * takes in everything the account has; disconnecting forgets what was synced and keeps Saved.
 */
class ServiceManager(
    private val scope: CoroutineScope,
    private val library: LibraryRepository,
    private val instapaper: InstapaperAccount,
    private val raindrop: RaindropAccount,
    private val http: OkHttpClient,
    /** The service new saves go to (a setting), when it is connected. */
    private val preferredService: () -> String? = { null },
    /** What the two copies of an article in both services share (a setting, #121). */
    private val duplicates: () -> DuplicateSync = { DuplicateSync() },
) {
    private val _statuses = MutableStateFlow<Map<String, ServiceStatus>>(emptyMap())

    /** Each connected service's status, by id. */
    val statuses: StateFlow<Map<String, ServiceStatus>> = _statuses.asStateFlow()

    private val _presence = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    /** Which connected services keep each saved item, by item key (#121: source icons, duplicates). */
    val presence: StateFlow<Map<String, Set<String>>> = _presence.asStateFlow()

    private val _copying = MutableStateFlow<CopyProgress?>(null)

    /** The copy between services that is running or just ended (#121), or null. */
    val copying: StateFlow<CopyProgress?> = _copying.asStateFlow()

    private var copyJob: Job? = null

    private val lock = Mutex()

    /** The connected services, by id, in a fixed order (Instapaper first). */
    @Volatile
    private var services: Map<String, ReadingListService> = emptyMap()

    private var started = false

    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            // Both accounts are read before the first sync, so neither service looks like the only one
            // for a moment and takes in items that belong to the other.
            val instapaperTokens = instapaper.state.filter { it != InstapaperState.Loading }.map { (it as? InstapaperState.SignedIn)?.session?.token }
            val raindropOn = raindrop.state.filter { it != RaindropState.Loading }.map { it == RaindropState.SignedIn }
            var last: Pair<String?, Boolean>? = null
            combine(instapaperTokens, raindropOn) { token, on -> token to on }
                .distinctUntilChanged()
                .collect { now ->
                    val (token, on) = now
                    if (last == null || last?.first != token) connect(InstapaperReadingList.ID, token?.let { InstapaperReadingList(InstapaperClient(http, it)) })
                    if (last == null || last?.second != on) connect(RaindropSaved.ID, if (on) RaindropSaved(RaindropClient(http, raindrop::token, raindrop::refresh)) else null)
                    last = now
                    if (services.isNotEmpty()) sync()
                }
        }
        scope.launch {
            // A change to Saved: its items' core, progress and note.
            library.library
                .filterNotNull()
                .map { lib -> lib.items.filter { it.readingList != null }.map(::core) }
                .distinctUntilChanged()
                .drop(1)
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
        // Disconnected (or another account): what was synced belongs to the old connection.
        if (before != null) lock.withLock { library.saveServiceState(id, library.serviceState(id), null) }
        _statuses.update { if (service == null) it - id else it + (id to ServiceStatus.Idle) }
        refreshPresence()
    }

    private suspend fun refreshPresence() {
        val map = HashMap<String, MutableSet<String>>()
        for (id in services.keys) {
            for (key in library.serviceState(id).rows.keys) map.getOrPut(key) { LinkedHashSet() } += id
        }
        _presence.value = map
    }

    /** The service new saves go to: the one chosen in settings while it is connected, else the first connected. */
    fun defaultService(): String? = preferredService()?.takeIf { it in services } ?: services.keys.firstOrNull()

    /** The connected services' ids, Instapaper first. */
    val connected: List<String> get() = services.keys.toList()

    /** Syncs every connected service now; [full] is false for the quick sync after a change. */
    suspend fun sync(full: Boolean = true) {
        for ((id, service) in services) {
            lock.withLock {
                _statuses.update { it + (id to ServiceStatus.Syncing) }
                val state = library.serviceState(id)
                val elsewhere = HashMap<String, String>()
                for (other in services.keys) {
                    if (other != id) library.serviceState(other).rows.keys.forEach { elsewhere.putIfAbsent(it, other) }
                }
                val result =
                    ServiceSync.run(service, state, isDefault = defaultService() == id, full = full, shared = duplicates(), elsewhere = elsewhere) { change ->
                        library.edit(change)
                    }
                library.saveServiceState(id, state, result.state)
                _statuses.update { if (id in services) it + (id to (result.problem?.let { p -> ServiceStatus.Failed(p) } ?: ServiceStatus.Idle)) else it - id }
                if (result.problem == ServiceProblem.SignedOut) {
                    when (id) {
                        InstapaperReadingList.ID -> instapaper.signOut()
                        RaindropSaved.ID -> raindrop.signOut()
                    }
                }
            }
        }
        lock.withLock { refreshPresence() }
    }

    /**
     * Removes an item from one service only (#121: an article both keep); it stays in Saved, kept in
     * the other. False when that didn't work (offline, or it isn't there).
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
            val other = services.keys.firstOrNull { it != id && key in library.serviceState(it).rows }
            library.edit { lib -> lib.changeItem(key) { if (it.service == id || it.service == null) it.copy(service = other) else it } }
            refreshPresence()
            true
        }

    /** How many articles copying [from]'s to [to] would add (the ones [to] hasn't yet). */
    suspend fun copyCount(
        from: String,
        to: String,
    ): Int =
        lock.withLock {
            ServiceCopy.candidates(library.library.filterNotNull().first(), library.serviceState(from).rows.keys, library.serviceState(to).rows.keys).size
        }

    /** Copies [from]'s articles to [to] once (#121), until done, stopped or [cancelCopy]. */
    fun startCopy(
        from: String,
        to: String,
    ) {
        if (copyJob?.isActive == true) return
        copyJob =
            scope.launch {
                val target = services[to] ?: return@launch
                val items = lock.withLock { ServiceCopy.candidates(library.library.filterNotNull().first(), library.serviceState(from).rows.keys, library.serviceState(to).rows.keys) }
                _copying.value = CopyProgress(from, to, 0, items.size)
                var problem: ServiceProblem? = null
                // A few at a time, so a sync can run in between and what got through is kept.
                for (chunk in items.chunked(COPY_BATCH)) {
                    lock.withLock {
                        val state = library.serviceState(to)
                        val rows = HashMap(state.rows)
                        try {
                            problem = ServiceCopy.run(target, chunk, rows) { _copying.update { it?.copy(done = it.done + 1) } }
                        } finally {
                            withContext(NonCancellable) { library.saveServiceState(to, state, state.copy(rows = rows)) }
                        }
                    }
                    if (problem != null) break
                }
                lock.withLock { refreshPresence() }
                _copying.update { it?.copy(problem = problem, finished = true) }
            }
    }

    fun cancelCopy() {
        copyJob?.cancel()
        _copying.value = null
        scope.launch { lock.withLock { refreshPresence() } }
    }

    /** Forgets a copy that ended, once its result was shown. */
    fun clearCopy() {
        if (copyJob?.isActive != true) _copying.value = null
    }

    /**
     * A service's own text of an item (Instapaper's parsed text, own account only), kept on the
     * device under the service's id; null when no service has one.
     */
    suspend fun text(item: Item): String? {
        for ((id, service) in services) {
            if (ServiceFeature.Text !in service.features) continue
            library.texts(item.key)[id]?.let { return it }
            val remoteId = library.serviceState(id).rows[item.key]?.remoteId ?: continue
            val text = runCatching { service.text(remoteId) }.getOrNull() ?: continue
            library.putText(item.key, id, text)
            return text
        }
        return null
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

        const val COPY_BATCH = 20

        val ORDER = listOf(InstapaperReadingList.ID, RaindropSaved.ID)
    }
}
