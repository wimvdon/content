package be.wimvandonck.content.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.contextmenu.modifier.filterTextContextMenuComponents
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import android.content.ClipData
import be.wimvandonck.content.ui.ExternalApps
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import be.wimvandonck.content.data.Mark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import be.wimvandonck.content.R
import be.wimvandonck.content.feed.ArticleBlock
import be.wimvandonck.content.feed.EmbedKind

/** The article's main picture above the title, 16:9. Disappears when it can't be loaded. */
@Composable
fun HeroImage(url: String) {
    var failed by remember(url) { mutableStateOf(false) }
    if (failed) return
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onError = { failed = true },
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

/**
 * Highlights in the reader (#102): each paragraph's marks, and what selecting text and tapping a mark
 * do. Null where the text can't be highlighted.
 */
class Highlighting(
    val marks: Map<Int, List<Mark>>,
    /** Text selected in paragraph [Int] was highlighted, with a note to write when the Boolean is true. */
    val onCreate: (Int, String, Boolean) -> Unit,
    /** A highlight was tapped. */
    val onOpen: (String) -> Unit,
)

/** A paragraph's place among the reader's paragraphs, for [Highlighting]. */
class Paragraph(
    val index: Int,
    val highlighting: Highlighting,
)

/** A paragraph's text as the reader shows it, for anchoring highlights (the same text [TextBlock] draws). */
fun paragraphText(html: String): String = AnnotatedString.fromHtml(html).trimmed().text

/**
 * A run of article text in the reader's style; links open through [onOpenLink]. With [paragraph],
 * its highlights are drawn and selecting text offers Highlight and Note.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TextBlock(
    html: String,
    style: TextStyle,
    links: TextLinkStyles,
    modifier: Modifier = Modifier,
    paragraph: Paragraph? = null,
    onOpenLink: (String) -> Unit,
) {
    val open by rememberUpdatedState(onOpenLink)
    val openHighlight by rememberUpdatedState(paragraph?.highlighting?.onOpen)
    val marks = paragraph?.let { it.highlighting.marks[it.index] }.orEmpty()
    val exactColor = HIGHLIGHT
    val roughColor = HIGHLIGHT.copy(alpha = 0.18f)
    val text =
        remember(html, links, marks) {
            val base =
                AnnotatedString
                    .fromHtml(html, linkStyles = links) { link -> (link as? LinkAnnotation.Url)?.let { open(it.url) } }
                    .trimmed()
            if (marks.isEmpty()) {
                base
            } else {
                buildAnnotatedString {
                    append(base)
                    for (mark in marks) {
                        val start = mark.start.coerceIn(0, base.length)
                        val end = mark.end.coerceIn(start, base.length)
                        if (start == end) continue
                        addStyle(
                            SpanStyle(
                                background = if (mark.approximate) roughColor else exactColor,
                                textDecoration = if (mark.approximate) TextDecoration.Underline else null,
                            ),
                            start,
                            end,
                        )
                        addLink(LinkAnnotation.Clickable(mark.highlightId) { openHighlight?.invoke(mark.highlightId) }, start, end)
                    }
                }
            }
        }
    if (text.isEmpty()) return
    if (paragraph == null) {
        SelectionContainer { Text(text, style = style, modifier = modifier) }
        return
    }
    // Highlight and Note come first in the menu over selected text (#251). Compose always puts its own
    // items first, so the whole menu is made here: Highlight, Note, Copy, Share, Select all, then the
    // apps that act on selected text (Translate, …), as Android offers them.
    val selection = rememberSelectionState()
    val highlightLabel = stringResource(R.string.highlight_action)
    val noteLabel = stringResource(R.string.note_title)
    val copyLabel = stringResource(android.R.string.copy)
    val shareLabel = stringResource(R.string.action_share)
    val selectAllLabel = stringResource(android.R.string.selectAll)
    val context = LocalContext.current
    val textApps = remember(context) { ExternalApps.processTextApps(context) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val create by rememberUpdatedState(paragraph.highlighting.onCreate)
    val index = paragraph.index
    fun selected() = selection.selectedTexts.joinToString("") { it.text }
    SelectionContainer(
        selection,
        Modifier
            .filterTextContextMenuComponents { it.key is OwnMenuKey }
            .appendTextContextMenuComponents {
                item(HighlightKey, highlightLabel, R.drawable.ic_highlight) {
                    val selected = selection.selectedTexts.joinToString("") { it.text }
                    close()
                    selection.clear()
                    create(index, selected, false)
                }
                item(NoteKey, noteLabel, R.drawable.ic_edit_note) {
                    val selected = selection.selectedTexts.joinToString("") { it.text }
                    close()
                    selection.clear()
                    create(index, selected, true)
                }
                item(CopyKey, copyLabel) {
                    val text = selected()
                    close()
                    selection.clear()
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(text, text))) }
                }
                item(ShareKey, shareLabel) {
                    val text = selected()
                    close()
                    ExternalApps.shareText(context, text, shareLabel)
                }
                item(SelectAllKey, selectAllLabel) { selection.selectAll() }
                for (app in textApps) {
                    item(ProcessTextKey(app.component.flattenToString()), app.label) {
                        val text = selected()
                        close()
                        ExternalApps.processText(context, app, text)
                    }
                }
            },
    ) { Text(text, style = style, modifier = modifier) }
}

/** A highlight's colour: a marker yellow that reads on the light, sepia and dark pages. */
private val HIGHLIGHT = Color(0xFFFFD54F).copy(alpha = 0.45f)

/** The menu's own items: everything else Compose would add is left out (see [TextBlock]). */
private interface OwnMenuKey

private object HighlightKey : OwnMenuKey

private object NoteKey : OwnMenuKey

private object CopyKey : OwnMenuKey

private object ShareKey : OwnMenuKey

private object SelectAllKey : OwnMenuKey

private data class ProcessTextKey(
    val component: String,
) : OwnMenuKey

/** A heading inside the article: bolder and, for h1 to h3, larger than the text. */
@Composable
fun HeadingBlock(
    block: ArticleBlock.Heading,
    style: TextStyle,
    links: TextLinkStyles,
    paragraph: Paragraph? = null,
    onOpenLink: (String) -> Unit,
) {
    val scale =
        when (block.level) {
            1, 2 -> 1.3f
            3 -> 1.15f
            else -> 1f
        }
    TextBlock(
        block.html,
        style.copy(fontSize = style.fontSize * scale, fontWeight = FontWeight.Bold, lineHeight = 1.3.em),
        links,
        Modifier.padding(top = 8.dp).semantics { heading() },
        paragraph,
        onOpenLink,
    )
}

/** A quote: its paragraphs beside a bar in the theme colour. */
@Composable
fun QuoteBlock(
    block: ArticleBlock.Quote,
    style: TextStyle,
    links: TextLinkStyles,
    /** The first paragraph's place; the others follow it. */
    first: Paragraph? = null,
    onOpenLink: (String) -> Unit,
) {
    val barColor = MaterialTheme.colorScheme.primary
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .drawBehind { drawRect(barColor, size = Size(3.dp.toPx(), size.height)) }
                .padding(start = 16.dp),
    ) {
        block.paragraphs.forEachIndexed { i, paragraph ->
            TextBlock(
                paragraph,
                style.copy(fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant),
                links,
                paragraph = first?.let { Paragraph(it.index + i, it.highlighting) },
                onOpenLink = onOpenLink,
            )
        }
    }
}

