package to.wim.content.ui.reader

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import to.wim.content.R

/**
 * The reader's toolbar buttons for a saved article (2026-11-02), none with a fill: first what moves
 * it. While it is to read, **Archive** with its name (the next step, so it has words); once archived,
 * only the unarchive icon (named To read), as it is rarer. Then **Like** (a filled heart while
 * liked) and **Tags** as icons; Share follows ([ReaderToolbar]). The note and the highlights sit
 * under the article's title ([SavedHeader]); Remove is in the reader's ⋮. Long-pressing an icon
 * shows its name.
 */
@Composable
internal fun SavedBar(item: ReaderItem) {
    if (item.item.archived) {
        val toReadLabel = stringResource(R.string.reading_list_to_read)
        Tipped(toReadLabel) {
            IconButton(onClick = item.onArchive) {
                Icon(painterResource(R.drawable.ic_unarchive), contentDescription = toReadLabel)
            }
        }
    } else {
        TextButton(onClick = item.onArchive, colors = toolbarTextButtonColors()) {
            Icon(painterResource(R.drawable.ic_inventory), contentDescription = null)
            Text(stringResource(R.string.action_archive), modifier = Modifier.padding(start = 8.dp), maxLines = 1)
        }
    }
    val liked = item.item.liked
    val likeLabel = stringResource(R.string.action_like)
    Tipped(likeLabel) {
        IconToggleButton(checked = liked, onCheckedChange = { item.onLike() }) {
            Icon(painterResource(if (liked) R.drawable.ic_favorite_filled else R.drawable.ic_favorite), contentDescription = likeLabel)
        }
    }
    val tagsLabel = stringResource(R.string.tags_title)
    Tipped(tagsLabel) {
        IconButton(onClick = item.onTopics) {
            Icon(painterResource(R.drawable.ic_label), contentDescription = tagsLabel)
        }
    }
}

/** An icon button with its name in a tooltip on a long press, as Material's icon buttons have it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Tipped(
    label: String,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        content = content,
    )
}
