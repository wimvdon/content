package to.wim.content.ui.home

import to.wim.content.data.NewsletterAddresses
import to.wim.content.ui.feeds.FolderSheet
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import to.wim.content.ui.reader.SheetSection
import to.wim.content.ui.feeds.TopicEditActions
import to.wim.content.data.Folder
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.material3.IconButton
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
    /** New tag: it gets its address too. */
    val topicActions: TopicEditActions? = null,
    val onCopy: (String) -> Unit = {},
)

/**
 * Newsletter feeds, from + › Newsletter feed and from Settings (2026-11-12): every tag has an email
 * address, and so does No tag ([NewsletterAddresses]); newsletters sent to one arrive in that tag's
 * Newsletters feed. Each tag in its order, then No tag, with its address and Copy; New tag makes
 * another tag, with its own address. Without a Content account, Sign in first. Nothing to make,
 * name or remove: an address comes and goes with its tag.
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
    var creating by rememberSaveable { mutableStateOf(false) }
    // A new tag gets its address as soon as it is there.
    val tagIds = choices.topics.map { it.id }
    LaunchedEffect(choices.signedIn, tagIds) {
        if (!choices.signedIn) return@LaunchedEffect
        addresses = choices.loadAddresses()
        loaded = true
    }
    val rows = remember(choices.topics, addresses) { NewsletterAddresses.rows(choices.topics, addresses.orEmpty()) }
    ContentSheet(onDismiss = onBack) {
        Column(
            Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetHeader(title, onCancel = onBack)
            Text(stringResource(R.string.newsletter_own_explained), style = MaterialTheme.typography.bodyMedium)
            when {
                !choices.signedIn -> {
                    Text(stringResource(R.string.newsletter_own_sign_in_first), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = choices.onSignIn) { Text(stringResource(R.string.newsletter_own_sign_in)) }
                }
                !loaded -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                addresses == null -> Text(stringResource(R.string.newsletter_own_offline), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    rows.forEach { row ->
                        SheetSection(row.topic?.title ?: stringResource(R.string.folder_none))
                        val address = row.address
                        if (address != null) {
                            AddressRow(address, choices.onCopy)
                        } else {
                            Text(stringResource(R.string.newsletter_own_pending), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            // New tag at the left (it gets its own address), Done at the right.
            SheetActions(
                onCancel = onBack,
                cancel = false,
                leading = {
                    if (choices.signedIn && choices.topicActions != null) {
                        FilledTonalButton(onClick = { creating = true }, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                            Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Text(stringResource(R.string.folder_new), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                },
            ) {
                val close = rememberSheetClose()
                TextButton(onClick = { close(onBack) }) { Text(stringResource(R.string.action_done)) }
            }
        }
    }
    val actions = choices.topicActions
    if (creating && actions != null) {
        FolderSheet(
            folder = null,
            onSave = { name, icon ->
                creating = false
                actions.onNew(name, icon)
            },
            onDismiss = { creating = false },
        )
    }
}

/** One address and Copy. */
@Composable
private fun AddressRow(
    address: NewsletterAddress,
    onCopy: (String) -> Unit,
) {
    Surface(shape = AppShapes.Item, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(address.address, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { onCopy(address.address) }) {
                Icon(painterResource(R.drawable.ic_copy), contentDescription = stringResource(R.string.newsletter_own_copy))
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