/** Code or other preformatted text: monospace, its own line breaks, scrolls sideways. */
@Composable
fun CodeBlock(
    block: ArticleBlock.Code,
    style: TextStyle,
) {
    SelectionContainer {
        Text(
            block.text,
            style = style.copy(fontFamily = FontFamily.Monospace, fontSize = style.fontSize * 0.85f, lineHeight = 1.4.em),
            softWrap = false,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
        )
    }
}

/** A list: each item behind its bullet or number, nested items further in. */
@Composable
fun ListBlockView(
    block: ArticleBlock.ListBlock,
    style: TextStyle,
    links: TextLinkStyles,
    /** The first item's place; the others follow it. */
    first: Paragraph? = null,
    onOpenLink: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        block.items.forEachIndexed { i, item ->
            Row(Modifier.padding(start = (20 * (item.depth - 1)).dp)) {
                Text(
                    item.marker,
                    style = style,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(28.dp).padding(end = 8.dp).clearAndSetSemantics {},
                )
                TextBlock(item.html, style, links, Modifier.weight(1f), first?.let { Paragraph(it.index + i, it.highlighting) }, onOpenLink)
            }
        }
    }
}

/** A picture inside the article, full width, with its caption. */
@Composable
fun InlineImage(
    url: String,
    caption: String?,
) {
    var failed by remember(url) { mutableStateOf(false) }
    if (failed) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AsyncImage(
            model = url,
            contentDescription = caption,
            contentScale = ContentScale.FillWidth,
            onError = { failed = true },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium),
        )
        if (caption != null) {
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Embedded content (a video, a post) as a card: a preview picture with a play button when there
 * is one, and what tapping it opens. The player itself opens in its app or the browser.
 */
@Composable
fun EmbedCard(
    embed: ArticleBlock.Embed,
    showThumbnail: Boolean,
    onOpen: () -> Unit,
) {
    val label =
        stringResource(
            when (embed.kind) {
                EmbedKind.YouTube -> R.string.embed_youtube
                EmbedKind.Vimeo -> R.string.embed_vimeo
                EmbedKind.Video -> R.string.embed_video
                EmbedKind.Other -> R.string.embed_other
            },
        )
    val playable = embed.kind != EmbedKind.Other
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            if (showThumbnail && embed.thumbnailUrl != null) {
                Box(contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = embed.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_play),
                        contentDescription = null,
                        tint = Color.White,
                        modifier =
                            Modifier
                                .size(56.dp)
                                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                                .padding(12.dp),
                    )
                }
            }
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    painterResource(if (playable) R.drawable.ic_play else R.drawable.ic_open_in_new),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        embed.url.substringAfter("://").substringBefore('/'),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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

