package be.wimvandonck.content.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalResources
import be.wimvandonck.content.R
import be.wimvandonck.content.data.AddFeedResult
import be.wimvandonck.content.instapaper.SharedLink
import be.wimvandonck.content.rssReaderApp
import be.wimvandonck.content.ui.home.AddFeedDialog
import be.wimvandonck.content.ui.home.errorRes
import be.wimvandonck.content.ui.theme.RssReaderAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The share menu's Add feed: a website or feed shared from another app opens Add feed over that app,
 * with the link filled in, without opening the whole of Content. Adding it shows "Added …" as the
 * save screen does ([ShareConfirmation]) and goes back; closing the dialog goes back at once. The feed goes at the top level, as Add feed on Feeds does
 * outside a folder.
 */
class AddFeedFromShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Brought back after the system closed it: don't ask again.
        if (savedInstanceState != null || intent.action != Intent.ACTION_SEND) {
            finish()
            return
        }
        val link =
            SharedLink.of(intent.getStringExtra(Intent.EXTRA_SUBJECT), intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString())
        if (link == null) {
            Toast.makeText(this, R.string.share_no_link, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        setContent {
            RssReaderAppTheme {
                val app = rssReaderApp
                val resources = LocalResources.current
                var busy by rememberSaveable { mutableStateOf(false) }
                var error by rememberSaveable { mutableStateOf<String?>(null) }
                var added by rememberSaveable { mutableStateOf<String?>(null) }
                added?.let { text ->
                    ShareConfirmation(text, onDone = ::finish)
                    return@RssReaderAppTheme
                }
                AddFeedDialog(
                    busy = busy,
                    error = error,
                    initialAddress = link.url,
                    onAdd = { address ->
                        busy = true
                        error = null
                        // In the app's scope, so the feed is added even if this screen goes first.
                        app.appScope.launch(Dispatchers.Main) {
                            val result = app.library.addFeed(address, null)
                            busy = false
                            error = result.errorRes()?.let(resources::getString)
                            if (error == null && result is AddFeedResult.Added) {
                                added = resources.getString(R.string.add_feed_added, result.feed.title)
                            }
                        }
                    },
                    onDismiss = { if (!busy) finish() },
                )
            }
        }
    }
}
