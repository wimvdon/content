package io.github.impca201.rssreader.ui.reader

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import io.github.impca201.rssreader.R
import io.github.impca201.rssreader.data.Article
import io.github.impca201.rssreader.data.Feed
import io.github.impca201.rssreader.ui.home.FeedIcon
import io.github.impca201.rssreader.ui.home.FeedIconSize
import io.github.impca201.rssreader.data.ReaderStyle
import io.github.impca201.rssreader.data.ShareTarget
import io.github.impca201.rssreader.feed.ArticleBlock
import io.github.impca201.rssreader.feed.ArticleBlocks

/** Whether the reader shows the full article loaded from the website, and how far it got. */
sealed interface FullTextState {
    /** Showing the feed's text. */
    data object Off : FullTextState

    data object Loading : FullTextState

    data class Shown(val html: String) : FullTextState

    /** Loading failed; showing the feed's text. */
    data object Failed : FullTextState
}

/** What the reader's buttons do. Implemented by the navigation, which talks to other apps. */
class ReaderActions(
    val onBack: () -> Unit,
    val onStyleChange: (ReaderStyle) -> Unit,
    val onSave: () -> Unit,
    val onShare: () -> Unit,
    val onOpenInBrowser: () -> Unit,
    val onMarkUnread: () -> Unit,
    /** Switches between the full article and the feed's text. */
    val onToggleFullText: () -> Unit,
    val onOpenLink: (String) -> Unit,
)

/** One article, in the reader's font, size, spacing and page colour. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    article: Article?,
    feed: Feed?,
    style: ReaderStyle,
    saveTarget: ShareTarget,
    shareTarget: ShareTarget,
    fullText: FullTextState,
    showImages: Boolean,
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
                                    (saveTarget as? ShareTarget.App)?.let { stringResource(R.string.action_save_to, it.label) }
                                        ?: stringResource(R.string.action_save_later),
                            )
                        }
                        IconButton(onClick = actions.onShare) {
                            Icon(
                                painterResource(R.drawable.ic_share),
                                contentDescription =
                                    (shareTarget as? ShareTarget.App)?.let { stringResource(R.string.action_share_to, it.label) }
                                        ?: stringResource(R.string.action_share),
                            )
                        }
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.action_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (hasLink) {
                            val showingFull = fullText is FullTextState.Shown || fullText is FullTextState.Loading
                            DropdownMenuItem(
                                text = { Text(stringResource(if (showingFull) R.string.reader_show_feed_text else R.string.reader_show_full)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_article), contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    actions.onToggleFullText()
                                },
                            )
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
                showImages = showImages,
                article = article,
                fullText = fullText,
                feed = feed,
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
                        .padding(vertical = 8.dp)
                        .navigationBarsPadding(),
                horizontalPadding = 24.dp,
            )
        }
    }
}

@Composable
private fun ArticleBody(
    article: Article,
    fullText: FullTextState,
    feed: Feed?,
    style: ReaderStyle,
    showImages: Boolean,
    onOpenLink: (String) -> Unit,
    onOpenInBrowser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val openLink by rememberUpdatedState(onOpenLink)
    val html = (fullText as? FullTextState.Shown)?.html ?: article.html
    val baseUrl = article.link ?: article.feedUrl
    val hero = article.imageUrl?.takeIf { showImages }
    val blocks =
        remember(html, baseUrl, hero, showImages) {
            ArticleBlocks
                .split(html, baseUrl)
                // The hero already shows this picture; without pictures, keep only text and embeds.
                .filterNot { it is ArticleBlock.Image && (!showImages || ArticleBlocks.isSameImage(it.url, hero)) }
        }
    val links = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
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
            if (hero != null) HeroImage(hero)
            if (feed != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeedIcon(feed, FeedIconSize.Medium)
                    Text(feed.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            // Tapping the title opens the article on its website, like "Read on website".
            val openLabel = stringResource(R.string.action_open_in_browser)
            Text(
                text = article.title,
                style =
                    MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = style.font.fontFamily(),
                        fontWeight = FontWeight.Bold,
                    ),
                modifier =
                    Modifier
                        .semantics { heading() }
                        .then(
                            if (article.link != null) {
                                Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpenInBrowser)
                            } else {
                                Modifier
                            },
                        ),
            )
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when (fullText) {
                FullTextState.Loading -> {
                    val loading = stringResource(R.string.reader_loading_full)
                    LinearProgressIndicator(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = loading },
                    )
                }

                FullTextState.Failed ->
                    Text(
                        text = stringResource(R.string.reader_full_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )

                else -> Unit
            }
            for (block in blocks) {
                when (block) {
                    is ArticleBlock.Text -> TextBlock(block.html, bodyStyle, links) { openLink(it) }
                    is ArticleBlock.Image -> InlineImage(block.url, block.caption)
                    is ArticleBlock.Embed -> EmbedCard(block, showThumbnail = showImages, onOpen = { openLink(block.url) })
                }
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
