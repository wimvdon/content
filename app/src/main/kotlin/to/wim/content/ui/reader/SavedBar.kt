package to.wim.content.ui.reader

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import to.wim.content.R

/**
 * The reader's toolbar buttons for a saved article (#139, #251, #305, #331): two buttons that say in
 * words what they do. **Archive** (the Archive's box) archives it in one tap, keeps the article open
 * and shows Undo; an archived article has only **Organise** (a tag), which opens the sheet with To
 * read / Archive, the topics, Like and the note. The highlights and Share follow in the floating
 * toolbar ([ReaderToolbar]).
 */
@Composable
internal fun SavedBar(item: ReaderItem) {
    // Archive is the quick action while it is to read; archived, there is only Organise (its To read
    // / Archive choice puts it back).
    val archived = item.item.archived
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!archived) {
            BarButton(
                icon = R.drawable.ic_inventory,
                label = stringResource(R.string.action_archive),
                onClick = item.onArchive,
            )
        }
        BarButton(
            icon = R.drawable.ic_label,
            label = stringResource(R.string.organise_title),
            onClick = item.onOrganise,
        )
    }
}

@Composable
private fun BarButton(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(onClick = onClick, modifier = modifier, colors = toolbarTextButtonColors()) {
        Icon(painterResource(icon), contentDescription = null)
        Text(label, modifier = Modifier.padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
