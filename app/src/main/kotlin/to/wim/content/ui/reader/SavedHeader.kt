package to.wim.content.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import to.wim.content.R
import to.wim.content.ui.home.rememberFolderIconPainter

/**
 * What a saved article carries, under its title and details (2026-11-01, instead of toolbar
 * buttons): they describe the article, so they sit with it. One row of chips: its tags as input
 * chips (Material 3: the user's own tags), each with its tag's icon; **+ Tag** (an assist chip: an
 * action on this content) to change them; **+ Note** while there is no note; and, with highlights,
 * the quote mark and how many, which opens their list. A note shows below as a filled card with its
 * text; tapping it edits it. Every chip opens what it names.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SavedHeader(
    item: ReaderItem,
    highlightCount: Int,
    onHighlights: () -> Unit,
) {
    val note = item.item.note.trim()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item.item.tags.forEach { tag ->
                InputChip(
                    selected = false,
                    onClick = item.onTopics,
                    label = { Text(tag) },
                    leadingIcon =
                        item.tagIcons[tag.trim().lowercase()]?.let { icon ->
                            { Icon(rememberFolderIconPainter(icon), contentDescription = null, modifier = Modifier.size(InputChipDefaults.IconSize)) }
                        },
                )
            }
            AddChip(stringResource(R.string.feed_folder), onClick = item.onTopics)
            if (note.isEmpty()) AddChip(stringResource(R.string.note_title), onClick = item.onNote)
            if (highlightCount > 0) {
                AssistChip(
                    onClick = onHighlights,
                    label = { Text(pluralStringResource(R.plurals.highlights_count, highlightCount, highlightCount)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_quote), contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
                )
            }
        }
        if (note.isNotEmpty()) {
            val editLabel = stringResource(R.string.note_title)
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().clickable(onClickLabel = editLabel, role = Role.Button, onClick = item.onNote),
            ) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/** An assist chip with a plus: adds what it names (a tag, a note). */
@Composable
private fun AddChip(
    label: String,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
    )
}
