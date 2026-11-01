package to.wim.content.ui.home

import to.wim.content.data.ReadingState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import android.text.format.DateUtils
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import to.wim.content.data.ListView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import to.wim.content.data.ArticleDays
import java.time.LocalDate
import java.time.ZoneId
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import to.wim.content.R
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.CircularProgressIndicator
import to.wim.content.ui.settings.EmailLinkFields
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.widthIn
import to.wim.content.data.Article
import to.wim.content.data.Feed
import to.wim.content.data.Folder
import to.wim.content.data.Item
import to.wim.content.data.Saved
import to.wim.content.data.SavedFilter
import to.wim.content.data.FeedChips
import to.wim.content.data.FeedSelection
import to.wim.content.data.ScrollRead
import to.wim.content.data.feedTagsInOrder
import kotlinx.coroutines.flow.distinctUntilChanged
import to.wim.content.feed.Html
import to.wim.content.ui.theme.AppShapes
import to.wim.content.ui.theme.EmptyState
import to.wim.content.ui.theme.Motion
import to.wim.content.ui.theme.SkeletonLine
import to.wim.content.ui.theme.readableWidth

/** What the article list shows. */
data class HomeState(
    val feeds: List<Feed>,
    val articles: List<Article>,
    val selection: FeedSelection,
    val unreadOnly: Boolean,
    val unreadCounts: Map<FeedSelection, Int>,
    val refreshing: Boolean,
    /** False until the first articles have been downloaded. */
    val hasAnyArticles: Boolean,
    /** False until the stored feeds have been read, so the empty state doesn't flash at start. */
    val loaded: Boolean = true,
    /** Show each article's picture as a thumbnail. */
    val showImages: Boolean = true,
    val folders: List<Folder> = emptyList(),
    val markReadOnScroll: Boolean = false,
    val listView: ListView = ListView.Medium,
    /** Which destination is shown: Feeds or Saved. */
    val section: HomeSection = HomeSection.Feeds,
    /** Saved, for its destination and the drawer. */
    val saved: SavedHome? = null,
    /** How many articles are still to read, for the drawer and the navigation. */
    val toReadCount: Int = 0,
    /** Signed in to a Content account: the Inbox shows in the navigation bar (#246). */
    val signedIn: Boolean = false,
    /** The Content account's address, for its picture on Settings in the search bar; null when signed out. */
    val accountEmail: String? = null,
    /** Feeds' topic chips: the topics with articles in the state shown, and the one chosen (feedChips). */
    val topicChips: List<String>? = null,
    /** How many articles shared with you are unread, for the Inbox's badge. */
    val inboxUnread: Int = 0,
    /** The Inbox, for its destination; null while signed out. */
    val inbox: InboxHome? = null,
)

/** The app's top-level destinations (#119, #246): Feeds, Saved and the Inbox. */
enum class HomeSection { Feeds, Saved, Inbox }

class HomeActions(
    val onSelect: (FeedSelection) -> Unit,
    val onUnreadOnlyChange: (Boolean) -> Unit,
    val onOpenArticle: (Article) -> Unit,
    /** Long-press › Mark as read or unread (#221). */
    val onToggleRead: (Article) -> Unit,
    /** Long-press › Save: saved, to read later (#107, #221). */
    val onReadLater: (Article) -> Unit = {},
    /** Long-press › Share (#221): a feed article, or a saved item. */
    val onShareArticle: (Article) -> Unit = {},
    val onShareItem: (Item) -> Unit = {},
    /** Puts an item shared with you in To read or the Archive (#167). */
    /** Shows the Inbox (#246): what other Content users shared with you. */
    val onShowInbox: () -> Unit = {},
    val onRefresh: () -> Unit,
    val onMarkAllRead: () -> Unit,
    /** Articles that scrolled off the top, with Mark as read on scroll on. */
    val onScrolledPast: (Set<String>) -> Unit,
    val onAddFeed: () -> Unit,
    val onImportOpml: () -> Unit,
    /** Emails a sign-in link from the welcome screen; gives back whether it was sent (#166). */
    val onSendLink: (email: String, result: (Boolean) -> Unit) -> Unit = { _, result -> result(false) },
    val onOpenSettings: () -> Unit,
    /** The navigation bar's destinations. */
    val onShowFeeds: () -> Unit = {},
    val onShowSaved: () -> Unit = {},
    val onSavedFilter: (SavedFilter) -> Unit = {},
    val onOpenItem: (Item) -> Unit = {},
    /** The search bar's +, in every place: asks Subscribe to feed or Save article. */
    val onAdd: () -> Unit = {},
    /** Archives a saved item, or puts an archived one back to read (a card's menu in Saved). */
    val onArchiveItem: (Item) -> Unit = {},
    /** Opens Organise for a saved item (a card's menu in Saved). */
    val onFileItem: (Item) -> Unit = {},
    /** Long-press › Remove from Saved (#263), with Undo. */
    val onRemoveItem: (Item) -> Unit = {},
    /** Opens the search through every feed's articles and Saved. */
    val onOpenSearch: () -> Unit = {},
)

