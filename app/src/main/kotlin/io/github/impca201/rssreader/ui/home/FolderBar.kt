package io.github.impca201.rssreader.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.impca201.rssreader.R
import io.github.impca201.rssreader.data.Folder
import io.github.impca201.rssreader.ui.theme.Motion

/**
 * The floating bar at the bottom, shown once there are folders: All (every feed) and then each
 * folder, as icon and name like Material's navigation bar. Every tab keeps its size, so switching
 * only moves the highlight and nothing shifts.
 */
@Composable
fun FolderBar(
    folders: List<Folder>,
    selectedFolderId: String?,
    onSelectAll: () -> Unit,
    onSelectFolder: (Folder) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        tonalElevation = 3.dp,
        modifier = modifier,
    ) {
        Row(
            modifier =
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .selectableGroup(),
        ) {
            Tab(
                icon = painterResource(R.drawable.ic_rss_feed),
                label = stringResource(R.string.folder_all),
                selected = selectedFolderId == null,
                onClick = onSelectAll,
            )
            folders.forEach { folder ->
                Tab(
                    icon = rememberFolderIconPainter(folder.icon),
                    label = folder.title,
                    selected = selectedFolderId == folder.id,
                    onClick = { onSelectFolder(folder) },
                )
            }
        }
    }
}

@Composable
private fun Tab(
    icon: Painter,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val indicator by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        Motion.fadeSpec(),
        label = "indicator",
    )
    val contentColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        Motion.fadeSpec(),
        label = "content",
    )
    Column(
        modifier =
            Modifier
                .width(TAB_WIDTH)
                .clip(MaterialTheme.shapes.large)
                .selectable(selected = selected, role = Role.Tab, onClick = onClick)
                .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(width = 56.dp, height = 32.dp)
                .background(indicator, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = contentColor)
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val TAB_WIDTH = 72.dp
