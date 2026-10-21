package be.wimvandonck.reader.ui.home

import androidx.compose.foundation.horizontalScroll
import be.wimvandonck.reader.data.SearchFilters
import be.wimvandonck.reader.data.SearchResults
import be.wimvandonck.reader.data.Saved
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import be.wimvandonck.reader.data.SavedType
import be.wimvandonck.reader.data.Folder
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
import be.wimvandonck.reader.R
import be.wimvandonck.reader.data.Article
import be.wimvandonck.reader.data.Feed
import be.wimvandonck.reader.data.Library
import be.wimvandonck.reader.data.ListView
import be.wimvandonck.reader.data.SearchFilter
import be.wimvandonck.reader.data.SearchHit
import be.wimvandonck.reader.data.Item
import be.wimvandonck.reader.data.ReadingLists
import be.wimvandonck.reader.ui.theme.EmptyState
import be.wimvandonck.reader.ui.theme.readableWidth

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
    /** The feeds' folders, for the Folder filter. */
    val feedFolders: List<Folder> = emptyList(),
    /** Saved's folders and tags (most used first), for its filters. */
    val savedFolders: List<String> = emptyList(),
    val savedTags: List<String> = emptyList(),
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
)

/**
 * The Search tab (#171): the articles of every feed and Saved (archived articles too). A search field
 * in the top bar; chips for the kind (All, Feeds, Saved) and that kind's filters (feeds: a folder or
 * one feed; Saved: a folder, a tag and a type); below, the matches, mixed and best first, each
 * labelled Feed or Saved. With nothing typed it lists everything the filters let through, newest
 * first, as the All views did.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchState,
    actions: SearchActions,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val wide = isWideWindow()
    Row(Modifier.fillMaxSize()) {
    if (wide) MainNavigationRail(MainTab.Search, actions.onTab)
    Scaffold(
        modifier = Modifier.weight(1f),
        bottomBar = { if (!wide) MainNavigationBar(MainTab.Search, actions.onTab) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    Icon(painterResource(R.drawable.ic_search), contentDescription = null, modifier = Modifier.padding(horizontal = 12.dp))
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
            if (state.hasItems) FilterRow(state.filters.scope) { actions.onFiltersChange(state.filters.withScope(it)) }
            NarrowRow(state, actions.onFiltersChange)
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

/** All, Feeds or Reading list: filter chips above the results, as Material places chips that filter a list. */
@Composable
private fun FilterRow(
    filter: SearchFilter,
    onChange: (SearchFilter) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            SearchFilter.All to R.string.filter_all,
            SearchFilter.Feeds to R.string.feeds_title,
            SearchFilter.Saved to R.string.saved_title,
        ).forEach { (option, label) ->
            FilterChip(
                selected = filter == option,
                onClick = { onChange(option) },
                label = { Text(stringResource(label)) },
                leadingIcon =
                    if (filter == option) {
                        { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else {
                        null
                    },
            )
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * The chosen kind's filters (#171), as chips that open a menu: feeds by folder or by feed, Saved by
 * folder, tag and type. A chosen filter shows its value and a check.
 */
@Composable
private fun NarrowRow(
    state: SearchState,
    onChange: (SearchFilters) -> Unit,
) {
    val f = state.filters
    val anyFolder = stringResource(R.string.search_any_folder)
    val anyFeed = stringResource(R.string.search_any_feed)
    val anyTag = stringResource(R.string.search_any_tag)
    val chips: List<@Composable () -> Unit> =
        when (f.scope) {
            SearchFilter.All -> emptyList()
            SearchFilter.Feeds -> {
                val feeds = state.feeds.filter { f.feedFolderId == null || it.folderId == f.feedFolderId }
                listOfNotNull(
                    if (state.feedFolders.isEmpty()) {
                        null
                    } else {
                        {
                            MenuChip(
                                label = stringResource(R.string.folder_field),
                                chosen = state.feedFolders.firstOrNull { it.id == f.feedFolderId }?.title,
                                options = listOf<Pair<String?, String>>(null to anyFolder) + state.feedFolders.map { it.id to it.title },
                                onPick = { onChange(f.copy(feedFolderId = it, feedUrl = null)) },
                            )
                        }
                    },
                    {
                        MenuChip(
                            label = stringResource(R.string.search_feed),
                            chosen = state.feeds.firstOrNull { it.url == f.feedUrl }?.title,
                            options = listOf<Pair<String?, String>>(null to anyFeed) + feeds.map { it.url to it.title },
                            onPick = { onChange(f.copy(feedUrl = it)) },
                        )
                    },
                )
            }
            SearchFilter.Saved ->
                listOfNotNull(
                    if (state.savedFolders.isEmpty()) {
                        null
                    } else {
                        {
                            MenuChip(
                                label = stringResource(R.string.folder_field),
                                chosen = f.savedFolder,
                                options = listOf<Pair<String?, String>>(null to anyFolder) + state.savedFolders.map { it to it },
                                onPick = { onChange(f.copy(savedFolder = it)) },
                            )
                        }
                    },
                    if (state.savedTags.isEmpty()) {
                        null
                    } else {
                        {
                            MenuChip(
                                label = stringResource(R.string.search_tag),
                                chosen = f.tag,
                                options = listOf<Pair<String?, String>>(null to anyTag) + state.savedTags.map { it to it },
                                onPick = { onChange(f.copy(tag = it)) },
                            )
                        }
                    },
                    {
                        val types = listOf(SavedType.Article to R.string.type_articles, SavedType.Video to R.string.type_videos, SavedType.Audio to R.string.type_audio)
                        val names = types.associate { (t, label) -> t.name to stringResource(label) }
                        MenuChip(
                            label = stringResource(R.string.type_filter),
                            chosen = f.type?.let { names[it.name] },
                            options = listOf<Pair<String?, String>>(null to stringResource(R.string.type_all)) + types.map { (t, _) -> t.name to names.getValue(t.name) },
                            onPick = { name -> onChange(f.copy(type = name?.let { SavedType.valueOf(it) })) },
                        )
                    },
                )
        }
    if (chips.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { chips.forEach { it() } }
}

/** A filter chip that opens a menu of [options] (value to name); the first is "any". */
@Composable
private fun MenuChip(
    label: String,
    chosen: String?,
    options: List<Pair<String?, String>>,
    onPick: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = chosen != null,
            onClick = { open = true },
            label = { Text(chosen ?: label, maxLines = 1) },
            leadingIcon =
                if (chosen != null) {
                    { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
            trailingIcon = { Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        open = false
                        onPick(value)
                    },
                )
            }
        }
    }
}
