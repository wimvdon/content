package be.wimvandonck.content.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import be.wimvandonck.content.data.itemTags
import be.wimvandonck.content.data.tagWithFeedFolder
import be.wimvandonck.content.instapaper.SharedLink
import be.wimvandonck.content.rssReaderApp
import be.wimvandonck.content.ui.reader.SaveSheet
import be.wimvandonck.content.ui.reader.SheetKind
import be.wimvandonck.content.ui.theme.RssReaderAppTheme
import kotlinx.coroutines.launch

/**
 * The save sheet on a see-through screen (#95, #103): over the custom tab for its Save button, and
 * over the other app for a link shared to Reader (through the ShareActivity alias in the manifest),
 * without opening the whole app. Saving shows a message with Open (#142), then goes back to where the
 * user was; closing the sheet goes back at once.
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
        // Other apps reach this screen only through the share alias, and only with a shared link.
        if (!shared && intent.component?.className?.endsWith(SHARE_ALIAS) == true) {
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
        setContent {
            RssReaderAppTheme {
                SaveFromTab(link, onDone = ::finish)
            }
        }
    }

    private companion object {
        /** The exported alias other apps share links to (AndroidManifest.xml). */
        const val SHARE_ALIAS = ".ShareActivity"
    }
}

@Composable
private fun SaveFromTab(
    link: SharedLink,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val app = context.rssReaderApp
    val library = app.library.library.collectAsStateWithLifecycle().value ?: return
    val tagWithFolder = app.settings.settings.collectAsStateWithLifecycle().value?.tagWithFolder ?: true

    // After Save, the message with Open (#142): the only thing of Reader this screen shows besides
    // the sheet. It goes when it times out or is tapped, and so does the screen.
    var sheetOpen by rememberSaveable { mutableStateOf(true) }
    var confirmation by remember { mutableStateOf<Confirmation?>(null) }

    // The one save sheet (#143), for a new save: the link, its folder (or Archive), Like, tags and a note.
    if (sheetOpen) {
        SaveSheet(
            kind = SheetKind.New,
            initial = SaveChoice(),
            url = link.url,
            title = link.title,
            folders = Saved.folders(library),
            allTags = library.itemTags(),
            onSave = { url, choice ->
                val key = ItemKeys.of(url)
                if (url != null && key != null) {
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
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.BottomCenter) { SnackbarHost(snackbar) }
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
