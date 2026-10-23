package be.wimvandonck.reader.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import be.wimvandonck.reader.R
import be.wimvandonck.reader.data.Feed
import be.wimvandonck.reader.data.FeedSelection
import be.wimvandonck.reader.data.Folder
import be.wimvandonck.reader.data.Saved
import be.wimvandonck.reader.data.SavedFilter
import be.wimvandonck.reader.data.choosesFolder

/**
 * The ☰ menu: Feeds and Saved as two collapsible sections, plus connected services and settings.
 * Each section collapses and expands automatically when you are in it.
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
    saved: SavedHome? = null,
    inSaved: Boolean = false,
    toReadCount: Int = 0,
    onSavedFilter: (SavedFilter) -> Unit = {},
) {
    // While Saved is shown, no feed section is selected.
    @Suppress("NAME_SHADOWING")
    val selection: FeedSelection? = selection.takeIf { !inSaved }
    // The section you're in is open, the other folded shut, so the drawer stays short. Folding by
    // hand lasts until you go to the other section.
    var feedsOpen by rememberSaveable(inSaved) { mutableStateOf(!inSaved) }
    var savedOpen by rememberSaveable(inSaved) { mutableStateOf(inSaved) }

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

            // Feeds section: folders and feeds, followed by Manage feeds.
            SectionHeader(
                title = stringResource(R.string.feeds_title),
                open = feedsOpen,
                count = if (feedsOpen) 0 else unreadCounts[FeedSelection.All] ?: 0,
                onToggle = { feedsOpen = !feedsOpen },
            )
            AnimatedVisibility(visible = feedsOpen, enter = expandVertically(), exit = shrinkVertically()) {
                Column {
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.home_tab_feed)) },
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
                            selected = selection?.choosesFolder(folder) == true,
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
                    HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 16.dp))
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.menu_manage_feeds)) },
                        icon = { Icon(painterResource(R.drawable.ic_manage_feeds), contentDescription = null) },
                        selected = false,
                        onClick = onOpenFeeds,
                    )
                }
            }

            // Saved section: Saved (everything) and, under Tags, Liked and the tags, as the chips (#205), followed by Manage tags. The
            // state (To read, Archive, All) is the chip's; picking here keeps it. Counts are what is
            // still to read, as Feeds counts what is unread.
            if (saved != null) {
                val tagCounts = remember(saved.library) { Saved.toReadTagCounts(saved.library) }
                val likedToRead = remember(saved.library) { Saved.likedToReadCount(saved.library) }
                SectionHeader(
                    title = stringResource(R.string.saved_title),
                    open = savedOpen,
                    count = if (savedOpen) 0 else toReadCount,
                    onToggle = { savedOpen = !savedOpen },
                    modifier = Modifier.padding(top = 8.dp),
                )
                AnimatedVisibility(visible = savedOpen, enter = expandVertically(), exit = shrinkVertically()) {
                    Column {
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.saved_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            icon = { Icon(painterResource(R.drawable.ic_bookmarks_outline), contentDescription = null) },
                            badge = { UnreadBadge(toReadCount) },
                            selected = inSaved && !saved.filter.likedOnly && saved.filter.tags.isEmpty(),
                            onClick = { onSavedFilter(saved.filter.copy(likedOnly = false, tags = emptySet())) },
                        )
                        // Under Tags: Liked first, as on the chips, then the tags.
                        SectionHeading(stringResource(R.string.tags_title))
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.reading_list_liked), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            icon = { Icon(painterResource(R.drawable.ic_favorite), contentDescription = null) },
                            badge = { UnreadBadge(likedToRead) },
                            selected = inSaved && saved.filter.likedOnly,
                            onClick = { onSavedFilter(saved.filter.copy(likedOnly = true, tags = emptySet())) },
                        )
                        for ((tag, count) in tagCounts) {
                            val tagSelected = inSaved && saved.filter.tags.any { it.equals(tag, ignoreCase = true) }
                            NavigationDrawerItem(
                                label = { Text(tag, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                icon = { Icon(painterResource(R.drawable.ic_label), contentDescription = null) },
                                badge = { UnreadBadge(count) },
                                selected = tagSelected,
                                onClick = { onSavedFilter(saved.filter.copy(likedOnly = false, tags = setOf(tag))) },
                            )
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 16.dp))
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_manage_tags)) },
                            icon = { Icon(painterResource(R.drawable.ic_tags), contentDescription = null) },
                            selected = false,
                            onClick = onOpenTagsManage,
                        )
                    }
                }
            }

            // Settings lives at the bottom; Connected services and OPML are in it.
            HorizontalDivider(Modifier.padding(vertical = 12.dp, horizontal = 16.dp))
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
    if (count > 0) {
        // Read out as "12 unread", not a bare number.
        val spoken = pluralStringResource(R.plurals.home_subtitle_unread, count, count)
        Text(count.toString(), style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { contentDescription = spoken })
    }
}
