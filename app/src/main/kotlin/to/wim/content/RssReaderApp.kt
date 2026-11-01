package to.wim.content

import to.wim.content.instapaper.InstapaperText
import to.wim.content.data.InstapaperLinks
import android.app.Application
import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import to.wim.content.data.FullArticle
import to.wim.content.data.FullArticles
import to.wim.content.data.SharedText
import to.wim.content.data.IconCatalog
import to.wim.content.data.LibraryRepository
import to.wim.content.data.SettingsStore
import to.wim.content.feed.FeedFetcher
import to.wim.content.instapaper.InstapaperAccount
import to.wim.content.push.PushNotifications
import to.wim.content.data.SiteSignInStore
import to.wim.content.feed.SiteSignIns
import to.wim.content.feed.WebArticleExtractor
import to.wim.content.sync.AccountState
import to.wim.content.sync.ContentAccount
import to.wim.content.ui.DirectShare
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import to.wim.content.services.ServiceManager
import to.wim.content.sync.SyncManager
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
        FeedFetcher(userAgent = "RSSReader/$version (Android)", cookieFor = { url -> SiteSignIns.cookieFor(url, siteSignIns.signIns.value) })
    }

    val library: LibraryRepository by lazy { LibraryRepository(this, appScope, fetch = fetcher::fetch) }

    private val http: OkHttpClient by lazy { OkHttpClient() }

    /** The user's Instapaper account (sign-in, saving, and the saved list). See instapaper/. */
    val instapaper: InstapaperAccount by lazy { InstapaperAccount(this, appScope, http) }


    /** The user's Content account on this device: an emailed sign-in link, or opened by Instapaper (#166). See docs/account.md. */
    val account: ContentAccount by lazy { ContentAccount(this, appScope, http) }

    /** Sync with the user's Content account. See sync/ and docs/account.md. */
    val sync: SyncManager by lazy { SyncManager(this, appScope, library, settings, account, instapaper, http) }

    /** Saved's connected service (Instapaper). See services/. */
    val services: ServiceManager by lazy {
        ServiceManager(
            appScope,
            library,
            instapaper,
            http,
            importOf = { id -> settings.settings.value?.imports?.get(id) },
            recordImport = { id, record -> settings.setImport(id, record) },
            // The one text every device shows for a saved article (data/SharedText.kt), so Instapaper shows it too (#247).
            textOf = { item ->
                SharedText.resolve(
                    saved = true,
                    stored = { sync.storedText(item.key) },
                    extract = { articleText(item.url) },
                    store = { html -> sync.storeText(item.key, html) },
                )
            },
        )
    }

    /** Notifications when something is shared (#235). See push/. */
    val push: PushNotifications by lazy { PushNotifications(this, settings, sync) }

    /** Sites signed in to (#180): their cookies go with requests to them. On this device only. */
    val siteSignIns: SiteSignInStore by lazy { SiteSignInStore(this, appScope) }

    private val instapaperText: InstapaperText by lazy { InstapaperText(http) }

    /**
     * This device's text for the article at [url]: the full article Content extracts from its page,
     * or, for an Instapaper article without a web address (a forwarded email: `InstapaperLinks`),
     * Instapaper's text through the feed loader. Null when there is none.
     */
    suspend fun articleText(url: String): String? {
        val bookmark = InstapaperLinks.bookmarkId(url) ?: return (fullArticles.load(url) as? FullArticle.Loaded)?.html
        val token = instapaper.session()?.token ?: return null
        return instapaperText.load(token, bookmark)
    }

    val fullArticles: FullArticles by lazy {
        FullArticles(File(cacheDir, "articles"), fetch = fetcher::fetch, extract = WebArticleExtractor(this)::extract)
    }

    /** Folder icons (Material Symbols), read from the assets the first time they are needed. */
    val iconCatalog: IconCatalog by lazy {
        IconCatalog.parse(assets.open("material_symbols.txt").bufferedReader().use { it.readText() })
    }

    override fun onCreate() {
        super.onCreate()
        // Read the folder icons in the background now, so the first screen doesn't wait for them.
        appScope.launch { iconCatalog }
        // Notifications (#235): Firebase starts when the build has its settings; signing out turns them off.
        push.start()
        appScope.launch {
            account.state.collect { state -> if (state is AccountState.SignedOut) push.signedOut() }
        }
        // Once signed in and the settings are read: this phone's token to the feed loader again.
        appScope.launch {
            account.state.first { it is AccountState.SignedIn }
            settings.settings.filterNotNull().first()
            push.refresh()
        }
        // Direct share (#235, #256): the people shared with, in other apps' share menus, while signed in;
        // the feed folders in the launcher's menu (#251).
        appScope.launch {
            val folders = library.library.filterNotNull().map { lib -> lib.folders.map { Triple(it.id, it.title, it.icon) } }.distinctUntilChanged()
            combine(settings.settings.filterNotNull(), account.state, folders) { s, state, f ->
                Triple(state is AccountState.SignedIn, s.recipients, s.showImages) to f
            }.distinctUntilChanged().collect { (shown, _) ->
                val (signedIn, recipients, showImages) = shown
                // A failure here must not end the collecting: the next change would never be published (#249).
                runCatching {
                    val lib = library.library.value
                    DirectShare.publish(
                        this@RssReaderApp,
                        if (signedIn) recipients else emptyList(),
                        showImages,
                        sync::profileName,
                        lib?.folders.orEmpty(),
                        iconCatalog::icon,
                    )
                }.onFailure { Log.w("RssReader", "Shortcuts not published", it) }
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