/** The article list: Feeds, Saved or the Inbox, with their chips; no ☰ menu (every place is a chip). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeState,
    snackbarHostState: SnackbarHostState,
    actions: HomeActions,
) {
    // Saved replaces the feed sections: its own title, tabs and chips, no Mark all read.
    val reading = state.saved?.takeIf { state.section == HomeSection.Saved }
    val inbox = state.inbox?.takeIf { state.section == HomeSection.Inbox }
    val inFeeds = reading == null && inbox == null
    val unreadHere = state.unreadCounts[state.selection] ?: 0
    val feedTags = state.topicChips ?: remember(state.feeds, state.folders) { feedTagsInOrder(state.feeds, state.folders) }
    val wide = isWideWindow()
    // Feed, Saved and the Inbox in the navigation bar (#189, #246); Search opens from the search bar.
    val tab =
        when {
            reading != null -> MainTab.Saved
            inbox != null -> MainTab.Inbox
            else -> MainTab.Feeds
        }
    val onTab: (MainTab) -> Unit = { t ->
        when (t) {
            MainTab.Feeds -> actions.onShowFeeds()
            MainTab.Saved -> actions.onShowSaved()
            MainTab.Inbox -> actions.onShowInbox()
            MainTab.Search -> actions.onOpenSearch()
        }
    }

    run {
        val hasArticles =
            when {
                reading != null -> reading.items.isNotEmpty()
                inbox != null -> inbox.items.isNotEmpty()
                else -> state.articles.isNotEmpty()
            }
        // The whole header slides away while scrolling down and returns when scrolling up.
        val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(canScroll = { hasArticles })
        // Another section, or a list with nothing left to scroll, shows the header again.
        LaunchedEffect(state.selection, state.section, reading?.filter, hasArticles) { scrollBehavior.state.heightOffset = 0f }
        // On wide windows Feed and Saved move to a navigation rail at the side.
        Row(Modifier.fillMaxSize()) {
            if (wide) MainNavigationRail(tab, onTab, state.inbox != null, state.inboxUnread)
            Scaffold(
                modifier = Modifier.weight(1f).nestedScroll(scrollBehavior.nestedScrollConnection),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    CollapsingHeader(scrollBehavior) {
                        HomeSearchBar(
                            onSearch = actions.onOpenSearch,
                            onSettings = actions.onOpenSettings,
                            // + is the same in every place: it asks Subscribe to feed or Save article.
                            onAdd = actions.onAdd,
                            addLabel = stringResource(R.string.add_title),
                            accountEmail = state.accountEmail,
                            showImages = state.showImages,
                        )
                        if (reading != null) {
                            HomeTabs(
                                labels = listOf(stringResource(R.string.reading_list_to_read), stringResource(R.string.filter_all)),
                                selected = Saved.states.indexOf(reading.filter.tab).coerceAtLeast(0),
                                // A topic stays chosen when the tab changes.
                                onSelect = { i -> Saved.states.getOrNull(i)?.let { actions.onSavedFilter(reading.filter.copy(tab = it)) } },
                            )
                            TagChips(
                                tags = reading.tags,
                                iconOf = { name -> state.folders.firstOrNull { it.title.equals(name, ignoreCase = true) }?.icon },
                                selected = reading.filter.tags.firstOrNull(),
                                onSelect = { t -> actions.onSavedFilter(reading.filter.copy(likedOnly = false, tags = setOfNotNull(t))) },
                                // Liked is a group like a tag (#206): one at a time, before the tags; only when
                                // something liked is in the tab shown (or Liked is chosen).
                                liked =
                                    if (!reading.showsLiked) {
                                        null
                                    } else {
                                        {
                                            val on = reading.filter.likedOnly
                                            FilterChip(
                                                selected = on,
                                                onClick = { actions.onSavedFilter(reading.filter.copy(likedOnly = !on, tags = emptySet())) },
                                                label = { Text(stringResource(R.string.reading_list_liked)) },
                                                leadingIcon = {
                                                    Icon(
                                                        painterResource(if (on) R.drawable.ic_favorite_filled else R.drawable.ic_favorite),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                                                    )
                                                },
                                            )
                                        }
                                    },
                            )
                        } else if (inFeeds && state.feeds.isNotEmpty()) {
                            HomeTabs(
                                labels = listOf(stringResource(R.string.filter_unread), stringResource(R.string.filter_read)),
                                selected = if (state.unreadOnly) 0 else 1,
                                onSelect = { i -> actions.onUnreadOnlyChange(i == 0) },
                            )
                            val chips = remember(state.feeds, state.folders, feedTags, state.selection) { FeedChips.of(state.feeds, state.folders, feedTags, state.selection) }
                            TagChips(
                                tags = chips.topics,
                                iconOf = { name -> state.folders.firstOrNull { it.title.equals(name, ignoreCase = true) }?.icon },
                                selected = chips.chosenTopic,
                                // A topic, or nothing: tapping the chosen topic (or its feed's) again lets go.
                                onSelect = { t -> actions.onSelect(if (t == null) FeedSelection.All else FeedSelection.Tagged(t)) },
                                // The feeds without a topic, at the end of the row (#251 had them in ☰).
                                loose =
                                    if (chips.loose.isEmpty()) {
                                        null
                                    } else {
                                        {
                                            chips.loose.forEach { feed ->
                                                val on = feed.url == chips.chosenFeed
                                                FeedChip(feed, on) { actions.onSelect(if (on) FeedSelection.All else FeedSelection.Single(feed.url)) }
                                            }
                                        }
                                    },
                            )
                            // A topic chosen: its feeds, a second row.
                            val topic = chips.chosenTopic
                            if (topic != null && chips.topicFeeds.isNotEmpty()) {
                                TopicFeedChips(chips.topicFeeds, chips.chosenFeed) { url ->
                                    actions.onSelect(if (url == null) FeedSelection.Tagged(topic) else FeedSelection.Single(url))
                                }
                            }
                        }
                    }
                },
                bottomBar = { if (!wide) MainNavigationBar(tab, onTab, state.inbox != null, state.inboxUnread) },
                floatingActionButton = {
                    // Marks everything in the section being viewed as read: All, a folder or a feed (Unread only).
                    if (state.feeds.isNotEmpty() && unreadHere > 0 && inFeeds && state.unreadOnly) {
                        FloatingActionButton(onClick = actions.onMarkAllRead) {
                            Icon(painterResource(R.drawable.ic_done_all), contentDescription = stringResource(R.string.action_mark_all_read))
                        }
                    }
                },
            ) { innerPadding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                if (inbox != null) {
                    PullToRefreshBox(
                        isRefreshing = state.refreshing,
                        onRefresh = actions.onRefresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        InboxContent(inbox, state.listView, state.showImages)
                    }
                } else if (reading != null) {
                    PullToRefreshBox(
                        isRefreshing = state.refreshing,
                        onRefresh = actions.onRefresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        SavedContent(
                            reading,
                            state.showImages,
                            actions.onOpenItem,
                            onArchive = actions.onArchiveItem,
                            onFile = actions.onFileItem,
                            onShare = actions.onShareItem,
                            onRemove = actions.onRemoveItem,
                            onClearFilters = { actions.onSavedFilter(reading.filter.copy(likedOnly = false, tags = emptySet())) },
                        )
                    }
                } else {
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = actions.onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val content =
                        when {
                            !state.loaded -> HomeContent.Loading
                            state.feeds.isEmpty() -> HomeContent.NoFeeds
                            !state.hasAnyArticles && state.refreshing -> HomeContent.Loading
                            !state.hasAnyArticles -> HomeContent.NoArticles
                            state.articles.isEmpty() -> HomeContent.Empty
                            else -> HomeContent.Articles
                        }
                    AnimatedContent(
                        targetState = content,
                        transitionSpec = { fadeIn(Motion.fadeSpec()) togetherWith fadeOut(Motion.fadeSpec()) },
                        label = "home",
                    ) { shown ->
                        when (shown) {
                            HomeContent.NoFeeds ->
                                if (state.signedIn) {
                                    NoFeeds(actions.onAddFeed, actions.onImportOpml)
                                } else {
                                    Welcome(actions.onSendLink, actions.onAddFeed, actions.onImportOpml)
                                }
                            HomeContent.Loading -> ArticleSkeletons()
                            HomeContent.NoArticles -> NothingToShow(unread = null, state.refreshing, actions.onRefresh)
                            HomeContent.Empty -> NothingToShow(unread = state.unreadOnly, state.refreshing, actions.onRefresh)
                            HomeContent.Articles -> ArticleList(state, actions)
                        }
                    }
                }
                }
                }
            }
        }
    }
}

private enum class HomeContent { NoFeeds, Loading, NoArticles, Empty, Articles }

private const val SKELETON_ROWS = 6

@Composable
private fun ArticleList(
    state: HomeState,
    actions: HomeActions,
) {
    // A different feed starts at the top.
    val listState = rememberSaveable(state.selection, saver = LazyListState.Saver) { LazyListState() }
    val showFeedName = state.selection !is FeedSelection.Single
    val feedsByUrl = remember(state.feeds) { state.feeds.associateBy { it.url } }
    val days = remember(state.articles) { ArticleDays.group(state.articles, ZoneId.systemDefault()) }
    // Where each list item is: a date header, or how many articles come before it.
    val articlesBefore =
        remember(days) {
            buildList {
                var count = 0
                days.forEach { (_, articles) ->
                    add(count)
                    articles.forEach { _ -> add(count++) }
                }
            }
        }
    if (state.markReadOnScroll) {
        val articles by rememberUpdatedState(state.articles)
        val positions by rememberUpdatedState(articlesBefore)
        val onScrolledPast by rememberUpdatedState(actions.onScrolledPast)
        LaunchedEffect(listState) {
            // Only articles that were on screen count (#157): new ones arriving above aren't marked unseen.
            val seen = HashSet<String>()
            snapshotFlow { listState.firstVisibleItemIndex to listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String } }
                .distinctUntilChanged()
                .collect { (first, visible) ->
                    seen += visible
                    val keys = ScrollRead.scrolledPast(articles, positions.getOrElse(first) { articles.size }, seen)
                    if (keys.isNotEmpty()) onScrolledPast(keys)
                }
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().readableWidth(),
        // Room at the end for the floating action button.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        days.forEach { (day, articles) ->
            stickyHeader(key = "day:$day") { DayHeader(day) }
            items(articles, key = { it.key }) { article ->
                // No swipes (#305): the long press holds every action.
                Box(Modifier.animateItem()) {
                    ArticleRow(
                        article = article,
                        feed = if (showFeedName) feedsByUrl[article.feedUrl] else null,
                        view = state.listView,
                        showImage = state.showImages,
                        onClick = { actions.onOpenArticle(article) },
                        onFeedClick = { actions.onSelect(FeedSelection.Single(article.feedUrl)) },
                        // Long-press (#221): read or unread, Save, Share.
                        menu =
                            listOfNotNull(
                                RowMenuAction(
                                    if (article.read) R.drawable.ic_mark_unread else R.drawable.ic_mark_read,
                                    stringResource(if (article.read) R.string.action_mark_unread else R.string.action_mark_read),
                                ) { actions.onToggleRead(article) },
                                article.link?.let { RowMenuAction(R.drawable.ic_list, stringResource(R.string.action_save)) { actions.onReadLater(article) } },
                                article.link?.let { RowMenuAction(R.drawable.ic_share, stringResource(R.string.action_share)) { actions.onShareArticle(article) } },
                            ),
                    )
                }
            }
        }
    }
}

/** "Today", "Yesterday", or the date, above that day's articles; stays at the top while scrolling. */
@Composable
private fun DayHeader(day: LocalDate) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val label =
        when (day) {
            today -> stringResource(R.string.day_today)
            today.minusDays(1) -> stringResource(R.string.day_yesterday)
            else -> {
                val millis = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val flags =
                    DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or
                        if (day.year == today.year) DateUtils.FORMAT_NO_YEAR else DateUtils.FORMAT_SHOW_YEAR
                DateUtils.formatDateTime(context, millis, flags)
            }
        }
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
                .semantics { heading() },
    )
}

