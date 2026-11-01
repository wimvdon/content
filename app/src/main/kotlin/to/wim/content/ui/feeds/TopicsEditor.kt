package to.wim.content.ui.feeds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import to.wim.content.data.Folder
import to.wim.content.ui.home.rememberFolderIconPainter
import to.wim.content.ui.theme.DragHandle
import to.wim.content.ui.theme.ReorderableColumn

/** What a sheet's Manage topics page (#307) can do with the topics; each is stored at once. */
class TopicEditActions(
    val onNew: (title: String, icon: String) -> Unit,
    /** A new name and icon; the name of another topic joins the two. */
    val onEdit: (topic: Folder, title: String, icon: String) -> Unit,
    val onRemove: (Folder) -> Unit,
    val onReorder: (ids: List<String>) -> Unit,
)

/**
 * Manage topics as a page inside the Add feed and Save sheets (#307), opened with the pen next to
 * their topics: Back (to the sheet), then every topic in the user's order, which drag changes. Tap a
 * topic (or its pen) to rename it or change its icon, or remove it (which asks first, on this page:
 * no dialog opens on top of a sheet); New topic makes one. The same rows as Manage topics in
 * Settings, without what each topic holds.
 */
@Composable
fun TopicsEditor(
    topics: List<Folder>,
    actions: TopicEditActions,
    onBack: () -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
            }
            Text(
                stringResource(R.string.manage_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp).semantics { heading() },
            )
        }
        val toRemove = topics.firstOrNull { it.id == removing }
        if (toRemove != null) {
            // Remove topic? asks here, in the sheet, instead of a dialog over it.
            Column(Modifier.padding(start = 12.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.folder_remove_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.folder_remove_body, toRemove.title), style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { removing = null }) { Text(stringResource(R.string.action_cancel)) }
                    TextButton(
                        onClick = {
                            removing = null
                            actions.onRemove(toRemove)
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.action_remove)) }
                }
            }
        } else {
            if (topics.isEmpty()) {
                Text(
                    stringResource(R.string.folders_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            ReorderableColumn(
                items = topics,
                key = { it.id },
                onMove = { from, to ->
                    val ids = topics.map { it.id }.toMutableList()
                    ids.add(to, ids.removeAt(from))
                    actions.onReorder(ids)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { topic ->
                TopicLine(
                    icon = { Icon(rememberFolderIconPainter(topic.icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer) },
                    title = topic.title,
                    detail = null,
                    failing = false,
                    lifted = isDragging,
                    onClick = { editing = topic.id },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { editing = topic.id }) {
                                Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.folder_edit))
                            }
                            DragHandle(handleModifier)
                        }
                    },
                )
            }
            TextButton(onClick = { creating = true }, modifier = Modifier.padding(top = 4.dp)) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                Text(stringResource(R.string.folder_new), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }

    if (creating) {
        FolderSheet(
            folder = null,
            onSave = { title, icon ->
                creating = false
                actions.onNew(title, icon)
            },
            onDismiss = { creating = false },
        )
    }
    topics.firstOrNull { it.id == editing }?.let { topic ->
        FolderSheet(
            folder = topic,
            onSave = { title, icon ->
                editing = null
                actions.onEdit(topic, title, icon)
            },
            onDismiss = { editing = null },
            onRemove = {
                editing = null
                removing = topic.id
            },
        )
    }
}
