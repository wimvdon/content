package io.github.impca201.rssreader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import io.github.impca201.rssreader.R
import io.github.impca201.rssreader.feed.ArticleBlock
import io.github.impca201.rssreader.feed.EmbedKind

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

/** A run of article text in the reader's style; links open through [onOpenLink]. */
@Composable
fun TextBlock(
    html: String,
    style: TextStyle,
    links: TextLinkStyles,
    onOpenLink: (String) -> Unit,
) {
    val open by rememberUpdatedState(onOpenLink)
    val text =
        remember(html, links) {
            AnnotatedString
                .fromHtml(html, linkStyles = links) { link -> (link as? LinkAnnotation.Url)?.let { open(it.url) } }
                .trimmed()
        }
    if (text.isNotEmpty()) SelectionContainer { Text(text, style = style) }
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

