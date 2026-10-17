package be.wimvandonck.reader.ui.home

import android.text.format.DateUtils
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import be.wimvandonck.reader.data.ListView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import be.wimvandonck.reader.data.ArticleDays
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
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import be.wimvandonck.reader.R
import be.wimvandonck.reader.data.Article
import be.wimvandonck.reader.data.Feed
import be.wimvandonck.reader.data.Folder
import be.wimvandonck.reader.data.FeedSelection
import be.wimvandonck.reader.data.ScrollRead
import kotlinx.coroutines.flow.distinctUntilChanged
import be.wimvandonck.reader.feed.Html
import be.wimvandonck.reader.ui.theme.AppShapes
import be.wimvandonck.reader.ui.theme.EmptyState
import be.wimvandonck.reader.ui.theme.Motion
import be.wimvandonck.reader.ui.theme.SkeletonLine
import kotlinx.coroutines.launch

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
)

class HomeActions(
    val onSelect: (FeedSelection) -> Unit,
    val onUnreadOnlyChange: (Boolean) -> Unit,
    val onOpenArticle: (Article) -> Unit,
    /** Swiped either way: read becomes unread and the other way round. */
    val onToggleRead: (Article) -> Unit,
    val onRefresh: () -> Unit,
    val onMarkAllRead: () -> Unit,
    /** Articles that scrolled off the top, with Mark as read on scroll on. */
    val onScrolledPast: (Set<String>) -> Unit,
    val onAddFeed: () -> Unit,
    val onImportOpml: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenFeeds: () -> Unit,
)

/** The article list, with the feeds in a navigation drawer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeState,
    snackbarHostState: SnackbarHostState,
    actions: HomeActions,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val selectedFeed = (state.selection as? FeedSelection.Single)?.let { s -> state.feeds.firstOrNull { it.url == s.feedUrl } }
    val selectedFolder = (state.selection as? FeedSelection.InFolder)?.let { s -> state.folders.firstOrNull { it.id == s.folderId } }
    val title = selectedFeed?.title ?: selectedFolder?.title ?: stringResource(R.string.feeds_all)
    // The selected folder tab: the folder being viewed, or the folder of the feed being viewed.
    val sectionFolderId = selectedFolder?.id ?: selectedFeed?.folderId
    val showFolderTabs = state.folders.isNotEmpty() && state.feeds.isNotEmpty()
    val unreadHere = state.unreadCounts[state.selection] ?: 0

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Opened with the ☰ button only: an edge swipe would fight with swiping articles.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            FeedDrawer(
                feeds = state.feeds,
                folders = state.folders,
                selection = state.selection,
                unreadCounts = state.unreadCounts,
                onSelect = {
                    actions.onSelect(it)
                    scope.launch { drawerState.close() }
                },
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    actions.onOpenSettings()
                },
                onOpenFeeds = {
                    scope.launch { drawerState.close() }
                    actions.onOpenFeeds()
                },
            )
        },
    ) {
        val hasArticles = state.articles.isNotEmpty()
        // The whole header slides away while scrolling down and returns when scrolling up.
        val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(canScroll = { hasArticles })
        // Another section, or a list with nothing left to scroll, shows the header again.
        LaunchedEffect(state.selection, hasArticles) { scrollBehavior.state.heightOffset = 0f }
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CollapsingHeader(scrollBehavior) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (state.feeds.isNotEmpty()) {
                                    Text(
                                        if (state.unreadOnly) {
                                            pluralStringResource(R.plurals.home_subtitle_unread, unreadHere, unreadHere)
                                        } else {
                                            pluralStringResource(R.plurals.home_subtitle_all, unreadHere, unreadHere)
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(painterResource(R.drawable.ic_menu), contentDescription = stringResource(R.string.action_open_feeds))
                            }
                        },
                        windowInsets = WindowInsets(0),
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                    )
                    if (showFolderTabs) {
                        FolderTabs(
                            folders = state.folders,
                            selectedFolderId = sectionFolderId,
                            onSelectAll = { actions.onSelect(FeedSelection.All) },
                            onSelectFolder = { actions.onSelect(FeedSelection.InFolder(it.id)) },
                        )
                    }
                    if (state.feeds.isNotEmpty()) {
                        ReadFilter(
                            unreadOnly = state.unreadOnly,
                            onUnreadOnlyChange = actions.onUnreadOnlyChange,
                            // Room around the filter so the header doesn't feel cramped.
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
                        )
                    }
                }
            },
            floatingActionButton = {
                // Marks everything in the section being viewed as read: All, a folder or a feed.
                if (state.feeds.isNotEmpty() && unreadHere > 0) {
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
                        state.articles.isEmpty() -> HomeContent.Empty
                        else -> HomeContent.Articles
                    }
                AnimatedContent(
                    targetState = content,
                    transitionSpec = { fadeIn(Motion.fadeSpec()) togetherWith fadeOut(Motion.fadeSpec()) },
                    label = "home",
                ) { shown ->
                    when (shown) {
                        HomeContent.NoFeeds -> NoFeeds(actions.onAddFeed, actions.onImportOpml)
                        HomeContent.Loading -> ArticleSkeletons()
                        HomeContent.Empty -> AllCaughtUp(state.unreadOnly) { actions.onUnreadOnlyChange(false) }
                        HomeContent.Articles -> ArticleList(state, actions)
                    }
                }
            }
            }
        }
    }
}

private enum class HomeContent { NoFeeds, Loading, Empty, Articles }

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
            snapshotFlow { listState.firstVisibleItemIndex }
                .distinctUntilChanged()
                .collect { first ->
                    val keys = ScrollRead.scrolledPast(articles, positions.getOrElse(first) { articles.size })
                    if (keys.isNotEmpty()) onScrolledPast(keys)
                }
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        // Room at the end for the floating action button.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        days.forEach { (day, articles) ->
            stickyHeader(key = "day:$day") { DayHeader(day) }
            items(articles, key = { it.key }) { article ->
                SwipeToToggleRead(article, onToggle = { actions.onToggleRead(article) }, modifier = Modifier.animateItem()) {
                    ArticleRow(
                        article = article,
                        feed = if (showFeedName) feedsByUrl[article.feedUrl] else null,
                        view = state.listView,
                        showImage = state.showImages,
                        onClick = { actions.onOpenArticle(article) },
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

/**
 * Swiping a row either way past [SWIPE_THRESHOLD] of its width marks it read, or unread when it
 * was read; the row always springs back. A short vibration says when letting go will toggle.
 */
