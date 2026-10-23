package be.wimvandonck.content.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import be.wimvandonck.content.R
import be.wimvandonck.content.data.Anchor
import be.wimvandonck.content.data.Highlight

/**
 * An article's highlights in the reader (#102): what there is, how each was found in the text shown,
 * and what changing them does. Built by the navigation.
 */
class ReaderHighlights(
    val highlights: List<Highlight>,
    val onAdd: (Highlight) -> Unit,
    val onNote: (id: String, note: String) -> Unit,
    val onRemove: (id: String) -> Unit,
    /** The ones Instapaper refused (#164): "Not in Instapaper", tried again a day later. */
    val notCopied: Set<String> = emptySet(),
)

/** One highlight: its passage, how it was found, its note to edit, and Remove. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightSheet(
    highlight: Highlight,
    anchor: Anchor?,
    onSave: (note: String) -> Unit,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var note by rememberSaveable(highlight.id) { mutableStateOf(highlight.note) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.highlight_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Quote(highlight.quote, maxLines = 8)
            Status(anchor)
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.note_title)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (onRemove != null) {
                    TextButton(onClick = {
                        onRemove()
                        onDismiss()
                    }) { Text(stringResource(R.string.highlight_remove)) }
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    Button(onClick = {
                        onSave(note)
                        onDismiss()
                    }) { Text(stringResource(R.string.action_save)) }
                }
            }
        }
    }
}

/** Every highlight of the article, in the order they were made; tap one to see or change it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightsSheet(
    highlights: List<Highlight>,
    anchors: Map<String, Anchor>,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
    notCopied: Set<String> = emptySet(),
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.highlights_title) + " · " + highlights.size,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            if (highlights.isEmpty()) {
                Text(stringResource(R.string.highlights_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            for (h in highlights.sortedBy { it.createdAt }) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onOpen(h.id) }
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Quote(h.quote, maxLines = 4)
                    if (h.note.isNotBlank()) {
                        Text(h.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 15.dp))
                    }
                    Box(Modifier.padding(start = 15.dp)) { Status(anchors[h.id]) }
                    if (h.id in notCopied) {
                        Text(
                            stringResource(R.string.highlight_not_in_instapaper),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 15.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Quote(
    text: String,
    maxLines: Int,
) {
    Row(Modifier.height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/** Approximate or not found in this text; nothing when all is well. */
@Composable
private fun Status(anchor: Anchor?) {
    val line =
        when (anchor) {
            is Anchor.Approximate -> stringResource(R.string.highlight_approximate)
            Anchor.NotFound -> stringResource(R.string.highlight_not_found)
            else -> null
        }
    if (line != null) Text(line, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
