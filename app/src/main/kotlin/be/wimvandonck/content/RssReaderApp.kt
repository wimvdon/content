package be.wimvandonck.content

import android.app.Application
import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import be.wimvandonck.content.data.FullArticle
import be.wimvandonck.content.data.FullArticles
import be.wimvandonck.content.data.SharedText
import be.wimvandonck.content.data.IconCatalog
import be.wimvandonck.content.data.LibraryRepository
import be.wimvandonck.content.data.ServiceScope
import be.wimvandonck.content.data.SettingsStore
import be.wimvandonck.content.feed.FeedFetcher
import be.wimvandonck.content.instapaper.InstapaperAccount
import be.wimvandonck.content.push.PushNotifications
import be.wimvandonck.content.data.SiteSignInStore
import be.wimvandonck.content.feed.SiteSignIns
import be.wimvandonck.content.instapaper.InstapaperReadingList
import be.wimvandonck.content.instapaper.InstapaperState
import be.wimvandonck.content.sync.AccountState
import be.wimvandonck.content.sync.ContentAccount
import be.wimvandonck.content.ui.DirectShare
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import be.wimvandonck.content.services.ServiceManager
import be.wimvandonck.content.sync.SyncManager
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
            // The one text every device shows for a saved article (data/SharedText.kt), so Instapaper shows it too (#247).
            textOf = { item ->
                SharedText.resolve(
                    saved = true,
                    stored = { sync.storedText(item.key) },
                    extract = { (fullArticles.load(item.url) as? FullArticle.Loaded)?.html },
                    store = { html -> sync.storeText(item.key, html) },
                )
            },
        )
    }

    /** Notifications when something is shared (#235). See push/. */
    val push: PushNotifications by lazy { PushNotifications(this, settings, sync) }

    /** Sites signed in to (#180): their cookies go with requests to them. On this device only. */
    val siteSignIns: SiteSignInStore by lazy { SiteSignInStore(this, appScope) }

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
        // Notifications (#235): Firebase starts when the build has its settings; signing out turns them off.
        push.start()
        appScope.launch {
            account.state.collect { state -> if (state is AccountState.SignedOut) push.signedOut() }
        }
        // Direct share (#235, #246): Send with Content and the people shared with, in Android's share
        // menu, while signed in.
        appScope.launch {
            combine(settings.settings.filterNotNull(), account.state) { s, state ->
                Triple(state is AccountState.SignedIn, s.recipients, s.showImages)
            }.distinctUntilChanged().collect { (signedIn, recipients, showImages) ->
                DirectShare.showSendTarget(this@RssReaderApp, signedIn)
                DirectShare.publish(this@RssReaderApp, if (signedIn) recipients else emptyList(), showImages, sync::profileName)
            }
        }
        // Signed in with Instapaper before #166: that account key moves to the Content account, once.
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
