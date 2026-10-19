package be.wimvandonck.reader.ui

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
 * The custom tab's Save button (#95): a see-through screen on top of the tab with the save sheet
 * over it (reading list or bookmark). Saving or closing the sheet goes back to the tab.
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
        val link =
            SharedLink.fromTab(
                intent.dataString,
                intent.getStringExtra(ExternalApps.EXTRA_OPENED_URL),
                intent.getStringExtra(ExternalApps.EXTRA_OPENED_TITLE),
            )
        if (link == null) {
            finish()
            return
        }
        setContent {
            RssReaderAppTheme {
                SaveFromTab(link, onDone = ::finish)
            }
        }
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
