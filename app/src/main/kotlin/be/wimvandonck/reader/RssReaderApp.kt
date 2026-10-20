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
import be.wimvandonck.reader.data.DuplicateSync
import be.wimvandonck.reader.data.SettingsStore
import be.wimvandonck.reader.feed.FeedFetcher
import be.wimvandonck.reader.instapaper.InstapaperAccount
import be.wimvandonck.reader.raindrop.RaindropAccount
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


    /** Sync with the user's Reader account, which signing in with Instapaper opens. See sync/ and docs/account.md. */
    val sync: SyncManager by lazy { SyncManager(this, appScope, library, settings, instapaper, http) }

    /** Raindrop.io, connected in Connected services (#112). See raindrop/. */
    val raindrop: RaindropAccount by lazy { RaindropAccount(this, appScope, http) }

    /** Saved's connected services (Instapaper, Raindrop). See services/. */
    val services: ServiceManager by lazy {
        ServiceManager(
            appScope,
            library,
            instapaper,
            raindrop,
            http,
            preferredService = { settings.settings.value?.saveService },
            duplicates = { settings.settings.value?.duplicates ?: DuplicateSync() },
            copyHighlights = { settings.settings.value?.copyHighlights == true },
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
