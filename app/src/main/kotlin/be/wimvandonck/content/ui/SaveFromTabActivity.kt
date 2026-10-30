package be.wimvandonck.content.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import be.wimvandonck.content.data.SharedArticle
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
                    SaveFromTab(link, person, sendOnly = target == ShareTargets.Target.Send, fromOtherApp = shared, onDone = ::finish)
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
    /** Shared from another app, which has its own Send target, so no Don't save. */
    fromOtherApp: Boolean,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val app = context.rssReaderApp
    val library = app.library.library.collectAsStateWithLifecycle().value ?: return
    val tagWithFolder = app.settings.settings.collectAsStateWithLifecycle().value?.tagWithFolder ?: true
    val signedIn = app.account.state.collectAsStateWithLifecycle().value is AccountState.SignedIn

    // After Save, the message with Open (#142): the only thing of Reader this screen shows besides
    // the sheet. It goes when it times out or is tapped, and so does the screen.
    var sheetOpen by rememberSaveable { mutableStateOf(true) }
    var confirmation by remember { mutableStateOf<Confirmation?>(null) }

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
            dontSave = !fromOtherApp,
            sendOnly = sendOnly,
            onSave = { url, choice ->
                val key = ItemKeys.of(url)
                // Sent with Content (#256): a short message from Android says how it went, as this screen closes.
                if (url != null && choice.sendTo.isNotEmpty()) {
                    app.appScope.launch {
                        val results = choice.sendTo.map { email -> email to app.sync.share(email, listOf(SharedArticle(url, link.title ?: url))) }
                        val text =
                            when (val outcome = SendTo.outcome(results)) {
                                is SendTo.Outcome.Sent ->
                                    outcome.to.singleOrNull()?.let { resources.getString(R.string.share_user_sent, it) }
                                        ?: resources.getQuantityString(R.plurals.share_sent_people, outcome.to.size, outcome.to.size)
                                is SendTo.Outcome.Failed -> resources.getString(outcome.result.messageRes())
                                null -> null
                            }
                        if (text != null) Toast.makeText(app, text, Toast.LENGTH_SHORT).show()
                    }
                }
                if (url != null && key != null && choice.keep) {
                    val place =
                        when (val p = choice.place) {
                            Place.ToRead -> resources.getString(R.string.reading_list_to_read)
                            Place.Archive -> resources.getString(R.string.reading_list_archive)
                            is Place.Folder -> p.name
                        }
                    confirmation = Confirmation(resources.getString(R.string.saved_to, place), Article.keyOf(ReadingLists.ITEM_FEED, key))
                    app.appScope.launch {
                        var saved: Item? = null
                        app.library.edit { lib ->
                            val (updated, item) = SaveChoices.saveNew(lib, url, link.title, System.currentTimeMillis(), choice)
                            saved = item
                            if (item != null && tagWithFolder) updated.tagWithFeedFolder(item.key) else updated
                        }
                        saved?.let { app.library.describe(it.key) }
                    }
                }
            },
            onDismiss = {
                sheetOpen = false
                if (confirmation == null) onDone()
            },
        )
    }
    val shown = confirmation
    if (!sheetOpen && shown != null) {
        val snackbar = remember { SnackbarHostState() }
        // A tap anywhere (but Open) goes back to the other app at once.
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = null, indication = null, onClickLabel = stringResource(R.string.action_back), onClick = onDone)
                .safeDrawingPadding(),
            contentAlignment = Alignment.BottomCenter,
        ) { SnackbarHost(snackbar) }
        val open = stringResource(R.string.action_open)
        LaunchedEffect(shown) {
            if (snackbar.showSnackbar(shown.text, actionLabel = open, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_OPEN_ARTICLE, shown.readerKey)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            onDone()
        }
    }
}

/** What the message after saving says, and the article its Open goes to (its reader key). */
private data class Confirmation(
    val text: String,
    val readerKey: String,
)
