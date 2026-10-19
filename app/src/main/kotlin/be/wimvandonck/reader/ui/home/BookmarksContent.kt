package be.wimvandonck.reader.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import be.wimvandonck.reader.R
import be.wimvandonck.reader.data.BookmarkFilter
import be.wimvandonck.reader.data.BookmarkKind
import be.wimvandonck.reader.data.Bookmarks
import be.wimvandonck.reader.data.BookmarksView
import be.wimvandonck.reader.data.Feed
import be.wimvandonck.reader.data.Item
import be.wimvandonck.reader.data.Library
import be.wimvandonck.reader.data.ReadingLists
import be.wimvandonck.reader.ui.theme.AppShapes
import be.wimvandonck.reader.ui.theme.EmptyState
import be.wimvandonck.reader.ui.theme.readableWidth
import coil3.compose.AsyncImage

/** Bookmarks as the home screen shows them (#108). */
data class BookmarksHome(
    val filter: BookmarkFilter,
    val items: List<Item>,
    val collections: List<String>,
    val view: BookmarksView,
    val library: Library,
)

/** Every bookmark's kind's label and icon. */
internal fun BookmarkKind.labelRes(): Int =
    when (this) {
        BookmarkKind.Article -> R.string.bookmark_kind_articles
        BookmarkKind.Tool -> R.string.bookmark_kind_tools
        BookmarkKind.Repo -> R.string.bookmark_kind_repos
        BookmarkKind.Video -> R.string.bookmark_kind_videos
    }

internal fun BookmarkKind.singleRes(): Int =
    when (this) {
        BookmarkKind.Article -> R.string.bookmark_kind_article
        BookmarkKind.Tool -> R.string.bookmark_kind_tool
        BookmarkKind.Repo -> R.string.bookmark_kind_repo
        BookmarkKind.Video -> R.string.bookmark_kind_video
    }

internal fun BookmarkKind.iconRes(): Int =
    when (this) {
        BookmarkKind.Article -> R.drawable.ic_article
        BookmarkKind.Tool -> R.drawable.ic_construction
        BookmarkKind.Repo -> R.drawable.ic_code
        BookmarkKind.Video -> R.drawable.ic_smart_display
    }

/**
 * Bookmarks' header under the top app bar: All and each collection as tabs (collections are flat),
 * then the types (Articles, Tools, Repos, Videos) as filter chips.
 */
@Composable
fun BookmarksTabRow(
    filter: BookmarkFilter,
    collections: List<String>,
    onChange: (BookmarkFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        val tabs = listOf<String?>(null) + collections
        PrimaryScrollableTabRow(
            selectedTabIndex = tabs.indexOfFirst { it.equals(filter.collection, ignoreCase = true) }.coerceAtLeast(0),
            edgePadding = 16.dp,
            containerColor = Color.Transparent,
        ) {
            tabs.forEach { collection ->
                LeadingIconTab(
                    selected = collection.equals(filter.collection, ignoreCase = true),
                    onClick = { onChange(filter.copy(collection = collection)) },
                    text = {
                        Text(
                            collection ?: stringResource(R.string.filter_all),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 160.dp),
                        )
                    },
                    icon = { Icon(painterResource(if (collection == null) R.drawable.ic_bookmarks_outline else R.drawable.ic_folder), contentDescription = null) },
                )
            }
        }
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BookmarkKind.entries.forEach { kind ->
                val selected = filter.kind == kind
                FilterChip(
                    selected = selected,
                    onClick = { onChange(filter.copy(kind = if (selected) null else kind)) },
                    label = { Text(stringResource(kind.labelRes())) },
                    leadingIcon =
                        if (selected) {
                            { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                        } else {
                            null
                        },
                )
            }
        }
    }
}

/** Bookmarks: two columns of cards, or one list (the top bar switches). */
@Composable
internal fun BookmarksContent(
    home: BookmarksHome,
    showImages: Boolean,
    onOpen: (Item) -> Unit,
) {
    if (home.items.isEmpty()) {
        val all = home.filter == BookmarkFilter()
        EmptyState(
            painterResource(R.drawable.ic_bookmarks_outline),
            stringResource(if (all) R.string.bookmarks_empty_title else R.string.reading_list_none_title),
            body = if (all) stringResource(R.string.bookmarks_empty_body) else null,
        )
        return
    }
    val padding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp)
    when (home.view) {
        BookmarksView.Grid ->
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Adaptive(160.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
                verticalItemSpacing = 8.dp,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(home.items, key = { it.key }) { item ->
                    val source = remember(item, home.library) { ReadingLists.source(item, home.library) }
                    BookmarkCard(item, source, showImages, onClick = { onOpen(item) }, modifier = Modifier.animateItem())
                }
            }

        BookmarksView.List ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().readableWidth(),
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(home.items, key = { it.key }) { item ->
                    val source = remember(item, home.library) { ReadingLists.source(item, home.library) }
                    BookmarkCard(item, source, showImages, onClick = { onOpen(item) }, modifier = Modifier.animateItem(), wide = true)
                }
            }
    }
}

/**
 * A bookmark as a card that fits what it is: an article or a video with its picture (a play mark on
 * videos), a tool or a repository with its kind; then the site, the title, your note in a note box,
 * the tags and the number of highlights.
 */
@Composable
internal fun BookmarkCard(
    item: Item,
    source: Feed?,
    showImage: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
) {
    val kind = remember(item.url) { Bookmarks.kindOf(item.url) }
    Surface(onClick = onClick, shape = AppShapes.Item, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column {
            val image = item.imageUrl?.takeIf { showImage && !wide && (kind == BookmarkKind.Article || kind == BookmarkKind.Video) }
            if (image != null) CardImage(image, video = kind == BookmarkKind.Video)
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (source != null) FeedIcon(source, FeedIconSize.Small)
                    Text(
                        item.host,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (kind != BookmarkKind.Article) {
                        Row(
                            Modifier
                                .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(painterResource(kind.iconRes()), contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(stringResource(kind.singleRes()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
                Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (item.note.isNotBlank()) {
                    Text(
                        item.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.small)
                                .padding(8.dp),
                    )
                } else if (item.description.isNotBlank() && kind != BookmarkKind.Article) {
                    Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                if (item.tags.isNotEmpty() || item.highlights.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            item.tags.joinToString(" ") { "#$it" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (item.highlights.isNotEmpty()) {
                            val description = pluralStringResource(R.plurals.highlights_count, item.highlights.size, item.highlights.size)
                            Row(Modifier.semantics(mergeDescendants = true) { contentDescription = description }, verticalAlignment = Alignment.CenterVertically) {
                                Icon(painterResource(R.drawable.ic_highlight), contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(item.highlights.size.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardImage(
    url: String,
    video: Boolean,
) {
    var failed by remember(url) { mutableStateOf(false) }
    if (failed) return
    Box(contentAlignment = Alignment.Center) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { failed = true },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        if (video) {
            Icon(
                painterResource(R.drawable.ic_play),
                contentDescription = null,
                tint = Color.White,
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(8.dp),
            )
        }
    }
}
