package be.wimvandonck.reader.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import be.wimvandonck.reader.R
import be.wimvandonck.reader.ui.feeds.InstapaperManageState
import be.wimvandonck.reader.ui.feeds.InstapaperManageActions
import be.wimvandonck.reader.ui.feeds.InstapaperManageScreen
import kotlinx.coroutines.flow.flowOf
import be.wimvandonck.reader.data.Feed
import be.wimvandonck.reader.ui.reader.JustSavedSheet
import be.wimvandonck.reader.ui.reader.SavedArticle
import be.wimvandonck.reader.ui.home.InstapaperHome
import be.wimvandonck.reader.instapaper.InstapaperView
import be.wimvandonck.reader.instapaper.InstapaperLists
import be.wimvandonck.reader.data.AddFeedResult
import be.wimvandonck.reader.data.Article
import be.wimvandonck.reader.data.FeedSelection
import be.wimvandonck.reader.data.FullArticle
import be.wimvandonck.reader.data.Library
import be.wimvandonck.reader.data.ShareTarget
import be.wimvandonck.reader.data.Settings
import be.wimvandonck.reader.data.needsFullText
import be.wimvandonck.reader.instapaper.InstapaperException
import be.wimvandonck.reader.instapaper.InstapaperNotice
import be.wimvandonck.reader.instapaper.InstapaperState
import be.wimvandonck.reader.rssReaderApp
import be.wimvandonck.reader.ui.feeds.FeedsActions
import be.wimvandonck.reader.ui.feeds.FeedsScreen
import be.wimvandonck.reader.ui.feeds.FeedsState
import be.wimvandonck.reader.ui.home.AddFeedDialog
import be.wimvandonck.reader.ui.home.HomeActions
import be.wimvandonck.reader.ui.home.HomeScreen
import be.wimvandonck.reader.ui.home.HomeState
import be.wimvandonck.reader.ui.home.errorRes
import be.wimvandonck.reader.ui.instapaper.messageRes
import be.wimvandonck.reader.ui.reader.FullTextState
import be.wimvandonck.reader.ui.reader.ReaderActions
import be.wimvandonck.reader.ui.reader.ReaderScreen
import be.wimvandonck.reader.ui.reader.ReaderTheme
import be.wimvandonck.reader.ui.settings.SettingsActions
import be.wimvandonck.reader.ui.settings.SettingsScreen
import be.wimvandonck.reader.ui.settings.SettingsState
import be.wimvandonck.reader.ui.theme.Motion
import be.wimvandonck.reader.ui.theme.SystemBarsFollowTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Screens of the app. A tiny back stack; no navigation library needed. */
sealed interface Screen {
    data object Home : Screen

    data class Reader(val articleKey: String) : Screen

    data object Settings : Screen

    /** Manage feeds: feeds and folders. */
    data object Feeds : Screen

    /** Manage Instapaper: its folders and tags. */
    data object InstapaperManage : Screen

