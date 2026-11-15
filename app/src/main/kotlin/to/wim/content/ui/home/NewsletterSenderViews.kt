package to.wim.content.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import to.wim.content.R
import to.wim.content.data.Feed
import to.wim.content.data.NewsletterSender
import to.wim.content.data.NewsletterSenders
import to.wim.content.ui.reader.ContentSheet
import to.wim.content.ui.reader.SheetActions
import to.wim.content.ui.reader.SheetHeader
import to.wim.content.ui.reader.SheetSection
import to.wim.content.ui.reader.rememberSheetClose
import to.wim.content.ui.theme.AppShapes
import to.wim.content.ui.theme.SwitchSetting

/** The newsletter senders' names, merges and mutes, for every list and the reader. */
val LocalNewsletterSenders = staticCompositionLocalOf { NewsletterSenders() }

/** What a sender's page can change (NewsletterSenders). */
class SenderActions(
    val onRename: (key: String, name: String) -> Unit = { _, _ -> },
    val onMute: (key: String, muted: Boolean) -> Unit = { _, _ -> },
    /** Merges the first into the second, which keeps the name given. */
    val onMerge: (from: String, into: String, intoName: String) -> Unit = { _, _, _ -> },
    val onSplit: (key: String) -> Unit = {},
)

/** Senders, on the Newsletter feed page: each sender of the tag's feed, tap for its page. */
@Composable
internal fun SenderList(
    senders: List<NewsletterSender>,
    feed: Feed?,
    onOpen: (NewsletterSender) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SheetSection(stringResource(R.string.newsletter_senders))
        Text(stringResource(R.string.newsletter_senders_explained), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = AppShapes.Item, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
            Column {
                senders.forEach { sender ->
                    val count = pluralStringResource(R.plurals.newsletter_count, sender.count, sender.count)
                    ListItem(
                        headlineContent = { Text(sender.name, maxLines = 1) },
                        supportingContent = { Text(if (sender.muted) stringResource(R.string.newsletter_sender_muted_count, count) else count) },
                        leadingContent = { FeedIcon(sender.shownAs(feed), FeedIconSize.Medium, envelopeBadge = NewsletterSenders.siteOf(sender.key) != null) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier.clickable(role = Role.Button) { onOpen(sender) },
                    )
                }
            }
        }
    }
}

/**
 * One sender's page, a page of the Newsletter feed sheet: its name (empty: the sender's own),
 * Mute, Combine with another sender, and the senders combined into it, each with Separate.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SenderPage(
    sender: NewsletterSender,
    others: List<NewsletterSender>,
    feed: Feed?,
    givenName: String?,
    actions: SenderActions,
    onBack: () -> Unit,
) {
    var name by rememberSaveable(sender.key) { mutableStateOf(givenName.orEmpty()) }
    val save = { if (name.trim() != givenName.orEmpty()) actions.onRename(sender.key, name) }
    ContentSheet(onDismiss = {
        save()
        onBack()
    }) {
        Column(
            Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetHeader(sender.name, onCancel = {
                save()
                onBack()
            })
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.feed_name)) },
                placeholder = { Text(sender.members.first().name) },
                supportingText = { Text(stringResource(R.string.newsletter_sender_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SwitchSetting(
                title = stringResource(R.string.newsletter_sender_mute),
                summary = stringResource(R.string.newsletter_sender_mute_summary),
                checked = sender.muted,
                onCheckedChange = { actions.onMute(sender.key, it) },
            )
            if (others.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SheetSection(stringResource(R.string.newsletter_sender_combine))
                    Text(stringResource(R.string.newsletter_sender_combine_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        others.forEach { other ->
                            FeedChip(other.shownAs(feed), chosen = false, envelopeBadge = true) {
                                save()
                                actions.onMerge(sender.key, other.key, other.name)
                                onBack()
                            }
                        }
                    }
                }
            }
            val combined = sender.members.drop(1)
            if (combined.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SheetSection(stringResource(R.string.newsletter_sender_combined))
                    combined.forEach { member ->
                        val label = stringResource(R.string.newsletter_sender_separate_one, member.name)
                        ListItem(
                            headlineContent = { Text(member.name, maxLines = 1) },
                            trailingContent = {
                                TextButton(onClick = { actions.onSplit(member.key) }, modifier = Modifier.semantics { contentDescription = label }) {
                                    Text(stringResource(R.string.newsletter_sender_separate))
                                }
                            },
                        )
                    }
                }
            }
            SheetActions(onCancel = onBack, cancel = false) {
                val close = rememberSheetClose()
                TextButton(onClick = {
                    save()
                    close(onBack)
                }) { Text(stringResource(R.string.action_done)) }
            }
        }
    }
}
