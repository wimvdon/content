package to.wim.content.ui.home

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material3.Tab
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import to.wim.content.R
import to.wim.content.data.Feed
import to.wim.content.sync.Gravatar

/**
 * The title bar as a large search bar (#189): the magnifier, "Search articles" (which opens Search),
 * then + (the same everywhere: it asks Subscribe to feed or Save article), and at the end Settings: the account's
 * picture when it has one (Gravatar, with Show images on), else a cogwheel. There is no ☰ menu:
 * every place is a chip or in the navigation bar.
 */
@Composable
fun HomeSearchBar(
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    /** The + and what it says to a screen reader; null: no +. */
    onAdd: (() -> Unit)? = null,
    addLabel: String = "",
    /** The Content account's address, for its picture; null when signed out. */
    accountEmail: String? = null,
    showImages: Boolean = true,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .heightIn(min = 56.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onSearch),
    ) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Text(
                stringResource(R.string.search_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            if (onAdd != null) {
                IconButton(onClick = onAdd) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = addLabel)
                }
            }
            IconButton(onClick = onSettings) {
                val cog: @Composable () -> Unit = {
                    Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.menu_settings))
                }
                if (accountEmail != null && showImages) {
                    SubcomposeAsyncImage(
                        model = Gravatar.url(accountEmail, 96),
                        contentDescription = stringResource(R.string.menu_settings),
                        loading = { cog() },
                        error = { cog() },
                        modifier = Modifier.size(32.dp).clip(CircleShape),
                    )
                } else {
                    cog()
                }
            }
        }
    }
}

/**
 * The tabs under the search bar: the views of the place shown, Unread and Read in Feeds, To read and
 * All in Saved. Material's secondary tabs (lighter than primary ones: a thin line under the chosen
 * one), as they switch between views within one place; the chips below them narrow the view to a
 * topic or feed. The whole header slides away while scrolling down (CollapsingHeader).
 */
@Composable
fun HomeTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    SecondaryTabRow(selectedTabIndex = selected, containerColor = MaterialTheme.colorScheme.surface) {
        labels.forEachIndexed { index, label ->
            Tab(
                selected = index == selected,
                onClick = { if (index != selected) onSelect(index) },
                text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

/**
 * The pages under [HomeTabs]: swiping sideways goes to the next or previous tab, and choosing a
 * tab slides to its page. [content] draws one page (its tab's list).
 */
@Composable
fun TabPager(
    count: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
    enabled: Boolean = true,
    content: @Composable (page: Int) -> Unit,
) {
    val pager = rememberPagerState(initialPage = selected) { count }
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)
    // A tab tapped: slide to its page.
    LaunchedEffect(selected) { if (pager.currentPage != selected) pager.animateScrollToPage(selected) }
    // A page swiped to: choose its tab.
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page -> if (page != currentSelected) currentOnSelect(page) }
    }
    HorizontalPager(
        state = pager,
        userScrollEnabled = enabled,
        // The neighbouring page is drawn only while it is being swiped in.
        beyondViewportPageCount = 0,
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.Top,
    ) { page -> content(page) }
}

/**
 * A chip row: the groups as chips, one at a time (topics and feeds without one in Feeds, Liked and
 * topics in Saved, topics in Search): tapping the chosen one again lets go, so there is no X to
 * clear it (#307). Search puts its kind chips first ([state]), with a divider after them. Nothing
 * at all to show: no row.
 */
@Composable
fun TagChips(
    tags: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    /** Chips before the groups, set apart by a divider (Search's Feeds and Saved). */
    state: (@Composable () -> Unit)? = null,
    /** Saved's Liked chip (#206), the first group. */
    liked: (@Composable () -> Unit)? = null,
    /** Feeds' feeds without a topic (FeedChips), after the topics and a divider. */
    loose: (@Composable () -> Unit)? = null,
    /** A topic's icon by its name (#314: the same chips in Feeds and Saved); null: no icon. */
    iconOf: (String) -> String? = { null },
) {
    if (state == null && tags.isEmpty() && liked == null && loose == null) return
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state != null) {
            state()
            if (tags.isNotEmpty() || liked != null) VerticalDivider(Modifier.height(24.dp))
        }
        liked?.invoke()
        tags.forEach { tag ->
            val on = selected != null && tag.equals(selected, ignoreCase = true)
            // No check mark: it would widen the chip and push the others along. The fill shows it.
            FilterChip(
                selected = on,
                onClick = { onSelect(if (on) null else tag) },
                label = { Text(tag) },
                leadingIcon =
                    iconOf(tag)?.let { icon ->
                        { Icon(rememberFolderIconPainter(icon), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    },
            )
        }
        if (loose != null) {
            if (tags.isNotEmpty() || liked != null) VerticalDivider(Modifier.height(24.dp))
            loose()
        }
    }
}

/** A feed as a chip, with its icon: in the first row (no topic) or the second (a topic's feeds). */
@Composable
fun FeedChip(
    feed: Feed,
    chosen: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = chosen,
        onClick = onClick,
        label = { Text(feed.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp)) },
        leadingIcon = { FeedIcon(feed, FeedIconSize.Small) },
    )
}

/** The second chip row, while a topic is chosen (FeedChips): its feeds; one tapped shows it alone, again the topic. */
@Composable
fun TopicFeedChips(
    feeds: List<Feed>,
    chosenFeed: String?,
    onSelect: (feedUrl: String?) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        feeds.forEach { feed ->
            val on = feed.url == chosenFeed
            FeedChip(feed, on) { onSelect(if (on) null else feed.url) }
        }
    }
}
