package io.github.impca201.rssreader.ui.home

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.impca201.rssreader.R
import io.github.impca201.rssreader.data.Article
import io.github.impca201.rssreader.data.Feed
import io.github.impca201.rssreader.data.FeedSelection
import io.github.impca201.rssreader.feed.Html
import io.github.impca201.rssreader.ui.theme.AppShapes
import io.github.impca201.rssreader.ui.theme.Motion
import io.github.impca201.rssreader.ui.theme.SkeletonLine
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
    /** Show each article's picture as a thumbnail. */
    val showImages: Boolean = true,
)

class HomeActions(
    val onSelect: (FeedSelection) -> Unit,
    val onUnreadOnlyChange: (Boolean) -> Unit,
    val onOpenArticle: (Article) -> Unit,
    val onRefresh: () -> Unit,
    val onMarkAllRead: () -> Unit,
    val onAddFeed: () -> Unit,
    val onImportOpml: () -> Unit,
    val onImportInoreader: () -> Unit,
    val onOpenSettings: () -> Unit,
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
    val title = selectedFeed?.title ?: stringResource(R.string.feeds_all)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            FeedDrawer(
                feeds = state.feeds,
                selection = state.selection,
                unreadCounts = state.unreadCounts,
                onSelect = {
                    actions.onSelect(it)
                    scope.launch { drawerState.close() }
                },
                onAddFeed = {
                    scope.launch { drawerState.close() }
                    actions.onAddFeed()
                },
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    actions.onOpenSettings()
                },
            )
        },
    ) {
        val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
        var menuOpen by remember { mutableStateOf(false) }
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(painterResource(R.drawable.ic_menu), contentDescription = stringResource(R.string.action_open_feeds))
                        }
                    },
                    actions = {
                        if (state.feeds.isNotEmpty()) {
                            IconButton(onClick = actions.onMarkAllRead, enabled = (state.unreadCounts[state.selection] ?: 0) > 0) {
                                Icon(painterResource(R.drawable.ic_done_all), contentDescription = stringResource(R.string.action_mark_all_read))
                            }
                        }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (state.feeds.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_refresh)) },
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_refresh), contentDescription = null) },
                                    enabled = !state.refreshing,
                                    onClick = {
                                        menuOpen = false
                                        actions.onRefresh()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.feeds_add)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    actions.onAddFeed()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_settings)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_settings), contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    actions.onOpenSettings()
                                },
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { innerPadding ->
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = actions.onRefresh,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
            ) {
                val content =
                    when {
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
                        HomeContent.NoFeeds -> NoFeeds(actions.onAddFeed, actions.onImportOpml, actions.onImportInoreader)
                        HomeContent.Loading -> ArticleSkeletons()
                        HomeContent.Empty -> AllCaughtUp(state.unreadOnly) { actions.onUnreadOnlyChange(false) }
                        HomeContent.Articles -> ArticleList(state, actions)
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
    val showFeedName = state.selection == FeedSelection.All
    val feedTitles = remember(state.feeds) { state.feeds.associate { it.url to it.title } }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "filter") { UnreadFilter(state, actions.onUnreadOnlyChange) }
        items(state.articles, key = { it.key }) { article ->
            ArticleRow(
                article = article,
                feedTitle = if (showFeedName) feedTitles[article.feedUrl] else null,
                showImage = state.showImages,
                onClick = { actions.onOpenArticle(article) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun UnreadFilter(
    state: HomeState,
    onUnreadOnlyChange: (Boolean) -> Unit,
) {
    val unread = state.unreadCounts[state.selection] ?: 0
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
        FilterChip(
            selected = state.unreadOnly,
            onClick = { onUnreadOnlyChange(true) },
            label = { Text(pluralStringResource(R.plurals.filter_unread, unread, unread)) },
            leadingIcon = if (state.unreadOnly) checkIcon() else null,
        )
        FilterChip(
            selected = !state.unreadOnly,
            onClick = { onUnreadOnlyChange(false) },
            label = { Text(stringResource(R.string.filter_all)) },
            leadingIcon = if (!state.unreadOnly) checkIcon() else null,
        )
    }
}

private fun checkIcon(): @Composable () -> Unit =
    {
        Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
    }

@Composable
private fun ArticleRow(
    article: Article,
    feedTitle: String?,
    showImage: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
    val unreadLabel = stringResource(R.string.article_unread)
    Surface(
        onClick = onClick,
        shape = AppShapes.Item,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!article.read) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .clearAndSetSemantics { contentDescription = unreadLabel },
                        )
                    }
                    Text(
                        text = listOfNotNull(feedTitle, time).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (article.read) FontWeight.Normal else FontWeight.SemiBold,
                    color = if (article.read) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (snippet.isNotEmpty()) {
                    Text(
                        text = snippet,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (showImage && article.imageUrl != null) {
                Thumbnail(article.imageUrl, Modifier.padding(top = 20.dp))
            }
        }
    }
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
    onImportInoreader: () -> Unit,
) {
    EmptyState(
        title = stringResource(R.string.home_no_feeds_title),
        body = stringResource(R.string.home_no_feeds_body),
    ) {
        Button(onClick = onAddFeed) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null)
            Text(stringResource(R.string.feeds_add), modifier = Modifier.padding(start = 8.dp))
        }
        OutlinedButton(onClick = onImportOpml) { Text(stringResource(R.string.feeds_import)) }
        TextButton(onClick = onImportInoreader) { Text(stringResource(R.string.inoreader_title)) }
    }
}

@Composable
private fun AllCaughtUp(
    unreadOnly: Boolean,
    onShowAll: () -> Unit,
) {
    EmptyState(
        title = stringResource(if (unreadOnly) R.string.home_caught_up_title else R.string.home_no_articles_title),
        body = stringResource(if (unreadOnly) R.string.home_caught_up_body else R.string.home_no_articles_body),
    ) {
        if (unreadOnly) TextButton(onClick = onShowAll) { Text(stringResource(R.string.home_show_all)) }
    }
}

@Composable
private fun EmptyState(
    title: String,
    body: String,
    buttons: @Composable () -> Unit,
) {
    // Scrollable so pull-to-refresh works on empty screens too.
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(
            painterResource(R.drawable.ic_rss_feed),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp).padding(top = 48.dp),
        )
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        buttons()
    }
}
