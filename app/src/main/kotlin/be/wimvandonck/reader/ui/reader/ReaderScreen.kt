package be.wimvandonck.reader.ui.reader

import android.text.format.DateUtils
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import be.wimvandonck.reader.data.Item
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
import be.wimvandonck.reader.R
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextOverflow
import be.wimvandonck.reader.data.Saved
import be.wimvandonck.reader.data.Article
import be.wimvandonck.reader.data.Feed
import be.wimvandonck.reader.ui.home.FeedIcon
import be.wimvandonck.reader.ui.home.FeedIconSize
import be.wimvandonck.reader.data.ReaderStyle
import be.wimvandonck.reader.data.ShareTarget
import be.wimvandonck.reader.feed.ArticleBlock
import be.wimvandonck.reader.feed.ArticleBlocks

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
    /** Null for what has no read state (an item that isn't a feed article). */
    val onMarkUnread: (() -> Unit)?,
    /** Switches between the full article and the feed's text. */
    val onToggleFullText: () -> Unit,
    val onOpenLink: (String) -> Unit,
    /** Keep as bookmark (#108), for a feed article not saved yet; null hides it. */
    val onKeepAsBookmark: (() -> Unit)? = null,
)

/**
 * A saved item in the reader (#107, #119): its state and what its buttons do. Null for feed articles
 * not saved.
 */
