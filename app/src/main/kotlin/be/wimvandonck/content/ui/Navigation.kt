package be.wimvandonck.content.ui

import be.wimvandonck.content.data.ReadingListEntry
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
import be.wimvandonck.content.R
import be.wimvandonck.content.ui.feeds.ManageTab
import be.wimvandonck.content.data.ArticleReport
import be.wimvandonck.content.data.SharedArticle
import be.wimvandonck.content.data.Sharing
import be.wimvandonck.content.sync.AccountState
import be.wimvandonck.content.sync.ShareResult
import be.wimvandonck.content.feed.SiteSignIn
import be.wimvandonck.content.feed.SiteSignIns
import be.wimvandonck.content.ui.reader.SiteSignInScreen
import be.wimvandonck.content.ui.reader.forgetSiteCookies
import be.wimvandonck.content.data.Item
import be.wimvandonck.content.data.ItemSearch
import be.wimvandonck.content.data.Place
import be.wimvandonck.content.data.SaveChoice
import be.wimvandonck.content.data.SaveChoices
import be.wimvandonck.content.data.Saved
import be.wimvandonck.content.data.SharedText
import be.wimvandonck.content.data.SavedFilter
import be.wimvandonck.content.data.SavedTab
import be.wimvandonck.content.data.ReadingLists
import be.wimvandonck.content.data.ReadingState
import be.wimvandonck.content.data.addToReadingList
import be.wimvandonck.content.data.addHighlight
import be.wimvandonck.content.data.removeHighlight
import be.wimvandonck.content.data.setHighlightNote
import be.wimvandonck.content.data.changeItem
import be.wimvandonck.content.data.item
import be.wimvandonck.content.data.itemFor
import be.wimvandonck.content.data.itemTags
import be.wimvandonck.content.data.tagWithFeedFolder
import be.wimvandonck.content.data.removeTag
import be.wimvandonck.content.data.renameTag
import be.wimvandonck.content.data.setLiked
import be.wimvandonck.content.data.setReadingState
import be.wimvandonck.content.data.tagCounts
import be.wimvandonck.content.feed.FeedDiscovery
import be.wimvandonck.content.ui.feeds.TagsManageScreen
import be.wimvandonck.content.ui.home.HomeSection
import be.wimvandonck.content.ui.home.SavedHome
import be.wimvandonck.content.ui.reader.SaveSheet
import be.wimvandonck.content.ui.reader.ReaderHighlights
import be.wimvandonck.content.ui.reader.SheetKind
import be.wimvandonck.content.ui.reader.ReaderItem
import be.wimvandonck.content.ui.reader.fontFamily
import be.wimvandonck.content.ui.settings.ServiceScreen
import be.wimvandonck.content.ui.settings.serviceName
import be.wimvandonck.content.ui.settings.ServicesActions
import be.wimvandonck.content.ui.settings.ServicesScreen
import be.wimvandonck.content.ui.settings.ServicesState
import be.wimvandonck.content.data.AddFeedResult
import be.wimvandonck.content.data.Article
import be.wimvandonck.content.data.ArticleSearch
import be.wimvandonck.content.data.SearchFilters
import be.wimvandonck.content.data.SearchResults
import be.wimvandonck.content.ui.home.MainTab
import be.wimvandonck.content.data.SearchFilter
import be.wimvandonck.content.ui.home.SearchActions
import be.wimvandonck.content.ui.home.SearchScreen
import be.wimvandonck.content.ui.home.SearchState
import be.wimvandonck.content.data.FeedSelection
import be.wimvandonck.content.data.FullArticle
import be.wimvandonck.content.data.Library
import be.wimvandonck.content.data.ShareTarget
import be.wimvandonck.content.data.Settings
import be.wimvandonck.content.data.needsFullText
import be.wimvandonck.content.data.wantsFullText
import be.wimvandonck.content.instapaper.InstapaperNotice
import be.wimvandonck.content.sync.AccountNotice
import be.wimvandonck.content.instapaper.InstapaperState
import be.wimvandonck.content.rssReaderApp
import be.wimvandonck.content.ui.feeds.FeedsActions
import be.wimvandonck.content.ui.feeds.FeedsScreen
import be.wimvandonck.content.ui.feeds.FeedsState
import be.wimvandonck.content.ui.home.AddFeedDialog
import be.wimvandonck.content.ui.home.HomeActions
import be.wimvandonck.content.ui.home.HomeScreen
import be.wimvandonck.content.ui.home.HomeState
import be.wimvandonck.content.ui.home.errorRes
import be.wimvandonck.content.ui.reader.FullTextState
import be.wimvandonck.content.ui.reader.ReaderActions
import be.wimvandonck.content.ui.reader.ReaderScreen
import be.wimvandonck.content.ui.reader.ReaderTheme
import be.wimvandonck.content.ui.settings.SettingsActions
import be.wimvandonck.content.ui.settings.SettingsScreen
import be.wimvandonck.content.ui.settings.SettingsState
import be.wimvandonck.content.ui.theme.Motion
import be.wimvandonck.content.ui.theme.SystemBarsFollowTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Screens of the app. A tiny back stack; no navigation library needed. */
sealed interface Screen {
    data object Home : Screen

