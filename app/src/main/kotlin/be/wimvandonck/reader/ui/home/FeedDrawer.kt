package be.wimvandonck.reader.ui.home

import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import be.wimvandonck.reader.R
import be.wimvandonck.reader.data.Feed
import be.wimvandonck.reader.data.FeedSelection
import be.wimvandonck.reader.data.Folder
import be.wimvandonck.reader.data.ReadingListFilter
import be.wimvandonck.reader.data.BookmarkFilter
import be.wimvandonck.reader.data.Bookmarks
import be.wimvandonck.reader.data.ReadingListTab
import be.wimvandonck.reader.data.itemTags

/**
 * The app's navigation: Feeds and the reading list as sections that fold open and shut. Feeds: All
 * articles, each folder with its feeds and the feeds outside folders (each with its unread count).
 * Reading list: To read, Liked, its tags and Archive. Bookmarks: All and each collection. Then Manage feeds, Tags, Connected services
 * and Settings. The same wherever the app is (DESIGN.md), with the section being shown open and the
 * other folded shut.
 */
@Composable
fun FeedDrawer(
    feeds: List<Feed>,
    folders: List<Folder>,
    selection: FeedSelection,
    unreadCounts: Map<FeedSelection, Int>,
    onSelect: (FeedSelection) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFeeds: () -> Unit,
    onOpenTagsManage: () -> Unit = {},
    onOpenServices: () -> Unit = {},
    readingList: ReadingListHome? = null,
    inReadingList: Boolean = false,
    toReadCount: Int = 0,
    onReadingListFilter: (ReadingListFilter) -> Unit = {},
    bookmarks: BookmarksHome? = null,
    inBookmarks: Boolean = false,
    bookmarkCount: Int = 0,
    onBookmarksFilter: (BookmarkFilter) -> Unit = {},
) {
    val inFeeds = !inReadingList && !inBookmarks
    // While the reading list or Bookmarks is shown, no feed section is selected.
    @Suppress("NAME_SHADOWING")
    val selection: FeedSelection? = selection.takeIf { inFeeds }
    // The section you're in is open, the others folded shut, so the drawer stays short. Folding by
    // hand lasts until you go to another section.
    var feedsOpen by rememberSaveable(inReadingList, inBookmarks) { mutableStateOf(inFeeds) }
    var readingOpen by rememberSaveable(inReadingList, inBookmarks) { mutableStateOf(inReadingList) }
    var bookmarksOpen by rememberSaveable(inReadingList, inBookmarks) { mutableStateOf(inBookmarks) }
    ModalDrawerSheet {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            // The app's mark and name, as a header.
            Row(
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // The app's logo: the launcher icon's foreground on its white background.
                Image(
                    painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colorResource(R.color.ic_launcher_background))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                )
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
            }
            SectionHeader(
                title = stringResource(R.string.feeds_title),
                open = feedsOpen,
                // Folded shut, the header keeps the unread count.
                count = if (feedsOpen) 0 else unreadCounts[FeedSelection.All] ?: 0,
                onToggle = { feedsOpen = !feedsOpen },
            )
            AnimatedVisibility(visible = feedsOpen, enter = expandVertically(), exit = shrinkVertically()) {
                Column {
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.feeds_all)) },
                        icon = { Icon(painterResource(R.drawable.ic_rss_feed), contentDescription = null) },
                        badge = { UnreadBadge(unreadCounts[FeedSelection.All] ?: 0) },
                        selected = selection == FeedSelection.All,
                        onClick = { onSelect(FeedSelection.All) },
                    )
                    for (folder in folders) {
                        val folderSelection = FeedSelection.InFolder(folder.id)
                        NavigationDrawerItem(
                            label = { Text(folder.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            icon = { Icon(rememberFolderIconPainter(folder.icon), contentDescription = null) },
                            badge = { UnreadBadge(unreadCounts[folderSelection] ?: 0) },
                            selected = selection == folderSelection,
                            onClick = { onSelect(folderSelection) },
                        )
                        for (feed in feeds.filter { it.folderId == folder.id }) {
                            FeedItem(feed, selection, unreadCounts, onSelect, Modifier.padding(start = 24.dp))
                        }
                    }
                    val loose = feeds.filter { feed -> feed.folderId == null || folders.none { it.id == feed.folderId } }
                    if (loose.isNotEmpty() && folders.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 16.dp))
                    for (feed in loose) {
                        FeedItem(feed, selection, unreadCounts, onSelect)
                    }
                }
            }
            if (readingList != null) {
                SectionHeader(
                    title = stringResource(R.string.reading_list_title),
                    open = readingOpen,
                    count = if (readingOpen) 0 else toReadCount,
                    onToggle = { readingOpen = !readingOpen },
                    modifier = Modifier.padding(top = 8.dp),
                )
                AnimatedVisibility(visible = readingOpen, enter = expandVertically(), exit = shrinkVertically()) {
                    Column { ReadingListSection(readingList.filter.takeIf { inReadingList }, readingList.library.itemTags(), toReadCount, onReadingListFilter) }
                }
            }
            if (bookmarks != null) {
                SectionHeader(
                    title = stringResource(R.string.bookmarks_title),
                    open = bookmarksOpen,
                    count = if (bookmarksOpen) 0 else bookmarkCount,
                    onToggle = { bookmarksOpen = !bookmarksOpen },
                    modifier = Modifier.padding(top = 8.dp),
                )
                AnimatedVisibility(visible = bookmarksOpen, enter = expandVertically(), exit = shrinkVertically()) {
                    Column { BookmarksSection(bookmarks.filter.takeIf { inBookmarks }, Bookmarks.collectionCounts(bookmarks.library), bookmarkCount, onBookmarksFilter) }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp, horizontal = 16.dp))
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.menu_manage_feeds)) },
                icon = { Icon(painterResource(R.drawable.ic_manage_feeds), contentDescription = null) },
                selected = false,
                onClick = onOpenFeeds,
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.menu_manage_tags)) },
                icon = { Icon(painterResource(R.drawable.ic_tags), contentDescription = null) },
                selected = false,
                onClick = onOpenTagsManage,
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.services_title)) },
                icon = { Icon(painterResource(R.drawable.ic_sync_alt), contentDescription = null) },
                selected = false,
                onClick = onOpenServices,
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.menu_settings)) },
                icon = { Icon(painterResource(R.drawable.ic_settings), contentDescription = null) },
                selected = false,
                onClick = onOpenSettings,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** A section's header: its name in the primary colour, a fold arrow, and a count while folded shut. */
