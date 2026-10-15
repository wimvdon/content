package io.github.impca201.rssreader.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.impca201.rssreader.R
import io.github.impca201.rssreader.data.Feed
import io.github.impca201.rssreader.data.FeedSelection
import io.github.impca201.rssreader.data.Folder

/**
 * Home, then each folder with its feeds, then the feeds outside folders (each with its
 * unread count), then Add feed and Settings.
 */
@Composable
fun FeedDrawer(
    feeds: List<Feed>,
    folders: List<Folder>,
    selection: FeedSelection,
    unreadCounts: Map<FeedSelection, Int>,
    onSelect: (FeedSelection) -> Unit,
    onAddFeed: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFeeds: () -> Unit,
) {
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
                Box(
                    Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_rss_feed),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
            }
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.folder_home)) },
                icon = { Icon(painterResource(R.drawable.ic_home), contentDescription = null) },
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
            if (loose.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.feeds_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                            .semantics { heading() },
                )
            }
            for (feed in loose) {
                FeedItem(feed, selection, unreadCounts, onSelect)
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp, horizontal = 16.dp))
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.feeds_add)) },
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                selected = false,
                onClick = onAddFeed,
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.feeds_page_title)) },
                icon = { Icon(painterResource(R.drawable.ic_rss_feed), contentDescription = null) },
                selected = false,
                onClick = onOpenFeeds,
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

@Composable
private fun FeedItem(
    feed: Feed,
    selection: FeedSelection,
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
