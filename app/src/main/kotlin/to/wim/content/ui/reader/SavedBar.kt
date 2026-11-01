package to.wim.content.ui.reader

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import to.wim.content.R

/**
 * The reader's toolbar buttons for a saved article (2026-11-01, instead of Archive and Organise):
 * every action one tap away. First what moves it, in words: **Archive** while it is to read, a tonal
 * button like Save on a feed article (the next step), or **To read** once archived, a plain one (it
 * puts it back). Then icons: **Topics** opens the topic chips, **Note** the note, **Like** is a
 * toggle (a filled heart while liked). The highlights' count and Share (its own round button) follow
 * ([ReaderToolbar]); Remove is in the reader's ⋮, kept rare. Long-pressing an icon shows its name.
 */
@Composable
internal fun SavedBar(item: ReaderItem) {
    if (item.item.archived) {
        TextButton(onClick = item.onArchive, colors = toolbarTextButtonColors()) {
            Icon(painterResource(R.drawable.ic_reading_list), contentDescription = null)
            Text(stringResource(R.string.reading_list_to_read), modifier = Modifier.padding(start = 8.dp), maxLines = 1)
        }
    } else {
        FilledTonalButton(onClick = item.onArchive) {
            Icon(painterResource(R.drawable.ic_inventory), contentDescription = null)
            Text(stringResource(R.string.action_archive), modifier = Modifier.padding(start = 8.dp), maxLines = 1)
        }
    }
    val topicsLabel = stringResource(R.string.tags_title)
    Tipped(topicsLabel) {
        IconButton(onClick = item.onTopics) {
            Icon(painterResource(R.drawable.ic_label), contentDescription = topicsLabel)
        }
    }
    val noteLabel = stringResource(R.string.note_title)
    Tipped(noteLabel) {
        IconButton(onClick = item.onNote) {
            // A note written: the note with a pen, so it shows there is one.
            Icon(painterResource(if (item.item.note.isBlank()) R.drawable.ic_note else R.drawable.ic_edit_note), contentDescription = noteLabel)
        }
    }
    val liked = item.item.liked
    val likeLabel = stringResource(R.string.action_like)
    Tipped(likeLabel) {
        IconToggleButton(checked = liked, onCheckedChange = { item.onLike() }) {
            Icon(painterResource(if (liked) R.drawable.ic_favorite_filled else R.drawable.ic_favorite), contentDescription = likeLabel)
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
