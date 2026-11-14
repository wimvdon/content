package to.wim.content.ui.home

import to.wim.content.data.NewsletterAddresses
import to.wim.content.data.NewsletterActivity
import to.wim.content.ui.feeds.TopicChipRow
import android.text.format.DateUtils
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import to.wim.content.ui.reader.SheetSection
import to.wim.content.ui.feeds.TopicEditActions
import to.wim.content.data.Folder
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import to.wim.content.R
import to.wim.content.ui.reader.ContentSheet
import to.wim.content.ui.reader.SheetActions
import to.wim.content.ui.reader.SheetHeader
import to.wim.content.ui.theme.AppShapes
import androidx.compose.material3.Button
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * The search bar's +, the same in Feed, Saved and the Inbox: first what to add, under two headings
 * with their destination's icon (2026-11-11). **Add feed**: RSS feed opens the Add feed sheet,
 * Newsletter feed a page of this sheet with every tag's email address ([NewsletterFeedPage]). **Save article**: Save article opens the Save sheet. Both sheets
 * then ask for the address.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddChoiceSheet(
    onSubscribe: () -> Unit,
    onSaveArticle: () -> Unit,
    onDismiss: () -> Unit,
    /** For Newsletter feed: the account, the tags and their addresses. */
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
            AddHeading(painterResource(R.drawable.ic_rss_feed), stringResource(R.string.feeds_add))
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
            AddHeading(painterResource(R.drawable.ic_list), stringResource(R.string.add_choice_article))
            AddChoice(
                icon = painterResource(R.drawable.ic_bookmark_add),
                title = stringResource(R.string.add_choice_article),
                body = stringResource(R.string.add_choice_article_body),
                onClick = { close(onSaveArticle) },
            )
            SheetActions(onCancel = onDismiss)
        }
        // A page of this sheet (#323): its back arrow comes back to the choices.
        if (newsletterOpen) NewsletterFeedPage(newsletters, onBack = { newsletterOpen = false })
    }
}

/** A heading in the Add sheet: its destination's icon and name, as a tag's heading in Manage feeds. */
@Composable
private fun AddHeading(
    icon: Painter,
    title: String,
) {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
    }
}

/** What Newsletter feed needs from the app. */
data class NewsletterChoices(
    /** Signed in to a Content account: every tag has an address. */
    val signedIn: Boolean = false,
    /** Settings › Account, to sign in. */
    val onSignIn: () -> Unit = {},
    /** The tags, each with its address. */
    val topics: List<Folder> = emptyList(),
    /** This account's addresses, every tag's made first; null when they can't be read (offline). */
    val loadAddresses: suspend () -> List<NewsletterAddress>? = { emptyList() },
    /** Edit tags (Manage tags as a page of this sheet): a new tag gets its address too. */
    val topicActions: TopicEditActions? = null,
    /** What a newsletter feed (by its address) has received so far. */
    val activity: (feedUrl: String) -> NewsletterActivity = { NewsletterActivity(0, null) },
    val onCopy: (String) -> Unit = {},
    /** Android's share sheet with the address. */
    val onShare: (String) -> Unit = {},
)

/**
 * Newsletter feed, from + and from Settings › Newsletter feeds (2026-11-13, owner's choice from
 * mockups): pick a tag with the tag chips (No tag first, as in Add feed; Edit tags after them), and
 * the page shows that tag's one address, large, with Copy address and Share, and what its
 * Newsletters feed has received so far. Every tag has an address, and so does No tag
 * ([NewsletterAddresses]); nothing to make, name or remove. Without a Content account: Sign in.
 */
