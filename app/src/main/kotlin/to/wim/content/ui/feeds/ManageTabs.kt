package to.wim.content.ui.feeds

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import to.wim.content.R

/** Which part of Manage feeds and Saved is shown (#222, #251). */
enum class ManageTab { Feeds, Saved }

/**
 * Manage feeds and Saved is one page (#222, #251): one title, and tabs for the feeds with their
 * folders and OPML ([FeedsTab]), and for Saved's folders and tags, Tag with the feed's folder and
 * Instapaper ([SavedManageTab]). ☰'s Manage feeds and Saved opens it on Feeds. A tab only changes
 * what is below the tabs: the title bar stays as it is, and each tab keeps where it was scrolled to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageScreen(
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    feeds: @Composable (PaddingValues) -> Unit,
    saved: @Composable (PaddingValues) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(ManageTab.Feeds) }
    val tabStates = rememberSaveableStateHolder()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                LargeTopAppBar(
                    title = { Text(stringResource(R.string.manage_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
                PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                    Tab(selected = tab == ManageTab.Feeds, onClick = { tab = ManageTab.Feeds }, text = { Text(stringResource(R.string.feeds_title)) })
                    Tab(selected = tab == ManageTab.Saved, onClick = { tab = ManageTab.Saved }, text = { Text(stringResource(R.string.saved_title)) })
                }
            }
        },
    ) { innerPadding ->
        tabStates.SaveableStateProvider(tab.name) {
            when (tab) {
                ManageTab.Feeds -> feeds(innerPadding)
                ManageTab.Saved -> saved(innerPadding)
            }
        }
    }
}
