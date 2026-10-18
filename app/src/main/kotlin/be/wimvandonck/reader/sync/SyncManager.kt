package be.wimvandonck.reader.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import be.wimvandonck.reader.data.LibraryRepository
import be.wimvandonck.reader.data.SettingsStore
import be.wimvandonck.reader.instapaper.InstapaperAccount
import be.wimvandonck.reader.instapaper.InstapaperState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
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
 * Syncs feeds, folders, read articles and settings with the user's Reader account (signing in with
 * Instapaper, docs/account.md) while the app is in use: when it comes on screen, whenever another
 * device saved (the live connection), every minute while that connection is down, a moment after a
 * change, on refresh, and on request. Never in the background.
 */
class SyncManager(
    context: Context,
    private val scope: CoroutineScope,
    private val library: LibraryRepository,
    private val settings: SettingsStore,
    private val instapaper: InstapaperAccount,
    private val http: OkHttpClient,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** The document as this device last saved or read it, and its version: the base for [SyncMerge]. */
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

    /** The account's version this device last saved or read, to skip live messages about it. */
    @Volatile
    private var syncedVersion: Long? = null

    @Volatile
    private var live: LiveUpdates? = null

    /** Starts syncing for this app session (once; later calls do nothing). */
    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        // The first sync comes from whileVisible, which runs as soon as the app is on screen.
        scope.launch {
            val saved = base()
            syncedVersion = saved?.version
            library.knownRead = saved?.let { SyncDocument.decode(it.text) }?.read?.keys.orEmpty()
        }
        scope.launch {
            // Signing in starts with a fresh base (the new account's document is combined with this
            // library); signing out, here or because Instapaper refused the token, forgets it.
            instapaper.state
                .filter { it != InstapaperState.Loading }
                .map { accountKeyOf(it) }
                .distinctUntilChanged()
                .drop(1)
                .collect { key ->
                    forget()
                    if (key != null) sync()
                }
        }
        scope.launch {
            // A change to the library or a synced setting.
            combine(
                library.library.filterNotNull().map { SyncDocument.of(it) },
                settings.settings.filterNotNull(),
            ) { doc, s -> doc.copy(settings = SyncSettings.of(s)) }
                .distinctUntilChanged()
                .drop(1)
                .debounce(SyncTiming.CHANGE_DELAY_MS)
                .collect { sync() }
        }
    }

    /** Syncs now, if signed in to an account. */
    suspend fun sync() {
        lock.withLock { syncLocked() }
    }

    /** Syncs unless the last sync was only seconds ago (see [SyncTiming.isDue]). */
    suspend fun syncIfDue() {
        lock.withLock {
            if (SyncTiming.isDue(settings.settings.filterNotNull().first().lastSyncedAt, clock())) syncLocked()
        }
    }

    /**
     * Syncs while the app is on screen: now (unless it just did), whenever another device saves,
     * and every minute while the live connection is down. Call it from a coroutine that is
     * cancelled when the app leaves the screen; that also closes the live connection. A sync that
     * has started runs on in the app's scope, so leaving never cuts one off halfway.
     */
    suspend fun whileVisible() =
        coroutineScope {
            launch {
                instapaper.state.map { accountKeyOf(it) }.distinctUntilChanged().collect { key ->
                    live?.stop()
                    live =
                        key?.let {
                            LiveUpdates(http, it, scope) { version -> if (version != syncedVersion) scope.launch { sync() } }.also(LiveUpdates::start)
                        }
                }
            }
            try {
                while (true) {
                    if (live?.connected != true) scope.launch { syncIfDue() }.join()
                    delay(SyncTiming.VISIBLE_INTERVAL_MS)
                }
            } finally {
                live?.stop()
                live = null
            }
        }

    /** Signs out of the account on the server (this device only), then here: Instapaper too. */
    suspend fun signOut() {
        accountKey()?.let { key -> runCatching { AccountClient(http, key).signOut() } }
        instapaper.signOut()
    }

    /** Deletes everything the account keeps, then signs out here. False when the server couldn't be reached. */
    suspend fun deleteAccountData(): Boolean {
        val key = accountKey() ?: return false
        try {
            AccountClient(http, key).deleteAll()
        } catch (e: SyncException) {
            return false
        }
        instapaper.signOut()
        return true
    }

    private suspend fun accountKey(): String? = instapaper.session()?.accountKey

    private fun accountKeyOf(state: InstapaperState): String? = (state as? InstapaperState.SignedIn)?.session?.accountKey

    private suspend fun syncLocked() {
        val key = accountKey() ?: return
        _status.value = SyncStatus.Syncing
        _status.value =
            try {
                syncWith(AccountClient(http, key))
                settings.setLastSyncedAt(clock())
                SyncStatus.Idle
            } catch (e: SyncException) {
                SyncStatus.Failed(e.problem)
            } catch (e: IOException) {
                // Storage on this device failed.
                SyncStatus.Failed(SyncProblem.ServerError)
            }
    }

    /** Forgets the base and the last sync, so the next account starts by combining this library with its document. */
    private suspend fun forget() {
        lock.withLock {
            baseStore.updateData { "" }
            syncedVersion = null
            library.knownRead = emptySet()
            settings.clearLastSyncedAt()
            _status.value = SyncStatus.Idle
        }
    }

    private suspend fun base(): SyncBase? = baseFrom(baseStore.data.first())

    private suspend fun syncWith(target: SyncTarget) {
        var newFeeds = false
        val next =
            SyncRound.run(target, base(), clock()) { base, remote ->
                val localSettings = SyncSettings.of(settings.settings.filterNotNull().first(), base?.settings.orEmpty())
                // Merged with the library as it is right now, so changes made while the document loaded count.
                val merged =
                    library.update { current ->
                        val local = SyncMerge.localDocument(current, base).copy(settings = localSettings)
                        val merged = if (remote == null) local else SyncMerge.merge(base, local, remote)
                        newFeeds = newFeeds || SyncMerge.hasNewFeeds(current, merged)
                        SyncMerge.apply(current, merged) to merged
                    }
                settings.applySynced(merged.settings)
                merged
            }
        baseStore.updateData { baseText(next) }
        syncedVersion = next.version
        library.knownRead = SyncDocument.decode(next.text)?.read?.keys.orEmpty()
        if (newFeeds) scope.launch { library.refreshAll() }
    }

    internal companion object {
        /** The stored base: the version, a line break, the document. Anything else (an older file) is no base. */
        fun baseText(base: SyncBase): String = "${base.version}\n${base.text}"

        fun baseFrom(text: String): SyncBase? {
            val version = text.substringBefore('\n', "").toLongOrNull() ?: return null
            return SyncBase(version, text.substringAfter('\n'))
        }
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
