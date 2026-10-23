package be.wimvandonck.content.ui.home

import be.wimvandonck.content.data.ReadingState
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import be.wimvandonck.content.R
import be.wimvandonck.content.data.CardBody
import be.wimvandonck.content.data.Feed
import be.wimvandonck.content.data.Item
import be.wimvandonck.content.data.Library
import be.wimvandonck.content.data.NoteContent
import be.wimvandonck.content.data.ListView
import be.wimvandonck.content.data.ReadingLists
import be.wimvandonck.content.data.Saved
import be.wimvandonck.content.data.SavedFilter
import be.wimvandonck.content.data.SavedTab
import be.wimvandonck.content.ui.theme.EmptyState
import be.wimvandonck.content.ui.theme.readableWidth
import coil3.compose.AsyncImage

/** Saved as the home screen shows it (#107, #119). */
data class SavedHome(
    val filter: SavedFilter,
    val items: List<Item>,
    /** Every folder, for the tabs. */
    val folders: List<String>,
    /** Every tag in Saved, most used first, for the chips (whichever state is shown). */
    val tags: List<String>,
    /** About how many minutes each item takes to read, by item key, when its text is known. */
    val minutes: Map<String, Int>,
    /** So items show where they came from, like feed articles. */
    val library: Library,
    /** Titles of notes in the reader's font. */
    val titleFont: FontFamily,
    /** The feed list's view (Small, Medium, Large), which To read shares. */
    val listView: ListView = ListView.Medium,
)

/**
 * Saved (#119): To read looks like the feeds, as it is a list to read through (with how many and
 * about how long on top); a folder and the Archive are collections, so their items are notes in a
 * staggered grid, as in Noter: picture, title, your note or the description, then site, tags and
 * highlights (Saved.showsNotes).
 */
@Composable
internal fun SavedContent(
    home: SavedHome,
    showImages: Boolean,
    onOpen: (Item) -> Unit,
    onArchive: (Item) -> Unit = {},
    onFile: (Item) -> Unit = {},
    /** Long-press › Share (#221). */
    onShare: (Item) -> Unit = {},
    /** Long-press in Shared with me › To read or Archive (#167). */
    onPlace: (Item, ReadingState) -> Unit = { _, _ -> },
    /** Clears Liked and the tags, for an empty list they filtered out. */
    onClearFilters: () -> Unit = {},
) {
    if (home.items.isEmpty()) {
        val plain = !home.filter.likedOnly && home.filter.tags.isEmpty()
        val toRead = home.filter.tab == SavedTab.ToRead && plain
        EmptyState(
            painterResource(
                when (home.filter.tab) {
                    SavedTab.Archive -> R.drawable.ic_inventory
                    is SavedTab.Folder -> R.drawable.ic_folder
                    else -> R.drawable.ic_reading_list
                },
            ),
            stringResource(
                when {
                    toRead -> R.string.reading_list_empty_title
                    home.filter.tab == SavedTab.Archive && plain -> R.string.reading_list_archive_empty_title
                    else -> R.string.reading_list_none_title
                },
            ),
            body = if (toRead) stringResource(R.string.saved_empty_body) else null,
        ) {
            // Filtered to nothing: one tap back to the whole tab, as Feeds' "All caught up" has Show all.
            if (!plain) Button(onClick = onClearFilters) { Text(stringResource(R.string.home_show_all)) }
        }
        return
    }
    if (Saved.showsNotes(home.filter.tab)) NotesGrid(home, showImages, onOpen, onFile, onShare, onPlace) else ArticleList(home, showImages, onOpen, onArchive, onFile)
}

/**
 * To read: the feed list's rows, newest saved first. Swipe left to archive, right to organise it
 * (#143).
 */
@Composable
private fun ArticleList(
    home: SavedHome,
    showImages: Boolean,
    onOpen: (Item) -> Unit,
    onArchive: (Item) -> Unit,
    onFile: (Item) -> Unit,
) {
    val archiveLabel = stringResource(R.string.action_archive)
    val unarchiveLabel = stringResource(R.string.action_unarchive)
    val organiseLabel = stringResource(R.string.organise_title)
    // Another list starts at the top.
    val listState = rememberSaveable(home.filter, saver = LazyListState.Saver) { LazyListState() }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().readableWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "summary") { Summary(home, Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)) }
        items(home.items, key = { it.key }) { item ->
            val article = remember(item, home.library) { ReadingLists.article(item, home.library) }
            val source = remember(item, home.library) { ReadingLists.source(item, home.library) }
            SwipeRow(
                right = SwipeAction(R.drawable.ic_folder, organiseLabel) { onFile(item) },
                left =
                    if (item.archived) {
                        SwipeAction(R.drawable.ic_unarchive, unarchiveLabel) { onArchive(item) }
                    } else {
                        SwipeAction(R.drawable.ic_inventory, archiveLabel) { onArchive(item) }
                    },
                modifier = Modifier.animateItem(),
            ) {
                ArticleRow(
                    article = article,
                    feed = source,
                    view = home.listView,
                    showImage = showImages,
                    showReadState = false,
                    onClick = { onOpen(item) },
                )
            }
        }
    }
}