/** One entry of a row's long-press menu (#221). */
internal class RowMenuAction(
    val icon: Int,
    val label: String,
    val onClick: () -> Unit,
)

/**
 * A row or card that opens [menu] on a long press (#221), with a short vibration; screen readers
 * get the same entries as actions. Without entries it is a plain click.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.longPressMenu(
    menu: List<RowMenuAction>,
    onClick: () -> Unit,
    onOpen: () -> Unit,
): Modifier {
    val haptics = LocalHapticFeedback.current
    return this
        .combinedClickable(
            onClick = onClick,
            onLongClick =
                if (menu.isEmpty()) {
                    null
                } else {
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onOpen()
                    }
                },
        ).semantics {
            if (menu.isNotEmpty()) {
                customActions =
                    menu.map { action ->
                        CustomAccessibilityAction(action.label) {
                            action.onClick()
                            true
                        }
                    }
            }
        }
}

/** The long-press menu itself, under the row. */
@Composable
internal fun RowMenu(
    menu: List<RowMenuAction>,
    open: Boolean,
    onClose: () -> Unit,
) {
    DropdownMenu(expanded = open, onDismissRequest = onClose) {
        for (action in menu) {
            DropdownMenuItem(
                text = { Text(action.label) },
                leadingIcon = { Icon(painterResource(action.icon), contentDescription = null) },
                onClick = {
                    onClose()
                    action.onClick()
                },
            )
        }
    }
}

