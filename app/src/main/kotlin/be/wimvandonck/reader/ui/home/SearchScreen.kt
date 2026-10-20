package be.wimvandonck.reader.ui.home

import androidx.compose.foundation.horizontalScroll
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
    /** The reading list is searched too; with something on it, chips pick the kind. */
    val hasItems: Boolean = false,
    val filter: SearchFilter = SearchFilter.All,
    /** So items show like the feed article they were saved from. */
    val library: Library? = null,
)

class SearchActions(
    val onQueryChange: (String) -> Unit,
    val onFilterChange: (SearchFilter) -> Unit = {},
    val onOpenArticle: (Article) -> Unit,
    val onOpenItem: (Item) -> Unit = {},
    val onBack: () -> Unit,
)

/**
 * Searches everything: the articles of every feed and the reading list (archived articles too). A
 * search field in the top bar, chips to show all results, only feeds or only the reading list, and
 * the matches below, mixed and best first, each labelled Feed or Reading list.
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
            if (state.hasItems) FilterRow(state.filter, actions.onFilterChange)
            when {
                results == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                state.query.isBlank() ->
                    if (!state.hasItems) {
                        EmptyState(painterResource(R.drawable.ic_search), stringResource(R.string.search_start_title), body = stringResource(R.string.search_start_body))
                    } else {
                        EmptyState(painterResource(R.drawable.ic_search), stringResource(R.string.search_start_title_saved), body = stringResource(R.string.search_start_body_saved))
                    }
                results.isEmpty() ->
                    EmptyState(painterResource(R.drawable.ic_search), stringResource(R.string.search_none_title), body = stringResource(R.string.search_none_body))
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
                                        label = feedLabel.takeIf { state.hasItems },
                                        onClick = { actions.onOpenArticle(hit.article) },
                                    )
                                is SearchHit.FromItem ->
                                    ArticleRow(
                                        article = ReadingLists.article(hit.item, state.library),
                                        feed = ReadingLists.source(hit.item, state.library),
                                        view = state.listView,
                                        showImage = state.showImages,
                                        showReadState = false,
                                        label = itemLabel,
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
