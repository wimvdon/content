package be.wimvandonck.reader.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import be.wimvandonck.reader.R
import be.wimvandonck.reader.data.BookmarkKind
import be.wimvandonck.reader.data.Bookmarks
import be.wimvandonck.reader.data.Feed
import be.wimvandonck.reader.data.Item
import be.wimvandonck.reader.ui.reader.BookmarkEditSheet
import be.wimvandonck.reader.ui.theme.AppShapes
import be.wimvandonck.reader.ui.theme.readableWidth
import coil3.compose.AsyncImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What the bookmark screen's buttons do. */
class BookmarkActions(
    val onBack: () -> Unit,
    /** Opens the link in the browser tab. */
    val onOpen: () -> Unit,
    /** Back on the reading list and open in the reader. */
    val onReadAgain: () -> Unit,
    val onShare: () -> Unit,
    val onEdit: (collection: String, note: String, tags: List<String>) -> Unit,
    val onRemove: () -> Unit,
)

/**
 * One bookmark (#108): its picture, site and title, when it was kept and where from, Open and Read
 * again, your note, its collection and tags, and its highlights with their notes.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookmarkScreen(
    item: Item?,
    source: Feed?,
    /** The feed it was kept from, if any. */
    fromFeed: String?,
    collections: List<String>,
    allTags: List<String>,
    showImages: Boolean,
    snackbarHostState: SnackbarHostState,
    actions: BookmarkActions,
) {
    var editOpen by rememberSaveable { mutableStateOf(false) }
    var removeOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (item != null) {
                        IconButton(onClick = { editOpen = true }) {
                            Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.bookmark_edit))
                        }
                        IconButton(onClick = actions.onShare) {
                            Icon(painterResource(R.drawable.ic_share), contentDescription = stringResource(R.string.action_share))
                        }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.bookmark_remove)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    removeOpen = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (item == null) {
            Text(
                text = stringResource(R.string.reader_article_gone),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(innerPadding).padding(24.dp),
            )
            return@Scaffold
        }
        val kind = remember(item.url) { Bookmarks.kindOf(item.url) }
        Column(
            Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .readableWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val image = item.imageUrl?.takeIf { showImages }
            if (image != null) {
                var failed by remember(image) { mutableStateOf(false) }
                if (!failed) {
                    AsyncImage(
                        model = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        onError = { failed = true },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(AppShapes.Item)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (source != null) FeedIcon(source, FeedIconSize.Small)
                    Text(item.host, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    if (kind != BookmarkKind.Article) {
                        Text("· " + stringResource(kind.singleRes()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(item.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                val kept = item.bookmark?.addedAt ?: item.createdAt
                val date = remember(kept) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(Instant.ofEpochMilli(kept).atZone(ZoneId.systemDefault())) }
                Text(
                    listOfNotNull(stringResource(R.string.bookmark_kept_on, date), fromFeed?.let { stringResource(R.string.bookmark_from, it) }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = actions.onOpen, modifier = Modifier.weight(1f)) {
                    Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.action_open), modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = actions.onReadAgain, modifier = Modifier.weight(1f)) {
                    Icon(painterResource(R.drawable.ic_reading_list), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.action_read_again), modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (item.description.isNotBlank() && kind != BookmarkKind.Article) {
                Text(item.description, style = MaterialTheme.typography.bodyMedium)
            }
            if (item.note.isNotBlank()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, AppShapes.Item)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(stringResource(R.string.note_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(item.note, style = MaterialTheme.typography.bodyMedium)
                }
            }
            val collection = item.bookmark?.collection
            if (collection != null || item.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (collection != null) {
                        AssistChip(
                            onClick = { editOpen = true },
                            label = { Text(collection) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_folder), contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                    }
                    item.tags.forEach { tag ->
                        AssistChip(
                            onClick = { editOpen = true },
                            label = { Text(tag) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_label), contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                    }
                }
            }
            if (item.highlights.isNotEmpty()) {
                Text(
                    stringResource(R.string.highlights_title) + " · " + item.highlights.size,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() },
                )
                item.highlights.sortedBy { it.createdAt }.forEach { highlight ->
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
                        )
                        Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(highlight.quote, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
                            if (highlight.note.isNotBlank()) {
                                Text(highlight.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (item != null && editOpen) {
        BookmarkEditSheet(item, collections, allTags, onSave = actions.onEdit, onDismiss = { editOpen = false })
    }
    if (removeOpen) {
        AlertDialog(
            onDismissRequest = { removeOpen = false },
            title = { Text(stringResource(R.string.bookmark_remove)) },
            text = { Text(stringResource(R.string.bookmark_remove_body)) },
            confirmButton = {
                TextButton(onClick = {
                    removeOpen = false
                    actions.onRemove()
                }) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = { TextButton(onClick = { removeOpen = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
