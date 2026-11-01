package to.wim.content.ui.home

import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import to.wim.content.data.SearchFilters
import to.wim.content.data.SearchResults
import to.wim.content.data.Saved
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import to.wim.content.data.Folder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import to.wim.content.R
import to.wim.content.data.Article
import to.wim.content.data.Feed
import to.wim.content.data.Library
import to.wim.content.data.ListView
import to.wim.content.data.SearchFilter
import to.wim.content.data.SearchHit
import to.wim.content.data.Item
import to.wim.content.data.ReadingLists
import to.wim.content.ui.theme.EmptyState
import to.wim.content.ui.theme.readableWidth

/**
 * What the search screen shows. [results] is null while the articles are being prepared for
 * searching; feed and reading list matches are mixed in one list, best first (MixedSearch).
 */
data class SearchState(
    val query: String,
    val results: List<SearchHit>?,
    val feeds: List<Feed>,
    val listView: ListView,
    val showImages: Boolean,
    /** Saved is searched too; with something in it, chips pick the kind. */
    val hasItems: Boolean = false,
    val filters: SearchFilters = SearchFilters(),
    /** So items show like the feed article they were saved from. */
    val library: Library? = null,
    /** The topics, for their chips and icons (SearchResults.topics: those with something in the kind shown). */
    val topics: List<String> = emptyList(),
    val folders: List<Folder> = emptyList(),
    /** The feeds the Feeds menu offers (SearchResults.menuFeeds). */
    val menuFeeds: List<Feed> = emptyList(),
    /** Titles of note cards in the reader's font, as in Saved's folders. */
    val titleFont: FontFamily = FontFamily.Default,
)

class SearchActions(
    val onQueryChange: (String) -> Unit,
    val onFiltersChange: (SearchFilters) -> Unit = {},
    val onOpenArticle: (Article) -> Unit,
    val onOpenItem: (Item) -> Unit = {},
    /** Another top-level destination (Feeds or Saved). */
    val onTab: (MainTab) -> Unit = {},
    /** A topic chip: picked, or null to let go (SearchFilters.withTopic needs the library). */
    val onTopic: (String?) -> Unit = {},
    /** Back to the list (#183): Search opens from the title bar's search button. */
    val onBack: () -> Unit = {},
)

/**
 * The Search tab (#171): the articles of every feed and Saved (archived articles too). A search field
 * in the top bar; one row of chips (SearchFilters): Feeds (a menu: all feeds, or some), Saved, then
 * the topics; below, the matches, mixed and best first, each labelled Feed or Saved. With nothing
 * typed it lists everything the filters let through, newest first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchState,
    actions: SearchActions,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    Row(Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.weight(1f),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = {
                    TextField(
                        value = state.query,
                        onValueChange = actions.onQueryChange,
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                },
                actions = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = {
                            actions.onQueryChange("")
                            focus.requestFocus()
                        }) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_clear))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            val results = state.results
            if (state.feeds.isNotEmpty() || state.hasItems) FilterRow(state, actions)
            when {
                results == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                results.isEmpty() && state.query.isBlank() && !state.filters.narrowed ->
                    EmptyState(painterResource(R.drawable.ic_search), stringResource(R.string.search_start_title), body = stringResource(R.string.search_start_body))
                results.isEmpty() ->
                    EmptyState(painterResource(R.drawable.ic_search), stringResource(R.string.search_none_title), body = stringResource(R.string.search_none_body))
                SearchResults.showsNotes(state.filters.scope) -> SearchNotes(state, results, actions)
                else -> {
                    val feedsByUrl = remember(state.feeds) { state.feeds.associateBy { it.url } }
                    val feedLabel = stringResource(R.string.search_label_feed)
                    val itemLabel = stringResource(R.string.saved_title)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().readableWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(results, key = { hit -> hit.key }) { hit ->
                            when (hit) {
                                is SearchHit.FromFeed ->
                                    ArticleRow(
                                        article = hit.article,
                                        feed = feedsByUrl[hit.article.feedUrl],
                                        view = state.listView,
                                        showImage = state.showImages,
                                        label = feedLabel.takeIf { state.hasItems && state.filters.scope == SearchFilter.All },
                                        onClick = { actions.onOpenArticle(hit.article) },
                                    )
                                is SearchHit.FromItem ->
                                    ArticleRow(
                                        article = ReadingLists.article(hit.item, state.library),
                                        feed = ReadingLists.source(hit.item, state.library),
                                        view = state.listView,
                                        showImage = state.showImages,
                                        showReadState = false,
                                        label = itemLabel.takeIf { state.filters.scope == SearchFilter.All },
                                        onClick = { actions.onOpenItem(hit.item) },
                                    )
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

/**
 * All and Saved as notes (#171 follow-up), like Saved's folders: a staggered grid of cards, two
 * columns on a phone. Feeds stays a list of rows, like the feeds.
 */
