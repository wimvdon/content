package be.wimvandonck.reader.ui.feeds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import be.wimvandonck.reader.R
import be.wimvandonck.reader.ui.theme.EmptyState
import be.wimvandonck.reader.ui.theme.readableWidth

/**
 * Collections and tags (☰): every bookmark collection with how many bookmarks it has, then every
 * tag on the reading list and in Bookmarks with how many articles have it. Tap to rename (renaming
 * to another one's name joins the two), or remove it: a tag from every article, a collection from
 * its bookmarks (they stay, without a collection).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsManageScreen(
    tags: Map<String, Int>,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRename: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    collections: Map<String, Int> = emptyMap(),
    onRenameCollection: (String, String) -> Unit = { _, _ -> },
    onRemoveCollection: (String) -> Unit = {},
) {
    // What is being renamed or removed: "t:<tag>" or "c:<collection>".
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.menu_manage_tags)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        if (tags.isEmpty() && collections.isEmpty()) {
            EmptyState(
                painterResource(R.drawable.ic_tags),
                stringResource(R.string.tags_manage_empty_title),
                body = stringResource(R.string.tags_manage_empty_body),
                modifier = Modifier.padding(innerPadding),
            )
            return@Scaffold
        }
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .readableWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        ) {
            if (collections.isNotEmpty()) {
                item(key = "collections") { Heading(stringResource(R.string.collections_title)) }
                items(collections.entries.toList(), key = { "c:" + it.key }) { (collection, count) ->
                    ListItem(
                        headlineContent = { Text(collection) },
                        supportingContent = { Text(pluralStringResource(R.plurals.bookmarks_count, count, count)) },
                        leadingContent = { Icon(painterResource(R.drawable.ic_folder), contentDescription = null) },
                        trailingContent = {
                            IconButton(onClick = { removing = "c:$collection" }) {
                                Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.collection_remove))
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(role = Role.Button, onClickLabel = stringResource(R.string.collection_rename)) { renaming = "c:$collection" },
                    )
                }
            }
            if (tags.isNotEmpty()) {
                if (collections.isNotEmpty()) item(key = "tags") { Heading(stringResource(R.string.tags_title)) }
                items(tags.entries.toList(), key = { "t:" + it.key }) { (tag, count) ->
                    ListItem(
                        headlineContent = { Text(tag) },
                        supportingContent = { Text(pluralStringResource(R.plurals.articles_count, count, count)) },
                        leadingContent = { Icon(painterResource(R.drawable.ic_label), contentDescription = null) },
                        trailingContent = {
                            IconButton(onClick = { removing = "t:$tag" }) {
                                Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.tags_remove_everywhere))
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(role = Role.Button, onClickLabel = stringResource(R.string.tags_rename)) { renaming = "t:$tag" },
                    )
                }
            }
        }
    }

    renaming?.let { target ->
        val collection = target.startsWith("c:")
        val tag = target.substring(2)
        var text by rememberSaveable(target) { mutableStateOf(tag) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(if (collection) R.string.collection_rename else R.string.tags_rename)) },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(stringResource(R.string.tags_name)) }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    renaming = null
                    if (text.trim() != tag) (if (collection) onRenameCollection else onRename)(tag, text.trim())
                }, enabled = text.isNotBlank()) { Text(stringResource(R.string.tags_rename)) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    removing?.let { target ->
        val collection = target.startsWith("c:")
        val tag = target.substring(2)
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(if (collection) R.string.collection_remove else R.string.tags_remove_everywhere)) },
            text = { Text(stringResource(if (collection) R.string.collection_remove_body else R.string.tags_remove_body, tag)) },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    if (collection) onRemoveCollection(tag) else onRemove(tag)
                }) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                .semantics { heading() },
    )
}
