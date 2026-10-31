package to.wim.content.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import to.wim.content.R
import to.wim.content.data.Item
import to.wim.content.data.Library
import to.wim.content.data.ListView
import to.wim.content.data.ReadingLists
import to.wim.content.data.SharedBy
import to.wim.content.ui.reader.RecipientAvatar
import to.wim.content.ui.theme.EmptyState
import to.wim.content.ui.theme.readableWidth

/**
 * The inbox (#246): what other Content users shared with you, newest first, like a feed's articles
 * with who sent it in place of the feed. Unread ones are bold with a dot; opening one reads it. A
 * swipe saves it; a long press marks it read or unread, saves, shares or removes it from the inbox.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    items: List<Item>,
    library: Library,
    listView: ListView,
    showImages: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpen: (Item) -> Unit,
    onToggleRead: (Item) -> Unit,
    onSave: (Item) -> Unit,
    onShare: (Item) -> Unit,
    onRemove: (Item) -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = { Text(stringResource(R.string.inbox_title)) },
            )
        },
    ) { innerPadding ->
        if (items.isEmpty()) {
            EmptyState(
                painterResource(R.drawable.ic_notifications),
                stringResource(R.string.inbox_empty_title),
                body = stringResource(R.string.inbox_empty_body),
                modifier = Modifier.padding(innerPadding),
            )
            return@Scaffold
        }
        val saveLabel = stringResource(R.string.action_save)
        val shareLabel = stringResource(R.string.action_share)
        val removeLabel = stringResource(R.string.inbox_remove)
        val readLabel = stringResource(R.string.action_mark_read)
        val unreadLabel = stringResource(R.string.action_mark_unread)
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).readableWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { it.key }) { item ->
                val by = item.sharedBy ?: return@items
                val article = remember(item, library) { ReadingLists.article(item, library).copy(read = by.read, publishedAt = by.at, fetchedAt = by.at) }
                // Already in Saved: nothing to save, so no swipe and no Save in the menu.
                val save = if (item.onReadingList) null else SwipeAction(R.drawable.ic_list, saveLabel) { onSave(item) }
                SwipeRow(right = save, left = save, modifier = Modifier.animateItem()) {
                    ArticleRow(
                        article = article,
                        feed = null,
                        view = listView,
                        showImage = showImages,
                        onClick = { onOpen(item) },
                        menu =
                            listOfNotNull(
                                RowMenuAction(if (by.read) R.drawable.ic_mark_unread else R.drawable.ic_mark_read, if (by.read) unreadLabel else readLabel) { onToggleRead(item) },
                                save?.let { RowMenuAction(R.drawable.ic_list, saveLabel) { onSave(item) } },
                                RowMenuAction(R.drawable.ic_share, shareLabel) { onShare(item) },
                                RowMenuAction(R.drawable.ic_delete, removeLabel) { onRemove(item) },
                            ),
                        source = { Sender(by, showImages) },
                    )
                }
            }
        }
    }
}

/** Who shared it: their Gravatar picture (else their first letter) and name (else their address). */
@Composable
private fun Sender(
    by: SharedBy,
    showImages: Boolean,
) {
    RecipientAvatar(by.email, by.name.ifBlank { null }, showImages, size = 18.dp)
    Text(
        text = by.label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
