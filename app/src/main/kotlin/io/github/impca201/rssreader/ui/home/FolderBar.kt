package io.github.impca201.rssreader.ui.home

import androidx.annotation.DrawableRes
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.impca201.rssreader.R
import io.github.impca201.rssreader.data.Folder
import io.github.impca201.rssreader.data.FolderIcon
import io.github.impca201.rssreader.ui.theme.Motion

/**
 * The floating bar at the bottom, shown once there are folders: Home (every feed) and then each
 * folder, as icon and name like Material's navigation bar. Every tab keeps its size, so switching
 * only moves the highlight and nothing shifts.
 */
@Composable
fun FolderBar(
    folders: List<Folder>,
    selectedFolderId: String?,
    onSelectHome: () -> Unit,
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
                icon = R.drawable.ic_home,
                label = stringResource(R.string.folder_home),
                selected = selectedFolderId == null,
                onClick = onSelectHome,
            )
            folders.forEach { folder ->
                Tab(
                    icon = folder.icon.drawableRes(),
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
    @DrawableRes icon: Int,
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
            Icon(painterResource(icon), contentDescription = null, tint = contentColor)
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

@DrawableRes
fun FolderIcon.drawableRes(): Int =
    when (this) {
        FolderIcon.Folder -> R.drawable.ic_folder
        FolderIcon.Star -> R.drawable.ic_star
        FolderIcon.Heart -> R.drawable.ic_heart
        FolderIcon.News -> R.drawable.ic_news
        FolderIcon.Tech -> R.drawable.ic_tech
        FolderIcon.Code -> R.drawable.ic_code
        FolderIcon.Science -> R.drawable.ic_science
        FolderIcon.Work -> R.drawable.ic_work
        FolderIcon.Money -> R.drawable.ic_money
        FolderIcon.Sports -> R.drawable.ic_sports
        FolderIcon.Games -> R.drawable.ic_games
        FolderIcon.Music -> R.drawable.ic_music
        FolderIcon.Movies -> R.drawable.ic_movies
        FolderIcon.Books -> R.drawable.ic_books
        FolderIcon.Photos -> R.drawable.ic_photos
        FolderIcon.Food -> R.drawable.ic_food
        FolderIcon.Travel -> R.drawable.ic_travel
        FolderIcon.Nature -> R.drawable.ic_nature
        FolderIcon.Home -> R.drawable.ic_home
    }
