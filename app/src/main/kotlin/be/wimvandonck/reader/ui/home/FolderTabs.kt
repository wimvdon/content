package be.wimvandonck.reader.ui.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import be.wimvandonck.reader.R
import be.wimvandonck.reader.data.Folder
import be.wimvandonck.reader.instapaper.InstapaperFolder
import be.wimvandonck.reader.instapaper.InstapaperTab
import be.wimvandonck.reader.instapaper.InstapaperTabs
import be.wimvandonck.reader.instapaper.InstapaperTag
import be.wimvandonck.reader.instapaper.InstapaperView

/**
 * Scrollable tabs under the top app bar, shown once there are folders: All (every feed) and then
 * each folder. Folders are peer views of the same article list, so Material 3 uses tabs here; a
 * navigation bar is for the app's top-level sections.
 */
@Composable
fun FolderTabs(
    folders: List<Folder>,
    selectedFolderId: String?,
    onSelectAll: () -> Unit,
    onSelectFolder: (Folder) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = folders.indexOfFirst { it.id == selectedFolderId } + 1
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = 16.dp,
        // The header behind it gives the colour.
        containerColor = Color.Transparent,
        modifier = modifier,
    ) {
        LeadingIconTab(
            selected = selectedIndex == 0,
            onClick = onSelectAll,
            text = { Text(stringResource(R.string.folder_all), maxLines = 1) },
            icon = { Icon(painterResource(R.drawable.ic_rss_feed), contentDescription = null) },
        )
        folders.forEach { folder ->
            LeadingIconTab(
                selected = folder.id == selectedFolderId,
                onClick = { onSelectFolder(folder) },
                text = {
                    Text(
                        folder.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = MAX_LABEL_WIDTH),
                    )
                },
                icon = { Icon(rememberFolderIconPainter(folder.icon), contentDescription = null) },
            )
        }
    }
}

private val MAX_LABEL_WIDTH = 160.dp

/**
 * Scrollable tabs under the top app bar on Saved, like the folder tabs on Feeds: Saved, Liked,
 * each Instapaper folder, Tags, then Archive. Under Tags, a row of filter chips picks the tag.
 */
@Composable
fun InstapaperTabRow(
    folders: List<InstapaperFolder>,
    tags: List<InstapaperTag>,
    view: InstapaperView,
    onSelect: (InstapaperView) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = InstapaperTabs.of(folders, tags)
    val current = InstapaperTabs.tabOf(view)
    Column(modifier) {
        PrimaryScrollableTabRow(
            selectedTabIndex = tabs.indexOf(current).coerceAtLeast(0),
            edgePadding = 16.dp,
            containerColor = Color.Transparent,
        ) {
            tabs.forEach { tab ->
                val (label, icon) =
                    when (tab) {
                        InstapaperTab.Unread -> stringResource(R.string.saved_list) to R.drawable.ic_article
                        InstapaperTab.Liked -> stringResource(R.string.instapaper_liked) to R.drawable.ic_favorite
                        InstapaperTab.Archive -> stringResource(R.string.instapaper_archive_list) to R.drawable.ic_archive
                        is InstapaperTab.Folder -> folders.firstOrNull { it.id == tab.folderId }?.title.orEmpty() to R.drawable.ic_folder
                        InstapaperTab.Tags -> stringResource(R.string.instapaper_tags) to R.drawable.ic_label
                    }
                LeadingIconTab(
                    selected = tab == current,
                    onClick = { if (tab != current) onSelect(InstapaperTabs.viewOf(tab, tags, view)) },
                    text = {
                        Text(
                            label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = MAX_LABEL_WIDTH),
                        )
                    },
                    icon = { Icon(painterResource(icon), contentDescription = null) },
                )
            }
        }
        if (view is InstapaperView.WithTag) {
            // Chips filter the Tags tab to one tag.
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tags.forEach { tag ->
                    FilterChip(
                        selected = tag.name == view.name,
                        onClick = { if (tag.name != view.name) onSelect(InstapaperView.WithTag(tag.name)) },
                        label = { Text(tag.name) },
                    )
                }
            }
        }
    }
}
