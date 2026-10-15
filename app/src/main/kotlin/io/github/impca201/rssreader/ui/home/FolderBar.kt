package io.github.impca201.rssreader.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.impca201.rssreader.R
import io.github.impca201.rssreader.data.Folder

/**
 * The floating bar at the bottom, shown once there are folders: All (every feed) and then each
 * folder. The same pill as Tracker for Simkl's navigation bar: the selected tab expands to show
 * its name next to its icon; the others show their icon only.
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
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier = modifier,
    ) {
        Row(
            modifier =
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(8.dp)
                    .height(TAB_HEIGHT)
                    .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
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
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "tabBackground",
    )
    val content by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "tabContent",
    )
    Row(
        Modifier
            .clip(CircleShape)
            .background(background)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .height(TAB_HEIGHT)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The selected tab shows its name, which TalkBack reads; don't read it twice.
        Icon(icon, contentDescription = if (selected) null else label, tint = content, modifier = Modifier.size(24.dp))
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(spring(stiffness = Spring.StiffnessMedium)),
            exit = shrinkHorizontally(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) + fadeOut(spring(stiffness = Spring.StiffnessMedium)),
        ) {
            Row {
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = MAX_LABEL_WIDTH),
                )
            }
        }
    }
}

private val TAB_HEIGHT = 52.dp
private val MAX_LABEL_WIDTH = 160.dp
