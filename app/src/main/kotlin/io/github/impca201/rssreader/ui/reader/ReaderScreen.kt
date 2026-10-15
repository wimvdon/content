package io.github.impca201.rssreader.ui.reader

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.impca201.rssreader.R
import io.github.impca201.rssreader.data.Article
import io.github.impca201.rssreader.data.ReaderStyle
import io.github.impca201.rssreader.data.SaveTarget
import io.github.impca201.rssreader.feed.Html

/** What the reader's buttons do. Implemented by the navigation, which talks to other apps. */
class ReaderActions(
    val onBack: () -> Unit,
    val onStyleChange: (ReaderStyle) -> Unit,
    val onSave: () -> Unit,
    val onShare: () -> Unit,
    val onOpenInBrowser: () -> Unit,
    val onMarkUnread: () -> Unit,
    val onOpenLink: (String) -> Unit,
)

/** One article, in the reader's font, size, spacing and page colour. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    article: Article?,
    feedTitle: String?,
    style: ReaderStyle,
    saveTarget: SaveTarget,
    snackbarHostState: SnackbarHostState,
    actions: ReaderActions,
) {
    var showStyleSheet by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val hasLink = article?.link != null

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showStyleSheet = true }) {
                        Icon(painterResource(R.drawable.ic_text_format), contentDescription = stringResource(R.string.reader_text_settings))
                    }
                    if (hasLink) {
                        IconButton(onClick = actions.onSave) {
                            Icon(
                                painterResource(R.drawable.ic_bookmark_add),
                                contentDescription =
                                    stringResource(
                                        if (saveTarget == SaveTarget.Instapaper) R.string.action_save_instapaper else R.string.action_save_later,
                                    ),
                            )
                        }
                        IconButton(onClick = actions.onShare) {
                            Icon(painterResource(R.drawable.ic_share), contentDescription = stringResource(R.string.action_share))
                        }
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.action_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (hasLink) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_open_in_browser)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    actions.onOpenInBrowser()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_mark_unread)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_mark_unread), contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                actions.onMarkUnread()
                            },
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        if (article == null) {
            Text(
                text = stringResource(R.string.reader_article_gone),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(innerPadding).padding(24.dp),
            )
        } else {
            ArticleBody(
                article = article,
                feedTitle = feedTitle,
                style = style,
                onOpenLink = actions.onOpenLink,
                onOpenInBrowser = actions.onOpenInBrowser,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

    if (showStyleSheet) {
        ModalBottomSheet(
            onDismissRequest = { showStyleSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Text(
                text = stringResource(R.string.reader_text_settings),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp).semantics { heading() },
            )
            ReaderStyleControls(
                style = style,
                onChange = actions.onStyleChange,
                modifier =
                    Modifier
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun ArticleBody(
    article: Article,
    feedTitle: String?,
    style: ReaderStyle,
    onOpenLink: (String) -> Unit,
    onOpenInBrowser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val openLink by rememberUpdatedState(onOpenLink)
    val body =
        remember(article.html, linkColor) {
            val links = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
            AnnotatedString
                .fromHtml(Html.clean(article.html), linkStyles = links) { link ->
                    (link as? LinkAnnotation.Url)?.let { openLink(it.url) }
                }.trimmed()
        }
    val bodyStyle = readerBodyStyle(style)
    val context = LocalContext.current
    val date =
        article.publishedAt?.let {
            DateUtils.formatDateTime(context, it, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_TIME)
        }
    val meta = listOfNotNull(article.author, date).joinToString(" · ")

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (feedTitle != null) {
                Text(feedTitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                text = article.title,
                style =
                    MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = style.font.fontFamily(),
                        fontWeight = FontWeight.Bold,
                    ),
                modifier = Modifier.semantics { heading() },
            )
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (body.isNotEmpty()) {
                SelectionContainer { Text(body, style = bodyStyle) }
            }
            if (article.link != null) {
                OutlinedButton(onClick = onOpenInBrowser, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null)
                    Text(stringResource(R.string.reader_read_on_website), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** Html → AnnotatedString keeps the leading and trailing line breaks of block elements. */
private fun AnnotatedString.trimmed(): AnnotatedString {
    val start = indexOfFirst { !it.isWhitespace() }
    if (start < 0) return AnnotatedString("")
    val end = indexOfLast { !it.isWhitespace() } + 1
    return subSequence(start, end)
}
