package to.wim.content.ui.feeds

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import to.wim.content.R
import to.wim.content.data.Feed
import to.wim.content.data.FeedProblem
import to.wim.content.data.Folder
import to.wim.content.ui.home.FeedIcon
import to.wim.content.ui.home.FeedIconSize
import to.wim.content.ui.home.problemText
import to.wim.content.ui.home.rememberFolderIconPainter
import to.wim.content.ui.theme.AppShapes
import to.wim.content.ui.theme.DragHandle
import to.wim.content.ui.theme.ReorderableColumn
import to.wim.content.ui.theme.readableWidth

/** One row of Manage topics: a topic with what it holds, and how many of its feeds fail. */
data class TopicRow(
    val topic: Folder,
    val feeds: Int,
    val saved: Int,
    val failing: Int,
)

/** What Manage topics can do with the feeds without a topic, listed under the topics. */
class LooseFeedActions(
    val onEditFeed: (feed: Feed, title: String, topicId: String?) -> Unit,
    val onRemoveFeed: (Feed) -> Unit,
    val onReorderFeeds: (urls: List<String>) -> Unit,
)

/**
 * Manage topics: every topic with its icon and what it holds (its feeds and saved articles, and a
 * feed that fails to download), in the user's order, which drag changes; then the feeds without a
 * topic, loose, as on a topic's page (tap one to rename it, give it a topic or remove it). Tap a
 * topic for its page. Add feed and New topic are the two buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicsScreen(
    topics: List<TopicRow>,
    looseFeeds: List<Feed>,
    allTopics: List<Folder>,
    problems: Map<String, FeedProblem>,
    looseActions: LooseFeedActions,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpen: (topicId: String) -> Unit,
    onReorder: (ids: List<String>) -> Unit,
    onNewTopic: (title: String, icon: String) -> Unit,
    /** Add feed, from the list: it lands without a topic, and you stay here. */
    onAddFeed: () -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.manage_title)) },
                navigationIcon = { BackButton(onBack) },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            // Two actions, one dominant: Add feed (the more frequent) as the FAB, New topic smaller
            // and quieter above it. Material's FAB menu isn't in the stable library yet.
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ExtendedFloatingActionButton(
                    onClick = { creating = true },
                    icon = { Icon(painterResource(R.drawable.ic_folder), contentDescription = null) },
                    text = { Text(stringResource(R.string.folder_new)) },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    elevation = FloatingActionButtonDefaults.loweredElevation(),
                )
                ExtendedFloatingActionButton(
                    onClick = onAddFeed,
                    icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                    text = { Text(stringResource(R.string.feeds_add)) },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .readableWidth(),
            // Room under the last row for the two buttons.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 160.dp),
        ) {
            if (topics.isEmpty()) {
                item(key = "hint") { Hint(stringResource(R.string.folders_explanation)) }
            }
            item(key = "topics") {
                ReorderableColumn(
                    items = topics,
                    key = { it.topic.id },
                    onMove = { from, to ->
                        val ids = topics.map { it.topic.id }.toMutableList()
                        ids.add(to, ids.removeAt(from))
                        onReorder(ids)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { row ->
                    TopicLine(
                        icon = { Icon(rememberFolderIconPainter(row.topic.icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer) },
                        title = row.topic.title,
                        detail = topicDetail(row.feeds, row.saved, row.failing),
                        failing = row.failing > 0,
                        lifted = isDragging,
                        onClick = { onOpen(row.topic.id) },
                        trailing = { DragHandle(handleModifier) },
                    )
                }
            }
            if (looseFeeds.isNotEmpty()) {
                item(key = "loose") {
                    FeedRows(
                        feeds = looseFeeds,
                        topics = allTopics,
                        problems = problems,
                        onEditFeed = looseActions.onEditFeed,
                        onRemoveFeed = looseActions.onRemoveFeed,
                        onReorderFeeds = looseActions.onReorderFeeds,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
    }
    if (creating) {
        FolderSheet(
            folder = null,
            onSave = { title, icon ->
                creating = false
                onNewTopic(title, icon)
            },
            onDismiss = { creating = false },
        )
    }
}

/** "4 feeds · 12 saved", and how many feeds fail when some do. */
@Composable
private fun topicDetail(
    feeds: Int,
    saved: Int,
    failing: Int,
): String {
    val counts =
        stringResource(
            R.string.topic_counts,
            pluralStringResource(R.plurals.feeds_count, feeds, feeds),
            pluralStringResource(R.plurals.topic_saved_count, saved, saved),
        )
    return if (failing == 0) counts else stringResource(R.string.topic_counts, counts, pluralStringResource(R.plurals.topic_failing_count, failing, failing))
}

/** A topic as a row: its icon in a circle, its name and [detail] (none in a sheet's Manage topics), then [trailing]. */
@Composable
internal fun TopicLine(
    icon: @Composable () -> Unit,
    title: String,
    detail: String?,
    failing: Boolean,
    lifted: Boolean,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = AppShapes.Item,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = if (lifted) 8.dp else 0.dp,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) { icon() }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detail != null) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (failing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            trailing()
        }
    }
}

/** What a topic page shows: the topic, its feeds and its counts. */
data class TopicPage(
    val topic: Folder,
    val feeds: List<Feed>,
    val saved: Int,
    val toRead: Int,
)

/** What a topic page can do. */
class TopicPageActions(
    val onBack: () -> Unit,
    val onAddFeed: () -> Unit,
    val onEditFeed: (feed: Feed, title: String, topicId: String?) -> Unit,
    val onRemoveFeed: (Feed) -> Unit,
    val onReorderFeeds: (urls: List<String>) -> Unit,
    val onEdit: (title: String, icon: String) -> Unit,
    val onMerge: (intoId: String) -> Unit,
    val onRemove: () -> Unit,
)

/**
 * One topic's page, for managing it (reading is in Feeds and Saved): its feeds (drag to reorder,
 * tap to rename, move or remove; Add a feed to it), then how many saved articles have it. The pen
 * changes its name and icon; ⋮ has Merge with… and Remove topic, which say what they do first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicScreen(
    page: TopicPage,
    topics: List<Folder>,
    problems: Map<String, FeedProblem>,
    snackbarHostState: SnackbarHostState,
    actions: TopicPageActions,
) {
    val topic = page.topic
    var editing by rememberSaveable { mutableStateOf(false) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var merging by rememberSaveable { mutableStateOf(false) }
    var removing by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = {
                    // The pen sits right after the icon and title, the two things it edits.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(rememberFolderIconPainter(topic.icon), contentDescription = null)
                        Text(topic.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        IconButton(onClick = { editing = true }) {
                            Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.folder_edit))
                        }
                    }
                },
                navigationIcon = { BackButton(actions.onBack) },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (topics.size > 1) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.topic_merge)) },
                                    onClick = {
                                        menuOpen = false
                                        merging = true
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.topic_remove), color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menuOpen = false
                                    removing = true
                                },
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .readableWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "feedsHeading") { SectionHeading(stringResource(R.string.feeds_title)) }
            item(key = "feeds") {
                FeedRows(
                    feeds = page.feeds,
                    topics = topics,
                    problems = problems,
                    onEditFeed = actions.onEditFeed,
                    onRemoveFeed = actions.onRemoveFeed,
                    onReorderFeeds = actions.onReorderFeeds,
                )
            }
            item(key = "addFeed") {
                TextButton(onClick = actions.onAddFeed) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                    Text(stringResource(R.string.topic_add_feed, topic.title), modifier = Modifier.padding(start = 8.dp))
                }
            }
            item(key = "savedHeading") { SectionHeading(stringResource(R.string.saved_title)) }
            item(key = "saved") {
                // Only what it holds: this page manages the topic, Saved is where its articles are read.
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(
                                R.string.topic_counts,
                                pluralStringResource(R.plurals.topic_saved_count, page.saved, page.saved),
                                pluralStringResource(R.plurals.topic_to_read_count, page.toRead, page.toRead),
                            ),
                        )
                    },
                )
            }
        }
    }

    if (editing) {
        FolderSheet(
            folder = topic,
            onSave = { title, icon ->
                editing = false
                actions.onEdit(title, icon)
            },
            onDismiss = { editing = false },
        )
    }
    if (merging) {
        MergeDialog(
            topic = topic,
            others = topics.filter { it.id != topic.id },
            onMerge = {
                merging = false
                actions.onMerge(it)
            },
            onDismiss = { merging = false },
        )
    }
    if (removing) {
        AlertDialog(
            onDismissRequest = { removing = false },
            title = { Text(stringResource(R.string.folder_remove_title)) },
            text = { Text(stringResource(R.string.folder_remove_body, topic.title)) },
            confirmButton = {
                TextButton(onClick = {
                    removing = false
                    actions.onRemove()
                }) { Text(stringResource(R.string.action_remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removing = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/**
 * Feeds as rows, on a topic's page and (those without a topic) in Manage topics: drag to reorder,
 * tap one to rename it, give it a topic or remove it (which asks first).
 */
@Composable
private fun FeedRows(
    feeds: List<Feed>,
    topics: List<Folder>,
    problems: Map<String, FeedProblem>,
    onEditFeed: (feed: Feed, title: String, topicId: String?) -> Unit,
    onRemoveFeed: (Feed) -> Unit,
    onReorderFeeds: (urls: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingFeed by rememberSaveable { mutableStateOf<String?>(null) }
    var removingFeed by rememberSaveable { mutableStateOf<String?>(null) }
    ReorderableColumn(
        items = feeds,
        key = { it.url },
        onMove = { from, to ->
            val urls = feeds.map { it.url }.toMutableList()
            urls.add(to, urls.removeAt(from))
            onReorderFeeds(urls)
        },
        modifier = modifier.fillMaxWidth(),
    ) { feed ->
        val problem = problems[feed.url]?.let { problemText(it) }
        ListItem(
            headlineContent = { Text(feed.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Text(
                    problem ?: feed.url.substringAfter("://"),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            leadingContent = { FeedIcon(feed, FeedIconSize.Medium) },
            trailingContent = { DragHandle(handleModifier) },
            tonalElevation = if (isDragging) 8.dp else 0.dp,
            modifier = Modifier.clickable(role = Role.Button, onClickLabel = stringResource(R.string.feed_edit)) { editingFeed = feed.url },
        )
    }
    feeds.firstOrNull { it.url == editingFeed }?.let { feed ->
        FeedDialog(
            feed = feed,
            folders = topics,
            problem = problems[feed.url]?.let { problemText(it) },
            onSave = { title, topicId ->
                editingFeed = null
                onEditFeed(feed, title, topicId)
            },
            onRemove = {
                editingFeed = null
                removingFeed = feed.url
            },
            onDismiss = { editingFeed = null },
        )
    }
    feeds.firstOrNull { it.url == removingFeed }?.let { feed ->
        AlertDialog(
            onDismissRequest = { removingFeed = null },
            title = { Text(stringResource(R.string.feeds_remove_title)) },
            text = { Text(stringResource(R.string.feeds_remove_body, feed.title)) },
            confirmButton = {
                TextButton(onClick = {
                    removingFeed = null
                    onRemoveFeed(feed)
                }) { Text(stringResource(R.string.action_remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removingFeed = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Merge with…: pick the topic that stays; it gets this one's feeds and saved articles. */
@Composable
private fun MergeDialog(
    topic: Folder,
    others: List<Folder>,
    onMerge: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.topic_merge_title, topic.title)) },
        text = {
            Column {
                Text(stringResource(R.string.topic_merge_body), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
                LazyColumn {
                    items(others.size) { i ->
                        val other = others[i]
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.RadioButton) { chosen = other.id }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RadioButton(selected = chosen == other.id, onClick = null)
                            Icon(rememberFolderIconPainter(other.icon), contentDescription = null)
                            Text(other.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { chosen?.let(onMerge) }, enabled = chosen != null) { Text(stringResource(R.string.topic_merge_action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
}