@Composable
private fun SwipeToToggleRead(
    article: Article,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var offset by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val toggle by rememberUpdatedState(onToggle)
    val threshold = width * SWIPE_THRESHOLD
    val label = stringResource(if (article.read) R.string.action_mark_unread else R.string.action_mark_read)
    val dragState =
        rememberDraggableState { delta ->
            val before = abs(offset) >= threshold
            offset = (offset + delta).coerceIn(-width.toFloat(), width.toFloat())
            if (abs(offset) >= threshold != before) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    Box(
        modifier
            .onSizeChanged { width = it.width }
            .semantics {
                customActions =
                    listOf(
                        CustomAccessibilityAction(label) {
                            toggle()
                            true
                        },
                    )
            },
    ) {
        if (offset != 0f) {
            val armed = abs(offset) >= threshold
            Row(
                Modifier
                    .matchParentSize()
                    .clip(AppShapes.Item)
                    .background(if (armed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (offset < 0) Arrangement.End else Arrangement.Start,
            ) {
                val tint = if (armed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                Icon(
                    painterResource(if (article.read) R.drawable.ic_mark_unread else R.drawable.ic_mark_read),
                    contentDescription = null,
                    tint = tint,
                )
                Text(label, style = MaterialTheme.typography.labelLarge, color = tint, modifier = Modifier.padding(horizontal = 12.dp))
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.roundToInt(), 0) }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        if (abs(offset) >= threshold) toggle()
                        scope.launch {
                            animate(offset, 0f, animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { value, _ ->
                                offset = value
                            }
                        }
                    },
                ),
        ) {
            content()
        }
    }
}

/** How far (as part of the row's width) a swipe must go to toggle read. */
private const val SWIPE_THRESHOLD = 0.3f

@Composable
private fun ArticleRow(
    article: Article,
    feed: Feed?,
    view: ListView,
    showImage: Boolean,
    onClick: () -> Unit,
) {
    val time =
        remember(article.sortTime) {
            DateUtils
                .getRelativeTimeSpanString(
                    article.sortTime,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE,
                ).toString()
        }
    val snippet = remember(article.html) { Html.snippet(article.html) }
    val image = article.imageUrl?.takeIf { showImage && view != ListView.Small }
    Surface(
        onClick = onClick,
        shape = AppShapes.Item,
        color = if (article.read) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        when (view) {
            ListView.Small -> {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    MetaLine(article, feed, time)
                    ArticleTitle(article, MaterialTheme.typography.titleSmall, maxLines = 2)
                }
            }

            ListView.Medium -> {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        MetaLine(article, feed, time)
                        ArticleTitle(article, MaterialTheme.typography.titleMedium, maxLines = 3)
                        Snippet(snippet, maxLines = 2)
                    }
                    if (image != null) Thumbnail(image, Modifier.padding(top = 20.dp))
                }
            }

            ListView.Large -> {
                Column {
                    if (image != null) LargePicture(image)
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MetaLine(article, feed, time)
                        ArticleTitle(article, MaterialTheme.typography.titleLarge, maxLines = 3)
                        Snippet(snippet, maxLines = 3)
                    }
                }
            }
        }
    }
}

/** Unread dot, feed icon and name, and age. */
@Composable
private fun MetaLine(
    article: Article,
    feed: Feed?,
    time: String,
) {
    val unreadLabel = stringResource(R.string.article_unread)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!article.read) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .clearAndSetSemantics { contentDescription = unreadLabel },
            )
        }
        if (feed != null) {
            FeedIcon(feed, FeedIconSize.Small)
            Text(
                text = feed.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Text(
            text = time,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
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

@Composable
private fun AllCaughtUp(
    unreadOnly: Boolean,
    onShowAll: () -> Unit,
) {
    EmptyState(
        painterResource(if (unreadOnly) R.drawable.ic_done_all else R.drawable.ic_article),
        stringResource(if (unreadOnly) R.string.home_caught_up_title else R.string.home_no_articles_title),
        body = stringResource(if (unreadOnly) R.string.home_caught_up_body else R.string.home_no_articles_body),
    ) {
        if (unreadOnly) Button(onClick = onShowAll) { Text(stringResource(R.string.home_show_all)) }
    }
}
