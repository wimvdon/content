package be.wimvandonck.reader.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import be.wimvandonck.reader.R
import be.wimvandonck.reader.data.Article
import be.wimvandonck.reader.data.Feed
import be.wimvandonck.reader.data.ListView
import be.wimvandonck.reader.instapaper.InstapaperBookmark
import be.wimvandonck.reader.instapaper.InstapaperLists
import be.wimvandonck.reader.ui.theme.EmptyState
import be.wimvandonck.reader.ui.theme.readableWidth

/** What the search screen shows. [results] is null while the articles are being prepared for searching. */
data class SearchState(
    val query: String,
    val results: List<Article>?,
    val feeds: List<Feed>,
    val listView: ListView,
    val showImages: Boolean,
    /** Signed in to Instapaper: its saved articles are searched too, in a section of their own. */
    val instapaper: SavedResults? = null,
)

/** The saved articles that match. [matches] is null while the saved articles are being read. */
data class SavedResults(
    val matches: List<InstapaperBookmark>?,
    val incomplete: Boolean = false,
    /** Search was opened from Instapaper, so its section comes first. */
    val first: Boolean = false,
)

class SearchActions(
    val onQueryChange: (String) -> Unit,
    val onOpenArticle: (Article) -> Unit,
    val onOpenSaved: (InstapaperBookmark) -> Unit = {},
    val onBack: () -> Unit,
)

/**
 * Searches everything: the articles of every feed and, signed in, the saved Instapaper articles. A
 * search field in the top bar, the matches below it (in a section for each when both are searched).
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
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            val results = state.results
            val saved = state.instapaper
            val savedMatches = saved?.matches
            when {
                results == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                state.query.isBlank() ->
                    if (saved == null) {
                        EmptyState(painterResource(R.drawable.ic_search), stringResource(R.string.search_start_title), body = stringResource(R.string.search_start_body))
                    } else {
                        EmptyState(painterResource(R.drawable.ic_search), stringResource(R.string.search_start_title_saved), body = stringResource(R.string.search_start_body_saved))
                    }
                results.isEmpty() && (saved == null || savedMatches?.isEmpty() == true && !saved.incomplete) ->
                    EmptyState(painterResource(R.drawable.ic_search), stringResource(R.string.search_none_title), body = stringResource(R.string.search_none_body))
                else -> {
                    val feedsByUrl = remember(state.feeds) { state.feeds.associateBy { it.url } }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().readableWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        fun feedSection() {
                            if (saved != null) {
                                item(key = "feeds") { SectionTitle(stringResource(R.string.feeds_title)) }
                                if (results.isEmpty()) item(key = "feeds-none") { Note(stringResource(R.string.search_section_none)) }
                            }
                            items(results, key = { it.key }) { article ->
                                ArticleRow(
                                    article = article,
                                    feed = feedsByUrl[article.feedUrl],
                                    view = state.listView,
                                    showImage = state.showImages,
                                    onClick = { actions.onOpenArticle(article) },
                                )
                            }
                        }

                        fun savedSection() {
                            if (saved == null) return
                            item(key = "saved") { SectionTitle(stringResource(R.string.section_instapaper)) }
                            when {
                                savedMatches == null ->
                                    item(key = "saved-loading") {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                            Note(stringResource(R.string.search_saved_loading))
                                        }
                                    }
                                savedMatches.isEmpty() -> item(key = "saved-none") { Note(stringResource(R.string.search_section_none)) }
                                else ->
                                    items(savedMatches, key = { "saved:${it.id}" }) { bookmark ->
                                        ArticleRow(
                                            article = InstapaperLists.article(bookmark),
                                            feed = bookmark.host.takeIf { it.isNotEmpty() }?.let { Feed(url = it, title = it) },
                                            view = state.listView,
                                            showImage = false,
                                            showUnreadDot = false,
                                            onClick = { actions.onOpenSaved(bookmark) },
                                        )
                                    }
                            }
                            if (saved.incomplete) item(key = "saved-incomplete") { Note(stringResource(R.string.search_saved_incomplete)) }
                        }

                        if (saved?.first == true) {
                            savedSection()
                            feedSection()
                        } else {
                            feedSection()
                            savedSection()
                        }
                    }
                }
            }
        }
    }
}

/** A section's name above its matches: Feeds or Instapaper. */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
