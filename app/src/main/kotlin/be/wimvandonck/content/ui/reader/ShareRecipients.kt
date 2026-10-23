package be.wimvandonck.content.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import be.wimvandonck.content.sync.Gravatar
import coil3.compose.SubcomposeAsyncImage

/**
 * What Send to a Content user offers (#235): the people shared with before, most recent first, how to
 * find their Gravatar names (#234), and whether pictures may load (Show images).
 */
class ShareRecipients(
    val emails: List<String> = emptyList(),
    val showImages: Boolean = false,
    val nameOf: suspend (String) -> String? = { null },
)

/** The recipients for the dialogs below the navigation, which opens them from the reader and Manage tags. */
val LocalShareRecipients = staticCompositionLocalOf { ShareRecipients() }

/** A recipient's Gravatar name, or null while unknown or without one. */
@Composable
fun recipientName(
    recipients: ShareRecipients,
    email: String,
): String? {
    val name by produceState<String?>(null, email) { value = recipients.nameOf(email)?.takeIf { it.isNotBlank() } }
    return name
}

/** A recipient's Gravatar picture, else the first letter of their name or address in a circle. */
@Composable
fun RecipientAvatar(
    email: String,
    name: String?,
    showImages: Boolean,
    size: Dp = 24.dp,
) {
    val letter: @Composable () -> Unit = {
        Box(
            Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                (name ?: email).trim().take(1).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
    if (!showImages) {
        letter()
        return
    }
    SubcomposeAsyncImage(
        model = Gravatar.url(email, 64),
        contentDescription = null,
        loading = { letter() },
        error = { letter() },
        modifier = Modifier.size(size).clip(CircleShape),
    )
}
