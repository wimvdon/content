package be.wimvandonck.reader.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import be.wimvandonck.reader.data.LibraryRepository
import be.wimvandonck.reader.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** What sync is doing, for Settings. */
sealed interface SyncStatus {
    data object Idle : SyncStatus

    data object Syncing : SyncStatus

    data class Failed(val problem: SyncProblem) : SyncStatus
}

/**
 * Syncs feeds, folders and read articles with the chosen service while the app is in use: when
 * it starts, a few seconds after a change, and on request. Never in the background.
 */
class SyncManager(
    context: Context,
    private val scope: CoroutineScope,
    private val library: LibraryRepository,
    private val settings: SettingsStore,
    private val provider: (SyncService) -> SyncProvider?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** The sync file as this device last wrote or read it: the base for [SyncMerge]. */
    private val baseStore: DataStore<String> =
        DataStoreFactory.create(
            serializer = TextSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { "" },
            scope = scope,
            produceFile = { context.applicationContext.dataStoreFile("sync-base.json") },
        )

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val lock = Mutex()

    private var started = false

    /** Starts syncing for this app session (once; later calls do nothing). */
    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            library.knownRead = base()?.read?.keys.orEmpty()
            sync()
        }
        scope.launch {
            library.library
                .filterNotNull()
                .map { SyncDocument.of(it) }
                .distinctUntilChanged()
                .drop(1)
                .debounce(CHANGE_DELAY_MS)
                .collect { sync() }
        }
    }

    /** Syncs now, if a service is chosen. */
    suspend fun sync() {
        val service = settings.settings.filterNotNull().first().syncService
        val target = provider(service) ?: return
        lock.withLock {
            _status.value = SyncStatus.Syncing
            _status.value =
                try {
                    syncWith(target)
                    settings.setLastSyncedAt(clock())
                    SyncStatus.Idle
                } catch (e: SyncException) {
                    SyncStatus.Failed(e.problem)
                } catch (e: IOException) {
                    // Storage on this device failed.
                    SyncStatus.Failed(SyncProblem.ServerError)
                }
        }
    }

    /** Stops syncing and forgets the base, so connecting again merges everything fresh. */
    suspend fun disconnect() {
        lock.withLock {
            settings.setSyncService(SyncService.None)
            baseStore.updateData { "" }
            library.knownRead = emptySet()
            _status.value = SyncStatus.Idle
        }
    }

    private suspend fun base(): SyncDocument? = baseStore.data.first().takeIf { it.isNotEmpty() }?.let(SyncDocument::decode)

    private suspend fun syncWith(target: SyncProvider) {
        val remoteText = target.read()
        val remote = remoteText?.let { SyncDocument.decode(it) ?: throw SyncException(SyncProblem.UnreadableFile) }
        // Without a file there is nothing to merge with: this device's library becomes the file.
        val base = if (remote == null) null else base()
        val (merged, newFeeds) =
            library.update { current ->
                val local = SyncMerge.localDocument(current, base)
                val merged = if (remote == null) local else SyncMerge.merge(base, local, remote)
                SyncMerge.apply(current, merged) to (merged to SyncMerge.hasNewFeeds(current, merged))
            }
        val out = merged.pruned(clock() - SyncDocument.KEEP_READ_MS)
        if (out != remote) target.write(SyncDocument.encode(out))
        baseStore.updateData { SyncDocument.encode(out) }
        library.knownRead = out.read.keys
        if (newFeeds) scope.launch { library.refreshAll() }
    }

    private companion object {
        const val CHANGE_DELAY_MS = 5_000L
    }
}

private object TextSerializer : Serializer<String> {
    override val defaultValue = ""

    override suspend fun readFrom(input: InputStream): String = input.readBytes().decodeToString()

    override suspend fun writeTo(
        t: String,
        output: OutputStream,
    ) {
        output.write(t.encodeToByteArray())
    }
}