@Composable
private fun SearchNotes(
    state: SearchState,
    results: List<SearchHit>,
    actions: SearchActions,
) {
    val feedsByUrl = remember(state.feeds) { state.feeds.associateBy { it.url } }
    val feedLabel = stringResource(R.string.search_label_feed)
    val itemLabel = stringResource(R.string.saved_title)
    val labelled = state.hasItems && state.filters.scope == SearchFilter.All
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalItemSpacing = 8.dp,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(results, key = { hit -> hit.key }) { hit ->
            when (hit) {
                is SearchHit.FromFeed -> {
                    val note = remember(hit.article) { Saved.note(hit.article) }
                    NoteCard(
                        note,
                        feedsByUrl[hit.article.feedUrl],
                        state.titleFont,
                        state.showImages,
                        onClick = { actions.onOpenArticle(hit.article) },
                        label = feedLabel.takeIf { labelled },
                    )
                }
                is SearchHit.FromItem -> {
                    val note = remember(hit.item) { Saved.note(hit.item) }
                    NoteCard(
                        note,
                        ReadingLists.source(hit.item, state.library),
                        state.titleFont,
                        state.showImages,
                        onClick = { actions.onOpenItem(hit.item) },
                        label = itemLabel.takeIf { labelled },
                    )
                }
            }
        }
    }
}

/** A unique key for the list: feed articles by their key, saved ones by their id. */
private val SearchHit.key: String
    get() =
        when (this) {
            is SearchHit.FromFeed -> article.key
            is SearchHit.FromItem -> "item:${item.key}"
        }

/**
 * The one chip row above the results: Feeds (a menu: All feeds, or some feeds with check boxes, and
 * Clear), Saved (a toggle), a divider, then the topics, one at a time, as in Feeds and Saved.
 */
@Composable
private fun FilterRow(
    state: SearchState,
    actions: SearchActions,
) {
    val f = state.filters
    TagChips(
        tags = state.topics,
        selected = f.topic,
        onSelect = actions.onTopic,
        iconOf = { name -> state.folders.firstOrNull { it.title.equals(name, ignoreCase = true) }?.icon },
        state = {
            if (state.feeds.isNotEmpty()) FeedsMenuChip(state, actions.onFiltersChange)
            if (state.hasItems) {
                val on = f.scope == SearchFilter.Saved
                FilterChip(
                    selected = on,
                    onClick = { actions.onFiltersChange(f.withScope(if (on) SearchFilter.All else SearchFilter.Saved)) },
                    label = { Text(stringResource(R.string.saved_title)) },
                    leadingIcon =
                        if (on) {
                            { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                        } else {
                            null
                        },
                )
            }
        },
    )
}

/**
 * Feeds as a chip with a menu: All feeds, then every feed (of the chosen topic) with a check box, so
 * several can be picked; the menu stays open while ticking them. Clear shows both kinds again. The
 * chip says what is picked: Feeds, the one feed's name, or how many.
 */
@Composable
private fun FeedsMenuChip(
    state: SearchState,
    onChange: (SearchFilters) -> Unit,
) {
    val f = state.filters
    val on = f.scope == SearchFilter.Feeds
    var open by remember { mutableStateOf(false) }
    val picked = f.feedUrls
    val label =
        when (picked.size) {
            0 -> stringResource(R.string.feeds_title)
            1 -> state.feeds.firstOrNull { it.url in picked }?.title ?: stringResource(R.string.feeds_title)
            else -> pluralStringResource(R.plurals.feeds_count, picked.size, picked.size)
        }
    Box {
        FilterChip(
            selected = on,
            onClick = { open = true },
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp)) },
            leadingIcon =
                if (on) {
                    { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
            trailingIcon = { Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.search_all_feeds)) },
                leadingIcon = { RadioButton(selected = on && picked.isEmpty(), onClick = null) },
                onClick = {
                    open = false
                    onChange(f.withScope(SearchFilter.Feeds))
                },
            )
            HorizontalDivider()
            state.menuFeeds.forEach { feed ->
                DropdownMenuItem(
                    text = { Text(feed.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Checkbox(checked = feed.url in picked, onCheckedChange = null) },
                    trailingIcon = { FeedIcon(feed, FeedIconSize.Small) },
                    onClick = { onChange(f.toggleFeed(feed.url)) },
                    modifier = Modifier.widthIn(max = 320.dp),
                )
            }
            if (on) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_clear)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_close), contentDescription = null) },
                    onClick = {
                        open = false
                        onChange(f.withScope(SearchFilter.All))
                    },
                )
            }
        }
    }
}
