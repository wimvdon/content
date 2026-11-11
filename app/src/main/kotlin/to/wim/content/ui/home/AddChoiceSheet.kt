package to.wim.content.ui.home

import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.CircularProgressIndicator
import to.wim.content.ui.reader.SheetSection
import to.wim.content.ui.feeds.TopicChipRow
import to.wim.content.ui.feeds.TopicEditActions
import to.wim.content.data.chosenTopic
import to.wim.content.data.Folder
import kotlinx.coroutines.launch
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import to.wim.content.data.NewsletterAddress
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
        if (newsletterOpen) NewsletterPage(newsletters, onBack = { newsletterOpen = false })
    }
}

/** What Subscribe to newsletter needs from the app. */
data class NewsletterChoices(
    val instapaperConnected: Boolean = false,
    val newsletterUnread: Boolean = false,
    val onNewsletterUnreadChange: (Boolean) -> Unit = {},
    val onConnectInstapaper: () -> Unit = {},
    /** Opens a site in the browser (Instapaper's Email page). */
    val onOpenSite: (String) -> Unit = {},
    /** Signed in to a Content account: own addresses can be made. */
    val signedIn: Boolean = false,
    /** Settings › Account, to sign in. */
    val onSignIn: () -> Unit = {},
    /** The topics, to pick the new address's feed's one (as in Add feed), and Edit topics. */
    val topics: List<Folder> = emptyList(),
    val topicActions: TopicEditActions? = null,
    /** Makes an address with this name and adds its feed under the topic; null when it didn't work. */
    val createAddress: suspend (name: String, topicId: String?) -> NewsletterAddress? = { _, _ -> null },
    val onCopy: (String) -> Unit = {},
)

/** Subscribe to newsletter: your own address (Content's), or straight into To read (Instapaper). */
@Composable
private fun NewsletterPage(
    choices: NewsletterChoices,
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
                icon = painterResource(R.drawable.ic_mail),
                title = stringResource(R.string.newsletter_own),
                body = stringResource(R.string.newsletter_own_body),
                onClick = { way = NewsletterSubscribe.Way.Own },
            )
            AddChoice(
                icon = painterResource(R.drawable.ic_reading_list),
                title = stringResource(R.string.newsletter_to_read),
                body = stringResource(R.string.newsletter_to_read_body),
                onClick = { way = NewsletterSubscribe.Way.Instapaper },
            )
        }
        when (way) {
            NewsletterSubscribe.Way.Own -> OwnAddressPage(choices, onBack = { way = null })
            NewsletterSubscribe.Way.Instapaper -> InstapaperWayPage(choices, onBack = { way = null })
            null -> Unit
        }
    }
}

/**
 * Your own address, like Add feed: a name for its feed and a topic (No topic, every topic, Edit
 * topics), then Create address; once made, the address with Copy. Without a Content account, Sign in
 * first. The addresses are in Manage feeds afterwards, each feed with its address.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OwnAddressPage(
    choices: NewsletterChoices,
    onBack: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var topicId by rememberSaveable { mutableStateOf<String?>(null) }
    var made by remember { mutableStateOf<NewsletterAddress?>(null) }
    // A topic made in the page: chosen once it exists.
    var newTopic by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(choices.topics, newTopic) {
        val title = newTopic ?: return@LaunchedEffect
        choices.topics.firstOrNull { it.title.equals(title, ignoreCase = true) }?.let {
            topicId = it.id
            newTopic = null
        }
    }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val chosen = chosenTopic(topicId, choices.topics)
    ContentSheet(onDismiss = onBack) {
        Column(
            Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetHeader(stringResource(R.string.newsletter_own), onCancel = onBack, enabled = !busy)
            Text(stringResource(R.string.newsletter_own_explained), style = MaterialTheme.typography.bodyMedium)
            val address = made
            when {
                NewsletterSubscribe.ownStep(choices.signedIn) == NewsletterSubscribe.OwnStep.SignIn -> {
                    Text(stringResource(R.string.newsletter_own_sign_in_first), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = choices.onSignIn) { Text(stringResource(R.string.newsletter_own_sign_in)) }
                }
                address != null -> {
                    // Made: the address to subscribe with; its feed is already in Feeds.
                    Text(stringResource(R.string.newsletter_own_made), style = MaterialTheme.typography.bodyMedium)
                    AddressRow(address, choices.onCopy)
                    SheetActions(onCancel = onBack, cancel = false) {
                        Button(onClick = onBack) { Text(stringResource(R.string.action_done)) }
                    }
                }
                else -> {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it.take(100)
                            failed = false
                        },
                        label = { Text(stringResource(R.string.feed_name)) },
                        singleLine = true,
                        enabled = !busy,
                        isError = failed,
                        supportingText = if (failed) ({ Text(stringResource(R.string.newsletter_own_failed)) }) else null,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SheetSection(stringResource(R.string.feed_folder))
                    TopicChipRow(
                        topics = choices.topics,
                        selected = { chosen == it.id },
                        onToggle = { topicId = if (chosen == it.id) null else it.id },
                        actions = choices.topicActions,
                        onCreated = { newTopic = it },
                        enabled = !busy,
                        leading = { TopicChip(stringResource(R.string.folder_none), icon = null, selected = chosen == null, enabled = !busy) { topicId = null } },
                    )
                    SheetActions(onCancel = onBack, enabled = !busy) {
                        Button(
                            enabled = NewsletterSubscribe.canCreate(name, busy),
                            onClick = {
                                busy = true
                                scope.launch {
                                    val result = choices.createAddress(name.trim(), chosen)
                                    busy = false
                                    if (result == null) failed = true else made = result
                                }
                            },
                        ) {
                            if (busy) {
                                CircularProgressIndicator(strokeWidth = 2.dp, color = LocalContentColor.current, modifier = Modifier.size(18.dp))
                            } else {
                                Text(stringResource(R.string.newsletter_own_create))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One address: its name, the address itself, and Copy. */
@Composable
private fun AddressRow(
    address: NewsletterAddress,
    onCopy: (String) -> Unit,
) {
    Surface(shape = AppShapes.Item, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(address.tag, style = MaterialTheme.typography.labelLarge)
                Text(address.address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { onCopy(address.address) }) {
                Icon(painterResource(R.drawable.ic_copy), contentDescription = stringResource(R.string.newsletter_own_copy))
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
                    Button(onClick = { choices.onOpenSite(NewsletterSubscribe.INSTAPAPER_EMAIL) }) {
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
