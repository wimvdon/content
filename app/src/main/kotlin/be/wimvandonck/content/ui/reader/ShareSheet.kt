package be.wimvandonck.content.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import be.wimvandonck.content.ui.ExternalApps

/**
 * Content's own share sheet (#251), in place of Android's: first Send with Content, the same as in
 * the Save and share sheet ([SendToSection], #256: the people shared with before as chips to pick,
 * Add address for someone new, then Send to everyone picked); then the apps used most from this sheet
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
    /** Sends to everyone picked under Send with Content. */
    onSend: (emails: List<String>) -> Unit,
    /** Whether an address added under Send with Content has a Content account; null when that couldn't be asked. */
    hasAccount: suspend (String) -> Boolean?,
    onApp: (ExternalApps.ShareApp) -> Unit,
    onCopyLink: () -> Unit,
    onMore: () -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(listOf<String>()) }
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
                Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SendToSection(picked, onChange = { picked = it }, hasAccount = hasAccount)
                    // Full width under the people: plainly the button for this part, not for the apps below.
                    Button(onClick = { onSend(picked) }, enabled = picked.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.share_user_send))
                    }
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
