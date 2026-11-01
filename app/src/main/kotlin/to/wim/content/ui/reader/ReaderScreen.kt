package to.wim.content.ui.reader

import android.text.format.DateUtils
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import to.wim.content.data.Item
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
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import to.wim.content.R
import to.wim.content.data.ArticleReport
import androidx.compose.ui.res.pluralStringResource
import java.util.UUID
import to.wim.content.data.Highlight
import to.wim.content.data.ArticleText
import to.wim.content.data.Anchors
import to.wim.content.data.Anchor
import to.wim.content.ui.theme.EmptyState
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextOverflow
import to.wim.content.data.Place
import to.wim.content.data.Article
import to.wim.content.data.Feed
import to.wim.content.ui.home.FeedIcon
import to.wim.content.ui.home.FeedIconSize
import to.wim.content.data.ReaderStyle
import to.wim.content.feed.ArticleBlock
import to.wim.content.feed.ArticleBlocks
import androidx.compose.runtime.snapshotFlow
import to.wim.content.data.ReadingPosition
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first

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
    /** Archive, or back to To read when archived; the reader stays open and a message says so. */
    val onArchive: () -> Unit = {},
    /** Opens the topic chips (the Topics sheet). */
    val onTopics: () -> Unit = {},
    /** Opens the note (the Note sheet). */
    val onNote: () -> Unit = {},
    /** Like, or Unlike when liked. */
    val onLike: () -> Unit = {},
    /** Remove from Saved, in ⋮ (it asks first). */
    val onRemove: () -> Unit = {},
    /** How far it was read, 0 to 1, as the reader scrolls. */
    val onProgress: (Float) -> Unit,
)

/**
 * One article, in the reader's font, size, spacing and page colour (#139). The top bar is for this
 * reading (Back, Aa, ⋮); the floating toolbar below is for this article in your library: one Save
 * before it is saved, then Archive, Topics, Note and Like ([SavedBar]); its highlights, and Share as
 * its own round button. Remove from Saved is in ⋮. The text ends with Read on website.
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
    /** In Saved: the bar below shows Archive and Organise instead of Save. */
    item: ReaderItem? = null,
    /** Highlights (#102); null where the article can't be highlighted (no link). */
    highlights: ReaderHighlights? = null,
) {
    var highlightsOpen by rememberSaveable { mutableStateOf(false) }
    var showStyleSheet by rememberSaveable { mutableStateOf(false) }
    var reportOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val hasLink = article?.link != null
    val onList = item?.item?.readingList != null
    val scrollState = rememberScrollState()
    // The library actions float over the article (#139, #156, #305), stepping aside while you read down.
    val hasToolbar = article != null && (hasLink || onList)
    val toolbarShown by remember {
        derivedStateOf { ReaderToolbarVisibility.shown(scrollBehavior.state.collapsedFraction, atEnd = !scrollState.canScrollForward) }
    }
    // The position is worked out on the text that stays: not while the full text is still loading (#270).
    if (item != null && onList) ReadingProgress(scrollState, item.item.progress, ready = fullText !is FullTextState.Loading, item.onProgress)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // Messages (Archived, with Undo) appear above the floating toolbar while it shows.
        snackbarHost = {
            SnackbarHost(snackbarHostState, Modifier.padding(bottom = if (hasToolbar && toolbarShown) ReaderToolbarHeight + ReaderToolbarMargin else 0.dp))
        },
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
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.action_more))
                    }
                    // In this order: Mark as unread, the feed's text or the full article (a feed article;
                    // a saved one always shows the whole article, #208), Open in browser, Sign in to
                    // website, Report a problem. Remove from Saved is in Organise.
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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
                        if (hasLink && !onList) {
                            val showingFull = fullText is FullTextState.Shown || fullText is FullTextState.Loading
                            DropdownMenuItem(
                                text = { Text(stringResource(if (showingFull) R.string.reader_show_feed_text else R.string.reader_show_full)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_article), contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    actions.onToggleFullText()
                                },
                            )
                        }
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
                        // A saved article: Remove from Saved, the rare one, here and not in the toolbar.
                        if (item != null && onList) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.saved_remove)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    item.onRemove()
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
            Box(Modifier.fillMaxSize()) {
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
                // Room under the text, so its end can scroll clear of the toolbar.
                bottomSpace = if (hasToolbar) ReaderToolbarHeight + ReaderToolbarMargin else 0.dp,
                modifier = Modifier.padding(innerPadding),
            )
            if (hasToolbar) {
                // Save, or once saved Archive, Topics, Note and Like; the highlights' count; Share as
                // its own round button next to the pill.
                ReaderToolbar(
                    visible = toolbarShown,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(innerPadding)
                            .padding(start = 16.dp, end = 16.dp, bottom = ReaderToolbarMargin),
                    fab =
                        if (hasLink) {
                            {
                                FloatingActionButton(
                                    onClick = actions.onShare,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(ReaderToolbarHeight),
                                ) {
                                    Icon(painterResource(R.drawable.ic_share), contentDescription = stringResource(R.string.action_share))
                                }
                            }
                        } else {
                            null
                        },
                ) {
                    if (item != null && onList) {
                        SavedBar(item)
                    } else {
                        FilledTonalButton(onClick = actions.onSave) {
                            Icon(painterResource(R.drawable.ic_list), contentDescription = null)
                            Text(
                                stringResource(R.string.action_save),
                                modifier = Modifier.padding(start = 8.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    val count = highlights?.highlights?.size ?: 0
                    if (count > 0) {
                        val label = pluralStringResource(R.plurals.highlights_count, count, count)
                        TextButton(onClick = { highlightsOpen = true }, colors = toolbarTextButtonColors(), modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = label }) {
                            Icon(painterResource(R.drawable.ic_quote), contentDescription = null)
                            Text(count.toString(), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
            }
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
    bottomSpace: Dp = 0.dp,
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
                    .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp + bottomSpace),
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
                    is ArticleBlock.Embed -> EmbedBlock(block, showThumbnail = showImages, onOpen = openLink)
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
 * doesn't send a change at every pause. Scrolling back waits until the text is [ready] and the
 * page's height has settled (#270); until then nothing is reported, so the stored progress stays.
 */
@OptIn(FlowPreview::class)
@Composable
private fun ReadingProgress(
    scrollState: ScrollState,
    start: Float,
    ready: Boolean,
    onProgress: (Float) -> Unit,
) {
    val report by rememberUpdatedState(onProgress)
    var restored by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(ready) {
        if (restored || !ready) return@LaunchedEffect
        val max = snapshotFlow { scrollState.maxValue }.debounce(ReadingPosition.SETTLE_MS).first { it > 0 }
        // Scrolled by hand meanwhile: that is where the reader is.
        if (scrollState.value == 0) ReadingPosition.scrollFor(start, max)?.let { scrollState.scrollTo(it) }
        restored = true
    }
    fun position(): Float? = if (restored) ReadingPosition.of(scrollState.value, scrollState.maxValue) else null
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { position()?.let(report) }
    DisposableEffect(Unit) { onDispose { position()?.let(report) } }
}
