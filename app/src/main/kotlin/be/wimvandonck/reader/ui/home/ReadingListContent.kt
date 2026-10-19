package be.wimvandonck.reader.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import be.wimvandonck.reader.R
import be.wimvandonck.reader.data.Feed
import be.wimvandonck.reader.data.Item
import be.wimvandonck.reader.data.Library
import be.wimvandonck.reader.data.ReadingListFilter
import be.wimvandonck.reader.data.ReadingListTab
import be.wimvandonck.reader.data.ReadingLists
import be.wimvandonck.reader.ui.theme.AppShapes
import be.wimvandonck.reader.ui.theme.EmptyState
import be.wimvandonck.reader.ui.theme.readableWidth
import coil3.compose.AsyncImage

/** The reading list as the home screen shows it (#107). */
data class ReadingListHome(
    val filter: ReadingListFilter,
    val items: List<Item>,
    /** The tags of the list shown, for the chips. */
    val tags: List<String>,
    /** About how many minutes each item takes to read, by item key, when its text is known. */
    val minutes: Map<String, Int>,
    /** So items show where they came from, like feed articles. */
    val library: Library,
    /** Titles in the reader's font, as the reading list is for reading. */
    val titleFont: FontFamily,
)

/**
 * The reading list: how many articles and about how long, then article-style rows (the title in the
 * reader's font, the site, reading time, progress and highlights). The newest article to read shows
 * as a large card with its picture.
 */
@Composable
internal fun ReadingListContent(
    home: ReadingListHome,
    showImages: Boolean,
    onOpen: (Item) -> Unit,
) {
    if (home.items.isEmpty()) {
        val toRead = home.filter.tab == ReadingListTab.ToRead && !home.filter.likedOnly && home.filter.tag == null
        EmptyState(
            painterResource(if (home.filter.tab == ReadingListTab.Archive) R.drawable.ic_inventory else R.drawable.ic_reading_list),
            stringResource(
                when {
                    toRead -> R.string.reading_list_empty_title
                    home.filter.tab == ReadingListTab.Archive && !home.filter.likedOnly && home.filter.tag == null -> R.string.reading_list_archive_empty_title
                    else -> R.string.reading_list_none_title
                },
            ),
            body = if (toRead) stringResource(R.string.reading_list_empty_body) else null,
        )
        return
    }
    // Another list starts at the top.
    val listState = rememberSaveable(home.filter, saver = LazyListState.Saver) { LazyListState() }
    val total = home.items.sumOf { ReadingLists.minutesLeft(home.minutes[it.key] ?: 0, it.progress) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().readableWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "summary") {
            Text(
                text =
                    pluralStringResource(R.plurals.reading_list_count, home.items.size, home.items.size) +
                        if (total > 0) " · " + stringResource(R.string.reading_list_about, duration(total)) else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
            )
        }
        itemsIndexed(home.items, key = { _, item -> item.key }) { index, item ->
            val source = remember(item, home.library) { ReadingLists.source(item, home.library) }
            ReadingListRow(
                item = item,
                source = source,
                minutes = home.minutes[item.key] ?: 0,
                titleFont = home.titleFont,
                // The first article to read, with its picture, as a large card.
                large = index == 0 && home.filter.tab == ReadingListTab.ToRead && showImages && item.imageUrl != null,
                showImage = showImages,
                onClick = { onOpen(item) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

/** "2 h 40 min" or "12 min". */
@Composable
private fun duration(minutes: Int): String =
    if (minutes >= 60) stringResource(R.string.duration_hours, minutes / 60, minutes % 60) else stringResource(R.string.duration_minutes, minutes)

@Composable
internal fun ReadingListRow(
    item: Item,
    source: Feed?,
    minutes: Int,
    titleFont: FontFamily,
    large: Boolean,
    showImage: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** What kind of item this is, shown at the end of its top line (Search). */
    label: String? = null,
) {
    Surface(
        onClick = onClick,
        shape = AppShapes.Item,
        color = if (item.archived) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        if (large) {
            Column {
                item.imageUrl?.let { CardPicture(it) }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ItemMeta(item, source, minutes, label)
                    ItemTitle(item, titleFont, large = true)
                    ItemProgress(item, minutes)
                }
            }
        } else {
            Row(Modifier.padding(start = 16.dp, top = 12.dp, end = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ItemMeta(item, source, minutes, label)
                    ItemTitle(item, titleFont, large = false)
                    ItemProgress(item, minutes)
                }
                val image = item.imageUrl?.takeIf { showImage }
                if (image != null) ItemThumbnail(image)
            }
        }
    }
}

/** The site's icon and name, the reading time, and the number of highlights. */
@Composable
private fun ItemMeta(
    item: Item,
    source: Feed?,
    minutes: Int,
    label: String?,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (source != null) {
            FeedIcon(source, FeedIconSize.Small)
            Text(
                source.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (minutes > 0) {
            Text("· " + stringResource(R.string.duration_minutes, minutes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                modifier =
                    Modifier
                        .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        if (item.highlights.isNotEmpty()) {
            val description = pluralStringResource(R.plurals.highlights_count, item.highlights.size, item.highlights.size)
            Row(
                Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = description },
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_highlight), contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(item.highlights.size.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (item.liked) {
            Icon(
                painterResource(R.drawable.ic_favorite_filled),
                contentDescription = stringResource(R.string.reading_list_liked),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ItemTitle(
    item: Item,
    font: FontFamily,
    large: Boolean,
) {
    Text(
        text = item.title,
        style = (if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium).copy(fontFamily = font),
        fontWeight = FontWeight.SemiBold,
        color = if (item.archived) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        maxLines = if (large) 3 else 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** A thin bar for how far it was read, and the minutes left; nothing before it is started. */
@Composable
private fun ItemProgress(
    item: Item,
    minutes: Int,
) {
    if (item.progress <= 0.01f || item.archived) return
    val left = ReadingLists.minutesLeft(minutes, item.progress)
    val label = stringResource(R.string.reading_list_progress)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LinearProgressIndicator(
            progress = { item.progress },
            modifier = Modifier.weight(1f).semantics { contentDescription = label },
        )
        if (left > 0) Text(stringResource(R.string.reading_list_left, left), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CardPicture(url: String) {
    var failed by remember(url) { mutableStateOf(false) }
    if (failed) return
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onError = { failed = true },
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

@Composable
private fun ItemThumbnail(url: String) {
    var failed by remember(url) { mutableStateOf(false) }
    if (failed) return
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onError = { failed = true },
        modifier =
            Modifier
                .size(64.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}
