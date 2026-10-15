package io.github.impca201.rssreader.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
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
import io.github.impca201.rssreader.data.SaveTarget
import io.github.impca201.rssreader.data.Settings
import io.github.impca201.rssreader.data.needsFullText
import io.github.impca201.rssreader.rssReaderApp
import io.github.impca201.rssreader.ui.home.AddFeedDialog
import io.github.impca201.rssreader.ui.home.HomeActions
import io.github.impca201.rssreader.ui.home.HomeScreen
import io.github.impca201.rssreader.ui.home.HomeState
import io.github.impca201.rssreader.ui.home.INOREADER_URL
import io.github.impca201.rssreader.ui.home.InoreaderImportDialog
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

    companion object {
        fun encode(screen: Screen): String =
            when (screen) {
                Home -> "home"
                Settings -> "settings"
                is Reader -> "reader:" + screen.articleKey
            }

        fun decode(text: String): Screen =
            when {
                text == "settings" -> Settings
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
    var inoreaderDialogOpen by rememberSaveable { mutableStateOf(false) }
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
        if (libraryOrNull != null && !app.refreshedThisSession && library.feeds.isNotEmpty()) {
            app.refreshedThisSession = true
            refresh()
        }
    }

    // Instapaper may have been installed or removed while the app was in the background.
    var instapaperInstalled by remember { mutableStateOf(ExternalApps.isInstapaperInstalled(context)) }
    LifecycleResumeEffect(Unit) {
        instapaperInstalled = ExternalApps.isInstapaperInstalled(context)
        onPauseOrDispose { }
    }

    fun save(article: Article) {
        val link = article.link ?: return
        when (current.saveTarget) {
            SaveTarget.Instapaper ->
                if (!ExternalApps.saveToInstapaper(context, article.title, link)) {
                    showMessage(resources.getString(R.string.save_instapaper_missing_short))
                    ExternalApps.share(context, article.title, link, resources.getString(R.string.action_share))
                }

            SaveTarget.ShareMenu -> ExternalApps.share(context, article.title, link, resources.getString(R.string.action_save_later))
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
                    // A removed feed can't stay selected.
                    val shownSelection =
                        (selection as? FeedSelection.Single)?.takeIf { library.feed(it.feedUrl) != null } ?: FeedSelection.All
                    val articles =
                        remember(library, shownSelection, current.unreadOnly, readThisVisit) {
                            library.articlesFor(shownSelection, current.unreadOnly, keep = readThisVisit)
                        }
                    val unreadCounts =
                        remember(library) {
                            buildMap {
                                put(FeedSelection.All, library.unreadCount(FeedSelection.All))
                                library.feeds.forEach { feed ->
                                    val single = FeedSelection.Single(feed.url)
                                    put(single, library.unreadCount(single))
                                }
                            }
                        }
                    HomeScreen(
                        state =
                            HomeState(
                                feeds = library.feeds,
                                articles = articles,
                                selection = shownSelection,
                                unreadOnly = current.unreadOnly,
                                unreadCounts = unreadCounts,
                                refreshing = refreshing,
                                hasAnyArticles = library.articles.isNotEmpty(),
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
                                onRefresh = ::refresh,
                                onMarkAllRead = { app.appScope.launch { repository.markAllRead(shownSelection) } },
                                onAddFeed = { addDialogOpen = true },
                                onImportOpml = importOpml,
                                onImportInoreader = { inoreaderDialogOpen = true },
                                onOpenSettings = { push(Screen.Settings) },
                            ),
                    )
                }

                is Screen.Reader -> {
                    val article = library.article(screen.articleKey)
                    // The full article loads by itself when the setting is on and the feed only has a
                    // summary; the reader's menu switches between the two (null: follow the setting).
                    var fullTextChoice by rememberSaveable(screen.articleKey) { mutableStateOf<Boolean?>(null) }
                    val isSummary = remember(article?.html) { article != null && needsFullText(article.html) }
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
                            feedTitle = article?.let { library.feed(it.feedUrl)?.title },
                            style = current.reader,
                            saveTarget = current.saveTarget,
                            fullText = fullText,
                            showImages = current.showImages,
                            snackbarHostState = snackbar,
                            actions =
                                ReaderActions(
                                    onBack = ::pop,
                                    onStyleChange = { style -> app.appScope.launch { app.settings.setReaderStyle(style) } },
                                    onSave = { article?.let(::save) },
                                    onShare = {
                                        article?.link?.let { ExternalApps.share(context, article.title, it, resources.getString(R.string.action_share)) }
                                    },
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
                                feeds = library.feeds,
                                problems = problems,
                                instapaperInstalled = instapaperInstalled,
                            ),
                        snackbarHostState = snackbar,
                        actions =
                            SettingsActions(
                                onBack = ::pop,
                                onAddFeed = { addDialogOpen = true },
                                onRemoveFeed = { feed -> app.appScope.launch { repository.removeFeed(feed.url) } },
                                onImportOpml = importOpml,
                                onImportInoreader = { inoreaderDialogOpen = true },
                                onExportOpml = exportOpml,
                                onReaderStyleChange = { style -> app.appScope.launch { app.settings.setReaderStyle(style) } },
                                onFullArticlesChange = { enabled -> app.appScope.launch { app.settings.setFullArticles(enabled) } },
                                onShowImagesChange = { enabled -> app.appScope.launch { app.settings.setShowImages(enabled) } },
                                onSaveTargetChange = { target -> app.appScope.launch { app.settings.setSaveTarget(target) } },
                                onGetInstapaper = { ExternalApps.openStoreListing(context, ExternalApps.INSTAPAPER_PACKAGE) },
                                onThemeModeChange = { mode -> app.appScope.launch { app.settings.setThemeMode(mode) } },
                                onDynamicColorChange = { enabled -> app.appScope.launch { app.settings.setDynamicColor(enabled) } },
                            ),
                    )
                }
            }
        }
    }

    if (inoreaderDialogOpen) {
        InoreaderImportDialog(
            onOpenInoreader = { ExternalApps.openLink(context, INOREADER_URL) },
            onChooseFile = {
                inoreaderDialogOpen = false
                importOpml()
            },
            onDismiss = { inoreaderDialogOpen = false },
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
                    val result = repository.addFeed(address)
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
        save = { if (it is FeedSelection.Single) it.feedUrl else "" },
        restore = { if (it.isEmpty()) FeedSelection.All else FeedSelection.Single(it) },
    )
