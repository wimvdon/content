package to.wim.content.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import to.wim.content.R
import to.wim.content.data.Feed
import to.wim.content.data.FeedSelection
import to.wim.content.data.Folder
import to.wim.content.data.Saved
import to.wim.content.data.SavedFilter
import to.wim.content.data.SavedTab
import to.wim.content.data.feedTopics
import to.wim.content.data.choosesFolder

/**
 * The ☰ menu: Feeds and Saved as two sections, always open; the Inbox while signed in; then Manage
 * and Settings. A feed folder shows its feeds only while you are in it (#251), so the list stays
 * short.
 */
@Composable
fun FeedDrawer(
    feeds: List<Feed>,
    folders: List<Folder>,
    selection: FeedSelection,
    unreadCounts: Map<FeedSelection, Int>,
    onSelect: (FeedSelection) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenManage: () -> Unit,
    saved: SavedHome? = null,
    inSaved: Boolean = false,
    toReadCount: Int = 0,
    onSavedFilter: (SavedFilter) -> Unit = {},
    /** Signed in: the Inbox shows, with what in it is unread. */
    showsInbox: Boolean = false,
    inInbox: Boolean = false,
    inboxUnread: Int = 0,
    onOpenInbox: () -> Unit = {},
) {
    // While Saved or the Inbox is shown, no feed section is selected.
    @Suppress("NAME_SHADOWING")
    val selection: FeedSelection? = selection.takeIf { !inSaved && !inInbox }

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
                // The app's logo on its own, no circle: the launcher icon's foreground, drawn larger than
                // its box so the C (48 of its 108 units; the rest is the launcher's safe zone) fills it.
                Box(Modifier.size(LOGO_SIZE), contentAlignment = Alignment.Center) {
                    Image(
                        painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.requiredSize(LOGO_SIZE * 108f / 48f),
                    )
                }
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
            }

            // Feeds section: its places (All and the topics with feeds, as the chips), and below a topic
            // its feeds, only while you are in it; then the feeds without a topic.
            SectionHeader(stringResource(R.string.feeds_title))
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.home_tab_feed)) },
                icon = { Icon(painterResource(R.drawable.ic_rss_feed), contentDescription = null) },
                badge = { UnreadBadge(unreadCounts[FeedSelection.All] ?: 0) },
                selected = selection == FeedSelection.All,
                onClick = { onSelect(FeedSelection.All) },
            )
            for (folder in feedTopics(feeds, folders)) {
                val folderSelection = FeedSelection.InFolder(folder.id)
                val inFolder = feeds.filter { it.folderId == folder.id }
                val open = selection?.choosesFolder(folder) == true || inFolder.any { selection == FeedSelection.Single(it.url) }
                NavigationDrawerItem(
                    label = { Text(folder.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { Icon(rememberFolderIconPainter(folder.icon), contentDescription = null) },
                    badge = { UnreadBadge(unreadCounts[folderSelection] ?: 0) },
                    selected = selection?.choosesFolder(folder) == true,
                    onClick = { onSelect(folderSelection) },
                )
                AnimatedVisibility(visible = open, enter = expandVertically(), exit = shrinkVertically()) {
                    Column {
                        for (feed in inFolder) {
                            FeedItem(feed, selection, unreadCounts, onSelect, Modifier.padding(start = 24.dp))
                        }
                    }
                }
            }
            val loose = feeds.filter { feed -> feed.folderId == null || folders.none { it.id == feed.folderId } }
            if (loose.isNotEmpty() && folders.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 16.dp))
            for (feed in loose) {
                FeedItem(feed, selection, unreadCounts, onSelect)
            }

            // Saved section: its places, as on the state chip: To read (with what is still to read)
            // and the Archive (no count: it is not a to-do). Liked and the topics are filters: chips only.
            if (saved != null) {
                val shownTab = saved.filter.tab
                SectionHeader(stringResource(R.string.saved_title), Modifier.padding(top = 8.dp))
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.reading_list_to_read), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { Icon(painterResource(R.drawable.ic_reading_list), contentDescription = null) },
                    badge = { UnreadBadge(toReadCount) },
                    selected = inSaved && shownTab == SavedTab.ToRead,
                    onClick = { onSavedFilter(saved.filter.copy(tab = SavedTab.ToRead)) },
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.reading_list_archive), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { Icon(painterResource(R.drawable.ic_inventory), contentDescription = null) },
                    selected = inSaved && shownTab == SavedTab.Archive,
                    onClick = { onSavedFilter(saved.filter.copy(tab = SavedTab.Archive)) },
                )
            }

            // The Inbox (#246): what other Content users shared with you, with what is still unread.
            if (showsInbox) {
                Spacer(Modifier.height(8.dp))
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.inbox_title)) },
                    icon = { Icon(painterResource(R.drawable.ic_notifications), contentDescription = null) },
                    badge = { UnreadBadge(inboxUnread) },
                    selected = inInbox,
                    onClick = onOpenInbox,
                )
            }

            // At the bottom: Manage topics (topics with their feeds), and Settings.
            HorizontalDivider(Modifier.padding(vertical = 12.dp, horizontal = 16.dp))
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.menu_manage)) },
                icon = { Icon(painterResource(R.drawable.ic_manage_feeds), contentDescription = null) },
                selected = false,
                onClick = onOpenManage,
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

/** A section's header: its name in the primary colour. */
@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(start = 16.dp, end = 16.dp)
                .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
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

/** How large the drawer's logo is. */
private val LOGO_SIZE = 32.dp
