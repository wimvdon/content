package be.wimvandonck.content.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import be.wimvandonck.content.R
import be.wimvandonck.content.data.ItemKeys
import be.wimvandonck.content.sync.ShareResult
import be.wimvandonck.content.ui.ExternalApps

/**
 * Content's own share sheet (#251), in place of Android's: first Send with Content, to the people
 * shared with before (one tap sends) or to another address, typed in the sheet itself (#256); then the apps used most from this sheet
 * (Android doesn't tell apps which ones you use elsewhere), Copy link, and More for Android's own
 * share sheet with every app.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShareSheet(
    title: String,
    url: String,
    /** Signed in to a Content account: Send with Content is offered. */
    signedIn: Boolean,
    /** The apps to show, in order (data/ShareApps.kt). */
    apps: List<ExternalApps.ShareApp>,
    onSendTo: (email: String) -> Unit,
    /** Sends to an address typed in the sheet (#256): what went wrong, shown under it, or null when it was sent. */
    onSendOther: suspend (email: String) -> ShareResult?,
    onApp: (ExternalApps.ShareApp) -> Unit,
    onCopyLink: () -> Unit,
    onMore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val recipients = LocalShareRecipients.current
    // Email address opens a field in the sheet itself, not a dialog on top of it (#256).
    var typing by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                Text(ItemKeys.hostOf(url).ifEmpty { url }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (signedIn) {
                Heading(stringResource(R.string.share_send_with_content))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    items(recipients.emails, key = { it }) { email ->
                        val name = recipientName(recipients, email)
                        Tile(label = name ?: email, onClick = { onSendTo(email) }) {
                            RecipientAvatar(email, name, recipients.showImages, size = TILE_ICON)
                        }
                    }
                    item(key = "other") {
                        Tile(label = stringResource(R.string.account_email), onClick = { typing = true }) {
                            Circle { Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                        }
                    }
                }
                if (typing) {
                    AddressField(
                        actionLabel = stringResource(R.string.share_user_send),
                        actionIcon = R.drawable.ic_share,
                        onSubmit = onSendOther,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
            Heading(stringResource(R.string.action_share))
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                maxItemsInEachRow = 4,
            ) {
                for (app in apps) {
                    val icon = remember(app) { app.icon.toBitmap(TILE_PIXELS, TILE_PIXELS).asImageBitmap() }
                    Tile(label = app.target.label, onClick = { onApp(app) }) {
                        Image(icon, contentDescription = null, modifier = Modifier.size(TILE_ICON))
                    }
                }
                Tile(label = stringResource(R.string.share_copy_link), onClick = onCopyLink) {
                    Circle { Icon(painterResource(R.drawable.ic_copy), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                }
                Tile(label = stringResource(R.string.share_more), onClick = onMore) {
                    Circle { Icon(painterResource(R.drawable.ic_more_horiz), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                }
            }
        }
    }
}

/** How big each tile's picture is. */
private val TILE_ICON: Dp = 48.dp

/** An app icon is drawn at this many pixels. */
private const val TILE_PIXELS = 144

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 24.dp).semantics { heading() },
    )
}

/** One choice: a round picture with its name below, the whole of it one button. */
@Composable
private fun Tile(
    label: String,
    onClick: () -> Unit,
    picture: @Composable () -> Unit,
) {
    Column(
        Modifier
            .width(76.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(TILE_ICON), contentAlignment = Alignment.Center) { picture() }
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Circle(content: @Composable () -> Unit) {
    Box(
        Modifier.size(TILE_ICON).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) { content() }
}
