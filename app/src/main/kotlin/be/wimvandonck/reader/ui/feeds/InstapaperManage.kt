package be.wimvandonck.reader.ui.feeds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import be.wimvandonck.reader.R
import be.wimvandonck.reader.instapaper.InstapaperFolder
import be.wimvandonck.reader.instapaper.InstapaperTag
import be.wimvandonck.reader.ui.theme.DragHandle
import be.wimvandonck.reader.ui.theme.ReorderableColumn
import be.wimvandonck.reader.ui.theme.readableWidth

/** Saved's folders and tags, for Manage Instapaper; only tags without an account ([account] false). */
data class InstapaperManageState(
    val folders: List<InstapaperFolder>,
    val tags: List<InstapaperTag>,
    val account: Boolean = true,
)

class InstapaperManageActions(
    val onCreateFolder: (String) -> Unit,
    /** Its articles go back to Saved at Instapaper. */
    val onDeleteFolder: (InstapaperFolder) -> Unit,
    val onMoveFolder: (InstapaperFolder, Int) -> Unit,
    val onRenameTag: (InstapaperTag, String) -> Unit,
)

/**
 * Manage Instapaper: its own page, apart from the feeds' folders (Manage feeds). Create, reorder
 * and delete folders, and rename tags (Instapaper creates a tag when it is first added to an
 * article).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstapaperManageScreen(
    state: InstapaperManageState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    actions: InstapaperManageActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(if (state.account) R.string.instapaper_manage_title else R.string.saved_manage_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .readableWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        ) {
            item(key = "instapaper") { InstapaperManageSection(state, actions) }
        }
    }
}

@Composable
private fun InstapaperManageSection(
    state: InstapaperManageState,
    actions: InstapaperManageActions,
) {
    var newFolder by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }
    var renaming by rememberSaveable { mutableStateOf<Long?>(null) }
    val rowColors = ListItemDefaults.colors(containerColor = Color.Transparent)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (state.account) {
            Text(
                stringResource(R.string.instapaper_folders),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            ReorderableColumn(
                items = state.folders,
                key = { it.id },
                onMove = { from, to -> actions.onMoveFolder(state.folders[from], to - from) },
            ) { folder ->
                ListItem(
                    headlineContent = { Text(folder.title) },
                    leadingContent = { Icon(painterResource(R.drawable.ic_folder), contentDescription = null) },
                    trailingContent = {
                        androidx.compose.foundation.layout.Row {
                            IconButton(onClick = { deleting = folder.id }) {
                                Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.instapaper_delete_folder))
                            }
                            DragHandle(handleModifier)
                        }
                    },
                    colors = rowColors,
                )
            }
            FilledTonalButton(onClick = { newFolder = true }) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                Text(stringResource(R.string.folder_new), modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (!state.account && state.tags.isEmpty()) {
            Text(stringResource(R.string.saved_manage_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.tags.isNotEmpty()) {
            Text(
                stringResource(R.string.instapaper_tags),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = if (state.account) 16.dp else 0.dp).semantics { heading() },
            )
            state.tags.forEach { tag ->
                ListItem(
                    headlineContent = { Text(tag.name) },
                    leadingContent = { Icon(painterResource(R.drawable.ic_label), contentDescription = null) },
                    trailingContent = { Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.instapaper_rename_tag)) },
                    colors = rowColors,
                    modifier = Modifier.clickable(role = Role.Button) { renaming = tag.id },
                )
            }
        }
    }

    if (newFolder) {
        NameDialog(R.string.folder_new, R.string.instapaper_folders, "", R.string.action_add, onDismiss = { newFolder = false }) {
            newFolder = false
            actions.onCreateFolder(it)
        }
    }
    state.folders.firstOrNull { it.id == deleting }?.let { folder ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.instapaper_delete_folder)) },
            text = { Text(folder.title + "\n\n" + stringResource(R.string.instapaper_delete_folder_note)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    actions.onDeleteFolder(folder)
                }) { Text(stringResource(R.string.instapaper_delete_folder)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    state.tags.firstOrNull { it.id == renaming }?.let { tag ->
        NameDialog(R.string.instapaper_rename_tag, R.string.instapaper_tag_name, tag.name, R.string.instapaper_rename_tag, onDismiss = { renaming = null }) {
            renaming = null
            if (it != tag.name) actions.onRenameTag(tag, it)
        }
    }
}

@Composable
private fun NameDialog(
    title: Int,
    label: Int,
    initial: String,
    confirm: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(stringResource(label)) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(stringResource(confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