@Composable
internal fun ArticleRow(
    article: Article,
    feed: Feed?,
    view: ListView,
    showImage: Boolean,
    onClick: () -> Unit,
    /** False for saved items: they have no read state, so they are never dimmed and have no dot (#157). */
    showReadState: Boolean = true,
    /** What kind of article this is, shown at the end of its top line (Search: Feed or Saved). */
    label: String? = null,
    modifier: Modifier = Modifier,
    /** Tapping the feed's name shows only that feed (#185); null where it isn't a filter. */
    onFeedClick: (() -> Unit)? = null,
    /** What a long press offers (#221). */
    menu: List<RowMenuAction> = emptyList(),
    /** Shown where the feed's name goes: who shared it, in the inbox (#246). */
    source: (@Composable () -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val shown = if (showReadState) article else article.copy(read = false)
    val time =
        remember(shown.sortTime) {
            DateUtils
                .getRelativeTimeSpanString(
                    shown.sortTime,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE,
                ).toString()
        }
    val snippet = remember(shown.html) { Html.snippet(shown.html) }
    val image = shown.imageUrl?.takeIf { showImage && view != ListView.Small }
    Surface(
        shape = AppShapes.Item,
        color = if (shown.read) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainer,
        modifier =
            modifier
                .fillMaxWidth()
                .clip(AppShapes.Item)
                .longPressMenu(menu, onClick) { menuOpen = true },
    ) {
        RowMenu(menu, menuOpen) { menuOpen = false }
        when (view) {
            ListView.Small -> {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    MetaLine(shown, feed, time, showReadState, label, onFeedClick, source)
                    ArticleTitle(shown, MaterialTheme.typography.titleSmall, maxLines = 2)
                }
            }

            ListView.Medium -> {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        MetaLine(shown, feed, time, showReadState, label, onFeedClick, source)
                        ArticleTitle(shown, MaterialTheme.typography.titleMedium, maxLines = 3)
                        Snippet(snippet, maxLines = 2)
                    }
                    if (image != null) Thumbnail(image, Modifier.padding(top = 20.dp))
                }
            }

            ListView.Large -> {
                Column {
                    if (image != null) LargePicture(image)
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MetaLine(shown, feed, time, showReadState, label, onFeedClick, source)
                        ArticleTitle(shown, MaterialTheme.typography.titleLarge, maxLines = 3)
                        Snippet(snippet, maxLines = 3)
                    }
                }
            }
        }
    }
}