    /** A feed article ("<feed url> <id>") or a saved item ("item <item key>"). */
    data class Reader(val articleKey: String) : Screen

    data object Settings : Screen

    /** Manage feeds: feeds and folders. */
    data object Feeds : Screen

    /** Folders and tags: rename and remove Saved's folders and tags. */
    data object Tags : Screen

    /** Connected services (#109). */
    data object Services : Screen

    /** One connected service: its account, import and what it syncs (#145). */
    data class Service(val id: String) : Screen

    /** Search through every feed's articles and Saved. */
    data object Search : Screen

    /** Signing in to a site (#180), at this address. */
    data class SiteSignIn(val url: String) : Screen

    companion object {
        fun encode(screen: Screen): String =
            when (screen) {
                Home -> "home"
                Settings -> "settings"
                Feeds -> "feeds"
                Tags -> "tags"
                Services -> "services"
                is Service -> "service:" + screen.id
                Search -> "search"
                is Reader -> "reader:" + screen.articleKey
                is SiteSignIn -> "signin:" + screen.url
            }

        fun decode(text: String): Screen =
            when {
                text == "settings" -> Settings
                text == "feeds" -> Feeds
                text == "tags" -> Tags
                text == "services" -> Services
                text.startsWith("service:") -> Service(text.removePrefix("service:"))
                text == "search" -> Search
                text.startsWith("reader:") -> Reader(text.removePrefix("reader:"))
                text.startsWith("signin:") -> SiteSignIn(text.removePrefix("signin:"))
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
fun RssReaderNavigation(
    /** An article to open (its reader key), asked for by the share sheet's Open (#142). */
    openRequest: String? = null,
    onOpened: () -> Unit = {},
) {
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
    val serviceStatuses by app.services.statuses.collectAsStateWithLifecycle()
    // Which services keep each saved item in step: removing one both keep asks which (#145).
    val servicePresence by app.services.presence.collectAsStateWithLifecycle()
    val notCopied by app.services.notCopied.collectAsStateWithLifecycle()
    val instapaperState by app.instapaper.state.collectAsStateWithLifecycle()
    val instapaperNotice by app.instapaper.notice.collectAsStateWithLifecycle()
    val accountState by app.account.state.collectAsStateWithLifecycle()
    val accountNotice by app.account.notice.collectAsStateWithLifecycle()
    val siteSignIns by app.siteSignIns.signIns.collectAsStateWithLifecycle()
    val current = settings ?: Settings()
    val library = libraryOrNull ?: Library()
    val snackbar = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()

    val backStack = rememberSaveable(saver = BackStackSaver) { mutableStateListOf<Screen>(Screen.Home) }
    var selection by rememberSaveable(stateSaver = SelectionSaver) { mutableStateOf<FeedSelection>(FeedSelection.All) }
    // Articles opened from the Unread list stay in it (marked as read) until the list changes:
    // another feed, the other filter, or a refresh. Otherwise rows would jump away.
    var readThisVisit by rememberSaveable { mutableStateOf(setOf<String>()) }

    // The destination shown (Feeds or Saved) and Saved's tab, chips and type.
    var section by rememberSaveable { mutableStateOf(HomeSection.Feeds) }
    var savedFilter by rememberSaveable(stateSaver = SavedFilterSaver) { mutableStateOf(SavedFilter()) }
    // The item just saved, while the sheet to file it is open, and whether that was in the reader
    // (already in the article: its message needs no Open).
    var addedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var addedFromReader by rememberSaveable { mutableStateOf(false) }
    // The item as it was before that save (null: not saved then), for Remove on the sheet.
    var addedBefore by remember { mutableStateOf<Item?>(null) }
    // The save sheet while it is open: the link (empty: type one) and its title.
    var saveOpen by rememberSaveable { mutableStateOf(false) }
    var saveUrl by rememberSaveable { mutableStateOf("") }
    var saveTitle by rememberSaveable { mutableStateOf<String?>(null) }

    fun openSave(
        url: String?,
        title: String?,
    ) {
        saveUrl = url.orEmpty()
        saveTitle = title
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

    /** Feeds and tags (#222): a tab replaces the shown screen, so Back leaves at once. */
    fun showManageTab(tab: ManageTab) {
        val next = if (tab == ManageTab.Feeds) Screen.Feeds else Screen.Tags
        if (backStack.lastOrNull() != next) backStack[backStack.lastIndex] = next
    }

    fun showMessage(text: String) {
        uiScope.launch { snackbar.showSnackbar(text) }
    }

    /** A message with one action (Undo, File), run when it is tapped. */
    fun showMessage(
        text: String,
        action: String,
        onAction: () -> Unit,
    ) {
        uiScope.launch {
            if (snackbar.showSnackbar(text, actionLabel = action, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) onAction()
        }
    }


    fun editLibrary(change: (Library) -> Library) {
        app.appScope.launch { repository.edit(change) }
    }

    // The saved item whose File sheet is open (a swipe right in Saved).
    var fileKey by rememberSaveable { mutableStateOf<String?>(null) }

    /** Archives a saved item, with Undo (back where it was, in its folder); an archived one goes back to read. */
    fun archive(item: Item) {
        if (item.archived) {
            editLibrary { it.setReadingState(item.key, ReadingState.ToRead) }
            showMessage(resources.getString(R.string.saved_to, resources.getString(R.string.reading_list_to_read)))
            return
        }
        val folder = item.folder
        editLibrary { it.setReadingState(item.key, ReadingState.Archived) }
        showMessage(resources.getString(R.string.saved_archived), resources.getString(R.string.action_undo)) {
            editLibrary { Saved.file(it, item.key, folder) }
        }
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
    // Which kind of results Search shows (the chips) and that kind's filters (#171).
    var searchFilters by rememberSaveable(stateSaver = SearchFiltersSaver) { mutableStateOf(SearchFilters()) }

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

    // Something another Content user shared arrived (#167): say so, once.
    LaunchedEffect(Unit) {
        app.sync.received.collect { got ->
            val names = got.from.joinToString(", ") { it.label }
            showMessage(resources.getQuantityString(R.plurals.share_user_received, got.count, names, got.count))
        }
    }

    // An emailed sign-in link opened here (#166): say how it went, once.
    LaunchedEffect(accountNotice) {
        val notice = accountNotice ?: return@LaunchedEffect
        showMessage(
            resources.getString(
                when (notice) {
                    AccountNotice.SignedIn -> R.string.account_link_signed_in
                    AccountNotice.EmailAdded -> R.string.account_link_added
                    AccountNotice.LinkExpired -> R.string.account_link_expired
                    AccountNotice.EmailTaken -> R.string.account_link_taken
                    AccountNotice.Failed -> R.string.account_link_failed
                },
            ),
        )
        app.account.clearNotice()
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
     * Saves [link] to To read (#107), then opens the sheet to file, like, tag and note it (#143); when
     * it closes, a message says where it went, with Open unless [inReader] (#142). A link without a
     * title gets the page's own afterwards.
     */
    fun readLater(
        link: String,
        title: String?,
        inReader: Boolean,
    ) {
        app.appScope.launch {
            var added: Item? = null
            repository.edit { library ->
                val (updated, item) = library.addToReadingList(link, title, System.currentTimeMillis())
                added = item
                addedBefore = item?.let { library.item(it.key) }
                if (item != null && current.tagWithFolder) updated.tagWithFeedFolder(item.key) else updated
            }
            val item = added
            if (item == null) {
                showMessage(resources.getString(R.string.add_link_invalid))
                return@launch
            }
            addedFromReader = inReader
            addedKey = item.key
            repository.describe(item.key)
        }
    }

    /** Where the sheet put an article, as the message says it: To read, the folder, or Archive. */
    fun placeName(place: Place): String =
        when (place) {
            Place.ToRead -> resources.getString(R.string.reading_list_to_read)
            Place.Archive -> resources.getString(R.string.reading_list_archive)
            is Place.Folder -> place.name
        }

    /** Saves a link from the save sheet (#143), says where it went, and fills in the page's title and picture. */
    fun save(
        url: String,
        title: String?,
        choice: SaveChoice,
    ) {
        app.appScope.launch {
            var saved: Item? = null
            repository.edit { library ->
                val (updated, item) = SaveChoices.saveNew(library, url, title, System.currentTimeMillis(), choice)
                saved = item
                if (item != null && current.tagWithFolder) updated.tagWithFeedFolder(item.key) else updated
            }
            val item = saved
            if (item == null) {
                showMessage(resources.getString(R.string.add_link_invalid))
                return@launch
            }
            // Not in the article yet: Open goes there (#142).
            showMessage(resources.getString(R.string.saved_to, placeName(choice.place)), resources.getString(R.string.action_open)) {
                push(Screen.Reader(ReadingLists.readerKey(item)))
            }
            repository.describe(item.key)
        }
    }

    fun openItem(item: Item) = push(Screen.Reader(ReadingLists.readerKey(item)))

    LaunchedEffect(openRequest) {
        val key = openRequest ?: return@LaunchedEffect
        section = HomeSection.Saved
        push(Screen.Reader(key))
        onOpened()
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
                    val shownFilter = remember(library, savedFilter) { Saved.shown(library, savedFilter) }
                    // Back from a folder, a feed or Saved goes to Home first; back on Home leaves the app.
                    BackHandler(enabled = backStack.size == 1 && (shownSelection != FeedSelection.All || section != HomeSection.Feeds)) {
                        // Back from Saved keeps what Feeds showed; only another selection starts afresh (#157).
                        if (selection != FeedSelection.All) readThisVisit = emptySet()
                        selection = FeedSelection.All
                        section = HomeSection.Feeds
                    }
                    val articles =
                        remember(library, shownSelection, current.unreadOnly, readThisVisit) {
                            library.articlesFor(shownSelection, current.unreadOnly, keep = readThisVisit)
                        }
                    val unreadCounts = remember(library) { library.unreadCounts() }
                    val savedItems = remember(library, shownFilter) { Saved.items(library, shownFilter) }
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
                                saved =
                                    SavedHome(
                                        filter = shownFilter,
                                        items = savedItems,
                                        folders = remember(library) { Saved.folders(library) },
                                        tags = remember(library) { Saved.tags(library, SavedTab.All) },
                                        minutes = minutes,
                                        library = library,
                                        titleFont = current.reader.font.fontFamily(),
                                        listView = current.listView,
                                    ),
                                toReadCount = remember(library) { library.items.count { Saved.isIn(it, SavedTab.ToRead) } },
                            ),
                        snackbarHostState = snackbar,
                        actions =
                            HomeActions(
                                onSelect = {
                                    // The same feed or folder again keeps the articles read this visit (#157).
                                    if (it != selection) readThisVisit = emptySet()
                                    selection = it
                                    section = HomeSection.Feeds
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
                                // Swipe left is Save, so it goes where the reader's Save button goes (Settings › Save button).
                                onReadLater = { article ->
                                    if (current.saveTarget == ShareTarget.Saved) {
                                        article.link?.let { readLater(it, article.title, inReader = false) }
                                    } else {
                                        send(article, current.saveTarget)
                                    }
                                },
                                onRefresh = ::refresh,
                                onMarkAllRead = {
                                    // Done with the section: nothing read stays in the Unread list (#157).
                                    readThisVisit = emptySet()
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
                                onShowFeeds = { section = HomeSection.Feeds },
                                onShowSaved = { section = HomeSection.Saved },
                                onSavedFilter = {
                                    savedFilter = it
                                    section = HomeSection.Saved
                                },
                                onOpenItem = ::openItem,
                                // Add link: the save sheet asks for the address, into the folder shown.
                                onAddLink = { openSave(null, null) },
                                onArchiveItem = ::archive,
                                onFileItem = { fileKey = it.key },
                                onShareArticle = { article -> send(article, current.shareTarget) },
                                onPlaceShared = { item, state ->
                                    val now = System.currentTimeMillis()
                                    editLibrary { lib -> lib.changeItem(item.key) { it.copy(readingList = ReadingListEntry(state, now)) } }
                                },
                                onShareItem = { item -> send(ReadingLists.article(item, library), current.shareTarget) },
                                onOpenSearch = { push(Screen.Search) },
                            ),
                    )
                }

                is Screen.Reader -> {
                    val itemKey = ReadingLists.itemKeyOf(screen.articleKey)
                    // A feed article, or an item of the reading list; a saved feed article is both (#104).
                    val feedArticle = if (itemKey == null) library.article(screen.articleKey) else null
                    val item = itemKey?.let { library.item(it) } ?: feedArticle?.let { library.itemFor(it) }
                    val fromFeed = feedArticle ?: item?.let { ReadingLists.feedArticle(it, library) }
                    // Always Reader's own text: the feed's, else the full article it extracts (#134).
                    val article = feedArticle ?: fromFeed ?: item?.let { ReadingLists.article(it, library) }
                    // The full article loads by itself when the setting is on and the feed only has a
                    // summary; the reader's menu switches between the two (null: follow the setting).
                    var fullTextChoice by rememberSaveable(screen.articleKey) { mutableStateOf<Boolean?>(null) }
                    val isSummary = remember(article?.html, article?.link) { article != null && needsFullText(article.html, article.link) }
                    val wantFullText = article?.link != null && wantsFullText(fullTextChoice, current.fullArticles, fromFeed != null, isSummary, saved = item?.readingList != null)
                    var fullText by remember(screen.articleKey) { mutableStateOf<FullTextState>(FullTextState.Off) }
                    // Signed in to the article's site (#180): its text loads again, with the sign-in.
                    val signedIn = article?.link?.let { SiteSignIns.forUrl(it, siteSignIns) }
                    LaunchedEffect(screen.articleKey, wantFullText, signedIn?.savedAt) {
                        val link = article?.link
                        fullText =
                            if (!wantFullText || link == null) {
                                FullTextState.Off
                            } else {
                                fullText = FullTextState.Loading
                                // A saved article shows the one text every device shows (stored in the account),
                                // unless this device is signed in to its site: then its own full text (#180).
                                val saved = item?.readingList != null && signedIn == null
                                val html =
                                    SharedText.resolve(
                                        saved,
                                        stored = { item?.let { app.sync.storedText(it.key) } },
                                        extract = { (app.fullArticles.load(link) as? FullArticle.Loaded)?.html },
                                        store = { own -> item?.let { app.sync.storeText(it.key, own) } },
                                    )
                                if (html != null) {
                                    // Kept with the item, so its reading time is known.
                                    item?.let { repository.putText(it.key, ReadingLists.EXTRACTED, html) }
                                    FullTextState.Shown(html)
                                } else {
                                    FullTextState.Failed
                                }
                            }
                    }
                    // Opening a feed article marks it as read.
                    LaunchedEffect(screen.articleKey) { fromFeed?.let { a -> app.appScope.launch { repository.setRead(a.key, true) } } }
                    // A saved article's highlights made in Instapaper's own apps come in when it opens (#164).
                    LaunchedEffect(screen.articleKey) { item?.takeIf { it.readingList != null }?.let { app.services.lookAtHighlights(it.key) } }
                    SystemBarsFollowTheme(current.reader.isDark(appDark))
                    ReaderTheme(style = current.reader, appearance = current.appearance, appDark = appDark) {
                        ReaderScreen(
                            article = article,
                            feed = fromFeed?.let { library.feed(it.feedUrl) } ?: item?.let { ReadingLists.source(it, library) },
                            style = current.reader,
                            // The same with or without a service, which only syncs Saved.
                            saveTarget = current.saveTarget,
                            shareTarget = current.shareTarget,
                            fullText = fullText,
                            showImages = current.showImages,
                            snackbarHostState = snackbar,
                            highlights =
                                (item?.url ?: article?.link)?.let { link ->
                                    ReaderHighlights(
                                        highlights = item?.highlights.orEmpty(),
                                        // A feed article that isn't saved yet goes to To read with its first highlight.
                                        onAdd = { h -> editLibrary { it.addHighlight(link, article?.title, h, System.currentTimeMillis()) } },
                                        onNote = { id, note -> item?.let { i -> editLibrary { it.setHighlightNote(i.key, id, note) } } },
                                        onRemove = { id -> item?.let { i -> editLibrary { it.removeHighlight(i.key, id) } } },
                                        notCopied = item?.let { notCopied[it.key] }.orEmpty(),
                                    )
                                },
                            actions =
                                ReaderActions(
                                    onBack = ::pop,
                                    onStyleChange = { style -> app.appScope.launch { app.settings.setReaderStyle(style) } },
                                    onSave = {
                                        article?.let {
                                            val inApp = current.saveTarget == ShareTarget.Saved
                                            if (inApp) it.link?.let { link -> readLater(link, it.title, inReader = true) } else send(it, current.saveTarget)
                                        }
                                    },
                                    onShare = { article?.let { send(it, current.shareTarget) } },
                                    onOpenInBrowser = { article?.let { a -> a.link?.let { ExternalApps.openInTab(context, it, a.title) } } },
                                    onSignIn = article?.link?.takeIf { SiteSignIns.siteOf(it) != null }?.let { link -> { push(Screen.SiteSignIn(link)) } },
                                    onShareWithUser =
                                        article?.link?.takeIf { accountState is AccountState.SignedIn }?.let { link ->
                                            { email, message, result ->
                                                uiScope.launch {
                                                    val shared = item?.let { Sharing.article(it) } ?: SharedArticle(link, article?.title ?: link)
                                                    val r = app.sync.share(email, listOf(shared), null, message)
                                                    result(r)
                                                    if (r == ShareResult.Sent) showMessage(resources.getString(R.string.share_user_sent, email))
                                                }
                                            }
                                        },
                                    onReport =
                                        article?.link?.let { link ->
                                            { problem, comment ->
                                                val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
                                                ExternalApps.openLink(context, ArticleReport.url(link, problem, comment, "Android", version))
                                            }
                                        },
                                    onMarkUnread =
                                        feedArticle?.let { a ->
                                            {
                                                app.appScope.launch { repository.setRead(a.key, false) }
                                                pop()
                                            }
                                        },
                                    onOpenLink = { url -> ExternalApps.openInTab(context, url, null) },
                                    onToggleFullText = { fullTextChoice = !wantFullText },
                                ),
                            item =
                                item?.let { i ->
                                    ReaderItem(
                                        item = i,
                                        folders = Saved.folders(library),
                                        tags = Saved.tags(library, SavedTab.All),
                                        onPlace = { place ->
                                            if (place == Place.Archive) {
                                                // Done with it: archived, back to the list, with Undo.
                                                archive(i)
                                                pop()
                                            } else {
                                                editLibrary { SaveChoices.apply(it, i.key, SaveChoices.of(i).copy(place = place)) }
                                            }
                                        },
                                        onLike = { editLibrary { it.setLiked(i.key, !i.liked) } },
                                        onTag = { tag -> editLibrary { lib -> lib.item(i.key)?.let { now -> SaveChoices.apply(lib, i.key, SaveChoices.withTag(SaveChoices.of(now), tag)) } ?: lib } },
                                        onOrganise = { fileKey = i.key },
                                        onRemove = {
                                            editLibrary { Saved.remove(it, i.key) }
                                            pop()
                                            showMessage(resources.getString(R.string.saved_removed))
                                        },
                                        services = servicePresence[i.key].orEmpty(),
                                        onRemoveFrom = { id ->
                                            app.appScope.launch {
                                                val removed = app.services.removeFrom(i.key, id)
                                                val name = resources.getString(serviceName(id))
                                                showMessage(resources.getString(if (removed) R.string.saved_removed_from else R.string.saved_remove_from_failed, name))
                                            }
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
                                account = accountState,
                                instapaperConnected = instapaperState is InstapaperState.SignedIn,
                                hasFeeds = library.feeds.isNotEmpty(),
                                siteSignIns = siteSignIns.map { it.site },
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
                                onOpenServices = { push(Screen.Services) },
                                onImportOpml = importOpml,
                                onExportOpml = exportOpml,
                                onRemoveSiteSignIn = { site ->
                                    siteSignIns.firstOrNull { it.site == site }?.let { forgetSiteCookies(it.site, it.cookie) }
                                    app.appScope.launch { app.siteSignIns.remove(site) }
                                },
                                onSendLink = { email, result ->
                                    uiScope.launch { result(runCatching { app.account.sendLink(email) }.isSuccess) }
                                },
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
                                onTab = ::showManageTab,
                            ),
                    )
                }

                is Screen.SiteSignIn -> {
                    SystemBarsFollowTheme(appDark)
                    SiteSignInScreen(
                        url = screen.url,
                        onBack = ::pop,
                        onDone = { site, cookie ->
                            pop()
                            if (cookie == null) {
                                showMessage(resources.getString(R.string.site_sign_in_nothing, site))
                            } else {
                                app.appScope.launch {
                                    app.siteSignIns.save(SiteSignIn(site, cookie, System.currentTimeMillis()))
                                    app.fullArticles.forget(screen.url)
                                }
                                showMessage(resources.getString(R.string.site_sign_in_saved, site))
                            }
                        },
                    )
                }

                Screen.Search -> {
                    SystemBarsFollowTheme(appDark)
                    // Preparing every article's text takes a moment with many feeds: off the main thread.
                    val search by produceState<ArticleSearch?>(null, library) { value = withContext(Dispatchers.Default) { ArticleSearch(library) } }
                    // Without words typed, the list is everything the filters let through (#171).
                    val feedMatches = remember(search, searchQuery) { if (searchQuery.isBlank()) emptyList() else search?.search(searchQuery) }
                    val itemMatches = remember(library.items, searchQuery) { ItemSearch.search(library.items.filter { it.readingList != null }, searchQuery) }
                    val results =
                        remember(library, feedMatches, itemMatches, searchQuery, searchFilters) {
                            feedMatches?.let { SearchResults.of(searchQuery, library, it, itemMatches, searchFilters) }
                        }
                    SearchScreen(
                        state =
                            SearchState(
                                query = searchQuery,
                                results = results,
                                feeds = library.feeds,
                                listView = current.listView,
                                showImages = current.showImages,
                                hasItems = library.items.any { it.readingList != null },
                                filters = searchFilters,
                                library = library,
                                feedFolders = library.folders,
                                savedFolders = remember(library) { Saved.folders(library) },
                                savedTags = remember(library) { Saved.tags(library, SavedTab.All) },
                                titleFont = current.reader.font.fontFamily(),
                            ),
                        actions =
                            SearchActions(
                                onQueryChange = { searchQuery = it },
                                onFiltersChange = { searchFilters = it },
                                onOpenArticle = { article ->
                                    // Like from the list: it stays in the Unread list when you go back (#157).
                                    readThisVisit = readThisVisit + article.key
                                    push(Screen.Reader(article.key))
                                },
                                onOpenItem = ::openItem,
                                onTab = { tab ->
                                    pop()
                                    section = if (tab == MainTab.Saved) HomeSection.Saved else HomeSection.Feeds
                                },
                                onBack = ::pop,
                            ),
                    )
                }

                Screen.Tags -> {
                    SystemBarsFollowTheme(appDark)
                    TagsManageScreen(
                        tags = remember(library) { library.tagCounts() },
                        folders = remember(library) { Saved.folderCounts(library) },
                        snackbarHostState = snackbar,
                        onBack = ::pop,
                        onRename = { from, to -> editLibrary { it.renameTag(from, to) } },
                        onRemove = { tag -> editLibrary { it.removeTag(tag) } },
                        onRenameFolder = { from, to -> editLibrary { Saved.renameFolder(it, from, to) } },
                        onRemoveFolder = { name -> editLibrary { Saved.removeFolder(it, name) } },
                        onTab = ::showManageTab,
                        onShareTag =
                            if (accountState is AccountState.SignedIn) {
                                { tag, email, message, result ->
                                    uiScope.launch {
                                        val r = app.sync.share(email, Sharing.tagArticles(library, tag), tag, message)
                                        result(r)
                                        if (r == ShareResult.Sent) showMessage(resources.getString(R.string.share_user_sent, email))
                                    }
                                }
                            } else {
                                null
                            },
                    )
                }

                Screen.Services, is Screen.Service -> {
                    SystemBarsFollowTheme(appDark)
                    val servicesState =
                        ServicesState(
                            instapaper = instapaperState,
                            statuses = serviceStatuses,
                            imports = current.imports,
                            copyHighlights = current.copyHighlights,
                        )
                    val servicesActions =
                        ServicesActions(
                            onBack = ::pop,
                            onSignIn = { uiScope.launch { ExternalApps.openLink(context, app.instapaper.begin()) } },
                            // Disconnects Instapaper only; the Content account stays signed in (#166).
                            onSignOut = { app.appScope.launch { app.instapaper.signOut() } },
                            onSyncNow = { app.appScope.launch { app.services.sync() } },
                            onOpenService = { id -> push(Screen.Service(id)) },
                            onCopyHighlightsChange = { on ->
                                app.appScope.launch {
                                    app.settings.setCopyHighlights(on)
                                    if (on) app.services.sync()
                                }
                            },
                        )
                    if (screen is Screen.Service) ServiceScreen(screen.id, servicesState, servicesActions) else ServicesScreen(servicesState, servicesActions)
                }

            }
        }
    }

    /** Just saved: says where it went; from the list, Open goes to the article (#142). */
    fun confirmSaved(
        item: Item,
        place: Place,
    ) {
        val text = resources.getString(R.string.saved_to, placeName(place))
        if (addedFromReader) {
            showMessage(text)
        } else {
            showMessage(text, resources.getString(R.string.action_open)) { push(Screen.Reader(ReadingLists.readerKey(item))) }
        }
    }

    // Right after something is saved (the reader, a swipe), or organising a saved one (a swipe
    // right in Saved): the one sheet (#143), which writes what it says when it closes.
    val sheetKey = addedKey ?: fileKey
    val sheetItem = sheetKey?.let { library.item(it) }
    if (sheetItem != null) {
        SaveSheet(
            kind = if (addedKey != null) SheetKind.JustSaved else SheetKind.Organise,
            initial = SaveChoices.of(sheetItem),
            folders = Saved.folders(library),
            allTags = library.itemTags(),
            onSave = { _, choice ->
                editLibrary { SaveChoices.apply(it, sheetItem.key, choice) }
                if (addedKey != null) confirmSaved(sheetItem, choice.place)
            },
            // Cancelled right after saving: it stays where it was saved (#156).
            onCancel = { if (addedKey != null) confirmSaved(sheetItem, SaveChoices.of(sheetItem).place) },
            // Remove right after saving takes the save back: as if it was never saved.
            onRemove = {
                val before = addedBefore
                editLibrary { SaveChoices.undoSave(it, sheetItem.key, before) }
                showMessage(resources.getString(R.string.saved_removed))
            },
            onDismiss = {
                addedKey = null
                fileKey = null
            },
            key = sheetItem.key,
        )
    }

    if (saveOpen) {
        SaveSheet(
            kind = SheetKind.New,
            // Add link from a folder in Saved starts in that folder.
            initial =
                SaveChoice(
                    place =
                        (savedFilter.tab as? SavedTab.Folder)
                            ?.takeIf { saveUrl.isEmpty() && section == HomeSection.Saved }
                            ?.let { Place.Folder(it.name) } ?: Place.ToRead,
                ),
            url = saveUrl.ifEmpty { null },
            title = saveTitle,
            folders = Saved.folders(library),
            allTags = library.itemTags(),
            onSave = { url, choice -> url?.let { save(it, saveTitle.takeIf { saveUrl.isNotEmpty() }, choice) } },
            onDismiss = { saveOpen = false },
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

/** Saved's tab, Liked, tags and type, kept across rotation and process death. */
private val SavedFilterSaver =
    Saver<SavedFilter, String>(
        save = { f ->
            val tab =
                when (val t = f.tab) {
                    SavedTab.ToRead -> "toread"
                    SavedTab.All -> "all"
                    SavedTab.Archive -> "archive"
                    SavedTab.Shared -> "shared"
                    is SavedTab.Folder -> "folder:" + t.name
                }
            // The third slot was the type (dropped); it stays empty so older saved states still read.
            listOf(tab, f.likedOnly.toString(), "", f.tags.joinToString("\u0001")).joinToString("\u0000")
        },
        restore = {
            val parts = it.split('\u0000')
            val tab = parts.getOrNull(0).orEmpty()
            SavedFilter(
                tab =
                    when {
                        tab == "all" -> SavedTab.All
                        tab == "archive" -> SavedTab.Archive
                        tab == "shared" -> SavedTab.Shared
                        tab.startsWith("folder:") -> SavedTab.Folder(tab.removePrefix("folder:"))
                        else -> SavedTab.ToRead
                    },
                likedOnly = parts.getOrNull(1) == "true",
                tags = parts.getOrNull(3)?.split('\u0001')?.filter { t -> t.isNotEmpty() }?.toSet().orEmpty(),
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
                is FeedSelection.Tagged -> "tag:" + it.tag
            }
        },
        restore = {
            when {
                it.startsWith("folder:") -> FeedSelection.InFolder(it.removePrefix("folder:"))
                it.startsWith("feed:") -> FeedSelection.Single(it.removePrefix("feed:"))
                it.startsWith("tag:") -> FeedSelection.Tagged(it.removePrefix("tag:"))
                else -> FeedSelection.All
            }
        },
    )

/** Search's kind and filters (#171), kept across rotation and process death. */
private val SearchFiltersSaver =
    Saver<SearchFilters, String>(
        save = { f ->
            listOf(f.scope.name, f.feedFolderId, f.feedUrl, f.savedFolder, f.tag).joinToString("\u0000") { it.orEmpty() }
        },
        restore = {
            val parts = it.split('\u0000').map { p -> p.ifEmpty { null } }
            SearchFilters(
                scope = SearchFilter.entries.firstOrNull { s -> s.name == parts.getOrNull(0) } ?: SearchFilter.All,
                feedFolderId = parts.getOrNull(1),
                feedUrl = parts.getOrNull(2),
                savedFolder = parts.getOrNull(3),
                tag = parts.getOrNull(4),
            )
        },
    )
