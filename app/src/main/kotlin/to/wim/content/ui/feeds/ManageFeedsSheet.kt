package to.wim.content.ui.feeds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import to.wim.content.R
import to.wim.content.data.Feed
import to.wim.content.data.FeedProblem
import to.wim.content.data.Folder
import to.wim.content.ui.home.FeedIcon
import to.wim.content.ui.home.FeedIconSize
import to.wim.content.ui.home.problemText
import to.wim.content.ui.home.rememberFolderIconPainter
import to.wim.content.ui.reader.SheetHeader

/** What the Manage feeds sheet can do with the feeds; each change is stored at once. */
class ManageFeedsActions(
    /** Add feed: it lands without a topic, and the sheet stays open under it. */
    val onAddFeed: () -> Unit,
    val onEditFeed: (feed: Feed, title: String, topicId: String?) -> Unit,
    val onRemoveFeed: (Feed) -> Unit,
    val onReorderFeeds: (topicId: String?, urls: List<String>) -> Unit,
)

/**
 * Manage feeds as a sheet (from Settings, and wherever else it is linked): the feeds as chips,
 * grouped by topic (in the topics' order, then No topic), like the chips in Feeds and the sheets.
 * Drag a chip by its handle to reorder it within its topic; tap it for the Edit feed sheet (name,
 * topic, Remove). A feed that fails to download has its name in the error colour. Add feed at the
 * end. Changes are stored at once: **Done** at the top right.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageFeedsSheet(
    feeds: List<Feed>,
    topics: List<Folder>,
    problems: Map<String, FeedProblem>,
    actions: ManageFeedsActions,
    topicActions: TopicEditActions?,
    onDismiss: () -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val groups =
        remember(feeds, topics) {
            val inTopics = topics.map { topic -> topic to feeds.filter { it.folderId == topic.id } }
            val loose = feeds.filter { feed -> feed.folderId == null || topics.none { it.id == feed.folderId } }
            (inTopics + (null to loose)).filter { it.second.isNotEmpty() }
        }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetHeader(stringResource(R.string.settings_feeds_title), onCancel = onDismiss, closeLabel = stringResource(R.string.action_done))
            Text(
                stringResource(if (feeds.isEmpty()) R.string.manage_feeds_none else R.string.manage_feeds_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            groups.forEach { (topic, inGroup) ->
                key(topic?.id) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (topic != null) Icon(rememberFolderIconPainter(topic.icon), contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(
                                topic?.title ?: stringResource(R.string.folder_none),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.semantics { heading() },
                            )
                        }
                        ReorderableChips(
                            items = inGroup,
                            key = { it.url },
                            label = { it.title },
                            icon = { FeedIcon(it, FeedIconSize.Small) },
                            onReorder = { urls -> actions.onReorderFeeds(topic?.id, urls) },
                            onEdit = { editing = it.url },
                            editLabel = stringResource(R.string.feed_edit),
                            isError = { problems.containsKey(it.url) },
                        )
                    }
                }
            }
            FilledTonalButton(onClick = actions.onAddFeed, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Text(stringResource(R.string.feeds_add), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
    feeds.firstOrNull { it.url == editing }?.let { feed ->
        FeedSheet(
            feed = feed,
            topics = topics,
            problem = problems[feed.url]?.let { problemText(it) },
            onSave = { title, topicId ->
                editing = null
                actions.onEditFeed(feed, title, topicId)
            },
            onRemove = {
                editing = null
                actions.onRemoveFeed(feed)
            },
            onDismiss = { editing = null },
            topicActions = topicActions,
        )
    }
}