class ReaderItem(
    val item: Item,
    /** Every tag in use, to add one with a tap. */
    val allTags: List<String>,
    /** Every folder in use. */
    val folders: List<String>,
    val onLike: () -> Unit,
    val onArchive: () -> Unit,
    /** Back to To read. */
    val onUnarchive: () -> Unit,
    /** Files it in a folder (empty: To read), with its note and tags. */
    val onFile: (folder: String, note: String, tags: List<String>) -> Unit,
    val onTags: (List<String>) -> Unit,
    /** Out of Saved, with its note, tags and highlights. */
    val onRemove: () -> Unit,
    /** How far it was read, 0 to 1, as the reader scrolls. */
    val onProgress: (Float) -> Unit,
    /** The services that keep it while both are connected (#121): with two, removing asks where. */
    val services: Set<String> = emptySet(),
    /** Removes it from one service only; it stays in Saved, kept in the other. */
    val onRemoveFrom: (String) -> Unit = {},
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
    /** On the reading list: its buttons replace Save and Mark as unread. */
    item: ReaderItem? = null,
    /** Where the text came from, when it isn't the feed's ("Instapaper text"), shown above the title. */
    textSource: String? = null,
) {
    var showStyleSheet by rememberSaveable { mutableStateOf(false) }
    var tagsOpen by rememberSaveable { mutableStateOf(false) }
    var removeOpen by rememberSaveable { mutableStateOf(false) }
    var finishOpen by rememberSaveable { mutableStateOf(false) }
    var finishChoice by rememberSaveable { mutableStateOf<FinishChoice?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val hasLink = article?.link != null
    val onList = item?.item?.readingList != null
    val scrollState = rememberScrollState()
    fun finish(choice: FinishChoice?) {
        finishChoice = choice
        finishOpen = true
    }
    if (item != null && onList) ReadingProgress(scrollState, item.item.progress, item.onProgress)

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
                    if (item != null && onList) {
                        val liked = item.item.liked
                        IconButton(onClick = item.onLike) {
                            Icon(
                                painterResource(if (liked) R.drawable.ic_favorite_filled else R.drawable.ic_favorite),
                                contentDescription = stringResource(if (liked) R.string.action_unlike else R.string.action_like),
                                tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (hasLink) {
                        if (!onList) IconButton(onClick = actions.onSave) {
                            Icon(
                                painterResource(if (saveTarget is ShareTarget.App) R.drawable.ic_bookmark_add else R.drawable.ic_reading_list),
                                contentDescription =
                                    (saveTarget as? ShareTarget.App)?.let { stringResource(R.string.action_save_to, it.label) }
                                        ?: stringResource(R.string.action_read_later),
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
                        if (item != null && onList) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tags_title)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_tags), contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    tagsOpen = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.saved_remove)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    removeOpen = true
                                },
                            )
                        }
                        val keep = actions.onKeepAsBookmark
                        if (keep != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.file_title)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_folder), contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    keep()
                                },
                            )
                        }
                        val markUnread = actions.onMarkUnread
                        if (markUnread != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_mark_unread)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_mark_unread), contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    markUnread()
                                },
                            )
                        }
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
        bottomBar = {
            // File and Archive for a saved article (#107, #119).
            if (item != null && onList && article != null) {
                BottomAppBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    val folder = Saved.folderOf(item.item)
                    if (folder != null) {
                        Row(Modifier.padding(start = 16.dp).weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(painterResource(R.drawable.ic_folder), contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(folder, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    TextButton(onClick = { finish(FinishChoice.File) }) {
                        Icon(painterResource(R.drawable.ic_folder), contentDescription = null)
                        Text(stringResource(R.string.action_file), modifier = Modifier.padding(start = 8.dp))
                    }
                    if (item.item.archived) {
                        TextButton(onClick = item.onUnarchive) {
                            Icon(painterResource(R.drawable.ic_unarchive), contentDescription = null)
                            Text(stringResource(R.string.action_unarchive), modifier = Modifier.padding(start = 8.dp))
                        }
                    } else {
                        // Archive at once (Undo in the message); the sheet at the end of the text still asks.
                        TextButton(onClick = item.onArchive) {
                            Icon(painterResource(R.drawable.ic_inventory), contentDescription = null)
                            Text(stringResource(R.string.action_archive), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
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
                scrollState = scrollState,
                textSource = textSource,
                onFinished = if (item != null && onList && !item.item.archived) ({ finish(null) }) else null,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

    if (item != null) {
        if (tagsOpen) TagsDialog(item.item, item.allTags, item.onTags, onDismiss = { tagsOpen = false })
        if (removeOpen) RemoveDialog(item.item, item.onRemove, onDismiss = { removeOpen = false }, services = item.services, onRemoveFrom = item.onRemoveFrom)
        if (finishOpen) {
            FinishSheet(
                item = item.item,
                initial = finishChoice,
                folders = item.folders,
                allTags = item.allTags,
                onArchive = item.onArchive,
                onFile = item.onFile,
                onDismiss = { finishOpen = false },
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
    scrollState: ScrollState,
    textSource: String?,
    /** At the end of an article on the reading list: "Finished reading" opens the sheet that asks what's next. */
    onFinished: (() -> Unit)?,
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
                .verticalScroll(scrollState),
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
            if (textSource != null && fullText !is FullTextState.Shown) {
                Text(textSource, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    is ArticleBlock.Heading -> HeadingBlock(block, bodyStyle, links) { openLink(it) }
                    is ArticleBlock.Quote -> QuoteBlock(block, bodyStyle, links) { openLink(it) }
                    is ArticleBlock.Code -> CodeBlock(block, bodyStyle)
                    is ArticleBlock.ListBlock -> ListBlockView(block, bodyStyle, links) { openLink(it) }
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
            if (onFinished != null) {
                FilledTonalButton(onClick = onFinished, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null)
                    Text(stringResource(R.string.finish_title), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/**
 * Keeps an item's reading progress: scrolls back to where it was left when it opens, and reports
 * the position (0 to 1) when the article is left or the app goes to the background, so reading
 * doesn't send a change at every pause.
 */
@Composable
private fun ReadingProgress(
    scrollState: ScrollState,
    start: Float,
    onProgress: (Float) -> Unit,
) {
    val report by rememberUpdatedState(onProgress)
    var restored by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(scrollState.maxValue > 0) {
        if (!restored && scrollState.maxValue > 0) {
            restored = true
            if (start in 0.02f..0.98f) scrollState.scrollTo((scrollState.maxValue * start).toInt())
        }
    }
    fun position(): Float? = if (restored && scrollState.maxValue > 0) (scrollState.value.toFloat() / scrollState.maxValue).coerceIn(0f, 1f) else null
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { position()?.let(report) }
    DisposableEffect(Unit) { onDispose { position()?.let(report) } }
}
