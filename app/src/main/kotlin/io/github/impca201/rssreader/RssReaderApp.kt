package io.github.impca201.rssreader

import android.app.Application
import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import io.github.impca201.rssreader.data.FullArticles
import io.github.impca201.rssreader.data.IconCatalog
import io.github.impca201.rssreader.data.LibraryRepository
import io.github.impca201.rssreader.data.SettingsStore
import io.github.impca201.rssreader.feed.FeedFetcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
