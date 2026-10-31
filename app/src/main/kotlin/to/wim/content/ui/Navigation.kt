package to.wim.content.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.core.content.pm.ShortcutManagerCompat
import to.wim.content.data.LauncherFolders
import to.wim.content.data.ShareApps
import to.wim.content.data.Sharing
import to.wim.content.instapaper.InstapaperReadingList
import to.wim.content.services.ServiceStatus
import to.wim.content.ui.reader.ShareSheet
import to.wim.content.ui.reader.messageRes
import to.wim.content.ui.settings.InstapaperImportDialog
import to.wim.content.ui.settings.InstapaperSettingsState
import to.wim.content.data.Feed
import to.wim.content.data.Inbox
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
import to.wim.content.R
import to.wim.content.ui.feeds.LooseFeedActions
import to.wim.content.ui.feeds.TopicPage
import to.wim.content.ui.feeds.TopicPageActions
import to.wim.content.ui.feeds.TopicRow
import to.wim.content.ui.feeds.TopicScreen
import to.wim.content.ui.feeds.TopicsScreen
import to.wim.content.data.ArticleReport
import to.wim.content.sync.AccountState
import to.wim.content.sync.ShareResult
import to.wim.content.feed.SiteSignIn
import to.wim.content.feed.SiteSignIns
import to.wim.content.ui.reader.SiteSignInScreen
import to.wim.content.ui.reader.forgetSiteCookies
import to.wim.content.data.Item
import to.wim.content.data.ItemSearch
import to.wim.content.data.Place
import to.wim.content.data.SaveChoice
import to.wim.content.data.SaveChoices
import to.wim.content.data.SendTo
import to.wim.content.data.Saved
import to.wim.content.data.SharedText
import to.wim.content.data.SavedFilter
import to.wim.content.data.SavedTab
import to.wim.content.data.ReadingLists
import to.wim.content.data.ReadingState
import to.wim.content.data.addToReadingList
import to.wim.content.data.addHighlight
import to.wim.content.data.removeHighlight
import to.wim.content.data.setHighlightNote
import to.wim.content.data.changeItem
import to.wim.content.data.putItem
import to.wim.content.data.item
import to.wim.content.data.itemFor
import to.wim.content.data.needsDescription
import to.wim.content.data.topicNames
import to.wim.content.data.topicCounts
import to.wim.content.data.up
import to.wim.content.data.chipTag
import to.wim.content.data.feedChips
import to.wim.content.data.tagWithFeedFolder
import to.wim.content.data.removeTag
import to.wim.content.data.renameTag
import to.wim.content.data.setLiked
import to.wim.content.data.setReadingState
import to.wim.content.data.tagCounts
import to.wim.content.feed.FeedDiscovery
import to.wim.content.ui.home.HomeSection
import to.wim.content.ui.home.SavedHome
import to.wim.content.ui.reader.RemoveDialog
import to.wim.content.ui.reader.SaveSheet
import to.wim.content.ui.reader.ReaderHighlights
import to.wim.content.ui.reader.SheetKind
import to.wim.content.ui.reader.ReaderItem
import to.wim.content.ui.reader.fontFamily
import to.wim.content.data.AddFeedResult
import to.wim.content.data.Article
import to.wim.content.data.ArticleOpening
import to.wim.content.data.ArticleSearch
import to.wim.content.data.SearchFilters
import to.wim.content.data.SearchResults
import to.wim.content.ui.home.MainTab
import to.wim.content.data.SearchFilter
import to.wim.content.ui.home.SearchActions
import to.wim.content.ui.home.SearchScreen
import to.wim.content.ui.home.SearchState
import to.wim.content.data.FeedSelection
import to.wim.content.data.FullArticle
import to.wim.content.data.Library
import to.wim.content.data.Settings
import to.wim.content.data.needsFullText
import to.wim.content.data.wantsFullText
import to.wim.content.instapaper.InstapaperNotice
import to.wim.content.sync.AccountNotice
import to.wim.content.instapaper.InstapaperState
import to.wim.content.rssReaderApp
import to.wim.content.ui.home.AddFeedDialog
import to.wim.content.ui.home.HomeActions
import to.wim.content.ui.home.HomeScreen
import to.wim.content.ui.home.HomeState
import to.wim.content.ui.home.InboxHome
import to.wim.content.ui.home.errorRes
import to.wim.content.ui.reader.FullTextState
import to.wim.content.ui.reader.ReaderActions
import to.wim.content.ui.reader.ReaderScreen
import to.wim.content.ui.reader.ReaderTheme
import to.wim.content.ui.settings.SettingsActions
import to.wim.content.ui.settings.SettingsScreen
import to.wim.content.ui.settings.SettingsState
import to.wim.content.ui.theme.Motion
import to.wim.content.ui.theme.SystemBarsFollowTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Screens of the app. A tiny back stack; no navigation library needed. */
sealed interface Screen {
    data object Home : Screen

