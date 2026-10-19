package be.wimvandonck.reader.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.wimvandonck.reader.R
import be.wimvandonck.reader.data.Item
import be.wimvandonck.reader.data.addToReadingList
import be.wimvandonck.reader.data.item
import be.wimvandonck.reader.data.itemTags
import be.wimvandonck.reader.data.setLiked
import be.wimvandonck.reader.data.setTags
import be.wimvandonck.reader.instapaper.SharedLink
import be.wimvandonck.reader.rssReaderApp
import be.wimvandonck.reader.ui.reader.AddedSheet
import be.wimvandonck.reader.ui.theme.RssReaderAppTheme
import kotlinx.coroutines.launch

/**
 * The custom tab's Save button (#95): a see-through screen on top of the tab that puts the page
 * shown on the reading list and opens the sheet over it. Closing the sheet goes back to the tab.
 */
class SaveFromTabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Brought back after the system closed it: the page was already saved then.
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
    val libraryOrNull by app.library.library.collectAsStateWithLifecycle()
    var addedKey by remember { mutableStateOf<String?>(null) }

    // On the reading list (#107), as Save does in the app; waits for the library to be read.
    LaunchedEffect(libraryOrNull != null) {
        if (libraryOrNull == null || addedKey != null) return@LaunchedEffect
        var added: Item? = null
        app.library.edit { library ->
            val (updated, item) = library.addToReadingList(link.url, link.title, System.currentTimeMillis())
            added = item
            updated
        }
        val item = added
        if (item == null) {
            Toast.makeText(context, resources.getString(R.string.add_link_invalid), Toast.LENGTH_SHORT).show()
            onDone()
            return@LaunchedEffect
        }
        addedKey = item.key
        app.appScope.launch { app.library.describe(item.key) }
    }

    val item = addedKey?.let { key -> libraryOrNull?.item(key) }
    val library = libraryOrNull
    if (item != null && library != null) {
        AddedSheet(
            item = item,
            allTags = library.itemTags(),
            onLike = { app.appScope.launch { app.library.edit { it.setLiked(item.key, !item.liked) } } },
            onTags = { tags -> app.appScope.launch { app.library.edit { it.setTags(item.key, tags) } } },
            onDismiss = onDone,
        )
    }
}