@Composable
fun NewsletterFeedPage(
    choices: NewsletterChoices,
    onBack: () -> Unit,
    title: String = stringResource(R.string.add_choice_newsletter),
) {
    // Not loaded yet; then the addresses, or null when they couldn't be read.
    var loaded by remember { mutableStateOf(false) }
    var addresses by remember { mutableStateOf<List<NewsletterAddress>?>(null) }
    // The tag picked (its id, or NO_TAG); null: none yet, so the first tag.
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    // A tag made with Edit tags: chosen once it exists.
    var made by rememberSaveable { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf<String?>(null) }
    val tagIds = choices.topics.map { it.id }
    LaunchedEffect(choices.signedIn, tagIds) {
        if (!choices.signedIn) return@LaunchedEffect
        addresses = choices.loadAddresses()
        loaded = true
    }
    LaunchedEffect(choices.topics, made) {
        val name = made ?: return@LaunchedEffect
        choices.topics.firstOrNull { it.title.equals(name, ignoreCase = true) }?.let {
            picked = it.id
            made = null
        }
    }
    val chosen = NewsletterAddresses.chosenTag(choices.topics, picked)
    ContentSheet(onDismiss = onBack) {
        Column(
            Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetHeader(title, onCancel = onBack)
            if (!choices.signedIn) {
                SignInFirst(choices.onSignIn)
                SheetActions(onCancel = onBack)
                return@Column
            }
            Text(stringResource(R.string.newsletter_own_explained), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetSection(stringResource(R.string.feed_folder))
                TopicChipRow(
                    topics = choices.topics,
                    selected = { it.id == chosen },
                    // One tag at a time: tapping the chosen one keeps it.
                    onToggle = { picked = it.id },
                    actions = choices.topicActions,
                    onCreated = { made = it },
                    leading = {
                        TopicChip(stringResource(R.string.folder_none), icon = null, selected = chosen == null, enabled = true) { picked = NewsletterAddresses.NO_TAG }
                    },
                )
            }
            val tagName = choices.topics.firstOrNull { it.id == chosen }?.title ?: stringResource(R.string.folder_none)
            AddressCard(
                tagName = tagName,
                address = if (loaded) addresses?.let { NewsletterAddresses.forTag(it, chosen) } else null,
                state =
                    when {
                        !loaded -> AddressState.Loading
                        addresses == null -> AddressState.Offline
                        else -> AddressState.Ready
                    },
                copied = copied,
                activity = choices.activity,
                onCopy = {
                    choices.onCopy(it)
                    copied = it
                },
                onShare = choices.onShare,
            )
            SheetActions(onCancel = onBack, cancel = false) {
                val close = rememberSheetClose()
                TextButton(onClick = { close(onBack) }) { Text(stringResource(R.string.action_done)) }
            }
        }
    }
}

private enum class AddressState { Loading, Offline, Ready }

/** The chosen tag's address, large, with Copy address (Copied, with a check, once tapped) and Share, then what it has received. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddressCard(
    tagName: String,
    address: NewsletterAddress?,
    state: AddressState,
    copied: String?,
    activity: (String) -> NewsletterActivity,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
) {
    Surface(shape = AppShapes.Item, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.newsletter_address_for, tagName), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when {
                    state == AddressState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
                    state == AddressState.Offline -> Text(stringResource(R.string.newsletter_own_offline), style = MaterialTheme.typography.bodyMedium)
                    address == null -> Text(stringResource(R.string.newsletter_own_pending), style = MaterialTheme.typography.bodyMedium)
                    else -> SelectionContainer { Text(address.address, style = MaterialTheme.typography.titleMedium) }
                }
            }
            if (address != null && state == AddressState.Ready) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val isCopied = copied == address.address
                    Button(onClick = { onCopy(address.address) }, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                        Icon(painterResource(if (isCopied) R.drawable.ic_check else R.drawable.ic_copy), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Text(stringResource(if (isCopied) R.string.newsletter_copied else R.string.newsletter_own_copy), modifier = Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(onClick = { onShare(address.address) }, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                        Icon(painterResource(R.drawable.ic_share), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Text(stringResource(R.string.action_share), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                val received = activity(address.feedUrl)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(R.drawable.ic_mail), contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(receivedText(received), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** "No newsletters yet", or "3 newsletters · Yesterday". */
@Composable
private fun receivedText(received: NewsletterActivity): String {
    val newest = received.newestAt ?: return stringResource(R.string.newsletter_none_yet)
    val count = pluralStringResource(R.plurals.newsletter_count, received.count, received.count)
    val day = DateUtils.getRelativeTimeSpanString(newest, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString()
    return "$count · $day"
}

/** Not signed in: the addresses need a Content account. */
@Composable
private fun SignInFirst(onSignIn: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(64.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_mail), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Text(stringResource(R.string.newsletters_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.newsletter_own_sign_in_first), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Button(onClick = onSignIn) { Text(stringResource(R.string.newsletter_own_sign_in)) }
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
