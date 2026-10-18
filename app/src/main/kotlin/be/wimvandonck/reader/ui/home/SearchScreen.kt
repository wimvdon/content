package be.wimvandonck.reader.ui.home

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.CircularProgressIndicator
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
import be.wimvandonck.reader.instapaper.InstapaperBookmark
import be.wimvandonck.reader.instapaper.InstapaperLists
import be.wimvandonck.reader.ui.theme.EmptyState
import be.wimvandonck.reader.ui.theme.readableWidth

/**
 * What the search screen shows. [results] is null while the articles are being prepared for
 * searching; feed and saved matches are mixed in one list, best first (MixedSearch).
 */
data class SearchState(
    val query: String,
    val results: List<SearchHit>?,
    val feeds: List<Feed>,
    val listView: ListView,
    val showImages: Boolean,
    /** Saved articles are searched too (always, unless they couldn't be read), with chips to pick the kind. */
    val saved: SavedSearchState? = null,
    val filter: SearchFilter = SearchFilter.All,
    /** So saved results show like the feed article they were saved from. */
    val library: Library? = null,
)

/** How searching the saved articles goes. */
data class SavedSearchState(
    /** The saved articles are still being read. */
    val loading: Boolean = false,
    /** Instapaper stopped answering part way, so some saved articles weren't searched. */
    val incomplete: Boolean = false,
)

class SearchActions(
    val onQueryChange: (String) -> Unit,
    val onFilterChange: (SearchFilter) -> Unit = {},
    val onOpenArticle: (Article) -> Unit,
    val onOpenSaved: (InstapaperBookmark) -> Unit = {},
    val onBack: () -> Unit,
)

/**
 * Searches everything: the articles of every feed and the saved articles. A search field in the top
 * bar, chips to show all results, only feeds or only saved, and the matches below, mixed and best
 * first, each labelled Feed or Saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchState,
    actions: SearchActions,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Opening search starts typing at once.
    LaunchedEffect(Unit) { if (state.query.isEmpty()) focus.requestFocus() }
    Scaffold(
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
            val saved = state.saved
            if (saved != null) FilterRow(state.filter, actions.onFilterChange)
            if (saved?.loading == true && results != null && state.query.isNotBlank()) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Note(stringResource(R.string.search_saved_loading))
                }
            }
            when {
                results == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                state.query.isBlank() ->
                    if (saved == null) {
                        EmptyState(painterResource(R.drawable.ic_search), stringResource(R.string.search_start_title), body = stringResource(R.string.search_start_body))
                    } else {
                        EmptyState(painterResource(R.drawable.ic_search), stringResource(R.string.search_start_title_saved), body = stringResource(R.string.search_start_body_saved))
                    }
                results.isEmpty() && saved?.loading != true && saved?.incomplete != true ->
                    EmptyState(painterResource(R.drawable.ic_search), stringResource(R.string.search_none_title), body = stringResource(R.string.search_none_body))
                else -> {
                    val feedsByUrl = remember(state.feeds) { state.feeds.associateBy { it.url } }
                    val feedLabel = stringResource(R.string.search_label_feed)
                    val savedLabel = stringResource(R.string.section_saved)
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
                                        label = feedLabel.takeIf { saved != null },
                                        onClick = { actions.onOpenArticle(hit.article) },
                                    )
                                is SearchHit.FromSaved ->
                                    ArticleRow(
                                        article = InstapaperLists.article(hit.bookmark, state.library),
                                        feed = InstapaperLists.source(hit.bookmark, state.library),
                                        view = state.listView,
                                        showImage = state.showImages,
                                        showUnreadDot = false,
                                        label = savedLabel,
                                        onClick = { actions.onOpenSaved(hit.bookmark) },
                                    )
                            }
                        }
                        if (saved?.incomplete == true) item(key = "saved-incomplete") { Note(stringResource(R.string.search_saved_incomplete)) }
                    }
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
            is SearchHit.FromSaved -> "saved:${bookmark.id}"
        }

/** All, Feeds or Saved: filter chips above the results, as Material places chips that filter a list. */
@Composable
private fun FilterRow(
    filter: SearchFilter,
    onChange: (SearchFilter) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            SearchFilter.All to R.string.filter_all,
            SearchFilter.Feeds to R.string.feeds_title,
            SearchFilter.Saved to R.string.section_saved,
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