    /** A feed article ("<feed url> <id>") or a saved item ("item <item key>"). */
    data class Reader(val articleKey: String) : Screen

    data object Settings : Screen

    /** Manage topics: every topic with what it holds (TopicsScreen). */
    data object Feeds : Screen

    /** One topic's page (TopicScreen). The feeds without a topic are listed in Manage topics itself. */
    data class Topic(val id: String) : Screen

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
                Search -> "search"
                is Reader -> "reader:" + screen.articleKey
                is SiteSignIn -> "signin:" + screen.url
                is Topic -> "topic:" + screen.id
            }

        fun decode(text: String): Screen =
            when {
                text == "settings" -> Settings
                text == "feeds" -> Feeds
                // Its Saved tab was a screen of its own before.
                text == "manage-saved" -> Feeds
                text == "search" -> Search
                text.startsWith("reader:") -> Reader(text.removePrefix("reader:"))
                text.startsWith("signin:") -> SiteSignIn(text.removePrefix("signin:"))
                // An empty id was the No topic page, gone: its feeds are in Manage topics now.
                text.startsWith("topic:") -> text.removePrefix("topic:").ifEmpty { null }?.let(::Topic) ?: Feeds
                else -> Home
            }
    }
}

private val BackStackSaver =
    listSaver<SnapshotStateList<Screen>, String>(
        save = { stack -> stack.map(Screen::encode) },
        // The Inbox was a screen of its own before it got its place in the navigation bar (#246): left out.
        restore = { saved -> saved.filter { it != "inbox" }.map(Screen::decode).toMutableStateList() },
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
    val notCopied by app.services.notCopied.collectAsStateWithLifecycle()
    val instapaperState by app.instapaper.state.collectAsStateWithLifecycle()
    val instapaperNotice by app.instapaper.notice.collectAsStateWithLifecycle()
    val accountState by app.account.state.collectAsStateWithLifecycle()
    // The name on the account address's Gravatar profile (#234), asked once per address.
    val accountEmail = (accountState as? AccountState.SignedIn)?.session?.email
    val accountName by produceState<String?>(null, accountEmail) { value = accountEmail?.let { app.sync.profileName(it) } }
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

    fun showMessage(text: String) {
        uiScope.launch { snackbar.showSnackbar(text) }
    }

    /** A feed's new name and topic, from its dialog in Manage topics or on a topic's page. */
    fun editFeed(
        feed: Feed,
        title: String,
        topicId: String?,
    ) {
        app.appScope.launch {
            if (title.trim() != feed.title) repository.renameFeed(feed.url, title)
            if (topicId != feed.folderId) repository.moveFeed(feed.url, topicId)
        }
    }

    /** Puts [text] on the clipboard; Android says so itself. */
    fun copyToClipboard(text: String) {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(resources.getString(R.string.app_name), text))
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
    // Organise was opened from the reader: removing the article there closes it too.
    var fileFromReader by rememberSaveable { mutableStateOf(false) }
    // Remove from Saved asks first, wherever it is tapped; from the reader, the reader closes after.
    var removingKey by rememberSaveable { mutableStateOf<String?>(null) }
    var removingFromReader by rememberSaveable { mutableStateOf(false) }

    fun askRemove(
        key: String,
        fromReader: Boolean,
    ) {
        removingKey = key
        removingFromReader = fromReader
    }

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
    // Add feed from a topic page: the topic to add into ("" for No topic), and stay on that page.
    var addIntoTopic by rememberSaveable { mutableStateOf<String?>(null) }
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
            // Open goes to the inbox (#246).
            showMessage(resources.getQuantityString(R.plurals.share_user_received, got.count, names, got.count), resources.getString(R.string.action_open)) {
                while (backStack.size > 1) pop()
                section = HomeSection.Inbox
            }
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

    // Content's own share sheet (#251) while it is open: the article's title and link.
    var shareTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var shareUrl by rememberSaveable { mutableStateOf<String?>(null) }
    /** Opens Content's share sheet for the article (#251). */
    fun share(article: Article) {
        val link = article.link ?: return
        shareTitle = article.title
        shareUrl = link
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
                if (item != null) updated.tagWithFeedFolder(item.key) else updated
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

    /**
     * Sends with Content to everyone picked in the Save and share sheet (#256), then one message says
     * how it went, after [saved] (where it was saved, or null when only sent), with Open for [open]
     * (the saved article, when not in it already).
     */
    fun sendAll(
        to: List<String>,
        title: String,
        url: String,
        saved: Place?,
        open: Item? = null,
    ) {
        uiScope.launch {
            val results = to.map { email -> email to app.sync.share(email, listOf(Sharing.articleFor(library, url, title))) }
            val sent =
                when (val outcome = SendTo.outcome(results)) {
                    is SendTo.Outcome.Sent ->
                        outcome.to.singleOrNull()?.let { resources.getString(R.string.share_user_sent, it) }
                            ?: resources.getQuantityString(R.plurals.share_sent_people, outcome.to.size, outcome.to.size)
                    is SendTo.Outcome.Failed -> resources.getString(outcome.result.messageRes())
                    null -> return@launch
                }
            val text = saved?.let { resources.getString(R.string.saved_and_sent, resources.getString(R.string.saved_to, placeName(it)), sent) } ?: sent
            if (open != null) {
                showMessage(text, resources.getString(R.string.action_open)) { push(Screen.Reader(ReadingLists.readerKey(open))) }
            } else {
                showMessage(text)
            }
        }
    }

    /** Send with Content (#167): the article to [email]'s inbox; the message says how it went. */
    fun sendTo(
        email: String,
        title: String,
        url: String,
        result: (ShareResult) -> Unit = {},
    ) {
        uiScope.launch {
            val r = app.sync.share(email, listOf(Sharing.articleFor(library, url, title)))
            result(r)
            showMessage(
                resources.getString(
                    when (r) {
                        ShareResult.Sent -> R.string.share_user_sent
                        ShareResult.NoAccount -> R.string.share_user_no_account
                        ShareResult.SignedOut -> R.string.share_user_signed_out
                        ShareResult.Offline -> R.string.problem_offline
                        ShareResult.Failed -> R.string.problem_server
                    },
                    email,
                ),
            )
        }
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
                if (item != null) updated.tagWithFeedFolder(item.key) else updated
            }
            val item = saved
            if (item == null) {
                showMessage(resources.getString(R.string.add_link_invalid))
                return@launch
            }
            if (choice.sendTo.isNotEmpty()) {
                // Saved and sent (#256): one message for both.
                sendAll(choice.sendTo, item.title, url, choice.place, item)
            } else {
                // Not in the article yet: Open goes there (#142).
                showMessage(resources.getString(R.string.saved_to, placeName(choice.place)), resources.getString(R.string.action_open)) {
                    push(Screen.Reader(ReadingLists.readerKey(item)))
                }
            }
            repository.describe(item.key)
        }
    }

    fun openItem(item: Item) = push(Screen.Reader(ReadingLists.readerKey(item)))

    /** A feed article: in the reader, or on its website with Always open the website (marked read then too). */
    fun openArticle(article: Article) {
        val website = ArticleOpening.websiteFor(current.alwaysOpenWebsite, article)
        if (website != null && ExternalApps.openInTab(context, website, article.title)) {
            if (!article.read) app.appScope.launch { repository.setRead(article.key, true) }
        } else {
            push(Screen.Reader(article.key))
        }
    }

    LaunchedEffect(openRequest) {
        val key = openRequest ?: return@LaunchedEffect
        val folder = LauncherFolders.folderOf(key)
        // The notification (#235): the inbox (#246), not an article.
        if (key == MainActivity.OPEN_SHARED) {
            while (backStack.size > 1) pop()
            section = HomeSection.Inbox
        } else if (folder != null) {
            // A folder from the launcher's menu (#251): its articles, on Home.
            ShortcutManagerCompat.reportShortcutUsed(context, LauncherFolders.idOf(folder))
            while (backStack.size > 1) pop()
            if (selection != FeedSelection.InFolder(folder)) readThisVisit = emptySet()
            selection = FeedSelection.InFolder(folder)
            section = HomeSection.Feeds
        } else {
            section = HomeSection.Saved
            push(Screen.Reader(key))
        }
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
                    // Back goes up a level: from Saved to Feeds, from a feed to its topic, from a topic to
                    // All; back on All leaves the app.
                    BackHandler(enabled = backStack.size == 1 && (shownSelection != FeedSelection.All || section != HomeSection.Feeds)) {
                        if (section != HomeSection.Feeds) {
                            // Back from Saved keeps what Feeds showed (#157).
                            section = HomeSection.Feeds
                        } else {
                            readThisVisit = emptySet()
                            selection = shownSelection.up(library.feeds) ?: FeedSelection.All
                        }
                    }
                    val articles =
                        remember(library, shownSelection, current.unreadOnly, readThisVisit) {
                            library.articlesFor(shownSelection, current.unreadOnly, keep = readThisVisit)
                        }
                    val unreadCounts = remember(library) { library.unreadCounts() }
                    val savedItems = remember(library, shownFilter) { Saved.items(library, shownFilter) }
                    // What others shared arrives with a sync (#167): take it in when the Inbox shows, not only at the next one.
                    LaunchedEffect(section) { if (section == HomeSection.Inbox) app.appScope.launch { app.sync.syncIfDue() } }
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
                                        tags = remember(library, shownFilter) { Saved.chipTags(library, shownFilter) },
                                        showsLiked = remember(library, shownFilter) { Saved.showsLiked(library, shownFilter) },
                                        minutes = minutes,
                                        library = library,
                                        titleFont = current.reader.font.fontFamily(),
                                        listView = current.listView,
                                    ),
                                toReadCount = remember(library) { library.items.count { Saved.isIn(it, SavedTab.ToRead) } },
                                signedIn = accountState is AccountState.SignedIn,
                                inboxUnread = remember(library) { Inbox.unread(library) },
                                inbox =
                                    if (accountState !is AccountState.SignedIn) {
                                        null
                                    } else {
                                        InboxHome(
                                            items = remember(library) { Inbox.items(library) },
                                            library = library,
                                            onOpen = { item ->
                                                editLibrary { Inbox.markRead(it, item.key) }
                                                openItem(item)
                                            },
                                            onToggleRead = { item -> editLibrary { Inbox.markRead(it, item.key, read = item.sharedBy?.read != true) } },
                                            onSave = { item -> readLater(item.url, item.title, inReader = false) },
                                            onShare = { item -> share(ReadingLists.article(item, library)) },
                                            onRemove = { item ->
                                                editLibrary { Inbox.remove(it, item.key) }
                                                showMessage(resources.getString(R.string.inbox_removed), resources.getString(R.string.action_undo)) {
                                                    editLibrary { lib -> lib.item(item.key)?.let { lib.putItem(it.copy(sharedBy = item.sharedBy)) } ?: lib.putItem(item) }
                                                }
                                            },
                                        )
                                    },
                                topicChips = remember(library, current.unreadOnly, shownSelection) { library.feedChips(current.unreadOnly, shownSelection.chipTag(library.folders)) },
                            ),
                        snackbarHostState = snackbar,
                        actions =
                            HomeActions(
                                onSendLink = { email, result ->
                                    uiScope.launch { result(runCatching { app.account.sendLink(email) }.isSuccess) }
                                },
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
                                    openArticle(article)
                                },
                                onToggleRead = { article ->
                                    // Stays in the Unread list until it changes, like opened articles.
                                    if (!article.read) readThisVisit = readThisVisit + article.key
                                    app.appScope.launch { repository.setRead(article.key, !article.read) }
                                },
                                // Swipe left is Save, like the reader's Save button.
                                onReadLater = { article -> article.link?.let { readLater(it, article.title, inReader = false) } },
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
                                onOpenManage = { push(Screen.Feeds) },
                                onShowFeeds = { section = HomeSection.Feeds },
                                onShowSaved = { section = HomeSection.Saved },
                                onShowInbox = { section = HomeSection.Inbox },
                                onSavedFilter = {
                                    savedFilter = it
                                    section = HomeSection.Saved
                                },
                                onOpenItem = ::openItem,
                                // Add link: the save sheet asks for the address, into the folder shown.
                                onAddLink = { openSave(null, null) },
                                onArchiveItem = ::archive,
                                onFileItem = {
                                    fileKey = it.key
                                    fileFromReader = false
                                },
                                onRemoveItem = { item -> askRemove(item.key, fromReader = false) },
                                onShareArticle = ::share,
                                onShareItem = { item -> share(ReadingLists.article(item, library)) },
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
                    // One that came in without the page's picture (the inbox, Instapaper, or saved before #268)
                    // gets it when it opens (#275).
                    LaunchedEffect(screen.articleKey) { item?.takeIf { it.needsDescription() }?.let { app.appScope.launch { repository.describe(it.key) } } }
                    SystemBarsFollowTheme(current.reader.isDark(appDark))
                    ReaderTheme(style = current.reader, appearance = current.appearance, appDark = appDark) {
                        ReaderScreen(
                            article = article,
                            feed = fromFeed?.let { library.feed(it.feedUrl) } ?: item?.let { ReadingLists.source(it, library) },
                            style = current.reader,
                            // The same with or without a service, which only syncs Saved.
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
                                    onSave = { article?.let { it.link?.let { link -> readLater(link, it.title, inReader = true) } } },
                                    onShare = { article?.let(::share) },
                                    onOpenInBrowser = { article?.let { a -> a.link?.let { ExternalApps.openInTab(context, it, a.title) } } },
                                    // Only while not signed in to the site yet (#251); Settings forgets a sign-in.
                                    onSignIn = article?.link?.takeIf { signedIn == null && SiteSignIns.siteOf(it) != null }?.let { link -> { push(Screen.SiteSignIn(link)) } },
                                    onReport =
                                        article?.link?.let { link ->
                                            { problem, comment ->
                                                val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
                                                // The text the reader shows, so it can be compared with the page (#251).
                                                val text = ArticleReport.textOf((fullText as? FullTextState.Shown)?.html ?: article.html)
                                                val report = ArticleReport.report(link, problem, comment, "Android", version, text)
                                                if (report.cut) copyToClipboard(text)
                                                ExternalApps.openLink(context, report.url)
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
                                        onOrganise = {
                                            fileKey = i.key
                                            fileFromReader = true
                                        },
                                        onArchive = {
                                            archive(i)
                                            pop()
                                        },
                                        onRemove = { askRemove(i.key, fromReader = true) },
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
                                syncStatus = syncStatus,
                                account = accountState,
                                accountName = accountName,
                                notifyShared = current.notifyShared.takeIf { app.push.available },
                                siteSignIns = siteSignIns.map { it.site },
                                hasFeeds = library.feeds.isNotEmpty(),
                                instapaper =
                                    InstapaperSettingsState(
                                        instapaper = instapaperState,
                                        status = serviceStatuses[InstapaperReadingList.ID] ?: ServiceStatus.None,
                                    ),
                            ),
                        snackbarHostState = snackbar,
                        actions =
                            SettingsActions(
                                onBack = ::pop,
                                onReaderStyleChange = { style -> app.appScope.launch { app.settings.setReaderStyle(style) } },
                                onInstapaperSignIn = { uiScope.launch { ExternalApps.openLink(context, app.instapaper.begin()) } },
                                onInstapaperSync = { app.appScope.launch { app.services.sync() } },
                                // Disconnects Instapaper only; the Content account stays signed in (#166).
                                onInstapaperSignOut = { app.appScope.launch { app.instapaper.signOut() } },
                                onInstapaperImport = { everything -> app.appScope.launch { app.services.chooseImport(InstapaperReadingList.ID, everything) } },
                                onImportOpml = importOpml,
                                onExportOpml = exportOpml,
                                onFullArticlesChange = { enabled -> app.appScope.launch { app.settings.setFullArticles(enabled) } },
                                onAlwaysOpenWebsiteChange = { enabled -> app.appScope.launch { app.settings.setAlwaysOpenWebsite(enabled) } },
                                onShowImagesChange = { enabled -> app.appScope.launch { app.settings.setShowImages(enabled) } },
                                onMarkReadOnScrollChange = { enabled -> app.appScope.launch { app.settings.setMarkReadOnScroll(enabled) } },
                                onListViewChange = { view -> app.appScope.launch { app.settings.setListView(view) } },
                                onThemeModeChange = { mode -> app.appScope.launch { app.settings.setThemeMode(mode) } },
                                onDynamicColorChange = { enabled -> app.appScope.launch { app.settings.setDynamicColor(enabled) } },
                                onSyncNow = {
                                    app.appScope.launch {
                                        app.sync.sync()
                                        app.services.sync()
                                    }
                                },
                                onSignOut = { app.appScope.launch { app.sync.signOut() } },
                                onRemoveSiteSignIn = { site ->
                                    siteSignIns.firstOrNull { it.site == site }?.let { forgetSiteCookies(it.site, it.cookie) }
                                    app.appScope.launch { app.siteSignIns.remove(site) }
                                },
                                onNotifyShared = { on, result ->
                                    uiScope.launch {
                                        if (on) {
                                            val ok = app.push.turnOn()
                                            if (!ok) showMessage(resources.getString(R.string.sync_failed_offline))
                                            result(ok)
                                        } else {
                                            app.push.turnOff()
                                            result(true)
                                        }
                                    }
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
                    val counts = remember(library) { library.topicCounts() }
                    TopicsScreen(
                        topics =
                            library.folders.map { topic ->
                                val count = counts[topic.id]
                                TopicRow(
                                    topic = topic,
                                    feeds = count?.feeds ?: 0,
                                    saved = count?.saved ?: 0,
                                    failing = library.feeds.count { it.folderId == topic.id && problems.containsKey(it.url) },
                                )
                            },
                        looseFeeds = library.feeds.filter { feed -> feed.folderId == null || library.folders.none { it.id == feed.folderId } },
                        allTopics = library.folders,
                        problems = problems,
                        looseActions =
                            LooseFeedActions(
                                onEditFeed = { feed, title, topicId -> editFeed(feed, title, topicId) },
                                onRemoveFeed = { feed -> app.appScope.launch { repository.removeFeed(feed.url) } },
                                onReorderFeeds = { urls -> app.appScope.launch { repository.reorderFeedsIn(null, urls) } },
                            ),
                        snackbarHostState = snackbar,
                        onBack = ::pop,
                        onOpen = { push(Screen.Topic(it)) },
                        onReorder = { ids -> app.appScope.launch { repository.reorderTopics(ids) } },
                        onAddFeed = {
                            // Without a topic, listed on this page, and stay here.
                            addIntoTopic = ""
                            addDialogOpen = true
                        },
                        onNewTopic = { title, icon ->
                            app.appScope.launch {
                                val id = repository.addFolder(title, icon)
                                uiScope.launch { push(Screen.Topic(id)) }
                            }
                        },
                    )
                }

                is Screen.Topic -> {
                    SystemBarsFollowTheme(appDark)
                    val topic = library.folder(screen.id)
                    // A removed or merged topic: back to the list.
                    if (topic == null) {
                        LaunchedEffect(Unit) { pop() }
                    } else {
                        val count = remember(library) { library.topicCounts()[topic.id] }
                        val topicFeeds = library.feeds.filter { it.folderId == topic.id }
                        TopicScreen(
                            page =
                                TopicPage(
                                    topic = topic,
                                    feeds = topicFeeds,
                                    saved = count?.saved ?: 0,
                                    toRead = count?.toRead ?: 0,
                                ),
                            topics = library.folders,
                            problems = problems,
                            snackbarHostState = snackbar,
                            actions =
                                TopicPageActions(
                                    onBack = ::pop,
                                    onAddFeed = {
                                        addIntoTopic = screen.id
                                        addDialogOpen = true
                                    },
                                    onEditFeed = { feed, title, topicId -> editFeed(feed, title, topicId) },
                                    onRemoveFeed = { feed -> app.appScope.launch { repository.removeFeed(feed.url) } },
                                    onReorderFeeds = { urls -> app.appScope.launch { repository.reorderFeedsIn(screen.id, urls) } },
                                    onEdit = { title, icon -> app.appScope.launch { repository.updateFolder(topic.id, title, icon) } },
                                    onMerge = { intoId ->
                                        app.appScope.launch { repository.mergeTopic(topic.id, intoId) }
                                        pop()
                                        push(Screen.Topic(intoId))
                                    },
                                    onRemove = {
                                        app.appScope.launch { repository.removeFolder(topic.id) }
                                        pop()
                                    },
                                ),
                        )
                    }
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
                                    openArticle(article)
                                },
                                onOpenItem = ::openItem,
                                onTab = { tab ->
                                    pop()
                                    section =
                                        when (tab) {
                                            MainTab.Saved -> HomeSection.Saved
                                            MainTab.Inbox -> HomeSection.Inbox
                                            else -> HomeSection.Feeds
                                        }
                                },
                                onBack = ::pop,
                            ),
                    )
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
            allTags = library.topicNames(),
            onSave = { _, choice ->
                editLibrary { SaveChoices.apply(it, sheetItem.key, choice) }
                when {
                    choice.sendTo.isNotEmpty() -> sendAll(choice.sendTo, sheetItem.title, sheetItem.url, choice.place.takeIf { addedKey != null }, sheetItem.takeIf { addedKey != null && !addedFromReader })
                    addedKey != null -> confirmSaved(sheetItem, choice.place)
                }
            },
            // Cancelled right after saving: neither saved nor sent, as if Save was never tapped.
            onCancel = {
                if (addedKey != null) {
                    val before = addedBefore
                    editLibrary { SaveChoices.undoSave(it, sheetItem.key, before) }
                }
            },
            // Remove in Organise: asks first, as everywhere.
            onRemove = { askRemove(sheetItem.key, fileFromReader) },
            onDismiss = {
                addedKey = null
                fileKey = null
            },
            key = sheetItem.key,
            signedIn = accountState is AccountState.SignedIn,
            hasAccount = app.sync::hasAccount,
        )
    }

    removingKey?.let { key ->
        RemoveDialog(
            onRemove = {
                editLibrary { Saved.remove(it, key) }
                if (removingFromReader) pop()
                showMessage(resources.getString(R.string.saved_removed))
            },
            onDismiss = { removingKey = null },
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
            allTags = library.topicNames(),
            onSave = { url, choice ->
                url?.let {
                    val title = saveTitle.takeIf { saveUrl.isNotEmpty() }
                    // Saving sends too; Don't save only sends.
                    if (choice.keep) save(it, title, choice) else sendAll(choice.sendTo, title ?: it, it, saved = null)
                }
            },
            onDismiss = { saveOpen = false },
            signedIn = accountState is AccountState.SignedIn,
            hasAccount = app.sync::hasAccount,
        )
    }

    // Just connected Instapaper (#251): import what it has, once, or not. Asked again next time if dismissed.
    var importAsked by rememberSaveable { mutableStateOf(false) }
    if (serviceStatuses[InstapaperReadingList.ID] == ServiceStatus.ImportChoice && !importAsked) {
        InstapaperImportDialog(
            onChoose = { everything ->
                importAsked = true
                app.appScope.launch { app.services.chooseImport(InstapaperReadingList.ID, everything) }
            },
            onDismiss = { importAsked = true },
        )
    }

    // Content's own share sheet (#251).
    val sharing = shareUrl
    if (sharing != null) {
        val title = shareTitle ?: sharing
        val close = {
            shareUrl = null
            shareTitle = null
        }
        ShareSheet(
            title = title,
            url = sharing,
            signedIn = accountState is AccountState.SignedIn,
            apps = remember(shareApps, current.shareCounts) { ShareApps.rank(shareApps, current.shareCounts, { it.id }, { it.target.label }).take(ShareApps.SHOWN) },
            onSend = { emails ->
                close()
                // One message for everyone picked, as the Save and share sheet says it.
                uiScope.launch {
                    val results = emails.map { email -> email to app.sync.share(email, listOf(Sharing.articleFor(library, sharing, title))) }
                    when (val outcome = SendTo.outcome(results)) {
                        is SendTo.Outcome.Sent ->
                            showMessage(
                                outcome.to.singleOrNull()?.let { resources.getString(R.string.share_user_sent, it) }
                                    ?: resources.getQuantityString(R.plurals.share_sent_people, outcome.to.size, outcome.to.size),
                            )
                        is SendTo.Outcome.Failed -> showMessage(resources.getString(outcome.result.messageRes()))
                        null -> Unit
                    }
                }
            },
            hasAccount = app.sync::hasAccount,
            onApp = { picked ->
                close()
                app.appScope.launch { app.settings.countShare(picked.id) }
                if (!ExternalApps.send(context, picked.target, title, sharing, resources.getString(R.string.action_share))) {
                    showMessage(resources.getString(R.string.share_target_missing_short, picked.target.label))
                }
            },
            onCopyLink = {
                close()
                copyToClipboard(sharing)
            },
            onMore = {
                close()
                ExternalApps.share(context, title, sharing, resources.getString(R.string.action_share))
            },
            onDismiss = close,
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
                    // Added to the topic whose page it came from, else to the topic being viewed, if any.
                    val fromTopicPage = addIntoTopic
                    val folderId = if (fromTopicPage != null) fromTopicPage.ifEmpty { null } else (library.shown(selection) as? FeedSelection.InFolder)?.folderId
                    val result = repository.addFeed(address, folderId)
                    addBusy = false
                    val error = result.errorRes()
                    if (error != null) {
                        addError = resources.getString(error)
                    } else if (result is AddFeedResult.Added) {
                        addDialogOpen = false
                        addIntoTopic = null
                        if (fromTopicPage == null) {
                            selection = FeedSelection.Single(result.feed.url)
                            section = HomeSection.Feeds
                        }
                        showMessage(resources.getString(R.string.add_feed_added, result.feed.title))
                    }
                }
            },
            onDismiss = {
                if (!addBusy) {
                    addDialogOpen = false
                    addIntoTopic = null
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
