package be.wimvandonck.content.ui.reader

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
import be.wimvandonck.content.data.Item
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
import androidx.compose.material3.IconToggleButton
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
import be.wimvandonck.content.R
import be.wimvandonck.content.data.ArticleReport
import androidx.compose.ui.res.pluralStringResource
import java.util.UUID
import be.wimvandonck.content.data.Highlight
import be.wimvandonck.content.data.ArticleText
import be.wimvandonck.content.data.Anchors
import be.wimvandonck.content.data.Anchor
import be.wimvandonck.content.ui.theme.EmptyState
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextOverflow
import be.wimvandonck.content.data.Place
import be.wimvandonck.content.data.Article
import be.wimvandonck.content.data.Feed
import be.wimvandonck.content.ui.home.FeedIcon
import be.wimvandonck.content.ui.home.FeedIconSize
import be.wimvandonck.content.data.ReaderStyle
import be.wimvandonck.content.feed.ArticleBlock
import be.wimvandonck.content.feed.ArticleBlocks

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
    /** Signs in to the article's site (#180), so articles you pay for load in full. Null without an https link. */
    val onSignIn: (() -> Unit)? = null,
    /** Reports a badly cleaned-up article (#181): a pre-filled GitHub issue in the browser. Null without a link. */
    val onReport: ((ArticleReport.Problem, String) -> Unit)? = null,
)

/**
 * A saved item in the reader (#107, #119): its state and what its buttons do. Null for feed articles
 * not saved.
 */
class ReaderItem(
    val item: Item,
    /** Opens Organise (#139, #143): the folder, tags with Like, and the note; New folder and Add a tag. */
    val onOrganise: () -> Unit,
    /** Out of Saved, with its note, tags and highlights. */
    val onRemove: () -> Unit,
    /** How far it was read, 0 to 1, as the reader scrolls. */
    val onProgress: (Float) -> Unit,
)