    companion object {
        fun encode(screen: Screen): String =
            when (screen) {
                Home -> "home"
                Settings -> "settings"
                Feeds -> "feeds"
                InstapaperManage -> "instapaper"
                is Reader -> "reader:" + screen.articleKey
            }

        fun decode(text: String): Screen =
            when {
                text == "settings" -> Settings
                text == "feeds" -> Feeds
                text == "instapaper" -> InstapaperManage
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
    val instapaperState by app.instapaper.state.collectAsStateWithLifecycle()
    val instapaperNotice by app.instapaper.notice.collectAsStateWithLifecycle()
    val current = settings ?: Settings()
    val library = libraryOrNull ?: Library()
    val snackbar = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()

    val backStack = rememberSaveable(saver = BackStackSaver) { mutableStateListOf<Screen>(Screen.Home) }
    var selection by rememberSaveable(stateSaver = SelectionSaver) { mutableStateOf<FeedSelection>(FeedSelection.All) }
    // Articles opened from the Unread list stay in it (marked as read) until the list changes:
    // another feed, the other filter, or a refresh. Otherwise rows would jump away.
    var readThisVisit by rememberSaveable { mutableStateOf(setOf<String>()) }

    // Instapaper, shown like the feeds: one model per sign-in, with the folders and tags for the
    // drawer and the list chosen there (null while a feed section is shown).
    val signedIn = instapaperState as? InstapaperState.SignedIn
    val instapaper = remember(signedIn?.session?.token) { signedIn?.let { app.instapaper.newModel(it.session, app.appScope).also { m -> m.start() } } }
    val instapaperUi by (instapaper?.state ?: flowOf(null)).collectAsStateWithLifecycle(null)
    var chosenView by rememberSaveable(stateSaver = InstapaperViewSaver) { mutableStateOf<InstapaperView?>(null) }
    val instapaperView = chosenView.takeIf { instapaper != null }
    // After a sign-in, or the app coming back, show the chosen list.
    LaunchedEffect(instapaper) {
        val view = chosenView
        if (instapaper != null && view != null && instapaper.state.value.view != view) instapaper.select(view)
    }

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
        if (instapaperView != null) {
            instapaper?.refresh()
            return
        }
        readThisVisit = emptySet()
        app.appScope.launch {
            // Read marks and feeds from other devices first, so they apply to the new articles.
            app.sync.sync()
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

    // Fresh articles once per app start, as soon as the stored library is known.
    LaunchedEffect(libraryOrNull != null) {
        if (libraryOrNull != null) app.sync.start()
        if (libraryOrNull != null && !app.refreshedThisSession && library.feeds.isNotEmpty()) {
            app.refreshedThisSession = true
            refresh()
        }
    }

    // Coming back from Instapaper's sign-in page: say how it went, once.
    LaunchedEffect(instapaperNotice) {
        val notice = instapaperNotice ?: return@LaunchedEffect
        showMessage(resources.getString(if (notice == InstapaperNotice.SignedIn) R.string.instapaper_sign_in_done else R.string.instapaper_sign_in_failed))
        app.instapaper.clearNotice()
    }

    // Sync while the app is on screen: when it comes back, live while it stays, and every minute
    // while the live connection is down (SyncManager).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, libraryOrNull != null) {
        if (libraryOrNull != null) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { app.sync.whileVisible() }
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

    /** Saves the article to the signed-in Instapaper account, then offers to file it (folder and tags). */
    fun saveToInstapaper(article: Article) {
        val link = article.link ?: return
        uiScope.launch {
            try {
                val model = instapaper
                if (model != null) {
                    model.save(link, article.title)
                    // The sheet says it was saved; a message is only needed when there is no sheet.
                    if (model.state.value.justSaved == null) showMessage(resources.getString(R.string.instapaper_saved))
                } else {
                    app.instapaper.save(link, article.title)
                    showMessage(resources.getString(R.string.instapaper_saved))
                }
            } catch (e: InstapaperException) {
                showMessage(resources.getString(e.problem.messageRes()))
            }
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
                    // Back from a folder, a feed or an Instapaper list goes to Home first; back on Home leaves the app.
                    BackHandler(enabled = backStack.size == 1 && (shownSelection != FeedSelection.All || instapaperView != null)) {
                        selection = FeedSelection.All
                        chosenView = null
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
                                instapaper =
                                    instapaperUi?.let { ui ->
                                        InstapaperHome(folders = ui.folders, tags = ui.tags, view = instapaperView, list = ui)
                                    },
                            ),
                        snackbarHostState = snackbar,
                        actions =
                            HomeActions(
                                onSelect = {
                                    selection = it
                                    chosenView = null
                                    readThisVisit = emptySet()
                                },
                                onSelectInstapaper = { view ->
                                    chosenView = view
                                    instapaper?.select(view)
                                },
                                onOpenSaved = { bookmark ->
                                    instapaper?.open(bookmark)
                                    push(Screen.Reader(InstapaperLists.article(bookmark).key))
                                },
                                onLoadMoreSaved = { instapaper?.loadMore() },
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
                                onOpenInstapaperManage = { push(Screen.InstapaperManage) },
                            ),
                    )
                }

                is Screen.Reader -> {
                    // A saved Instapaper article ("instapaper <id>") comes from the Instapaper model.
                    val savedKey = screen.articleKey.startsWith(InstapaperLists.FEED + " ")
                    val bookmark = if (savedKey) instapaperUi?.open?.takeIf { InstapaperLists.article(it).key == screen.articleKey } else null
                    val article = if (savedKey) bookmark?.let(InstapaperLists::article) else library.article(screen.articleKey)
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
                    // Opening an article marks it as read; saved Instapaper articles have no read state.
                    LaunchedEffect(screen.articleKey) { if (!savedKey) app.appScope.launch { repository.setRead(screen.articleKey, true) } }
                    SystemBarsFollowTheme(current.reader.isDark(appDark))
                    ReaderTheme(style = current.reader, appearance = current.appearance, appDark = appDark) {
                        ReaderScreen(
                            article = article,
                            feed =
                                if (savedKey) {
                                    bookmark?.host?.takeIf { it.isNotEmpty() }?.let { Feed(url = it, title = it) }
                                } else {
                                    article?.let { library.feed(it.feedUrl) }
                                },
                            style = current.reader,
                            // Signed in to Instapaper, Save goes straight to the account.
                            saveTarget = if (instapaperState is InstapaperState.SignedIn) ShareTarget.Instapaper else current.saveTarget,
                            shareTarget = current.shareTarget,
                            fullText = fullText,
                            showImages = current.showImages,
                            snackbarHostState = snackbar,
                            actions =
                                ReaderActions(
                                    onBack = ::pop,
                                    onStyleChange = { style -> app.appScope.launch { app.settings.setReaderStyle(style) } },
                                    onSave = {
                                        article?.let {
                                            if (instapaperState is InstapaperState.SignedIn) saveToInstapaper(it) else send(it, current.saveTarget)
                                        }
                                    },
                                    onShare = { article?.let { send(it, current.shareTarget) } },
                                    onOpenInBrowser = { article?.link?.let { ExternalApps.openLink(context, it) } },
                                    onMarkUnread = {
                                        app.appScope.launch { repository.setRead(screen.articleKey, false) }
                                        pop()
                                    },
                                    onOpenLink = { url -> ExternalApps.openLink(context, url) },
                                    onToggleFullText = { fullTextChoice = !wantFullText },
                                ),
                            saved =
                                if (bookmark != null && instapaper != null && instapaperUi != null) {
                                    SavedArticle(
                                        bookmark = bookmark,
                                        folders = instapaperUi?.folders.orEmpty(),
                                        tags = instapaperUi?.tags.orEmpty(),
                                        onLike = { instapaper.toggleLike(bookmark) },
                                        onArchive = { instapaper.toggleArchive(bookmark) },
                                        onMove = { folderId -> instapaper.moveTo(bookmark, folderId) },
                                        onMoveToNewFolder = { title -> instapaper.moveToNewFolder(bookmark, title) },
                                        onAddTag = { name -> instapaper.addTag(bookmark, name) },
                                        onRemoveTag = { tag -> instapaper.removeTag(bookmark, tag) },
                                        onDelete = {
                                            instapaper.delete(bookmark)
                                            pop()
                                        },
                                    )
                                } else {
                                    null
                                },
                        )
                    }
                }

                Screen.Settings -> {
                    SystemBarsFollowTheme(appDark)
                    SettingsScreen(
                        state =
                            SettingsState(
                                settings = current,
                                shareApps = shareApps,
                                syncStatus = syncStatus,
                                instapaper = instapaperState,
                            ),
                        snackbarHostState = snackbar,
                        actions =
                            SettingsActions(
                                onBack = ::pop,
                                onReaderStyleChange = { style -> app.appScope.launch { app.settings.setReaderStyle(style) } },
                                onFullArticlesChange = { enabled -> app.appScope.launch { app.settings.setFullArticles(enabled) } },
                                onShowImagesChange = { enabled -> app.appScope.launch { app.settings.setShowImages(enabled) } },
                                onMarkReadOnScrollChange = { enabled -> app.appScope.launch { app.settings.setMarkReadOnScroll(enabled) } },
                                onListViewChange = { view -> app.appScope.launch { app.settings.setListView(view) } },
                                onSaveTargetChange = { target -> app.appScope.launch { app.settings.setSaveTarget(target) } },
                                onShareTargetChange = { target -> app.appScope.launch { app.settings.setShareTarget(target) } },
                                onThemeModeChange = { mode -> app.appScope.launch { app.settings.setThemeMode(mode) } },
                                onDynamicColorChange = { enabled -> app.appScope.launch { app.settings.setDynamicColor(enabled) } },
                                onSignIn = { uiScope.launch { ExternalApps.openLink(context, app.instapaper.begin()) } },
                                onSyncNow = { app.appScope.launch { app.sync.sync() } },
                                onSignOut = { app.appScope.launch { app.sync.signOut() } },
                                onDeleteAccountData = {
                                    app.appScope.launch {
                                        val deleted = app.sync.deleteAccountData()
                                        showMessage(resources.getString(if (deleted) R.string.account_deleted else R.string.sync_failed_offline))
                                    }
                                },
                            ),
                    )
                }

                Screen.Feeds -> {
                    SystemBarsFollowTheme(appDark)
                    FeedsScreen(
                        state =
                            FeedsState(
                                feeds = library.feeds,
                                folders = library.folders,
                                problems = problems,
                            ),
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

                Screen.InstapaperManage -> {
                    SystemBarsFollowTheme(appDark)
                    val model = instapaper
                    // Signed out meanwhile (here or because Instapaper refused the token): nothing to manage.
                    LaunchedEffect(model == null) { if (model == null) pop() }
                    if (model != null) {
                        InstapaperManageScreen(
                            state = InstapaperManageState(instapaperUi?.folders.orEmpty(), instapaperUi?.tags.orEmpty()),
                            snackbarHostState = snackbar,
                            onBack = ::pop,
                            actions =
                                InstapaperManageActions(
                                    onCreateFolder = model::createFolder,
                                    onDeleteFolder = model::deleteFolder,
                                    onMoveFolder = model::moveFolder,
                                    onRenameTag = model::renameTag,
                                ),
                        )
                    }
                }
            }
        }
    }

    // Right after Save: file the new saved article (folder and tags) without leaving the article.
    val justSaved = instapaperUi?.justSaved
    if (instapaper != null && justSaved != null) {
        JustSavedSheet(
            bookmark = justSaved,
            folders = instapaperUi?.folders.orEmpty(),
            tags = instapaperUi?.tags.orEmpty(),
            onMove = { folderId -> instapaper.moveTo(justSaved, folderId) },
            onMoveToNewFolder = { title -> instapaper.moveToNewFolder(justSaved, title) },
            onAddTag = { name -> instapaper.addTag(justSaved, name) },
            onRemoveTag = { tag -> instapaper.removeTag(justSaved, tag) },
            onDismiss = instapaper::closeJustSaved,
        )
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

private val InstapaperViewSaver =
    Saver<InstapaperView?, String>(
        save = {
            when (it) {
                null -> ""
                InstapaperView.Home -> "home"
                InstapaperView.Liked -> "liked"
                InstapaperView.Archive -> "archive"
                is InstapaperView.InFolder -> "folder:" + it.folderId
                is InstapaperView.WithTag -> "tag:" + it.name
            }
        },
        restore = {
            when {
                it == "home" -> InstapaperView.Home
                it == "liked" -> InstapaperView.Liked
                it == "archive" -> InstapaperView.Archive
                it.startsWith("folder:") -> it.removePrefix("folder:").toLongOrNull()?.let(InstapaperView::InFolder)
                it.startsWith("tag:") -> InstapaperView.WithTag(it.removePrefix("tag:"))
                else -> null
            }
        },
    )

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
