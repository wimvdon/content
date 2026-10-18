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
import be.wimvandonck.reader.instapaper.InstapaperException
import be.wimvandonck.reader.instapaper.InstapaperState
import be.wimvandonck.reader.instapaper.SharedLink
import be.wimvandonck.reader.rssReaderApp
import be.wimvandonck.reader.ui.instapaper.messageRes
import be.wimvandonck.reader.ui.reader.JustSavedSheet
import be.wimvandonck.reader.ui.theme.RssReaderAppTheme
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update

/**
 * The custom tab's Save button (#95): a see-through screen on top of the tab that saves the page
 * shown and opens the save sheet over it. Closing the sheet goes back to the tab.
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
    val account by app.instapaper.state.collectAsStateWithLifecycle()
    // Saved as in the app: the Instapaper account when signed in, else this phone. Waits for the
    // sign-in to be read.
    val model =
        remember(account) {
            when (val a = account) {
                is InstapaperState.SignedIn -> app.instapaper.newModel(a.session, app.appScope)
                InstapaperState.SignedOut -> app.instapaper.newLocalModel(app.appScope)
                InstapaperState.Loading -> null
            }
        }
    val ui by (model?.state ?: flowOf(null)).collectAsStateWithLifecycle(null)
    var started by remember { mutableStateOf(false) }

    fun say(text: Int) = Toast.makeText(context, resources.getString(text), Toast.LENGTH_SHORT).show()

    LaunchedEffect(model) {
        if (model == null || started) return@LaunchedEffect
        started = true
        try {
            model.save(link.url, link.title)
            // Reader's own Saved list reads it again (Navigation).
            app.savedElsewhere.update { it + 1 }
            if (model.state.value.justSaved == null) {
                say(if (model.state.value.account) R.string.instapaper_saved else R.string.saved_done)
                onDone()
            }
        } catch (e: InstapaperException) {
            say(e.problem.messageRes())
            onDone()
        }
    }

    val saved = ui?.justSaved
    if (model != null && saved != null) {
        JustSavedSheet(
            bookmark = saved,
            account = ui?.account ?: true,
            folders = ui?.folders.orEmpty(),
            tags = ui?.tags.orEmpty(),
            onLike = { model.toggleLike(saved) },
            onArchive = { model.toggleArchive(saved) },
            onMove = { folderId -> model.moveTo(saved, folderId) },
            onMoveToNewFolder = { title -> model.moveToNewFolder(saved, title) },
            onAddTag = { name -> model.addTag(saved, name) },
            onRemoveTag = { tag -> model.removeTag(saved, tag) },
            onDismiss = {
                model.closeJustSaved()
                // Folder and tag changes also show in Reader's own Saved list.
                app.savedElsewhere.update { it + 1 }
                onDone()
            },
        )
    }
}