/** Unread dot, feed icon and name (or [source]), and age. */
@Composable
private fun MetaLine(
    article: Article,
    feed: Feed?,
    time: String,
    showUnreadDot: Boolean,
    label: String?,
    onFeedClick: (() -> Unit)? = null,
    source: (@Composable () -> Unit)? = null,
) {
    val unreadLabel = stringResource(R.string.article_unread)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showUnreadDot && !article.read) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .clearAndSetSemantics { contentDescription = unreadLabel },
            )
        }
        if (source != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) { source() }
        } else if (feed != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier
                        .weight(1f, fill = false)
                        .then(if (onFeedClick != null) Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onFeedClick) else Modifier),
            ) {
                FeedIcon(feed, FeedIconSize.Small)
                Text(
                    text = feed.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = time,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                modifier =
                    Modifier
                        .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun ArticleTitle(
    article: Article,
    style: TextStyle,
    maxLines: Int,
) {
    Text(
        text = article.title,
        style = style,
        fontWeight = if (article.read) FontWeight.Normal else FontWeight.SemiBold,
        color = if (article.read) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun Snippet(
    snippet: String,
    maxLines: Int,
) {
    if (snippet.isEmpty()) return
    Text(
        text = snippet,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The Large view's picture across the card. Disappears when it can't be loaded. */
@Composable
private fun LargePicture(url: String) {
    var failed by remember(url) { mutableStateOf(false) }
    if (failed) return
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onError = { failed = true },
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

/** The article's picture, square, beside the text. Disappears when it can't be loaded. */
@Composable
private fun Thumbnail(
    url: String,
    modifier: Modifier = Modifier,
) {
    var failed by remember(url) { mutableStateOf(false) }
    if (failed) return
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onError = { failed = true },
        modifier =
            modifier
                .size(THUMBNAIL_SIZE)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

private val THUMBNAIL_SIZE = 76.dp

@Composable
private fun ArticleSkeletons() {
    val loading = stringResource(R.string.loading)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp)
                .clearAndSetSemantics { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(SKELETON_ROWS) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, AppShapes.Item)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SkeletonLine(Modifier.fillMaxWidth(0.3f), height = 12.dp)
                SkeletonLine(Modifier.fillMaxWidth(0.9f), height = 18.dp)
                SkeletonLine(Modifier.fillMaxWidth(0.7f))
            }
        }
    }
}

@Composable
private fun NoFeeds(
    onAddFeed: () -> Unit,
    onImportOpml: () -> Unit,
) {
    EmptyState(
        painterResource(R.drawable.ic_rss_feed),
        stringResource(R.string.home_no_feeds_title),
        body = stringResource(R.string.home_no_feeds_body),
    ) {
        Button(onClick = onAddFeed) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null)
            Text(stringResource(R.string.feeds_add), modifier = Modifier.padding(start = 8.dp))
        }
        OutlinedButton(onClick = onImportOpml) { Text(stringResource(R.string.feeds_import)) }
    }
}

/**
 * The first screen, signed out and without feeds: the Content account first (an emailed link
 * signs in, or makes the account), as it keeps feeds and Saved on every device and in the web
 * app; starting on this phone only (Add feed, or importing from another reader) comes second.
 */
@Composable
private fun Welcome(
    onSendLink: (email: String, result: (Boolean) -> Unit) -> Unit,
    onAddFeed: () -> Unit,
    onImportOpml: () -> Unit,
) {
    EmptyState(
        painterResource(R.drawable.ic_rss_feed),
        stringResource(R.string.welcome_title),
        body = stringResource(R.string.welcome_body),
    ) {
        EmailLinkFields(R.string.account_email_send, onSendLink, Modifier.widthIn(max = 400.dp))
        Text(
            stringResource(R.string.welcome_without_account),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
        OutlinedButton(onClick = onAddFeed) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null)
            Text(stringResource(R.string.feeds_add), modifier = Modifier.padding(start = 8.dp))
        }
        TextButton(onClick = onImportOpml) { Text(stringResource(R.string.welcome_import)) }
    }
}

/**
 * Nothing to show: no articles downloaded yet ([unread] null), all caught up in Unread, or nothing
 * read yet in Read. Refresh looks for new articles (not in Read: new ones arrive unread).
 */
@Composable
private fun NothingToShow(
    unread: Boolean?,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    EmptyState(
        painterResource(if (unread == true) R.drawable.ic_done_all else R.drawable.ic_article),
        stringResource(
            when (unread) {
                null -> R.string.home_no_articles_title
                true -> R.string.home_caught_up_title
                false -> R.string.home_no_read_title
            },
        ),
    ) {
        if (unread != false) OutlinedButton(onClick = onRefresh, enabled = !refreshing) { Text(stringResource(R.string.action_refresh)) }
    }
}
