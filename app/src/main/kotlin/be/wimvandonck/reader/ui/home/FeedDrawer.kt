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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import be.wimvandonck.reader.R
import be.wimvandonck.reader.data.Feed
import be.wimvandonck.reader.data.FeedSelection
import be.wimvandonck.reader.data.Folder
import be.wimvandonck.reader.data.Saved
import be.wimvandonck.reader.data.SavedFilter
import be.wimvandonck.reader.data.SavedTab

/**
 * The ☰ menu: the app's name, then what you manage: feeds, tags, connected services and settings.
 * Feed and Saved are in the navigation bar, and their tabs and tag chips choose what the list shows.
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
    saved: SavedHome? = null,
    inSaved: Boolean = false,
    toReadCount: Int = 0,
    onSavedFilter: (SavedFilter) -> Unit = {},
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
            // Feed and Saved are the navigation bar's (#183); the menu holds what you manage.
            HorizontalDivider(Modifier.padding(vertical = 12.dp, horizontal = 16.dp))
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.menu_manage_feeds)) },
                icon = { Icon(painterResource(R.drawable.ic_manage_feeds), contentDescription = null) },
                selected = false,
                onClick = onOpenFeeds,
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.menu_folders_tags)) },
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

/** Saved in the drawer: To read with its count, All, each folder with its count, then Archive. */
@Composable
private fun SavedSection(
    /** The tab shown, or null while Feeds is shown. */
    shown: SavedTab?,
    folders: Map<String, Int>,
    toReadCount: Int,
    onSelect: (SavedFilter) -> Unit,
) {
    @Composable
    fun item(
        tab: SavedTab,
        label: String,
        icon: Int,
        badge: Int = 0,
    ) = NavigationDrawerItem(
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        icon = { Icon(painterResource(icon), contentDescription = null) },
        badge = { UnreadBadge(badge) },
        selected = if (shown is SavedTab.Folder && tab is SavedTab.Folder) shown.name.equals(tab.name, ignoreCase = true) else shown == tab,
        onClick = { onSelect(SavedFilter(tab)) },
    )
    item(SavedTab.ToRead, stringResource(R.string.reading_list_to_read), R.drawable.ic_reading_list, toReadCount)
    if (folders.isNotEmpty()) {
        SectionHeading(stringResource(R.string.saved_folders))
        for ((folder, n) in folders) item(SavedTab.Folder(folder), folder, R.drawable.ic_folder, n)
    }
    // Archive last, set apart so it doesn't read as one of the folders.
    HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 16.dp))
    item(SavedTab.Archive, stringResource(R.string.reading_list_archive), R.drawable.ic_inventory)
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
