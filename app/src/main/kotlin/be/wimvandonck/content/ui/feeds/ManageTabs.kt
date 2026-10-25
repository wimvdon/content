package be.wimvandonck.content.ui.feeds

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import be.wimvandonck.content.R

/** Which part of Manage feeds and Saved is shown (#222, #251). */
enum class ManageTab { Feeds, Saved }

/**
 * Manage feeds and Saved is one screen (#222, #251): one title, and tabs for the feeds with their
 * folders and OPML, and for Saved's folders and tags, Tag with the feed's folder and Instapaper.
 * ☰'s Manage feeds and Saved opens it on Feeds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageTopBar(
    tab: ManageTab,
    onTab: (ManageTab) -> Unit,
    onBack: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
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
            Tab(selected = tab == ManageTab.Feeds, onClick = { onTab(ManageTab.Feeds) }, text = { Text(stringResource(R.string.feeds_title)) })
            Tab(selected = tab == ManageTab.Saved, onClick = { onTab(ManageTab.Saved) }, text = { Text(stringResource(R.string.saved_title)) })
        }
    }
}
