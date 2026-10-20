package be.wimvandonck.reader.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import be.wimvandonck.reader.instapaper.InstapaperAuth
import be.wimvandonck.reader.raindrop.RaindropAuth
import be.wimvandonck.reader.rssReaderApp
import be.wimvandonck.reader.ui.theme.RssReaderAppTheme
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
            finishRaindropSignIn(intent)
            openRequest = intent?.getStringExtra(EXTRA_OPEN_ARTICLE)
        }
        setContent {
            RssReaderAppTheme {
                RssReaderNavigation(openRequest = openRequest, onOpened = { openRequest = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        finishInstapaperSignIn(intent)
        finishRaindropSignIn(intent)
        intent.getStringExtra(EXTRA_OPEN_ARTICLE)?.let { openRequest = it }
    }

    companion object {
        /** The reader key of an article to open (ReadingLists.readerKey), from the share sheet's Open. */
        const val EXTRA_OPEN_ARTICLE = "be.wimvandonck.reader.extra.OPEN_ARTICLE"
    }

    /** Raindrop sends the user back through the same kind of App Link (#112). */
    private fun finishRaindropSignIn(intent: Intent?) {
        val link = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString ?: return
        if (!RaindropAuth.isCallback(link)) return
        val app = rssReaderApp
        app.appScope.launch { app.raindrop.finish(link) }
    }

    /** Instapaper sends the user back through an Android App Link; this finishes the sign-in. */
    private fun finishInstapaperSignIn(intent: Intent?) {
        val link = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString ?: return
        if (!InstapaperAuth.isCallback(link)) return
        val app = rssReaderApp
        app.appScope.launch { app.instapaper.finish(link) }
    }
}
