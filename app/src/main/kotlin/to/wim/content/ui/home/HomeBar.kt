package to.wim.content.ui.home

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * and at the end Settings: the account's picture when it has one (Gravatar, with Show images on),
 * else a cogwheel. There is no ☰ menu: every place is a chip or in the navigation bar.
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
 * The state chip at the start of the chip row (#205): what of the place is shown, Unread or All in
 * Feeds, To read or Archive in Saved. A menu picks another; it is lit while it leaves things out.
 */
@Composable
fun StateChip(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    /** Whether the chosen state leaves things out (everything but All). */
    filtering: Boolean,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = filtering,
            onClick = { open = true },
            label = { Text(options[selected], maxLines = 1) },
            trailingIcon = { Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { index, label ->
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = { RadioButton(selected = index == selected, onClick = null) },
                    onClick = {
                        open = false
                        if (index != selected) onSelect(index)
                    },
                )
            }
        }
    }
}

/**
 * The chip row under the search bar: the [state] chip, then the groups as chips, one at a time
 * (folders in Feeds, tags in Saved): tapping the chosen one again lets go. While a group or another
 * filter is on, an X at the end clears it.
 */
@Composable
fun TagChips(
    tags: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    filtered: Boolean,
    onClear: () -> Unit,
    state: @Composable () -> Unit,
    /** Saved's Liked chip (#206), the first group. */
    liked: (@Composable () -> Unit)? = null,
    /** Feeds' feeds without a topic (FeedChips), after the topics and a divider. */
    loose: (@Composable () -> Unit)? = null,
    /** Manage topics, as the row's last button. */
    onManage: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state()
        if (tags.isNotEmpty() || liked != null) VerticalDivider(Modifier.height(24.dp))
        liked?.invoke()
        tags.forEach { tag ->
            val on = selected != null && tag.equals(selected, ignoreCase = true)
            // No check mark: it would widen the chip and push the others along. The fill shows it.
            FilterChip(
                selected = on,
                onClick = { onSelect(if (on) null else tag) },
                label = { Text(tag) },
            )
        }
        if (loose != null) {
            if (tags.isNotEmpty() || liked != null) VerticalDivider(Modifier.height(24.dp))
            loose()
        }
        if (filtered) {
            IconButton(onClick = onClear) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_clear))
            }
        }
        if (onManage != null) {
            IconButton(onClick = onManage) {
                Icon(painterResource(R.drawable.ic_manage_feeds), contentDescription = stringResource(R.string.menu_manage))
            }
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
