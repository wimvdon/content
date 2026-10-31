package to.wim.content.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import to.wim.content.instapaper.InstapaperAuth
import to.wim.content.rssReaderApp
import to.wim.content.sync.EmailLinks
import to.wim.content.ui.reader.ProvideShareRecipients
import to.wim.content.ui.theme.RssReaderAppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** An article the share sheet's Open asked for (#142), until the navigation has opened it. */
    private var openRequest by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A restored activity (a rotation) must not handle the same sign-in link twice.
        if (savedInstanceState == null) {
            finishInstapaperSignIn(intent)
            openRequest = intent?.getStringExtra(EXTRA_OPEN_ARTICLE)
        }
        setContent {
            RssReaderAppTheme {
                // Send with Content offers the people shared with before (#235), with their Gravatar names.
                ProvideShareRecipients {
                    RssReaderNavigation(openRequest = openRequest, onOpened = { openRequest = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        finishInstapaperSignIn(intent)
        intent.getStringExtra(EXTRA_OPEN_ARTICLE)?.let { openRequest = it }
    }

    companion object {
        /** The reader key of an article to open (ReadingLists.readerKey), from the share sheet's Open. */
        const val EXTRA_OPEN_ARTICLE = "to.wim.content.extra.OPEN_ARTICLE"

        /** In [EXTRA_OPEN_ARTICLE]: not an article but Saved › Shared with me (#235, the notification). */
        const val OPEN_SHARED = "shared-with-me"
    }

    /**
     * Instapaper sends the user back through an Android App Link, and an emailed sign-in link opens
     * here the same way (#166); this finishes either.
     */
    private fun finishInstapaperSignIn(intent: Intent?) {
        val link = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString ?: return
        val app = rssReaderApp
        when {
            InstapaperAuth.isCallback(link) -> app.appScope.launch { app.instapaper.finish(link) }
            EmailLinks.tokenOf(link) != null -> app.appScope.launch { app.account.finish(link) }
        }
    }
}
