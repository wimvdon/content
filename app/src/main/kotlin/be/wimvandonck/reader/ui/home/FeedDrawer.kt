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
import be.wimvandonck.reader.instapaper.InstapaperView

/**
 * The app's navigation: Feeds and, when signed in, Instapaper as two sections that fold open and
 * shut. Feeds: All articles, each folder with its feeds and the feeds outside folders (each with
 * its unread count). Instapaper: Unread, Liked, Archive, its folders and tags. Then Manage feeds,
 * Manage Instapaper and Settings. The same wherever the app is (DESIGN.md).
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
    onOpenInstapaperManage: () -> Unit = {},
    instapaper: InstapaperHome? = null,
    onSelectInstapaper: (InstapaperView) -> Unit = {},
) {
    // While an Instapaper list is shown, no feed section is selected.
    @Suppress("NAME_SHADOWING")
    val selection: FeedSelection? = selection.takeIf { instapaper?.view == null }
    var feedsOpen by rememberSaveable { mutableStateOf(true) }
    var instapaperOpen by rememberSaveable { mutableStateOf(true) }
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
            if (instapaper != null) {
                SectionHeader(
                    title = stringResource(R.string.section_instapaper),
                    open = instapaperOpen,
                    count = 0,
                    onToggle = { instapaperOpen = !instapaperOpen },
                    modifier = Modifier.padding(top = 8.dp),
                )
                AnimatedVisibility(visible = instapaperOpen, enter = expandVertically(), exit = shrinkVertically()) {
                    Column { InstapaperSection(instapaper, onSelectInstapaper) }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp, horizontal = 16.dp))
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.menu_manage_feeds)) },
                icon = { Icon(painterResource(R.drawable.ic_manage_feeds), contentDescription = null) },
                selected = false,
                onClick = onOpenFeeds,
            )
            if (instapaper != null) {
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.menu_manage_instapaper)) },
                    icon = { Icon(painterResource(R.drawable.ic_manage_instapaper), contentDescription = null) },
                    selected = false,
                    onClick = onOpenInstapaperManage,
                )
            }
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
private fun InstapaperSection(
    instapaper: InstapaperHome,
    onSelect: (InstapaperView) -> Unit,
) {
    @Composable
    fun item(
        view: InstapaperView,
        label: String,
        icon: Int,
    ) = NavigationDrawerItem(
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        icon = { Icon(painterResource(icon), contentDescription = null) },
        selected = instapaper.view == view,
        onClick = { onSelect(view) },
    )
    item(InstapaperView.Home, stringResource(R.string.filter_unread), R.drawable.ic_article)
    item(InstapaperView.Liked, stringResource(R.string.instapaper_liked), R.drawable.ic_favorite)
    item(InstapaperView.Archive, stringResource(R.string.instapaper_archive_list), R.drawable.ic_archive)
    for (folder in instapaper.folders) item(InstapaperView.InFolder(folder.id), folder.title, R.drawable.ic_folder)
    if (instapaper.tags.isNotEmpty()) {
        SectionHeading(stringResource(R.string.instapaper_tags))
        for (tag in instapaper.tags) item(InstapaperView.WithTag(tag.name), tag.name, R.drawable.ic_label)
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
