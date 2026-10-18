package be.wimvandonck.reader.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import be.wimvandonck.reader.R
import be.wimvandonck.reader.instapaper.InstapaperAuth
import be.wimvandonck.reader.instapaper.SharedLink
import be.wimvandonck.reader.rssReaderApp
import be.wimvandonck.reader.ui.theme.RssReaderAppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A restored activity (a rotation) must not handle the same sign-in link twice.
        if (savedInstanceState == null) {
            finishInstapaperSignIn(intent)
            takeSharedLink(intent)
        }
        setContent {
            RssReaderAppTheme {
                RssReaderNavigation()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        finishInstapaperSignIn(intent)
        takeSharedLink(intent)
    }

    /** A link shared from another app (#97) or a custom tab (#95): saved like an article, then the save sheet opens. */
    private fun takeSharedLink(intent: Intent?) {
        if (intent?.action == ExternalApps.ACTION_SAVE_FROM_TAB) {
            // A custom tab's Save button (#95): the browser adds the page shown as the data.
            SharedLink
                .fromTab(
                    intent.dataString,
                    intent.getStringExtra(ExternalApps.EXTRA_OPENED_URL),
                    intent.getStringExtra(ExternalApps.EXTRA_OPENED_TITLE),
                )?.let { rssReaderApp.sharedLink.value = it }
            return
        }
        if (intent?.action != Intent.ACTION_SEND) return
        val link =
            SharedLink.of(
                intent.getStringExtra(Intent.EXTRA_SUBJECT),
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
            )
        if (link == null) {
            Toast.makeText(this, R.string.share_no_link, Toast.LENGTH_SHORT).show()
        } else {
            rssReaderApp.sharedLink.value = link
        }
    }

    /** Instapaper sends the user back through an Android App Link; this finishes the sign-in. */
    private fun finishInstapaperSignIn(intent: Intent?) {
        val link = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString ?: return
        if (!InstapaperAuth.isCallback(link)) return
        val app = rssReaderApp
        app.appScope.launch { app.instapaper.finish(link) }
    }
}
