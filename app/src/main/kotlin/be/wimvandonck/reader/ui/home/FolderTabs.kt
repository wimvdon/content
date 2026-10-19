package be.wimvandonck.reader.ui.home

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import be.wimvandonck.reader.R
import be.wimvandonck.reader.data.Folder
import be.wimvandonck.reader.data.ReadingListFilter
import be.wimvandonck.reader.data.ReadingListTab

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
 * The reading list's header under the top app bar (#107): To read and Archive as tabs, then one row
 * of filter chips: Liked, and the list's tags (tags come and go too often for a tab each).
 */
@Composable
fun ReadingListTabRow(
    filter: ReadingListFilter,
    tags: List<String>,
    onChange: (ReadingListFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf(ReadingListTab.ToRead to (R.string.reading_list_to_read to R.drawable.ic_reading_list), ReadingListTab.Archive to (R.string.reading_list_archive to R.drawable.ic_inventory))
    Column(modifier) {
        PrimaryScrollableTabRow(
            selectedTabIndex = tabs.indexOfFirst { it.first == filter.tab }.coerceAtLeast(0),
            edgePadding = 16.dp,
            containerColor = Color.Transparent,
        ) {
            tabs.forEach { (tab, res) ->
                LeadingIconTab(
                    selected = tab == filter.tab,
                    onClick = { if (tab != filter.tab) onChange(ReadingListFilter(tab, filter.likedOnly)) },
                    text = { Text(stringResource(res.first), maxLines = 1) },
                    icon = { Icon(painterResource(res.second), contentDescription = null) },
                )
            }
        }
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = filter.likedOnly,
                onClick = { onChange(filter.copy(likedOnly = !filter.likedOnly)) },
                label = { Text(stringResource(R.string.reading_list_liked)) },
                leadingIcon = {
                    Icon(
                        painterResource(if (filter.likedOnly) R.drawable.ic_favorite_filled else R.drawable.ic_favorite),
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
            )
            tags.forEach { tag ->
                val selected = filter.tag.equals(tag, ignoreCase = true)
                FilterChip(
                    selected = selected,
                    onClick = { onChange(filter.copy(tag = if (selected) null else tag)) },
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
