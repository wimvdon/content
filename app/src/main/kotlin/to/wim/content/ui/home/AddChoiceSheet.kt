package to.wim.content.ui.home

import to.wim.content.ui.reader.rememberSheetClose
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import to.wim.content.R
import to.wim.content.ui.reader.ContentSheet
import to.wim.content.ui.reader.SheetActions
import to.wim.content.ui.reader.SheetHeader
import to.wim.content.ui.theme.AppShapes
import to.wim.content.data.NewsletterSubscribe
import to.wim.content.ui.settings.NewsletterUnreadSetting
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * The search bar's +, the same in Feeds, Saved and the Inbox: first what to add. Subscribe to feed
 * opens the Add feed sheet, Save article the Save sheet; both then ask for the address. Subscribe to
 * newsletter is a page of this sheet with two ways ([NewsletterSubscribe]), each a page of steps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddChoiceSheet(
    onSubscribe: () -> Unit,
    onSaveArticle: () -> Unit,
    onDismiss: () -> Unit,
    /** For Subscribe to newsletter: Instapaper connected, Mark incoming newsletters as unread, and the links. */
    newsletters: NewsletterChoices = NewsletterChoices(),
) {
    var newsletterOpen by rememberSaveable { mutableStateOf(false) }
    ContentSheet(onDismiss = onDismiss) {
        Column(
            Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetHeader(stringResource(R.string.add_title), onCancel = onDismiss)
            // A choice slides this sheet away before the next one comes up.
            val close = rememberSheetClose()
            AddChoice(
                icon = painterResource(R.drawable.ic_rss_feed),
                title = stringResource(R.string.add_choice_feed),
                body = stringResource(R.string.add_choice_feed_body),
                onClick = { close(onSubscribe) },
            )
            AddChoice(
                icon = painterResource(R.drawable.ic_mail),
                title = stringResource(R.string.add_choice_newsletter),
                body = stringResource(R.string.add_choice_newsletter_body),
                onClick = { newsletterOpen = true },
            )
            AddChoice(
                icon = painterResource(R.drawable.ic_bookmark_add),
                title = stringResource(R.string.add_choice_article),
                body = stringResource(R.string.add_choice_article_body),
                onClick = { close(onSaveArticle) },
            )
            SheetActions(onCancel = onDismiss)
        }
        // A page of this sheet (#323): its back arrow comes back to the three choices.
        if (newsletterOpen) NewsletterPage(newsletters, onAddFeed = onSubscribe, onBack = { newsletterOpen = false })
    }
}

/** What Subscribe to newsletter needs from the app. */
data class NewsletterChoices(
    val instapaperConnected: Boolean = false,
    val newsletterUnread: Boolean = false,
    val onNewsletterUnreadChange: (Boolean) -> Unit = {},
    val onConnectInstapaper: () -> Unit = {},
    /** Opens a site in the browser (Kill the Newsletter, Instapaper's Email page). */
    val onOpenSite: (String) -> Unit = {},
)

/** Subscribe to newsletter: as a feed (Kill the Newsletter) or straight into To read (Instapaper). */
@Composable
private fun NewsletterPage(
    choices: NewsletterChoices,
    onAddFeed: () -> Unit,
    onBack: () -> Unit,
) {
    var way by rememberSaveable { mutableStateOf<NewsletterSubscribe.Way?>(null) }
    ContentSheet(onDismiss = onBack) {
        Column(
            Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetHeader(stringResource(R.string.add_choice_newsletter), onCancel = onBack)
            AddChoice(
                icon = painterResource(R.drawable.ic_rss_feed),
                title = stringResource(R.string.newsletter_as_feed),
                body = stringResource(R.string.newsletter_as_feed_body),
                onClick = { way = NewsletterSubscribe.Way.Feed },
            )
            AddChoice(
                icon = painterResource(R.drawable.ic_reading_list),
                title = stringResource(R.string.newsletter_to_read),
                body = stringResource(R.string.newsletter_to_read_body),
                onClick = { way = NewsletterSubscribe.Way.Instapaper },
            )
        }
        when (way) {
            NewsletterSubscribe.Way.Feed -> FeedWayPage(choices, onAddFeed, onBack = { way = null })
            NewsletterSubscribe.Way.Instapaper -> InstapaperWayPage(choices, onBack = { way = null })
            null -> Unit
        }
    }
}

/** As a feed: make an inbox on Kill the Newsletter, subscribe with its address, add its feed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedWayPage(
    choices: NewsletterChoices,
    onAddFeed: () -> Unit,
    onBack: () -> Unit,
) {
    ContentSheet(onDismiss = onBack) {
        Column(
            Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetHeader(stringResource(R.string.newsletter_as_feed), onCancel = onBack)
            Steps(listOf(R.string.newsletter_feed_step_1, R.string.newsletter_feed_step_2, R.string.newsletter_feed_step_3))
            Text(stringResource(R.string.newsletter_feed_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { choices.onOpenSite(NewsletterSubscribe.site(NewsletterSubscribe.Way.Feed)) }) {
                    Text(stringResource(R.string.newsletter_open_kill_the_newsletter))
                }
                Button(onClick = onAddFeed) { Text(stringResource(R.string.newsletter_add_feed)) }
            }
        }
    }
}

/** Straight into To read: the Save to Instapaper address (connect first), and Mark incoming newsletters as unread. */
@Composable
private fun InstapaperWayPage(
    choices: NewsletterChoices,
    onBack: () -> Unit,
) {
    ContentSheet(onDismiss = onBack) {
        Column(
            Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetHeader(stringResource(R.string.newsletter_to_read), onCancel = onBack)
            Steps(listOf(R.string.newsletter_instapaper_step_1, R.string.newsletter_instapaper_step_2, R.string.newsletter_instapaper_step_3))
            when (NewsletterSubscribe.instapaperStep(choices.instapaperConnected)) {
                NewsletterSubscribe.InstapaperStep.Connect -> {
                    Text(stringResource(R.string.newsletter_instapaper_connect_first), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = choices.onConnectInstapaper) { Text(stringResource(R.string.instapaper_sign_in)) }
                }
                NewsletterSubscribe.InstapaperStep.FindAddress -> {
                    Button(onClick = { choices.onOpenSite(NewsletterSubscribe.site(NewsletterSubscribe.Way.Instapaper)) }) {
                        Text(stringResource(R.string.newsletter_find_address))
                    }
                    // The same switch as in Settings › Integrations › Instapaper.
                    NewsletterUnreadSetting(choices.newsletterUnread, choices.onNewsletterUnreadChange)
                }
            }
        }
    }
}

/** Numbered steps, one per line. */
@Composable
private fun Steps(steps: List<Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { i, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(step), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** One choice: its icon in a circle (as a topic's in Manage topics), what it does, and a line on it. */
@Composable
private fun AddChoice(
    icon: Painter,
    title: String,
    body: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = AppShapes.Item,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
