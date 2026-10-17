package io.github.impca201.rssreader.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import android.app.Activity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.impca201.rssreader.R
import io.github.impca201.rssreader.data.AddFeedResult
import io.github.impca201.rssreader.data.Article
import io.github.impca201.rssreader.data.FeedSelection
import io.github.impca201.rssreader.data.FullArticle
import io.github.impca201.rssreader.data.Library
import io.github.impca201.rssreader.data.ShareTarget
import io.github.impca201.rssreader.data.Settings
import io.github.impca201.rssreader.data.needsFullText
import io.github.impca201.rssreader.rssReaderApp
import io.github.impca201.rssreader.sync.GoogleAuth
import io.github.impca201.rssreader.sync.SyncService
import io.github.impca201.rssreader.ui.feeds.FeedsActions
import io.github.impca201.rssreader.ui.feeds.FeedsScreen
import io.github.impca201.rssreader.ui.feeds.FeedsState
import io.github.impca201.rssreader.ui.home.AddFeedDialog
import io.github.impca201.rssreader.ui.home.HomeActions
import io.github.impca201.rssreader.ui.home.HomeScreen
import io.github.impca201.rssreader.ui.home.HomeState
import io.github.impca201.rssreader.ui.home.errorRes
import io.github.impca201.rssreader.ui.reader.FullTextState
import io.github.impca201.rssreader.ui.reader.ReaderActions
import io.github.impca201.rssreader.ui.reader.ReaderScreen
import io.github.impca201.rssreader.ui.reader.ReaderTheme
import io.github.impca201.rssreader.ui.settings.SettingsActions
import io.github.impca201.rssreader.ui.settings.SettingsScreen
import io.github.impca201.rssreader.ui.settings.SettingsState
import io.github.impca201.rssreader.ui.theme.Motion
import io.github.impca201.rssreader.ui.theme.SystemBarsFollowTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Screens of the app. A tiny back stack; no navigation library needed. */
sealed interface Screen {
    data object Home : Screen

    data class Reader(val articleKey: String) : Screen

    data object Settings : Screen

    /** Feeds and folders. */
    data object Feeds : Screen

    companion object {
        fun encode(screen: Screen): String =
            when (screen) {
                Home -> "home"
                Settings -> "settings"
                Feeds -> "feeds"
                is Reader -> "reader:" + screen.articleKey
            }

        fun decode(text: String): Screen =
            when {
                text == "settings" -> Settings
                text == "feeds" -> Feeds
                text.startsWith("reader:") -> Reader(text.removePrefix("reader:"))
                else -> Home
            }
    }
}

private val BackStackSaver =
    listSaver<SnapshotStateList<Screen>, String>(
        save = { stack -> stack.map(Screen::encode) },
        restore = { saved -> saved.map(Screen::decode).toMutableStateList() },
    )

private const val OPML_FILE_NAME = "rss-reader-feeds.opml"

