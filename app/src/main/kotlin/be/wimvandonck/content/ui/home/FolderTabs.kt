package be.wimvandonck.content.ui.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import be.wimvandonck.content.R
import be.wimvandonck.content.data.Folder
import be.wimvandonck.content.data.SavedFilter
import be.wimvandonck.content.data.SavedTab

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
 * Saved's header under the top app bar (#119): To read, All, each folder and Archive as scrollable
 * tabs, then Liked (a flag, set apart by its heart and a divider) and the tab's tags as chips that
 * filter together.
 */
@Composable
fun SavedTabRow(
    filter: SavedFilter,
    folders: List<String>,
    tags: List<String>,
    onChange: (SavedFilter) -> Unit,
    modifier: Modifier = Modifier,
    /** False under the home tabs (#183), which choose To read or the Archive: only the tag chips. */
    showTabs: Boolean = true,
) {
    val tabs = listOf<SavedTab>(SavedTab.ToRead) + folders.map(SavedTab::Folder) + SavedTab.Archive
    fun same(
        a: SavedTab,
        b: SavedTab,
    ) = if (a is SavedTab.Folder && b is SavedTab.Folder) a.name.equals(b.name, ignoreCase = true) else a == b
    Column(modifier) {
        if (showTabs) PrimaryScrollableTabRow(
            selectedTabIndex = tabs.indexOfFirst { same(it, filter.tab) }.coerceAtLeast(0),
            edgePadding = 16.dp,
            containerColor = Color.Transparent,
        ) {
            tabs.forEach { tab ->
                LeadingIconTab(
                    selected = same(tab, filter.tab),
                    onClick = { if (!same(tab, filter.tab)) onChange(filter.copy(tab = tab, tags = emptySet())) },
                    text = {
                        Text(
                            when (tab) {
                                SavedTab.ToRead -> stringResource(R.string.reading_list_to_read)
                                SavedTab.All -> stringResource(R.string.filter_all)
                                SavedTab.Archive -> stringResource(R.string.reading_list_archive)
                                SavedTab.Shared -> stringResource(R.string.saved_shared)
                                is SavedTab.Folder -> tab.name
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 160.dp),
                        )
                    },
                    icon = {
                        Icon(
                            painterResource(
                                when (tab) {
                                    SavedTab.ToRead -> R.drawable.ic_reading_list
                                    SavedTab.All -> R.drawable.ic_bookmarks_outline
                                    SavedTab.Archive -> R.drawable.ic_inventory
                                    SavedTab.Shared -> R.drawable.ic_group
                                    is SavedTab.Folder -> R.drawable.ic_folder
                                },
                            ),
                            contentDescription = null,
                        )
                    },
                )
            }
        }
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tags.forEach { tag ->
                val selected = filter.tags.any { it.equals(tag, ignoreCase = true) }
                FilterChip(
                    selected = selected,
                    onClick = {
                        val others = filter.tags.filterNot { it.equals(tag, ignoreCase = true) }.toSet()
                        onChange(filter.copy(tags = if (selected) others else others + tag))
                    },
                    label = { Text(tag) },
                    leadingIcon =
                        if (selected) {
                            { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                        } else {
                            null
                        },
                )
            }
        }
    }
}