/** How many articles, and about how long they take. */
@Composable
private fun Summary(
    home: SavedHome,
    modifier: Modifier = Modifier,
) {
    val total = home.items.sumOf { ReadingLists.minutesLeft(home.minutes[it.key] ?: 0, it.progress) }
    Text(
        text =
            pluralStringResource(R.plurals.reading_list_count, home.items.size, home.items.size) +
                if (total > 0) " · " + stringResource(R.string.reading_list_about, duration(total)) else "",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** A folder or the Archive: its items as notes, two columns on a phone. */
@Composable
private fun NotesGrid(
    home: SavedHome,
    showImages: Boolean,
    onOpen: (Item) -> Unit,
    onFile: (Item) -> Unit,
    onShare: (Item) -> Unit,
    onPlace: (Item, ReadingState) -> Unit,
) {
    val organiseLabel = stringResource(R.string.organise_title)
    val shareLabel = stringResource(R.string.action_share)
    val toReadLabel = stringResource(R.string.saved_move_to_read)
    val archiveLabel = stringResource(R.string.action_archive)
    val gridState = rememberSaveable(home.filter, saver = LazyStaggeredGridState.Saver) { LazyStaggeredGridState() }
    LazyVerticalStaggeredGrid(
        state = gridState,
        columns = StaggeredGridCells.Adaptive(160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
        verticalItemSpacing = 8.dp,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "summary", span = StaggeredGridItemSpan.FullLine) { Summary(home, Modifier.padding(start = 4.dp, bottom = 4.dp)) }
        items(home.items, key = { it.key }) { item ->
            val source = remember(item, home.library) { ReadingLists.source(item, home.library) }
            val note = remember(item) { Saved.note(item) }
            NoteCard(
                note,
                source,
                home.titleFont,
                showImages,
                onClick = { onOpen(item) },
                modifier = Modifier.animateItem(),
                // Long-press (#221): Organise (tags, note) and Share. No swipes on cards.
                menu =
                    listOfNotNull(
                        // Shared with me (#167): put it in To read or the Archive.
                        if (item.waitingShared) RowMenuAction(R.drawable.ic_reading_list, toReadLabel) { onPlace(item, ReadingState.ToRead) } else null,
                        if (item.waitingShared) RowMenuAction(R.drawable.ic_inventory, archiveLabel) { onPlace(item, ReadingState.Archived) } else null,
                        RowMenuAction(R.drawable.ic_label, organiseLabel) { onFile(item) },
                        RowMenuAction(R.drawable.ic_share, shareLabel) { onShare(item) },
                    ),
            )
        }
    }
}

/**
 * A note card (Noter's cards, after Google Keep): an outlined card with the picture on top, the
 * title, for a saved item your note (else the first highlight, quoted, else the description), for a
 * feed article the start of its text, then the site, the tags and the number of highlights. [label]
 * says what kind it is in Search (Feed or Saved).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NoteCard(
    note: NoteContent,
    source: Feed?,
    titleFont: FontFamily,
    showImage: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    /** What a long press offers (#221). */
    menu: List<RowMenuAction> = emptyList(),
) {
    var menuOpen by remember { mutableStateOf(false) }
    OutlinedCard(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(CardDefaults.outlinedShape)
                .longPressMenu(menu, onClick) { menuOpen = true },
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        RowMenu(menu, menuOpen) { menuOpen = false }
        Column {
            val image = note.imageUrl?.takeIf { showImage }
            if (image != null) {
                var failed by remember(image) { mutableStateOf(false) }
                if (!failed) {
                    AsyncImage(
                        model = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        onError = { failed = true },
                        modifier = Modifier.fillMaxWidth().height(96.dp),
                    )
                }
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (label != null) Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    note.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = titleFont, fontWeight = FontWeight.Medium),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                // The text, kept short: the first highlight, quoted, or the site's description.
                when (val body = note.body) {
                    is CardBody.Quote ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(painterResource(R.drawable.ic_quote), contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                body.text,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = titleFont),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    null -> Unit
                    else ->
                        Text(
                            body.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                }
                // Your note, small: a glimpse, the whole of it is in the article.
                if (note.note.isNotEmpty()) {
                    val noteTitle = stringResource(R.string.note_title)
                    Row(
                        Modifier.semantics(mergeDescendants = true) { contentDescription = "$noteTitle: ${note.note}" },
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_note), contentDescription = null, modifier = Modifier.size(14.dp).padding(top = 1.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            note.note,
                            style = MaterialTheme.typography.labelMedium.copy(fontStyle = FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // The tags as small chips (#208), not as #words.
                if (note.tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (tag in note.tags) {
                            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                                Text(
                                    tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
                // Who shared it (#167).
                note.sharedBy?.let { by ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(painterResource(R.drawable.ic_group), contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.share_user_from, by.label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (by.message.isNotBlank()) {
                        Text(by.message, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (source != null) FeedIcon(source, FeedIconSize.Small)
                        Text(note.host, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    // How many highlights (your markings and their notes).
                    if (note.highlights > 0) {
                        val description = pluralStringResource(R.plurals.highlights_count, note.highlights, note.highlights)
                        Row(Modifier.semantics(mergeDescendants = true) { contentDescription = description }, verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_highlight), contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(note.highlights.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (note.liked) {
                        Icon(
                            painterResource(R.drawable.ic_favorite_filled),
                            contentDescription = stringResource(R.string.reading_list_liked),
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/** "2 h 40 min" or "12 min". */
@Composable
private fun duration(minutes: Int): String =
    if (minutes >= 60) stringResource(R.string.duration_hours, minutes / 60, minutes % 60) else stringResource(R.string.duration_minutes, minutes)
