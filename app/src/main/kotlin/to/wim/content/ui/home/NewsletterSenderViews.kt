package to.wim.content.ui.home

import android.text.format.DateUtils
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
import androidx.compose.material3.OutlinedButton
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
import to.wim.content.data.Cadence
import to.wim.content.data.Feed
import to.wim.content.data.Folder
import to.wim.content.data.NewsletterAddresses
import to.wim.content.data.NewsletterSender
import to.wim.content.data.NewsletterSenders
import to.wim.content.ui.feeds.TopicChipRow
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
data class SenderActions(
    val onRename: (key: String, name: String) -> Unit = { _, _ -> },
    /** Opens the article with this key in the reader, out of the sender's page. */
    val onOpenNewest: (articleKey: String) -> Unit = {},
    /** Unsubscribes from the sender, at the feed loader (NewsletterUnsubscribe). */
    val onUnsubscribe: (NewsletterSender) -> Unit = {},
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
                        supportingContent = { Text(count) },
                        leadingContent = { FeedIcon(sender.shownAs(feed), FeedIconSize.Medium, envelopeBadge = NewsletterSenders.siteOf(sender.key) != null) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier.clickable(role = Role.Button) { onOpen(sender) },
                    )
                }
            }
        }
    }
}

/** "Newest yesterday · About weekly · 2 unread": when the newest came, how often they come (left out under 3 issues) and how many are unread. */
@Composable
private fun senderFacts(sender: NewsletterSender): String {
    val newest = DateUtils.getRelativeTimeSpanString(sender.newestAt, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString()
    val cadence =
        when (NewsletterSenders.cadence(sender.times)) {
            Cadence.Daily -> stringResource(R.string.newsletter_cadence_daily)
            Cadence.Weekly -> stringResource(R.string.newsletter_cadence_weekly)
            Cadence.EveryTwoWeeks -> stringResource(R.string.newsletter_cadence_two_weeks)
            Cadence.Monthly -> stringResource(R.string.newsletter_cadence_monthly)
            Cadence.NowAndThen -> stringResource(R.string.newsletter_cadence_now_and_then)
            null -> null
        }
    val unread = if (sender.unread == 0) stringResource(R.string.newsletter_sender_all_read) else pluralStringResource(R.plurals.newsletter_sender_unread, sender.unread, sender.unread)
    return listOfNotNull(stringResource(R.string.newsletter_sender_newest, newest), cadence, unread).joinToString(" · ")
}

/**
 * One sender's page, a page of the Newsletter feed sheet: facts on it with Open newest, its name
 * (empty: the sender's own) and Unsubscribe (NewsletterUnsubscribe).
 */
@Composable
internal fun SenderPage(
    sender: NewsletterSender,
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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(senderFacts(sender), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                sender.newestKey?.let { key ->
                    TextButton(onClick = { actions.onOpenNewest(key) }) { Text(stringResource(R.string.newsletter_sender_open_newest)) }
                }
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.feed_name)) },
                placeholder = { Text(sender.members.first().name) },
                supportingText = { Text(stringResource(R.string.newsletter_sender_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { actions.onUnsubscribe(sender) }) {
                    Text(stringResource(R.string.newsletter_unsubscribe))
                }
                Text(stringResource(R.string.newsletter_unsubscribe_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
