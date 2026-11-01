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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.getValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
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
 * and at the end Settings: the account's picture when it has one (Gravatar, with Show images on), else a cogwheel. There is no ☰ menu:
 * every place is a chip or in the navigation bar.
 */
@Composable
fun HomeSearchBar(
    onSearch: () -> Unit,
    onSettings: () -> Unit,
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
 * Which view of the place: Unread / All in Feeds, To read / All in Saved. A segmented switch, like
 * To read / Archive in the save sheet: the same articles narrowed, not separate pages, so no tabs and
 * no swiping sideways between them.
 */
@Composable
fun ViewSwitch(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 8.dp),
    ) {
        labels.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selected,
                onClick = { if (index != selected) onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size),
            ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
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
    /** Less room under the row when a second row follows (a topic's feeds). */
    rowBelow: Boolean = false,
) {
    if (state == null && tags.isEmpty() && liked == null && loose == null) return
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = if (rowBelow) 0.dp else 8.dp),
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
    val overlap = with(LocalDensity.current) { RowOverlap.roundToPx() }
    Row(
        Modifier
            // The chips' touch areas reach above them: the row moves up a little, so the gap
            // between the two rows is close to the gap between the chips.
            .layout { measurable, constraints ->
                val p = measurable.measure(constraints)
                layout(p.width, (p.height - overlap).coerceAtLeast(0)) { p.place(0, -overlap) }
            }.fillMaxWidth()
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

/** How far the second chip row moves up into the first one's touch area. */
private val RowOverlap = 4.dp
