package be.wimvandonck.content.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.wimvandonck.content.R
import be.wimvandonck.content.data.Saved
import be.wimvandonck.content.data.Article
import be.wimvandonck.content.data.Item
import be.wimvandonck.content.data.ItemKeys
import be.wimvandonck.content.data.ReadingLists
import be.wimvandonck.content.data.Place
import be.wimvandonck.content.data.SaveChoice
import be.wimvandonck.content.data.SaveChoices
import be.wimvandonck.content.data.ShareShortcuts
import be.wimvandonck.content.data.ShareTargets
import be.wimvandonck.content.data.SendTo
import be.wimvandonck.content.data.Sharing
import be.wimvandonck.content.data.itemTags
import be.wimvandonck.content.data.tagWithFeedFolder
import be.wimvandonck.content.instapaper.SharedLink
import be.wimvandonck.content.rssReaderApp
import be.wimvandonck.content.sync.AccountState
import be.wimvandonck.content.ui.reader.SaveSheet
import be.wimvandonck.content.ui.reader.ProvideShareRecipients
import be.wimvandonck.content.ui.reader.messageRes
import be.wimvandonck.content.ui.reader.SheetKind
import be.wimvandonck.content.ui.theme.RssReaderAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The save sheet on a see-through screen (#95, #103): over the custom tab for its Save button, and
 * over the other app for a link shared to Content, without opening the whole app. Other apps' share
 * menus show two targets ([ShareTargets], aliases in the manifest): Save and share, the sheet without
 * Don't save, and Send with Content, which only sends. A person shown with Content in the share menu
 * (#235) opens Send with Content with that person picked. Saving shows a message with Open (#142);
 * it goes, and so does this screen, when it times out or the screen is tapped anywhere. Closing the
 * sheet goes back at once.
 */
class SaveFromTabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Brought back after the system closed it: don't ask again.
        if (savedInstanceState != null) {
            finish()
            return
        }
        val shared = intent.action == Intent.ACTION_SEND
        val shortcut = intent.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID)
        val target = ShareTargets.of(intent.component?.className, shortcut)
        // Other apps reach this screen only through the share aliases, and only with a shared link.
        if (shared != (target != null)) {
            finish()
            return
        }
        val link =
            if (shared) {
                SharedLink.of(
                    intent.getStringExtra(Intent.EXTRA_SUBJECT),
                    intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
                )
            } else {
                SharedLink.fromTab(
                    intent.dataString,
                    intent.getStringExtra(ExternalApps.EXTRA_OPENED_URL),
                    intent.getStringExtra(ExternalApps.EXTRA_OPENED_TITLE),
                )
            }
        if (link == null) {
            if (shared) Toast.makeText(this, R.string.share_no_link, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        // A person picked in the share menu (#235): ready to send to them.
        val person = ShareShortcuts.emailOf(shortcut)
        setContent {
            RssReaderAppTheme {
                // Send with Content offers the people shared with before here too (#249).
                ProvideShareRecipients {
                    SaveFromTab(link, person, sendOnly = target == ShareTargets.Target.Send, onDone = ::finish)
                }
            }
        }
    }
}

@Composable
private fun SaveFromTab(
    link: SharedLink,
    /** Picked in the share menu: the sheet starts sending to them. */
    person: String?,
    /** The share menu's Send with Content: nothing is saved. */
    sendOnly: Boolean,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val app = context.rssReaderApp
    val library = app.library.library.collectAsStateWithLifecycle().value ?: return
    val tagWithFolder = app.settings.settings.collectAsStateWithLifecycle().value?.tagWithFolder ?: true
    val signedIn = app.account.state.collectAsStateWithLifecycle().value is AccountState.SignedIn

    // After Save or Send, the message (with Open for a save, #142): the only thing of Content this
    // screen shows besides the sheet. It goes when it times out or is tapped, and so does the screen.
    var sheetOpen by rememberSaveable { mutableStateOf(true) }
    var confirmation by remember { mutableStateOf<Confirmation?>(null) }
    // Sending is under way: the screen stays for its message.
    var sending by remember { mutableStateOf(false) }

    // The one save sheet (#143), for a new save: the link, its folder (or Archive), Like, tags and a note.
    if (sheetOpen) {
        SaveSheet(
            kind = SheetKind.New,
            initial = SaveChoice(sendTo = listOfNotNull(person)),
            url = link.url,
            title = link.title,
            folders = Saved.folders(library),
            allTags = library.itemTags(),
            signedIn = signedIn,
            hasAccount = app.sync::hasAccount,
            sendOnly = sendOnly,
            onSave = { url, choice ->
                val key = ItemKeys.of(url)
                if (url == null) return@SaveSheet
                val saved =
                    if (key != null && choice.keep) {
                        val place =
                            when (val p = choice.place) {
                                Place.ToRead -> resources.getString(R.string.reading_list_to_read)
                                Place.Archive -> resources.getString(R.string.reading_list_archive)
                                is Place.Folder -> p.name
                            }
                        app.appScope.launch {
                            var item: Item? = null
                            app.library.edit { lib ->
                                val (updated, new) = SaveChoices.saveNew(lib, url, link.title, System.currentTimeMillis(), choice)
                                item = new
                                if (new != null && tagWithFolder) updated.tagWithFeedFolder(new.key) else updated
                            }
                            item?.let { app.library.describe(it.key) }
                        }
                        Confirmation(resources.getString(R.string.saved_to, place), Article.keyOf(ReadingLists.ITEM_FEED, key))
                    } else {
                        null
                    }
                if (choice.sendTo.isEmpty()) {
                    confirmation = saved
                    return@SaveSheet
                }
                // Sent with Content (#256): one message says where it went and who got it, once sending is done.
                // In the app's scope, so sending finishes even if this screen goes first.
                sending = true
                app.appScope.launch(Dispatchers.Main) {
                    val results = choice.sendTo.map { email -> email to app.sync.share(email, listOf(Sharing.articleFor(library, url, link.title ?: url))) }
                    val sent =
                        when (val outcome = SendTo.outcome(results)) {
                            is SendTo.Outcome.Sent ->
                                outcome.to.singleOrNull()?.let { resources.getString(R.string.share_user_sent, it) }
                                    ?: resources.getQuantityString(R.plurals.share_sent_people, outcome.to.size, outcome.to.size)
                            is SendTo.Outcome.Failed -> resources.getString(outcome.result.messageRes())
                            null -> null
                        }
                    sending = false
                    confirmation =
                        when {
                            saved == null -> sent?.let { Confirmation(it, null) }
                            sent == null -> saved
                            else -> saved.copy(text = resources.getString(R.string.saved_and_sent, saved.text, sent))
                        }
                    if (confirmation == null && !sheetOpen) onDone()
                }
            },
            onDismiss = {
                sheetOpen = false
                if (confirmation == null && !sending) onDone()
            },
        )
    }
    val shown = confirmation
    if (!sheetOpen && shown != null) {
        val readerKey = shown.readerKey
        ShareConfirmation(
            shown.text,
            onDone = onDone,
            // Open only for a save: it goes to the article in Saved.
            actionLabel = readerKey?.let { stringResource(R.string.action_open) },
            onAction = {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_OPEN_ARTICLE, readerKey)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )
    }
}

/** What the message after saving or sending says, and the article its Open goes to (its reader key; null when only sent). */
private data class Confirmation(
    val text: String,
    val readerKey: String?,
)