@Composable
fun RssReaderNavigation() {
    val context = LocalContext.current
    val resources = LocalResources.current
    val app = context.rssReaderApp
    val repository = app.library
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val libraryOrNull by repository.library.collectAsStateWithLifecycle()
    val refreshing by repository.refreshing.collectAsStateWithLifecycle()
    val problems by repository.problems.collectAsStateWithLifecycle()
    val syncStatus by app.sync.status.collectAsStateWithLifecycle()
    val current = settings ?: Settings()
    val library = libraryOrNull ?: Library()
    val snackbar = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()

    val backStack = rememberSaveable(saver = BackStackSaver) { mutableStateListOf<Screen>(Screen.Home) }
    var selection by rememberSaveable(stateSaver = SelectionSaver) { mutableStateOf<FeedSelection>(FeedSelection.All) }
    // Articles opened from the Unread list stay in it (marked as read) until the list changes:
    // another feed, the other filter, or a refresh. Otherwise rows would jump away.
    var readThisVisit by rememberSaveable { mutableStateOf(setOf<String>()) }

    fun push(screen: Screen) {
        if (backStack.last() != screen) backStack.add(screen)
    }

    // Keeps each screen's saved state (such as the list's scroll position) while it is in the
    // back stack, and forgets it when the screen is closed.
    val screenStates = rememberSaveableStateHolder()

    fun pop() {
        if (backStack.size > 1) screenStates.removeState(Screen.encode(backStack.removeAt(backStack.lastIndex)))
    }

    fun showMessage(text: String) {
        uiScope.launch { snackbar.showSnackbar(text) }
    }

    fun refresh() {
        readThisVisit = emptySet()
        app.appScope.launch {
            val failed = repository.refreshAll()
            if (failed > 0) showMessage(resources.getQuantityString(R.plurals.refresh_failed, failed, failed))
        }
    }

    // Add feed dialog, shared by the list, the drawer and Settings.
    var addDialogOpen by rememberSaveable { mutableStateOf(false) }
    var addBusy by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }

    // OPML import and export through the system file picker (no storage permission needed).
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            app.appScope.launch {
                val bytes =
                    withContext(Dispatchers.IO) {
                        runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                    }
                val added = bytes?.let { repository.importOpml(it) }
                when {
                    added == null -> showMessage(resources.getString(R.string.import_failed))
                    added == 0 -> showMessage(resources.getString(R.string.import_nothing_new))
                    else -> {
                        showMessage(resources.getQuantityString(R.plurals.import_added, added, added))
                        refresh()
                    }
                }
            }
        }
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-opml")) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            app.appScope.launch {
                val opml = repository.exportOpml(resources.getString(R.string.app_name))
                val written =
                    withContext(Dispatchers.IO) {
                        runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(opml.toByteArray()) } }.isSuccess
                    }
                showMessage(resources.getString(if (written) R.string.export_done else R.string.export_failed))
            }
        }
    val importOpml = { importLauncher.launch(arrayOf("text/x-opml", "text/xml", "application/xml", "application/octet-stream", "*/*")) }
    val exportOpml = { exportLauncher.launch(OPML_FILE_NAME) }

    // Sync with Google Drive: Connect asks Google for access to the app's hidden Drive folder,
    // showing Google's approval screen the first time.
    fun finishConnect(result: GoogleAuth.Result?) {
        if (result is GoogleAuth.Result.Token) {
            app.appScope.launch {
                app.settings.setSyncService(SyncService.GoogleDrive)
                app.sync.sync()
            }
        } else {
            showMessage(resources.getString(R.string.sync_connect_failed))
        }
    }
    val approvalLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) finishConnect(app.googleAuth.fromIntent(result.data))
        }
    val connectSync = {
        uiScope.launch {
            when (val result = runCatching { app.googleAuth.authorize() }.getOrNull()) {
                is GoogleAuth.Result.NeedsApproval -> approvalLauncher.launch(IntentSenderRequest.Builder(result.intent.intentSender).build())
                else -> finishConnect(result)
            }
        }
        Unit
    }

    // Fresh articles once per app start, as soon as the stored library is known.
    LaunchedEffect(libraryOrNull != null) {
        if (libraryOrNull != null) app.sync.start()
        if (libraryOrNull != null && !app.refreshedThisSession && library.feeds.isNotEmpty()) {
            app.refreshedThisSession = true
            refresh()
        }
    }

    // Apps that accept shared links, for Settings › Save and share; apps may have been installed
    // or removed while this app was in the background.
    var shareApps by remember { mutableStateOf(emptyList<ExternalApps.ShareApp>()) }
    LifecycleResumeEffect(Unit) {
        shareApps = ExternalApps.shareApps(context)
        onPauseOrDispose { }
    }

    /** Sends the article to the Save or Share button's app; the share menu when that app is gone. */
    fun send(
        article: Article,
        target: ShareTarget,
    ) {
        val link = article.link ?: return
        val chooser = resources.getString(R.string.action_share)
        if (!ExternalApps.send(context, target, article.title, link, chooser)) {
            if (target is ShareTarget.App) showMessage(resources.getString(R.string.share_target_missing_short, target.label))
            ExternalApps.share(context, article.title, link, chooser)
        }
    }

    BackHandler(enabled = backStack.size > 1) { pop() }

    val appDark = current.appearance.isDark(isSystemInDarkTheme())
    val axisOffset = with(LocalDensity.current) { 30.dp.roundToPx() }
    AnimatedContent(
        targetState = backStack.size to backStack.last(),
        transitionSpec = { Motion.sharedAxisX(forward = targetState.first > initialState.first, offsetPx = axisOffset) },
        contentKey = { it.second },
        label = "screen",
    ) { (_, screen) ->
        screenStates.SaveableStateProvider(Screen.encode(screen)) {
            when (screen) {
                Screen.Home -> {
                    SystemBarsFollowTheme(appDark)
                    // A removed feed or folder can't stay selected.
                    val shownSelection = library.shown(selection)
                    // Back from a folder or a feed goes to Home first; back on Home leaves the app.
                    BackHandler(enabled = backStack.size == 1 && shownSelection != FeedSelection.All) {
                        selection = FeedSelection.All
                        readThisVisit = emptySet()
                    }
                    val articles =
                        remember(library, shownSelection, current.unreadOnly, readThisVisit) {
                            library.articlesFor(shownSelection, current.unreadOnly, keep = readThisVisit)
                        }
                    val unreadCounts = remember(library) { library.unreadCounts() }
                    HomeScreen(
                        state =
                            HomeState(
                                feeds = library.feeds,
                                folders = library.folders,
                                markReadOnScroll = current.markReadOnScroll,
                                listView = current.listView,
                                articles = articles,
                                selection = shownSelection,
                                unreadOnly = current.unreadOnly,
                                unreadCounts = unreadCounts,
                                refreshing = refreshing,
                                hasAnyArticles = library.articles.isNotEmpty(),
                                loaded = libraryOrNull != null,
                                showImages = current.showImages,
                            ),
                        snackbarHostState = snackbar,
                        actions =
                            HomeActions(
                                onSelect = {
                                    selection = it
                                    readThisVisit = emptySet()
                                },
                                onUnreadOnlyChange = {
                                    readThisVisit = emptySet()
                                    app.appScope.launch { app.settings.setUnreadOnly(it) }
                                },
                                onOpenArticle = { article ->
                                    readThisVisit = readThisVisit + article.key
                                    push(Screen.Reader(article.key))
                                },
                                onToggleRead = { article ->
                                    // Stays in the Unread list until it changes, like opened articles.
                                    if (!article.read) readThisVisit = readThisVisit + article.key
                                    app.appScope.launch { repository.setRead(article.key, !article.read) }
                                },
                                onRefresh = ::refresh,
                                onMarkAllRead = {
                                    app.appScope.launch {
                                        val keys = repository.markAllRead(shownSelection)
                                        if (keys.isEmpty()) return@launch
                                        uiScope.launch {
                                            val result =
                                                snackbar.showSnackbar(
                                                    resources.getQuantityString(R.plurals.marked_read, keys.size, keys.size),
                                                    actionLabel = resources.getString(R.string.action_undo),
                                                    duration = SnackbarDuration.Short,
                                                )
                                            if (result == SnackbarResult.ActionPerformed) app.appScope.launch { repository.setRead(keys, false) }
                                        }
                                    }
                                },
                                onScrolledPast = { keys ->
                                    // Kept in the Unread list until it changes, like opened articles.
                                    readThisVisit = readThisVisit + keys
                                    app.appScope.launch { repository.setRead(keys, true) }
                                },
                                onAddFeed = { addDialogOpen = true },
                                onImportOpml = importOpml,
                                onOpenSettings = { push(Screen.Settings) },
                                onOpenFeeds = { push(Screen.Feeds) },
                            ),
                    )
                }

                is Screen.Reader -> {
                    val article = library.article(screen.articleKey)
                    // The full article loads by itself when the setting is on and the feed only has a
                    // summary; the reader's menu switches between the two (null: follow the setting).
                    var fullTextChoice by rememberSaveable(screen.articleKey) { mutableStateOf<Boolean?>(null) }
                    val isSummary = remember(article?.html, article?.link) { article != null && needsFullText(article.html, article.link) }
                    val wantFullText = article?.link != null && (fullTextChoice ?: (current.fullArticles && isSummary))
                    var fullText by remember(screen.articleKey) { mutableStateOf<FullTextState>(FullTextState.Off) }
                    LaunchedEffect(screen.articleKey, wantFullText) {
                        val link = article?.link
                        fullText =
                            if (!wantFullText || link == null) {
                                FullTextState.Off
                            } else {
                                fullText = FullTextState.Loading
                                when (val loaded = app.fullArticles.load(link)) {
                                    is FullArticle.Loaded -> FullTextState.Shown(loaded.html)
                                    FullArticle.Failed -> FullTextState.Failed
                                }
                            }
                    }
                    // Opening an article marks it as read.
                    LaunchedEffect(screen.articleKey) { app.appScope.launch { repository.setRead(screen.articleKey, true) } }
                    SystemBarsFollowTheme(current.reader.isDark(appDark))
                    ReaderTheme(style = current.reader, appearance = current.appearance, appDark = appDark) {
                        ReaderScreen(
                            article = article,
                            feed = article?.let { library.feed(it.feedUrl) },
                            style = current.reader,
                            saveTarget = current.saveTarget,
                            shareTarget = current.shareTarget,
                            fullText = fullText,
                            showImages = current.showImages,
                            snackbarHostState = snackbar,
                            actions =
                                ReaderActions(
                                    onBack = ::pop,
                                    onStyleChange = { style -> app.appScope.launch { app.settings.setReaderStyle(style) } },
                                    onSave = { article?.let { send(it, current.saveTarget) } },
                                    onShare = { article?.let { send(it, current.shareTarget) } },
                                    onOpenInBrowser = { article?.link?.let { ExternalApps.openLink(context, it) } },
                                    onMarkUnread = {
                                        app.appScope.launch { repository.setRead(screen.articleKey, false) }
                                        pop()
                                    },
                                    onOpenLink = { url -> ExternalApps.openLink(context, url) },
                                    onToggleFullText = { fullTextChoice = !wantFullText },
                                ),
                        )
                    }
                }

                Screen.Settings -> {
                    SystemBarsFollowTheme(appDark)
                    SettingsScreen(
                        state =
                            SettingsState(
                                settings = current,
                                feedCount = library.feeds.size,
                                folderCount = library.folders.size,
                                problemCount = problems.size,
                                shareApps = shareApps,
                                syncStatus = syncStatus,
                            ),
                        snackbarHostState = snackbar,
                        actions =
                            SettingsActions(
                                onBack = ::pop,
                                onOpenFeeds = { push(Screen.Feeds) },
                                onReaderStyleChange = { style -> app.appScope.launch { app.settings.setReaderStyle(style) } },
                                onFullArticlesChange = { enabled -> app.appScope.launch { app.settings.setFullArticles(enabled) } },
                                onShowImagesChange = { enabled -> app.appScope.launch { app.settings.setShowImages(enabled) } },
                                onMarkReadOnScrollChange = { enabled -> app.appScope.launch { app.settings.setMarkReadOnScroll(enabled) } },
                                onListViewChange = { view -> app.appScope.launch { app.settings.setListView(view) } },
                                onSaveTargetChange = { target -> app.appScope.launch { app.settings.setSaveTarget(target) } },
                                onShareTargetChange = { target -> app.appScope.launch { app.settings.setShareTarget(target) } },
                                onThemeModeChange = { mode -> app.appScope.launch { app.settings.setThemeMode(mode) } },
                                onDynamicColorChange = { enabled -> app.appScope.launch { app.settings.setDynamicColor(enabled) } },
                                onConnectSync = connectSync,
                                onSyncNow = { app.appScope.launch { app.sync.sync() } },
                                onDisconnectSync = { app.appScope.launch { app.sync.disconnect() } },
                            ),
                    )
                }

                Screen.Feeds -> {
                    SystemBarsFollowTheme(appDark)
                    FeedsScreen(
                        state = FeedsState(feeds = library.feeds, folders = library.folders, problems = problems),
                        snackbarHostState = snackbar,
                        actions =
                            FeedsActions(
                                onBack = ::pop,
                                onAddFeed = { addDialogOpen = true },
                                onRemoveFeed = { feed -> app.appScope.launch { repository.removeFeed(feed.url) } },
                                onEditFeed = { feed, title, folderId ->
                                    app.appScope.launch {
                                        if (title.trim() != feed.title) repository.renameFeed(feed.url, title)
                                        if (folderId != feed.folderId) repository.moveFeed(feed.url, folderId)
                                    }
                                },
                                onArrange = { rows -> app.appScope.launch { repository.arrange(rows) } },
                                onAddFolder = { title, icon -> app.appScope.launch { repository.addFolder(title, icon) } },
                                onUpdateFolder = { folder -> app.appScope.launch { repository.updateFolder(folder.id, folder.title, folder.icon) } },
                                onRemoveFolder = { folder -> app.appScope.launch { repository.removeFolder(folder.id) } },
                                onImportOpml = importOpml,
                                onExportOpml = exportOpml,
                            ),
                    )
                }
            }
        }
    }

    if (addDialogOpen) {
        AddFeedDialog(
            busy = addBusy,
            error = addError,
            onAdd = { address ->
                addBusy = true
                addError = null
                app.appScope.launch {
                    // Added to the folder being viewed, if any.
                    val folderId = (library.shown(selection) as? FeedSelection.InFolder)?.folderId
                    val result = repository.addFeed(address, folderId)
                    addBusy = false
                    val error = result.errorRes()
                    if (error != null) {
                        addError = resources.getString(error)
                    } else if (result is AddFeedResult.Added) {
                        addDialogOpen = false
                        selection = FeedSelection.Single(result.feed.url)
                        showMessage(resources.getString(R.string.add_feed_added, result.feed.title))
                    }
                }
            },
            onDismiss = {
                if (!addBusy) {
                    addDialogOpen = false
                    addError = null
                }
            },
        )
    }
}

private val SelectionSaver =
    Saver<FeedSelection, String>(
        save = {
            when (it) {
                FeedSelection.All -> ""
                is FeedSelection.InFolder -> "folder:" + it.folderId
                is FeedSelection.Single -> "feed:" + it.feedUrl
            }
        },
        restore = {
            when {
                it.startsWith("folder:") -> FeedSelection.InFolder(it.removePrefix("folder:"))
                it.startsWith("feed:") -> FeedSelection.Single(it.removePrefix("feed:"))
                else -> FeedSelection.All
            }
        },
    )
