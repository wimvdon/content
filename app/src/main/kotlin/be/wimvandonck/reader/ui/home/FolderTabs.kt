package be.wimvandonck.reader.ui.home

import androidx.compose.foundation.layout.widthIn
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
