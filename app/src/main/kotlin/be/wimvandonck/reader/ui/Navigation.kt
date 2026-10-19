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
import androidx.compose.runtime.produceState
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
import be.wimvandonck.reader.data.Item
import be.wimvandonck.reader.data.ItemSearch
import be.wimvandonck.reader.data.ReadingListFilter
import be.wimvandonck.reader.data.ReadingListTab
import be.wimvandonck.reader.data.ReadingLists
import be.wimvandonck.reader.data.ReadingState
import be.wimvandonck.reader.data.addToReadingList
import be.wimvandonck.reader.data.bookmarks
import be.wimvandonck.reader.data.changeItem
import be.wimvandonck.reader.data.item
import be.wimvandonck.reader.data.itemFor
import be.wimvandonck.reader.data.itemTags
import be.wimvandonck.reader.data.keepAsBookmark
import be.wimvandonck.reader.data.tagWithFeedFolder
import be.wimvandonck.reader.data.removeTag
import be.wimvandonck.reader.data.renameTag
import be.wimvandonck.reader.data.setLiked
import be.wimvandonck.reader.data.setReadingState
import be.wimvandonck.reader.data.setTags
import be.wimvandonck.reader.data.tagCounts
import be.wimvandonck.reader.feed.FeedDiscovery
import be.wimvandonck.reader.ui.feeds.TagsManageScreen
import be.wimvandonck.reader.ui.home.HomeSection
import be.wimvandonck.reader.ui.home.ReadingListHome
import be.wimvandonck.reader.ui.home.BookmarkActions
import be.wimvandonck.reader.ui.home.BookmarkScreen
import be.wimvandonck.reader.ui.home.BookmarksHome
import be.wimvandonck.reader.ui.reader.SaveSheet
import be.wimvandonck.reader.ui.reader.ReaderHighlights
import be.wimvandonck.reader.ui.settings.MatchTestState
import be.wimvandonck.reader.data.addHighlight
import be.wimvandonck.reader.data.setHighlightNote
import be.wimvandonck.reader.data.removeHighlight
import android.content.pm.ApplicationInfo
import be.wimvandonck.reader.data.BookmarkFilter
import be.wimvandonck.reader.data.BookmarkKind
import be.wimvandonck.reader.data.Bookmarks
import be.wimvandonck.reader.data.SaveTo
import be.wimvandonck.reader.ui.reader.AddLinkDialog
import be.wimvandonck.reader.ui.reader.AddedSheet
import be.wimvandonck.reader.ui.reader.ReaderItem
import be.wimvandonck.reader.ui.reader.fontFamily
import be.wimvandonck.reader.ui.settings.ServicesScreen
import be.wimvandonck.reader.data.AddFeedResult
import be.wimvandonck.reader.data.Article
import be.wimvandonck.reader.data.ArticleSearch
import be.wimvandonck.reader.data.MixedSearch
import be.wimvandonck.reader.data.SearchFilter
import be.wimvandonck.reader.ui.home.SearchActions
import be.wimvandonck.reader.ui.home.SearchScreen
import be.wimvandonck.reader.ui.home.SearchState
import be.wimvandonck.reader.data.FeedSelection
import be.wimvandonck.reader.data.FullArticle
import be.wimvandonck.reader.data.Library
import be.wimvandonck.reader.data.ShareTarget
import be.wimvandonck.reader.data.Settings
import be.wimvandonck.reader.data.needsFullText
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

    /** A feed article ("<feed url> <id>") or an item of the reading list ("item <item key>"). */
    data class Reader(val articleKey: String) : Screen

    data object Settings : Screen

    /** Manage feeds: feeds and folders. */
    data object Feeds : Screen

    /** Tags: rename and remove the reading list's tags. */
    data object Tags : Screen

    /** Connected services (#109). */
    data object Services : Screen

    /** Search through every feed's articles and the reading list. */
    data object Search : Screen

    /** One bookmark (#108), by its item key. */
    data class Bookmark(val key: String) : Screen

    companion object {
        fun encode(screen: Screen): String =
            when (screen) {
                Home -> "home"
                Settings -> "settings"
                Feeds -> "feeds"
                Tags -> "tags"
                Services -> "services"
                Search -> "search"
                is Reader -> "reader:" + screen.articleKey
                is Bookmark -> "bookmark:" + screen.key
            }

        fun decode(text: String): Screen =
            when {
                text == "settings" -> Settings
                text == "feeds" -> Feeds
                text == "tags" -> Tags
                text == "services" -> Services
                text == "search" -> Search
                text.startsWith("reader:") -> Reader(text.removePrefix("reader:"))
                text.startsWith("bookmark:") -> Bookmark(text.removePrefix("bookmark:"))
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
    val textWords by repository.textWords.collectAsStateWithLifecycle()
    val syncStatus by app.sync.status.collectAsStateWithLifecycle()
    val serviceStatus by app.services.status.collectAsStateWithLifecycle()
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

    // The destination shown (Feeds or the reading list) and the reading list's list, chips and tag.
    var section by rememberSaveable { mutableStateOf(HomeSection.Feeds) }
    var readingFilter by rememberSaveable(stateSaver = ReadingFilterSaver) { mutableStateOf(ReadingListFilter()) }
    // The item just put on the reading list, while the sheet to like and tag it is open.
    var addedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var addLinkOpen by rememberSaveable { mutableStateOf(false) }
    // The highlight matching test in Connected services (#102, test builds).
    var matchTest by remember { mutableStateOf(MatchTestState()) }
    // Bookmarks' collection and type (#108).
    var bookmarkFilter by rememberSaveable(stateSaver = BookmarkFilterSaver) { mutableStateOf(BookmarkFilter()) }
    // The save sheet (Reading list or Bookmark) while it is open: the link (empty: type one), its title and the choice offered first.
    var saveOpen by rememberSaveable { mutableStateOf(false) }
    var saveUrl by rememberSaveable { mutableStateOf("") }
    var saveTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var saveInitial by rememberSaveable { mutableStateOf<SaveTo?>(null) }

    fun openSave(
        url: String?,
        title: String?,
        initial: SaveTo?,
    ) {
        saveUrl = url.orEmpty()
        saveTitle = title
        saveInitial = initial
        saveOpen = true
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

    fun editLibrary(change: (Library) -> Library) {
        app.appScope.launch { repository.edit(change) }
    }

    // A refresh syncs before it downloads; the indicator shows from the pull to the end of both,
    // set at once so it doesn't spring back and drop down again when the download starts.
    var refreshRunning by remember { mutableStateOf(false) }

    fun refresh() {
        refreshRunning = true
        if (section != HomeSection.Feeds) {
            app.appScope.launch {
                try {
                    app.sync.sync()
                    app.services.sync()
                } finally {
                    refreshRunning = false
                }
            }
            return
        }
        readThisVisit = emptySet()
        app.appScope.launch {
            try {
                // Read marks and feeds from other devices first, so they apply to the new articles.
                app.sync.sync()
                val failed = repository.refreshAll()
                if (failed > 0) showMessage(resources.getQuantityString(R.plurals.refresh_failed, failed, failed))
            } finally {
                refreshRunning = false
            }
        }
    }

    // What was typed in Search; kept while reading a result, so Back returns to the same results.
    var searchQuery by rememberSaveable { mutableStateOf("") }
    // Which kind of results Search shows (the chips): all, only feeds or only the reading list.
    var searchFilter by rememberSaveable { mutableStateOf(SearchFilter.All) }

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
        if (libraryOrNull != null) {
            app.sync.start()
            app.services.start()
        }
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
    // while the live connection is down (SyncManager); the reading list's service when it comes back.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, libraryOrNull != null) {
        if (libraryOrNull != null) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { app.services.sync() }
                app.sync.whileVisible()
            }
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

    /**
     * Puts [link] on the reading list (#107), then opens the sheet to like and tag it. A link without
     * a title gets the page's own afterwards.
     */
    fun readLater(
        link: String,
        title: String?,
        sheet: Boolean = true,
    ) {
        app.appScope.launch {
            var added: Item? = null
            repository.edit { library ->
                val (updated, item) = library.addToReadingList(link, title, System.currentTimeMillis())
                added = item
                if (item != null && current.tagWithFolder) updated.tagWithFeedFolder(item.key) else updated
            }
            val item = added
            if (item == null) {
                showMessage(resources.getString(R.string.add_link_invalid))
                return@launch
            }
            if (sheet) addedKey = item.key else showMessage(resources.getString(R.string.reading_list_added))
            repository.describe(item.key)
        }
    }

    /** Saves a link from the save sheet (#108), says where it went, and fills in the page's title and picture. */
    fun save(
        url: String,
        to: SaveTo,
        title: String?,
        collection: String,
        note: String,
        tags: List<String>,
    ) {
        app.appScope.launch {
            var saved: Item? = null
            repository.edit { library ->
                val (updated, item) = Bookmarks.save(library, url, title, to, System.currentTimeMillis(), collection, note, tags)
                saved = item
                if (item != null && current.tagWithFolder) updated.tagWithFeedFolder(item.key) else updated
            }
            val item = saved
            if (item == null) {
                showMessage(resources.getString(R.string.add_link_invalid))
                return@launch
            }
            showMessage(resources.getString(if (to == SaveTo.Bookmark) R.string.bookmark_saved else R.string.reading_list_added))
            repository.describe(item.key)
        }
    }

    // A link shared to Reader from another app (#97) opens the save sheet: reading list or bookmark.
    val sharedLink by app.sharedLink.collectAsStateWithLifecycle()
    LaunchedEffect(sharedLink, libraryOrNull != null) {
        val shared = sharedLink ?: return@LaunchedEffect
        if (libraryOrNull == null) return@LaunchedEffect
        app.sharedLink.value = null
        openSave(shared.url, shared.title, null)
    }

    /** Opens an item: in the reader while it is on the reading list, else its bookmark page. */
    fun openItem(item: Item) = push(if (item.readingList == null && item.bookmark != null) Screen.Bookmark(item.key) else Screen.Reader(ReadingLists.readerKey(item)))

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
                    val shownFilter = remember(library, readingFilter) { ReadingLists.shown(library, readingFilter) }
                    val shownBookmarks = remember(library, bookmarkFilter) { Bookmarks.shown(library, bookmarkFilter) }
                    // Back from a folder, a feed or the reading list goes to Home first; back on Home leaves the app.
                    BackHandler(enabled = backStack.size == 1 && (shownSelection != FeedSelection.All || section != HomeSection.Feeds)) {
                        selection = FeedSelection.All
                        section = HomeSection.Feeds
                        readThisVisit = emptySet()
                    }
                    val articles =
                        remember(library, shownSelection, current.unreadOnly, readThisVisit) {
                            library.articlesFor(shownSelection, current.unreadOnly, keep = readThisVisit)
                        }
                    val unreadCounts = remember(library) { library.unreadCounts() }
                    val readingItems = remember(library, shownFilter) { ReadingLists.items(library, shownFilter) }
                    val minutes by produceState(emptyMap<String, Int>(), library.items, library.articles, textWords) {
                        value = withContext(Dispatchers.Default) { readingMinutes(library, textWords) }
                    }
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
                                refreshing = refreshing || refreshRunning,
                                hasAnyArticles = library.articles.isNotEmpty(),
                                loaded = libraryOrNull != null,
                                showImages = current.showImages,
                                section = section,
                                readingList =
                                    ReadingListHome(
                                        filter = shownFilter,
                                        items = readingItems,
                                        tags = remember(library, shownFilter.tab) { ReadingLists.tags(library, shownFilter.tab) },
                                        minutes = minutes,
                                        library = library,
                                        titleFont = current.reader.font.fontFamily(),
                                    ),
                                toReadCount = remember(library) { library.items.count { it.toRead } },
                                bookmarks =
                                    BookmarksHome(
                                        filter = shownBookmarks,
                                        items = remember(library, shownBookmarks) { Bookmarks.items(library, shownBookmarks) },
                                        collections = remember(library) { Bookmarks.collections(library) },
                                        view = current.bookmarksView,
                                        library = library,
                                    ),
                                bookmarkCount = remember(library) { library.items.count { it.bookmark != null } },
                            ),
                        snackbarHostState = snackbar,
                        actions =
                            HomeActions(
                                onSelect = {
                                    selection = it
                                    section = HomeSection.Feeds
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
                                onReadLater = { article -> article.link?.let { readLater(it, article.title, sheet = false) } },
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
                                onOpenTagsManage = { push(Screen.Tags) },
                                onOpenServices = { push(Screen.Services) },
                                onShowFeeds = { section = HomeSection.Feeds },
                                onShowReadingList = { section = HomeSection.ReadingList },
                                onReadingListFilter = {
                                    readingFilter = it
                                    section = HomeSection.ReadingList
                                },
                                onOpenItem = ::openItem,
                                onAddLink = { addLinkOpen = true },
                                onShowBookmarks = { section = HomeSection.Bookmarks },
                                onOpenBookmark = { push(Screen.Bookmark(it.key)) },
                                onBookmarksFilter = {
                                    bookmarkFilter = it
                                    section = HomeSection.Bookmarks
                                },
                                onBookmarksView = { view -> app.appScope.launch { app.settings.setBookmarksView(view) } },
                                onAddBookmark = { openSave(null, null, SaveTo.Bookmark) },
                                onOpenSearch = {
                                    searchFilter = SearchFilter.All
                                    push(Screen.Search)
                                },
                            ),
                    )
                }

                is Screen.Reader -> {
                    val itemKey = ReadingLists.itemKeyOf(screen.articleKey)
                    // A feed article, or an item of the reading list; a saved feed article is both (#104).
                    val feedArticle = if (itemKey == null) library.article(screen.articleKey) else null
                    val item = itemKey?.let { library.item(it) } ?: feedArticle?.let { library.itemFor(it) }
                    val fromFeed = feedArticle ?: item?.let { ReadingLists.feedArticle(it, library) }
                    // Without the feed's text, the service's own (Instapaper's parsed text, own account only).
                    var serviceText by remember(screen.articleKey) { mutableStateOf<String?>(null) }
                    LaunchedEffect(screen.articleKey, item?.key, fromFeed == null) {
                        if (item != null && fromFeed == null && serviceText == null) serviceText = app.services.text(item)
                    }
                    val article = feedArticle ?: fromFeed?.takeIf { serviceText == null } ?: item?.let { ReadingLists.article(it, library, serviceText) }
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
                                    is FullArticle.Loaded -> {
                                        // Kept with the item, so its reading time is known.
                                        item?.let { repository.putText(it.key, ReadingLists.EXTRACTED, loaded.html) }
                                        FullTextState.Shown(loaded.html)
                                    }
                                    FullArticle.Failed -> FullTextState.Failed
                                }
                            }
                    }
                    // Highlights (#102): those made in Instapaper's apps come in when the article opens.
                    LaunchedEffect(item?.key) { item?.let { app.services.takeInHighlights(it) } }
                    val notCopied by produceState(emptySet<String>(), item?.key, item?.highlights, serviceStatus) {
                        value = item?.let { app.services.highlightsNotCopied(it.key) }.orEmpty()
                    }
                    val highlightLink = item?.url ?: feedArticle?.link
                    // Opening a feed article marks it as read.
                    LaunchedEffect(screen.articleKey) { fromFeed?.let { a -> app.appScope.launch { repository.setRead(a.key, true) } } }
                    SystemBarsFollowTheme(current.reader.isDark(appDark))
                    ReaderTheme(style = current.reader, appearance = current.appearance, appDark = appDark) {
                        ReaderScreen(
                            article = article,
                            feed = fromFeed?.let { library.feed(it.feedUrl) } ?: item?.let { ReadingLists.source(it, library) },
                            style = current.reader,
                            // Signed in to Instapaper, Save puts it on the reading list (which syncs there).
                            saveTarget = if (instapaperState is InstapaperState.SignedIn) ShareTarget.Saved else current.saveTarget,
                            shareTarget = current.shareTarget,
                            fullText = fullText,
                            showImages = current.showImages,
                            snackbarHostState = snackbar,
                            textSource = if (serviceText != null) resources.getString(R.string.reader_text_instapaper) else null,
                            highlights =
                                highlightLink?.let { link ->
                                    ReaderHighlights(
                                        highlights = item?.highlights.orEmpty(),
                                        notCopied = notCopied,
                                        // A feed article that isn't saved yet goes on the reading list with its first highlight (#108).
                                        onAdd = { h -> editLibrary { it.addHighlight(link, article?.title, h, System.currentTimeMillis()) } },
                                        onNote = { id, note -> item?.let { i -> editLibrary { it.setHighlightNote(i.key, id, note) } } },
                                        onRemove = { id -> item?.let { i -> editLibrary { it.removeHighlight(i.key, id) } } },
                                    )
                                },
                            actions =
                                ReaderActions(
                                    onBack = ::pop,
                                    onStyleChange = { style -> app.appScope.launch { app.settings.setReaderStyle(style) } },
                                    onSave = {
                                        article?.let {
                                            val inApp = instapaperState is InstapaperState.SignedIn || current.saveTarget == ShareTarget.Saved
                                            if (inApp) it.link?.let { link -> readLater(link, it.title) } else send(it, current.saveTarget)
                                        }
                                    },
                                    onShare = { article?.let { send(it, current.shareTarget) } },
                                    onOpenInBrowser = { article?.let { a -> a.link?.let { ExternalApps.openInTab(context, it, a.title) } } },
                                    onMarkUnread =
                                        feedArticle?.let { a ->
                                            {
                                                app.appScope.launch { repository.setRead(a.key, false) }
                                                pop()
                                            }
                                        },
                                    onOpenLink = { url -> ExternalApps.openInTab(context, url, null) },
                                    onToggleFullText = { fullTextChoice = !wantFullText },
                                    // A feed article not saved yet: keep it straight in Bookmarks (#108).
                                    onKeepAsBookmark =
                                        article?.link?.takeIf { item?.readingList == null && item?.bookmark == null }?.let { link ->
                                            { openSave(link, article.title, SaveTo.Bookmark) }
                                        },
                                ),
                            item =
                                item?.let { i ->
                                    ReaderItem(
                                        item = i,
                                        allTags = library.itemTags(),
                                        collections = library.bookmarks().mapNotNull { it.bookmark?.collection }.distinctBy { it.lowercase() },
                                        onLike = { editLibrary { it.setLiked(i.key, !i.liked) } },
                                        onArchive = { editLibrary { it.setReadingState(i.key, ReadingState.Archived) } },
                                        onUnarchive = { editLibrary { it.setReadingState(i.key, ReadingState.ToRead) } },
                                        onKeep = { collection, note, tags ->
                                            editLibrary { lib ->
                                                lib.keepAsBookmark(i.key, collection, System.currentTimeMillis()).changeItem(i.key) { it.copy(note = note.trim()) }.setTags(i.key, tags)
                                            }
                                            showMessage(resources.getString(R.string.kept_as_bookmark))
                                        },
                                        onTags = { tags -> editLibrary { it.setTags(i.key, tags) } },
                                        onRemove = {
                                            editLibrary { it.setReadingState(i.key, null) }
                                            pop()
                                        },
                                        onProgress = { progress ->
                                            if (kotlin.math.abs(progress - i.progress) >= 0.01f) {
                                                editLibrary { lib -> lib.changeItem(i.key) { if (it.readingList == null) it else it.copy(progress = progress) } }
                                            }
                                        },
                                    )
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
                                onTagWithFolderChange = { enabled -> app.appScope.launch { app.settings.setTagWithFolder(enabled) } },
                                onThemeModeChange = { mode -> app.appScope.launch { app.settings.setThemeMode(mode) } },
                                onDynamicColorChange = { enabled -> app.appScope.launch { app.settings.setDynamicColor(enabled) } },
                                onSignIn = { uiScope.launch { ExternalApps.openLink(context, app.instapaper.begin()) } },
                                onSyncNow = {
                                    app.appScope.launch {
                                        app.sync.sync()
                                        app.services.sync()
                                    }
                                },
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

                Screen.Search -> {
                    SystemBarsFollowTheme(appDark)
                    // Preparing every article's text takes a moment with many feeds: off the main thread.
                    val search by produceState<ArticleSearch?>(null, library) { value = withContext(Dispatchers.Default) { ArticleSearch(library) } }
                    val feedMatches = remember(search, searchQuery) { search?.search(searchQuery) }
                    val itemMatches = remember(library.items, searchQuery) { ItemSearch.search(library.items.filter { it.readingList != null || it.bookmark != null }, searchQuery) }
                    val results =
                        remember(feedMatches, itemMatches, searchQuery, searchFilter) {
                            feedMatches?.let { MixedSearch.mix(searchQuery, it, itemMatches, searchFilter) }
                        }
                    SearchScreen(
                        state =
                            SearchState(
                                query = searchQuery,
                                results = results,
                                feeds = library.feeds,
                                listView = current.listView,
                                showImages = current.showImages,
                                hasItems = library.items.any { it.readingList != null || it.bookmark != null },
                                filter = searchFilter,
                                library = library,
                            ),
                        actions =
                            SearchActions(
                                onQueryChange = { searchQuery = it },
                                onFilterChange = { searchFilter = it },
                                onOpenArticle = { article -> push(Screen.Reader(article.key)) },
                                onOpenItem = ::openItem,
                                onBack = ::pop,
                            ),
                    )
                }

                Screen.Tags -> {
                    SystemBarsFollowTheme(appDark)
                    TagsManageScreen(
                        tags = remember(library) { library.tagCounts() },
                        collections = remember(library) { Bookmarks.collectionCounts(library) },
                        snackbarHostState = snackbar,
                        onBack = ::pop,
                        onRename = { from, to -> editLibrary { it.renameTag(from, to) } },
                        onRemove = { tag -> editLibrary { it.removeTag(tag) } },
                        onRenameCollection = { from, to -> editLibrary { Bookmarks.renameCollection(it, from, to) } },
                        onRemoveCollection = { name -> editLibrary { Bookmarks.removeCollection(it, name) } },
                    )
                }

                is Screen.Bookmark -> {
                    SystemBarsFollowTheme(appDark)
                    val item = library.item(screen.key)?.takeIf { it.bookmark != null }
                    BookmarkScreen(
                        item = item,
                        source = item?.let { ReadingLists.source(it, library) },
                        fromFeed = item?.let { ReadingLists.feedArticle(it, library) }?.let { library.feed(it.feedUrl)?.title },
                        collections = remember(library) { Bookmarks.collections(library) },
                        allTags = remember(library) { library.itemTags() },
                        showImages = current.showImages,
                        snackbarHostState = snackbar,
                        actions =
                            BookmarkActions(
                                onBack = ::pop,
                                onOpen = { item?.let { ExternalApps.openInTab(context, it.url, it.title) } },
                                onReadAgain = {
                                    item?.let { i ->
                                        editLibrary { Bookmarks.readAgain(it, i.key, System.currentTimeMillis()) }
                                        push(Screen.Reader(ReadingLists.readerKey(i)))
                                    }
                                },
                                onShare = { item?.let { ExternalApps.share(context, it.title, it.url, resources.getString(R.string.action_share)) } },
                                onEdit = { collection, note, tags -> item?.let { i -> editLibrary { Bookmarks.edit(it, i.key, collection, note, tags) } } },
                                onRemove = {
                                    item?.let { i -> editLibrary { Bookmarks.remove(it, i.key) } }
                                    pop()
                                    showMessage(resources.getString(R.string.bookmark_removed))
                                },
                            ),
                    )
                }

                Screen.Services -> {
                    SystemBarsFollowTheme(appDark)
                    ServicesScreen(
                        instapaper = instapaperState,
                        status = serviceStatus,
                        onBack = ::pop,
                        onSignIn = { uiScope.launch { ExternalApps.openLink(context, app.instapaper.begin()) } },
                        onSyncNow = { app.appScope.launch { app.services.sync() } },
                        copyHighlights = current.copyHighlights,
                        onCopyHighlightsChange = { enabled -> app.appScope.launch { app.settings.setCopyHighlights(enabled) } },
                        matchTest = matchTest.takeIf { context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 },
                        onRunMatchTest = {
                            matchTest = MatchTestState(running = true)
                            app.appScope.launch {
                                val report =
                                    app.services.matchTest(
                                        extract = { url -> (app.fullArticles.load(url) as? FullArticle.Loaded)?.html },
                                        onProgress = { n -> matchTest = MatchTestState(running = true, checked = n) },
                                    )
                                matchTest = MatchTestState(checked = report?.articles ?: 0, result = report?.summary())
                            }
                        },
                    )
                }
            }
        }
    }

    // Right after something goes on the reading list: like and tag it without leaving where you are.
    val added = addedKey?.let { library.item(it) }
    if (added != null) {
        AddedSheet(
            item = added,
            allTags = library.itemTags(),
            onLike = { editLibrary { it.setLiked(added.key, !added.liked) } },
            onTags = { tags -> editLibrary { it.setTags(added.key, tags) } },
            onDismiss = { addedKey = null },
        )
    }

    if (saveOpen) {
        SaveSheet(
            url = saveUrl.ifEmpty { null },
            title = saveTitle,
            initial = saveInitial,
            collections = Bookmarks.collections(library),
            allTags = library.itemTags(),
            onSave = { url, to, collection, note, tags -> save(url, to, saveTitle.takeIf { saveUrl.isNotEmpty() }, collection, note, tags) },
            onDismiss = { saveOpen = false },
        )
    }

    if (addLinkOpen) {
        AddLinkDialog(
            onAdd = { address ->
                addLinkOpen = false
                val url = FeedDiscovery.normalizeUrl(address)
                if (url == null) showMessage(resources.getString(R.string.add_link_invalid)) else readLater(url, null)
            },
            onDismiss = { addLinkOpen = false },
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
                        section = HomeSection.Feeds
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

/**
 * About how many minutes each reading-list item takes to read: from the longest text known, the
 * feed's own (when it isn't a summary) or a text kept for the item.
 */
private fun readingMinutes(
    library: Library,
    textWords: Map<String, Int>,
): Map<String, Int> =
    library.items.filter { it.readingList != null }.associate { item ->
        val feedText = ReadingLists.feedArticle(item, library)?.html?.takeIf { !needsFullText(it, item.url) }
        val words = maxOf(feedText?.let { ReadingLists.words(it) } ?: 0, textWords[item.key] ?: 0)
        item.key to ReadingLists.minutes(words)
    }

private val ReadingFilterSaver =
    Saver<ReadingListFilter, String>(
        save = { "${it.tab.name}|${it.likedOnly}|${it.tag.orEmpty()}" },
        restore = {
            val parts = it.split('|', limit = 3)
            ReadingListFilter(
                tab = ReadingListTab.entries.firstOrNull { tab -> tab.name == parts.getOrNull(0) } ?: ReadingListTab.ToRead,
                likedOnly = parts.getOrNull(1) == "true",
                tag = parts.getOrNull(2)?.ifEmpty { null },
            )
        },
    )

private val BookmarkFilterSaver =
    Saver<BookmarkFilter, String>(
        save = { "${it.kind?.name.orEmpty()}|${it.collection.orEmpty()}" },
        restore = {
            val parts = it.split('|', limit = 2)
            BookmarkFilter(
                collection = parts.getOrNull(1)?.ifEmpty { null },
                kind = BookmarkKind.entries.firstOrNull { kind -> kind.name == parts.getOrNull(0) },
            )
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