/**
 * One article, in the reader's font, size, spacing and page colour (#139). The top bar is for this
 * reading (Back, Aa, ⋮); the bar below is for this article in your library: one Save before it is
 * saved, then its folder (which opens Organise); its highlights and Share at the right end. The text
 * ends with Read on website.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    article: Article?,
    feed: Feed?,
    style: ReaderStyle,
    fullText: FullTextState,
    showImages: Boolean,
    snackbarHostState: SnackbarHostState,
    actions: ReaderActions,
    /** In Saved: the bar below shows its folder, Like and Archive instead of Save. */
    item: ReaderItem? = null,
    /** Highlights (#102); null where the article can't be highlighted (no link). */
    highlights: ReaderHighlights? = null,
) {
    var highlightsOpen by rememberSaveable { mutableStateOf(false) }
    var showStyleSheet by rememberSaveable { mutableStateOf(false) }
    var removeOpen by rememberSaveable { mutableStateOf(false) }
    var reportOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val hasLink = article?.link != null
    val onList = item?.item?.readingList != null
    val scrollState = rememberScrollState()
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
                    // The full article or the feed's text, one tap away: on while the full article shows.
                    // Not for a saved article: it always shows the whole article (#208).
                    if (hasLink && !onList) {
                        val showingFull = fullText is FullTextState.Shown || fullText is FullTextState.Loading
                        IconToggleButton(checked = showingFull, onCheckedChange = { actions.onToggleFullText() }) {
                            Icon(
                                painterResource(R.drawable.ic_article),
                                contentDescription = stringResource(if (showingFull) R.string.reader_show_feed_text else R.string.reader_show_full),
                            )
                        }
                    }
                    IconButton(onClick = { showStyleSheet = true }) {
                        Icon(painterResource(R.drawable.ic_text_format), contentDescription = stringResource(R.string.reader_text_settings))
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
                        val signIn = actions.onSignIn
                        if (signIn != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.site_sign_in_menu)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_login), contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    signIn()
                                },
                            )
                        }
                        if (actions.onReport != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.report_menu)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_flag), contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    reportOpen = true
                                },
                            )
                        }
                        if (item != null && onList) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.saved_remove)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    removeOpen = true
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
            // The library bar (#139, #156): on the left Save, or once saved the folder and the tags;
            // on the right Share.
            if (article != null && (hasLink || onList)) {
                BottomAppBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    if (item != null && onList) {
                        SavedBar(item)
                    } else {
                        FilledTonalButton(onClick = actions.onSave, modifier = Modifier.padding(start = 16.dp).weight(1f, fill = false)) {
                            Icon(painterResource(R.drawable.ic_list), contentDescription = null)
                            Text(
                                stringResource(R.string.action_save),
                                modifier = Modifier.padding(start = 8.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    // The article's highlights, with their count, next to Share.
                    val count = highlights?.highlights?.size ?: 0
                    if (count > 0) {
                        val label = pluralStringResource(R.plurals.highlights_count, count, count)
                        TextButton(onClick = { highlightsOpen = true }, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = label }) {
                            Icon(painterResource(R.drawable.ic_quote), contentDescription = null)
                            Text(count.toString(), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    if (hasLink) {
                        IconButton(onClick = actions.onShare, modifier = Modifier.padding(end = 4.dp)) {
                            Icon(painterResource(R.drawable.ic_share), contentDescription = stringResource(R.string.action_share))
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        if (article == null) {
            // Like every empty screen: the shared empty state, with a way back.
            EmptyState(
                painterResource(R.drawable.ic_article),
                stringResource(R.string.reader_article_gone),
                modifier = Modifier.padding(innerPadding),
            ) {
                FilledTonalButton(onClick = actions.onBack) { Text(stringResource(R.string.action_back)) }
            }
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
                highlights = highlights,
                highlightsOpen = highlightsOpen,
                onHighlightsOpenChange = { highlightsOpen = it },
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

    // Any article can be reported, saved or not (it only showed for saved ones before #251).
    if (reportOpen) {
        actions.onReport?.let { report ->
            ReportSheet(
                onSend = { problem, comment ->
                    reportOpen = false
                    report(problem, comment)
                },
                onDismiss = { reportOpen = false },
            )
        }
    }
    if (item != null && removeOpen) RemoveDialog(item.onRemove, onDismiss = { removeOpen = false })

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
    highlights: ReaderHighlights?,
    highlightsOpen: Boolean,
    onHighlightsOpenChange: (Boolean) -> Unit,
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
    // The text's paragraphs, in order, as one text that highlights are anchored in (#102).
    val articleText =
        remember(blocks) {
            ArticleText(
                blocks
                    .flatMap { b ->
                        when (b) {
                            is ArticleBlock.Text -> listOf(b.html)
                            is ArticleBlock.Heading -> listOf(b.html)
                            is ArticleBlock.Quote -> b.paragraphs
                            is ArticleBlock.ListBlock -> b.items.map { it.html }
                            else -> emptyList()
                        }
                    }.map(::paragraphText),
            )
        }
    val placed = remember(articleText, highlights?.highlights) { highlights?.let { articleText.place(it.highlights) } }
    var openHighlight by rememberSaveable(article.key) { mutableStateOf<String?>(null) }
    // A new highlight waiting for its note (Note in the selection menu).
    var draft by remember(article.key) { mutableStateOf<Highlight?>(null) }
    val highlighting =
        if (highlights != null && placed != null) {
            Highlighting(
                marks = placed.marks,
                onCreate = { index, selected, withNote ->
                    articleText.locate(index, selected)?.let { range ->
                        val made = Anchors.create(articleText.text, range.first, range.last + 1, UUID.randomUUID().toString(), System.currentTimeMillis())
                        if (withNote) draft = made else highlights.onAdd(made)
                    }
                },
                onOpen = { openHighlight = it },
            )
        } else {
            null
        }
    var nextParagraph = 0

    fun paragraph(count: Int = 1): Paragraph? {
        val first = nextParagraph
        nextParagraph += count
        return highlighting?.let { Paragraph(first, it) }
    }
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
                    is ArticleBlock.Text -> TextBlock(block.html, bodyStyle, links, paragraph = paragraph()) { openLink(it) }
                    is ArticleBlock.Heading -> HeadingBlock(block, bodyStyle, links, paragraph()) { openLink(it) }
                    is ArticleBlock.Quote -> QuoteBlock(block, bodyStyle, links, paragraph(block.paragraphs.size)) { openLink(it) }
                    is ArticleBlock.Code -> CodeBlock(block, bodyStyle)
                    is ArticleBlock.ListBlock -> ListBlockView(block, bodyStyle, links, paragraph(block.items.size)) { openLink(it) }
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
    if (highlights != null) {
        HighlightDialogs(
            highlights = highlights,
            anchors = placed?.anchors.orEmpty(),
            openId = openHighlight,
            onOpenIdChange = { openHighlight = it },
            draft = draft,
            onDraftChange = { draft = it },
            listOpen = highlightsOpen,
            onListOpenChange = onHighlightsOpenChange,
        )
    }
}

/** The highlight sheets of the reader: one highlight, a new one's note, and the list. */
@Composable
private fun HighlightDialogs(
    highlights: ReaderHighlights,
    anchors: Map<String, Anchor>,
    openId: String?,
    onOpenIdChange: (String?) -> Unit,
    draft: Highlight?,
    onDraftChange: (Highlight?) -> Unit,
    listOpen: Boolean,
    onListOpenChange: (Boolean) -> Unit,
) {
    val open = openId?.let { id -> highlights.highlights.firstOrNull { it.id == id } }
    if (open != null) {
        HighlightSheet(
            highlight = open,
            anchor = anchors[open.id],
            onSave = { note -> if (note.trim() != open.note) highlights.onNote(open.id, note) },
            onRemove = { highlights.onRemove(open.id) },
            onDismiss = { onOpenIdChange(null) },
        )
    }
    if (draft != null) {
        HighlightSheet(
            highlight = draft,
            anchor = null,
            onSave = { note -> highlights.onAdd(draft.copy(note = note.trim())) },
            onRemove = null,
            onDismiss = { onDraftChange(null) },
        )
    }
    if (listOpen) {
        HighlightsSheet(
            highlights = highlights.highlights,
            anchors = anchors,
            notCopied = highlights.notCopied,
            onOpen = {
                onListOpenChange(false)
                onOpenIdChange(it)
            },
            onDismiss = { onListOpenChange(false) },
        )
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
