package be.wimvandonck.reader.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import be.wimvandonck.reader.data.Feed

/**
 * The feed's website icon, or the first letter of its name on a tinted square while the icon is
 * unknown or can't be loaded. Decorative: the feed's name is always shown next to it.
 */
@Composable
fun FeedIcon(
    feed: Feed?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(size / 4)
    var failed by remember(feed?.iconUrl) { mutableStateOf(false) }
    val boxModifier =
        modifier
            .size(size)
            .clip(shape)
    if (feed?.iconUrl != null && !failed) {
        // Site icons are made for light browser tabs: a light tile keeps dark logos visible in
        // dark mode too, and gives every icon the same shape.
        Box(
            boxModifier
                .background(ICON_TILE)
                .padding(size / 10),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = feed.iconUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    } else {
        Box(
            boxModifier.background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = feed?.title?.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "",
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                fontSize = (size.value * 0.55f).sp,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** Behind site icons in light and dark mode; Chrome uses a similar light tile for favicons. */
private val ICON_TILE = Color(0xFFF4F4F4)

/** Sizes used across the app. */
object FeedIconSize {
    val Small = 16.dp
    val Medium = 24.dp
    val Large = 32.dp
}
