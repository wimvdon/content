package to.wim.content.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import to.wim.content.data.Item
import to.wim.content.instapaper.InstapaperAccount
import to.wim.content.instapaper.InstapaperState
import to.wim.content.data.LibraryRepository
import to.wim.content.data.Received
import to.wim.content.data.SharedArticle
import to.wim.content.data.SharedBy
import to.wim.content.data.Sharing
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import to.wim.content.data.SettingsStore
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

/** What a change must be synced for: the document's part, the items and the read articles. */
private data class SyncedState(
    val document: SyncDocument,
    val items: List<Item>,
    val read: Set<String>,
)

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
    private val account: ContentAccount,
    /** The Instapaper sign-in, shared by the account's devices (SharedInstapaper). */
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

    /** The account's versions (document and items) this device last saved or read, to skip live messages about them. */
    @Volatile
    private var syncedVersion: Long? = null

    @Volatile
    private var syncedItemsVersion: Long? = null

    @Volatile
    private var live: LiveUpdates? = null

    /** Starts syncing for this app session (once; later calls do nothing). */
    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        // The first sync comes from whileVisible, which runs as soon as the app is on screen.
        scope.launch {
            syncedVersion = base()?.version
            syncedItemsVersion = library.itemSyncState().version
        }
        scope.launch {
            // Signing in starts with a fresh base (the new account's document is combined with this
            // library); signing out, here or because the key no longer works, forgets it.
            account.state
                .filter { it != AccountState.Loading }
                .map { accountKeyOf(it) }
                .distinctUntilChanged()
                .drop(1)
                .collect { key ->
                    forget()
                    if (key != null) sync()
                }
        }
        scope.launch {
            // A change to the library (feeds, folders, items, read marks), a synced setting or the Instapaper sign-in.
            combine(
                library.library.filterNotNull().map { SyncedState(SyncDocument.of(it), it.items, it.articles.filter { a -> a.read }.mapTo(HashSet()) { a -> a.key }) },
                settings.settings.filterNotNull(),
                instapaper.state.filter { it != InstapaperState.Loading },
            ) { state, s, i ->
                val token = (i as? InstapaperState.SignedIn)?.session?.let(SharedInstapaper::encode).orEmpty()
                state.copy(document = state.document.copy(settings = SyncSettings.of(s, instapaperToken = token)))
            }
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
                account.state.map { accountKeyOf(it) }.distinctUntilChanged().collect { key ->
                    live?.stop()
                    live =
                        key?.let {
                            LiveUpdates(http, it, scope) { versions ->
                                if (versions.document != syncedVersion || (versions.items != null && versions.items != syncedItemsVersion)) scope.launch { sync() }
                            }.also(LiveUpdates::start)
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

    /** The text stored in the account for saved item [key] (docs/account.md); throws when it can't be asked. */
    suspend fun storedText(key: String): String? = accountKey()?.let { AccountClient(http, it).text(key) }

    /** Stores [html] as saved item [key]'s text unless one is there; gives back the one that is stored. */
    suspend fun storeText(
        key: String,
        html: String,
    ): String? = accountKey()?.let { AccountClient(http, it).storeText(key, html) }

    /** Signs out of the account on the server (this device only), then here. Instapaper stays connected (#166). */
    suspend fun signOut() {
        accountKey()?.let { key -> runCatching { AccountClient(http, key).signOut() } }
        account.forget()
    }

    /** Deletes everything the account keeps, then signs out here. False when the server couldn't be reached. */
    suspend fun deleteAccountData(): Boolean {
        val key = accountKey() ?: return false
        try {
            AccountClient(http, key).deleteAll()
        } catch (e: SyncException) {
            return false
        }
        account.forget()
        return true
    }

    private suspend fun accountKey(): String? = account.session()?.key

    private fun accountKeyOf(state: AccountState): String? = (state as? AccountState.SignedIn)?.session?.key

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
            library.forgetItemSync()
            syncedVersion = null
            syncedItemsVersion = null
            settings.clearLastSyncedAt()
            _status.value = SyncStatus.Idle
        }
    }

    private suspend fun base(): SyncBase? = baseFrom(baseStore.data.first())

    private suspend fun syncWith(target: AccountClient) {
        var newFeeds = false
        val next =
            SyncRound.run(target, base()) { base, remote ->
                val session = instapaper.session()
                val localSettings =
                    SyncSettings.of(
                        settings.settings.filterNotNull().first(),
                        base?.settings.orEmpty(),
                        SharedInstapaper.local(session, base?.settings?.get(SyncSettings.INSTAPAPER_TOKEN)),
                    )
                // Merged with the library as it is right now, so changes made while the document loaded count.
                val merged =
                    library.update { current ->
                        val local = SyncMerge.localDocument(current).copy(settings = localSettings)
                        val merged = if (remote == null) local else SyncMerge.merge(base, local, remote)
                        newFeeds = newFeeds || SyncMerge.hasNewFeeds(current, merged)
                        SyncMerge.apply(current, merged) to merged
                    }
                settings.applySynced(merged.settings)
                // Connected or disconnected on another device: the same here (docs/instapaper.md).
                when (val action = SharedInstapaper.action(session, SyncSettings.instapaperToken(merged.settings))) {
                    SharedInstapaper.Action.None -> Unit
                    SharedInstapaper.Action.SignOut -> instapaper.signOut()
                    is SharedInstapaper.Action.SignIn -> instapaper.adopt(action.session)
                }
                merged
            }
        baseStore.updateData { baseText(next) }
        syncedVersion = next.version
        // Then the reading list, Bookmarks and read marks, one row per item (#106).
        val items = library.itemSyncState()
        val nextItems = ItemSync.run(target, items, clock()) { change -> library.edit(change) }
        library.saveItemSyncState(items, nextItems)
        syncedItemsVersion = nextItems.version
        if (newFeeds) scope.launch { library.refreshAll() }
        // What other Content users shared (#167): into the app's inbox (#246), then out of the account's.
        val inbox = runCatching { target.inbox() }.getOrDefault(emptyList())
        if (inbox.isNotEmpty()) {
            var received: Received? = null
            library.edit { lib ->
                val (next, got) = Sharing.takeIn(lib, inbox, clock())
                received = got
                next
            }
            runCatching { target.inboxDone(inbox.map { it.id }) }
            received?.takeIf { it.count > 0 }?.let { _received.tryEmit(it) }
        }
    }

    private val _received = MutableSharedFlow<Received>(extraBufferCapacity = 4)

    /** What arrived from other Content users (#167), to say so once. */
    val received: SharedFlow<Received> = _received.asSharedFlow()

    /** The name on [email]'s Gravatar profile (#234): "" when it has none, null when it couldn't be asked. Asked once per address while the app runs. */
    suspend fun profileName(email: String): String? {
        val clean = email.trim().lowercase()
        names[clean]?.let { return it }
        val name = accountKey()?.let { key -> runCatching { AccountClient(http, key).profileName(clean) }.getOrNull() }
        if (name != null) names[clean] = name
        return name
    }

    private val names = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Gives the feed loader this phone's push token (#235), or with null takes it back. False when signed out or it failed. */
    suspend fun setPush(token: String?): Boolean = accountKey()?.let { key -> runCatching { AccountClient(http, key).setPush(token) }.isSuccess } ?: false

    /** Whether [email] has a Content account, or null when it couldn't be asked. */
    suspend fun hasAccount(email: String): Boolean? = accountKey()?.let { key -> runCatching { AccountClient(http, key).hasAccount(email) }.getOrNull() }

    /** Shares with a Content user (#167): [ShareResult] says how it went. */
    suspend fun share(
        to: String,
        items: List<SharedArticle>,
    ): ShareResult {
        val session = account.session() ?: return ShareResult.SignedOut
        // Every account signs in with an email link, so it has an address; one without can't say who shares.
        val email = session.email ?: return ShareResult.SignedOut
        return try {
            if (AccountClient(http, session.key).share(to, SharedBy(email), items)) {
                // Offered first next time, on every device (#235).
                settings.rememberRecipient(to)
                ShareResult.Sent
            } else {
                ShareResult.NoAccount
            }
        } catch (e: SyncException) {
            if (e.problem == SyncProblem.Offline) ShareResult.Offline else ShareResult.Failed
        }
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

/** How sharing with a Content user went (#167). */
enum class ShareResult { Sent, NoAccount, SignedOut, Offline, Failed }
