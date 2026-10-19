package be.wimvandonck.reader.services

import be.wimvandonck.reader.data.Item
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
            // A change to the reading list: its items' core and progress.
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
            val result = ServiceSync.run(current, state) { change -> library.edit(change) }
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

    /** The connected service's name for the reader's text choice, or null. */
    val textSource: String? get() = service?.takeIf { ServiceFeature.Text in it.features }?.id

    private data class Core(
        val key: String,
        val state: Any?,
        val liked: Boolean,
        val tags: List<String>,
        val title: String,
        val progress: Int,
    )

    private fun core(item: Item) = Core(item.key, item.readingList?.state, item.liked, item.tags, item.title, (item.progress * 100).toInt())

    private companion object {
        /** A row of changes (archiving several, tagging) goes as one sync. */
        const val CHANGE_DELAY_MS = 2_000L
    }
}
