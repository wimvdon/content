package be.wimvandonck.reader

import android.app.Application
import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import be.wimvandonck.reader.data.FullArticles
import be.wimvandonck.reader.data.IconCatalog
import be.wimvandonck.reader.data.LibraryRepository
import be.wimvandonck.reader.data.ServiceScope
import be.wimvandonck.reader.data.SettingsStore
import be.wimvandonck.reader.feed.FeedFetcher
import be.wimvandonck.reader.instapaper.InstapaperAccount
import be.wimvandonck.reader.instapaper.InstapaperReadingList
import be.wimvandonck.reader.instapaper.InstapaperState
import be.wimvandonck.reader.sync.ContentAccount
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import be.wimvandonck.reader.services.ServiceManager
import be.wimvandonck.reader.sync.SyncManager
import okhttp3.OkHttpClient
import java.io.File

class RssReaderApp :
    Application(),
    SingletonImageLoader.Factory {
    /**
     * Pictures in articles and the list (Coil, with its OkHttp network layer). Kept in memory and
     * in the cache folder, which Android may clear and never backs up.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .crossfade(true)
            .build()

    /** Process-wide scope for work that must outlive a single screen, such as a refresh. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + LoggingExceptionHandler)

    val settings: SettingsStore by lazy { SettingsStore(this, appScope) }

    private val fetcher: FeedFetcher by lazy {
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()
        FeedFetcher(userAgent = "RSSReader/$version (Android)")
    }

    val library: LibraryRepository by lazy { LibraryRepository(this, appScope, fetch = fetcher::fetch) }

    private val http: OkHttpClient by lazy { OkHttpClient() }

    /** The user's Instapaper account (sign-in, saving, and the saved list). See instapaper/. */
    val instapaper: InstapaperAccount by lazy { InstapaperAccount(this, appScope, http) }


    /** The user's Content account on this device: an emailed sign-in link, or opened by Instapaper (#166). See docs/account.md. */
    val account: ContentAccount by lazy { ContentAccount(this, appScope, http) }

    /** Sync with the user's Content account. See sync/ and docs/account.md. */
    val sync: SyncManager by lazy { SyncManager(this, appScope, library, settings, account, http) }

    /** Saved's connected service (Instapaper). See services/. */
    val services: ServiceManager by lazy {
        ServiceManager(
            appScope,
            library,
            instapaper,
            http,
            // Folders became tags (#187), so a scope of folders means nothing any more: everything in Saved syncs.
            scopeOf = { ServiceScope.Everything },
            importOf = { id -> settings.settings.value?.imports?.get(id) },
            recordImport = { id, record -> settings.setImport(id, record) },
            copiesHighlights = { id -> id == InstapaperReadingList.ID && settings.settings.value?.copyHighlights == true },
        )
    }

    val fullArticles: FullArticles by lazy { FullArticles(File(cacheDir, "articles"), fetch = fetcher::fetch) }

    /** Folder icons (Material Symbols), read from the assets the first time they are needed. */
    val iconCatalog: IconCatalog by lazy {
        IconCatalog.parse(assets.open("material_symbols.txt").bufferedReader().use { it.readText() })
    }

    override fun onCreate() {
        super.onCreate()
        // Read the folder icons in the background now, so the first screen doesn't wait for them.
        appScope.launch { iconCatalog }
        // Raindrop.io was removed (#179): its sign-in, kept only on this device, goes with it.
        appScope.launch { File(filesDir, "datastore/raindrop.preferences_pb").delete() }
        // Signing in with Instapaper opens a Content account; the account keeps its key (#166).
        appScope.launch {
            instapaper.state.collect { state ->
                if ((state as? InstapaperState.SignedIn)?.session?.accountKey != null) instapaper.takeAccountKey()?.let { account.adopt(it) }
            }
        }
    }

    /** The article list refreshes once per app start; later refreshes are pull-to-refresh. */
    var refreshedThisSession = false
}

/**
 * Background work here is best effort (a failed write must not crash the app, e.g. when
 * storage is full), so failures are logged instead of propagated.
 */
val LoggingExceptionHandler =
    CoroutineExceptionHandler { _, error ->
        Log.w("RssReader", "Background task failed", error)
    }

val Context.rssReaderApp: RssReaderApp
    get() = applicationContext as RssReaderApp