@Composable
private fun SectionHeader(
    title: String,
    open: Boolean,
    count: Int,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val turn by animateFloatAsState(if (open) 0f else -90f, label = "fold")
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(CircleShape)
                .toggleable(value = open, role = Role.Button, onValueChange = { onToggle() })
                .padding(start = 16.dp, end = 16.dp)
                .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (count > 0) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Icon(
            painterResource(R.drawable.ic_expand_more),
            contentDescription = stringResource(if (open) R.string.action_collapse else R.string.action_expand),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(turn),
        )
    }
}

@Composable
private fun ReadingListSection(
    /** The list shown, or null while Feeds is shown. */
    shown: ReadingListFilter?,
    tags: List<String>,
    toReadCount: Int,
    onSelect: (ReadingListFilter) -> Unit,
) {
    @Composable
    fun item(
        filter: ReadingListFilter,
        label: String,
        icon: Int,
        badge: Int = 0,
    ) = NavigationDrawerItem(
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        icon = { Icon(painterResource(icon), contentDescription = null) },
        badge = { UnreadBadge(badge) },
        selected = shown == filter,
        onClick = { onSelect(filter) },
    )
    item(ReadingListFilter(), stringResource(R.string.reading_list_to_read), R.drawable.ic_reading_list, toReadCount)
    item(ReadingListFilter(likedOnly = true), stringResource(R.string.reading_list_liked), R.drawable.ic_favorite)
    if (tags.isNotEmpty()) {
        SectionHeading(stringResource(R.string.tags_title))
        for (tag in tags) item(ReadingListFilter(tag = tag), tag, R.drawable.ic_label)
    }
    // Archive last, set apart so it doesn't read as one of the tags.
    HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 16.dp))
    item(ReadingListFilter(ReadingListTab.Archive), stringResource(R.string.reading_list_archive), R.drawable.ic_inventory)
}

/** Bookmarks in the drawer: All, then each collection with its number of bookmarks. */
@Composable
private fun BookmarksSection(
    /** The bookmarks shown, or null while another section is shown. */
    shown: BookmarkFilter?,
    collections: Map<String, Int>,
    count: Int,
    onSelect: (BookmarkFilter) -> Unit,
) {
    NavigationDrawerItem(
        label = { Text(stringResource(R.string.filter_all)) },
        icon = { Icon(painterResource(R.drawable.ic_bookmarks_outline), contentDescription = null) },
        badge = { UnreadBadge(count) },
        selected = shown != null && shown.collection == null,
        onClick = { onSelect(BookmarkFilter()) },
    )
    if (collections.isNotEmpty()) {
        SectionHeading(stringResource(R.string.collections_title))
        for ((collection, n) in collections) {
            NavigationDrawerItem(
                label = { Text(collection, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                icon = { Icon(painterResource(R.drawable.ic_folder), contentDescription = null) },
                badge = { UnreadBadge(n) },
                selected = shown?.collection.equals(collection, ignoreCase = true),
                onClick = { onSelect(BookmarkFilter(collection = collection)) },
            )
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                .padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                .semantics { heading() },
    )
}

@Composable
private fun FeedItem(
    feed: Feed,
    selection: FeedSelection?,
    unreadCounts: Map<FeedSelection, Int>,
    onSelect: (FeedSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val feedSelection = FeedSelection.Single(feed.url)
    NavigationDrawerItem(
        label = { Text(feed.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        icon = { FeedIcon(feed, FeedIconSize.Medium) },
        badge = { UnreadBadge(unreadCounts[feedSelection] ?: 0) },
        selected = selection == feedSelection,
        onClick = { onSelect(feedSelection) },
        modifier = modifier,
    )
}

@Composable
private fun UnreadBadge(count: Int) {
    if (count > 0) Text(count.toString(), style = MaterialTheme.typography.labelLarge)
}
