package to.wim.content.ui.feeds

import androidx.compose.foundation.layout.Arrangement
import to.wim.content.ui.theme.DragHandle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import to.wim.content.ui.reader.ContentSheet
import to.wim.content.data.Feed
import to.wim.content.data.FeedProblem
import to.wim.content.data.Folder
import to.wim.content.ui.home.FeedIcon
import to.wim.content.ui.home.FeedIconSize
import to.wim.content.ui.home.problemText
import to.wim.content.ui.home.rememberFolderIconPainter
import to.wim.content.ui.reader.SheetActions
import to.wim.content.ui.reader.SheetHeader

/** What the Manage feeds sheet can do with the feeds; each change is stored at once. */
class ManageFeedsActions(
    /** Add feed: it lands without a topic, as a page of this sheet ([ManageFeedsSheet]'s `addFeed`). */
    val onAddFeed: () -> Unit,
    val onEditFeed: (feed: Feed, title: String, topicId: String?) -> Unit,
    val onRemoveFeed: (Feed) -> Unit,
    val onReorderFeeds: (topicId: String?, urls: List<String>) -> Unit,
    /** A feed dragged into another topic (null: none), with that topic's feeds in their new order. */
    val onMoveFeed: (feed: Feed, topicId: String?, urls: List<String>) -> Unit = { _, _, _ -> },
)

/**
 * Manage feeds as a sheet (from Settings, and wherever else it is linked): the feeds as chips,
 * grouped by topic (in the topics' order, then No topic), like the chips in Feeds and the sheets.
 * Every topic shows, an empty one too. Drag a chip by its handle to reorder it within its topic or
 * to move it into another topic (or No topic); tap it for the Edit feed sheet (name,
 * topic, Remove) as a page of this sheet, with a back arrow. A feed that fails to download has its name in the error colour. Add feed at the
 * bottom left. Changes are stored at once: **Done** at the bottom right.
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
    /** The Add feed sheet while it is open: declared here, so it opens as a page of this sheet. */
    addFeed: @Composable () -> Unit = {},
) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val groups =
        remember(feeds, topics) {
            val inTopics = topics.map { topic -> topic to feeds.filter { it.folderId == topic.id } }
            val loose = feeds.filter { feed -> feed.folderId == null || topics.none { it.id == feed.folderId } }
            // Every topic, so a feed can be dragged into an empty one; none at all without feeds.
            if (feeds.isEmpty()) emptyList() else inTopics + (null to loose)
        }
    ContentSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetHeader(stringResource(R.string.settings_feeds_title), onCancel = onDismiss)
            Text(
                stringResource(if (feeds.isEmpty()) R.string.manage_feeds_none else R.string.manage_feeds_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val folderNone = stringResource(R.string.folder_none)
            val dropHere = stringResource(R.string.manage_feeds_drop_here)
            // Rows under each tag's heading, as the design kit's reordering lists (DESIGN.md,
            // Reordering): drag a row by the grip at its end, within a tag or under another heading.
            ReorderableSections(
                sections = groups.map { (topic, inGroup) -> (topic?.id ?: NO_TOPIC) to inGroup },
                key = { it.url },
                onReorder = { groupId, urls -> actions.onReorderFeeds(groupId.takeUnless { it == NO_TOPIC }, urls) },
                onMove = { feed, groupId, urls -> actions.onMoveFeed(feed, groupId.takeUnless { it == NO_TOPIC }, urls) },
                heading = { groupId ->
                    val topic = topics.firstOrNull { it.id == groupId }
                    val empty = groups.firstOrNull { (t, _) -> (t?.id ?: NO_TOPIC) == groupId }?.second.isNullOrEmpty()
                    Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (topic != null) Icon(rememberFolderIconPainter(topic.icon), contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                topic?.title ?: folderNone,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.semantics { heading() },
                            )
                        }
                        // An empty tag: where a feed dragged under its heading goes.
                        if (empty) {
                            Text(dropHere, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                },
            ) { feed ->
                val editLabel = stringResource(R.string.feed_edit)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(onClickLabel = editLabel) { editing = feed.url },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    FeedIcon(feed, FeedIconSize.Medium)
                    Text(
                        feed.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (problems.containsKey(feed.url)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    DragHandle(handleModifier)
                }
            }
            // Changes are stored at once: Done at the bottom right, Add feed at the left.
            SheetActions(
                onCancel = onDismiss,
                cancel = false,
                leading = {
                    FilledTonalButton(onClick = actions.onAddFeed, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Text(stringResource(R.string.feeds_add), modifier = Modifier.padding(start = 8.dp))
                    }
                },
            ) {
                // Done only closes (changes are kept at once): a text button; Add feed is the action here.
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
            }
        }
        addFeed()
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

/** The No topic group's id among the topics' (a topic's id is never empty). */
private const val NO_TOPIC = ""
