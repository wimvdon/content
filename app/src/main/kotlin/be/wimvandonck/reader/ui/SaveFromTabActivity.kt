package be.wimvandonck.reader.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.wimvandonck.reader.R
import be.wimvandonck.reader.data.Saved
import be.wimvandonck.reader.data.Item
import be.wimvandonck.reader.data.itemTags
import be.wimvandonck.reader.data.tagWithFeedFolder
import be.wimvandonck.reader.instapaper.SharedLink
import be.wimvandonck.reader.rssReaderApp
import be.wimvandonck.reader.ui.reader.SaveSheet
import be.wimvandonck.reader.ui.theme.RssReaderAppTheme
import kotlinx.coroutines.launch

/**
 * The save sheet on a see-through screen (#95, #103): over the custom tab for its Save button, and
 * over the other app for a link shared to Reader (through the ShareActivity alias in the manifest),
 * without opening the whole app. Saving or closing the sheet goes back to where the user was.
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

    // The save sheet, as for links shared to Reader (#108, #119): its folder, a note and tags.
    SaveSheet(
        url = link.url,
        title = link.title,
        folders = Saved.folders(library),
        allTags = library.itemTags(),
        services = app.services.connected,
        defaultService = app.services.defaultService(),
        onSave = { url, folder, note, tags, service ->
            app.appScope.launch {
                var saved: Item? = null
                app.library.edit { lib ->
                    val (updated, item) = Saved.save(lib, url, link.title, System.currentTimeMillis(), folder, note, tags, service)
                    saved = item
                    if (item != null && tagWithFolder) updated.tagWithFeedFolder(item.key) else updated
                }
                val item = saved
                val message =
                    if (item == null) {
                        resources.getString(R.string.add_link_invalid)
                    } else {
                        resources.getString(R.string.saved_to, folder.trim().ifEmpty { resources.getString(R.string.reading_list_to_read) })
                    }
                // The tab is back by then: the app's context outlives this screen.
                Toast.makeText(app, message, Toast.LENGTH_SHORT).show()
                if (item != null) app.library.describe(item.key)
            }
        },
        onDismiss = onDone,
    )
}
