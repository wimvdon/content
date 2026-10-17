package be.wimvandonck.reader.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import be.wimvandonck.reader.instapaper.InstapaperAuth
import be.wimvandonck.reader.rssReaderApp
import be.wimvandonck.reader.ui.theme.RssReaderAppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A restored activity (a rotation) must not handle the same sign-in link twice.
        if (savedInstanceState == null) finishInstapaperSignIn(intent)
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
    }

    /** Instapaper sends the user back through an Android App Link; this finishes the sign-in. */
    private fun finishInstapaperSignIn(intent: Intent?) {
        val link = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString ?: return
        if (!InstapaperAuth.isCallback(link)) return
        val app = rssReaderApp
        app.appScope.launch { app.instapaper.finish(link) }
    }
}
