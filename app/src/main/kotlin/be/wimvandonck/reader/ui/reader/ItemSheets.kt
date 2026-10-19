package be.wimvandonck.reader.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import be.wimvandonck.reader.R
import be.wimvandonck.reader.data.Bookmarks
import be.wimvandonck.reader.data.Item
import be.wimvandonck.reader.data.ItemKeys
import be.wimvandonck.reader.data.SaveTo
import be.wimvandonck.reader.feed.FeedDiscovery
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow

/**
 * An item's tags as removable chips, a field for a new one, and the other tags in use to add with a
 * tap. [onChange] gets the whole new list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsEditor(
    tags: List<String>,
    allTags: List<String>,
    onChange: (List<String>) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    val others = allTags.filter { tag -> tags.none { it.equals(tag, ignoreCase = true) } }
    fun add(tag: String) {
        val clean = tag.trim()
        if (clean.isNotEmpty() && tags.none { it.equals(clean, ignoreCase = true) }) onChange(tags + clean)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { tag ->
                    InputChip(
                        selected = true,
                        onClick = { onChange(tags - tag) },
                        label = { Text(tag) },
                        trailingIcon = {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.tags_remove), modifier = Modifier.size(18.dp))
                        },
                    )
                }
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.tags_add)) },
            singleLine = true,
            trailingIcon = {
                IconButton(
                    onClick = {
                        add(name)
                        name = ""
                    },
                    enabled = name.isNotBlank(),
                ) { Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.tags_add)) }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (others.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                others.forEach { tag ->
                    AssistChip(
                        onClick = { add(tag) },
                        label = { Text(tag) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
        }
    }
}

/** The tags of an item in a dialog (the reader's ⋮ › Tags). */
@Composable
fun TagsDialog(
    item: Item,
    allTags: List<String>,
    onChange: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tags_title)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { TagsEditor(item.tags, allTags, onChange) } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

/**
 * Opens right after something is added to the reading list: like it and tag it at once. Every tap
 * applies straight away; closing the sheet leaves it on the reading list.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddedSheet(
    item: Item,
    allTags: List<String>,
    onLike: () -> Unit,
    onTags: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.reading_list_added), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(item.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = item.liked,
                    onClick = onLike,
                    label = { Text(stringResource(R.string.action_like)) },
                    leadingIcon = {
                        Icon(painterResource(if (item.liked) R.drawable.ic_favorite_filled else R.drawable.ic_favorite), contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                )
            }
            Text(stringResource(R.string.tags_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            TagsEditor(item.tags, allTags, onTags)
            Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.action_done)) }
        }
    }
}

/** What the sheet at the end of an article does. */
enum class FinishChoice { Archive, Keep }

/**
 * "Finished reading" (#107): archive (done with it; it stays searchable) or keep it as a bookmark
 * with a collection, a note and tags (it then stays on the reading list until archived). Keep is
 * chosen first when the article has highlights or a note. Opens at the end of an article and from
 * the reader's Keep and Archive buttons ([initial]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinishSheet(
    item: Item,
    initial: FinishChoice?,
    collections: List<String>,
    allTags: List<String>,
    onArchive: () -> Unit,
    onKeep: (collection: String, note: String, tags: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val worthKeeping = item.highlights.isNotEmpty() || item.note.isNotBlank()
    var choice by rememberSaveable(item.key) { mutableStateOf(initial ?: if (worthKeeping) FinishChoice.Keep else FinishChoice.Archive) }
    var collection by rememberSaveable(item.key) { mutableStateOf(item.bookmark?.collection.orEmpty()) }
    var note by rememberSaveable(item.key) { mutableStateOf(item.note) }
    var tags by remember(item.key) { mutableStateOf(item.tags) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.finish_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            if (worthKeeping) {
                val parts =
                    listOfNotNull(
                        item.highlights.size.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.highlights_count, it, it) },
                        if (item.note.isNotBlank()) stringResource(R.string.finish_with_note) else null,
                    )
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.selectableGroup()) {
                FinishOption(
                    selected = choice == FinishChoice.Archive,
                    icon = R.drawable.ic_inventory,
                    title = stringResource(R.string.action_archive),
                    body = stringResource(R.string.finish_archive_body),
                    onClick = { choice = FinishChoice.Archive },
                )
                FinishOption(
                    selected = choice == FinishChoice.Keep,
                    icon = R.drawable.ic_bookmark_add,
                    title = stringResource(R.string.action_keep_bookmark),
                    body = stringResource(R.string.finish_keep_body),
                    onClick = { choice = FinishChoice.Keep },
                )
            }
            if (choice == FinishChoice.Keep) {
                CollectionField(collection, collections, onChange = { collection = it })
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.note_title)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                TagsEditor(tags, allTags, onChange = { tags = it })
            }
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = {
                    if (choice == FinishChoice.Archive) onArchive() else onKeep(collection, note, tags)
                    onDismiss()
                }) { Text(stringResource(if (choice == FinishChoice.Archive) R.string.action_archive else R.string.action_keep)) }
            }
        }
    }
}

@Composable
private fun FinishOption(
    selected: Boolean,
    icon: Int,
    title: String,
    body: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RadioButton(selected = selected, onClick = null)
    }
}

/**
 * "Save to Reader" (#108): a link shared to Reader, the custom tab's Save, Add bookmark, or Keep as
 * bookmark on a feed article. Reading list or Bookmark (articles are suggested for the reading
 * list, other links for Bookmarks), then the collection (Bookmark only), a note and tags. Nothing is
 * saved until Save. Without [url] it asks for an address first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveSheet(
    url: String?,
    title: String?,
    initial: SaveTo?,
    collections: List<String>,
    allTags: List<String>,
    onSave: (url: String, to: SaveTo, collection: String, note: String, tags: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var address by rememberSaveable(url) { mutableStateOf(url.orEmpty()) }
    var chosen by rememberSaveable(url) { mutableStateOf(initial) }
    // A typed address may leave out https://.
    val link = remember(address) { url ?: FeedDiscovery.normalizeUrl(address)?.takeIf { ItemKeys.isWebLink(it) } }
    val to = chosen ?: Bookmarks.suggested(link ?: address)
    var collection by rememberSaveable(url) { mutableStateOf("") }
    var note by rememberSaveable(url) { mutableStateOf("") }
    var tags by remember(url) { mutableStateOf(emptyList<String>()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.save_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            if (url == null) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(stringResource(R.string.add_link_address)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Column {
                    if (!title.isNullOrBlank()) Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                    Text(ItemKeys.hostOf(url).ifEmpty { url }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SaveTo.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = to == option,
                        onClick = { chosen = option },
                        shape = SegmentedButtonDefaults.itemShape(index, SaveTo.entries.size),
                        icon = { SegmentedButtonDefaults.Icon(to == option) { Icon(painterResource(if (option == SaveTo.ReadingList) R.drawable.ic_reading_list else R.drawable.ic_bookmark_add), contentDescription = null, modifier = Modifier.size(SegmentedButtonDefaults.IconSize)) } },
                        label = { Text(stringResource(if (option == SaveTo.ReadingList) R.string.reading_list_title else R.string.save_to_bookmark)) },
                    )
                }
            }
            Text(stringResource(R.string.save_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (to == SaveTo.Bookmark) CollectionField(collection, collections, onChange = { collection = it })
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.note_title)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            TagsEditor(tags, allTags, onChange = { tags = it })
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(enabled = link != null, onClick = {
                    link?.let { onSave(it, to, collection, note, tags) }
                    onDismiss()
                }) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
}

/** Edit bookmark: its collection, note and tags. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkEditSheet(
    item: Item,
    collections: List<String>,
    allTags: List<String>,
    onSave: (collection: String, note: String, tags: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var collection by rememberSaveable(item.key) { mutableStateOf(item.bookmark?.collection.orEmpty()) }
    var note by rememberSaveable(item.key) { mutableStateOf(item.note) }
    var tags by remember(item.key) { mutableStateOf(item.tags) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.bookmark_edit), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            CollectionField(collection, collections, onChange = { collection = it })
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.note_title)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            TagsEditor(tags, allTags, onChange = { tags = it })
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = {
                    onSave(collection, note, tags)
                    onDismiss()
                }) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
}

/** A bookmark's one collection: typed, or one of those in use picked with a tap. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CollectionField(
    value: String,
    collections: List<String>,
    onChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(stringResource(R.string.collection_title)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_folder), contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        val others = collections.filterNot { it.equals(value.trim(), ignoreCase = true) }
        if (others.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                others.forEach { c -> AssistChip(onClick = { onChange(c) }, label = { Text(c) }) }
            }
        }
    }
}

/** Add link: an address to put on the reading list. */
@Composable
fun AddLinkDialog(
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var address by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_link_title)) },
        text = {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text(stringResource(R.string.add_link_address)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onAdd(address.trim()) }, enabled = address.isNotBlank()) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Remove from the reading list (the reader's ⋮): asks first, says what stays. */
@Composable
fun RemoveDialog(
    item: Item,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reading_list_remove)) },
        text = { Text(stringResource(if (item.bookmark != null) R.string.reading_list_remove_body_bookmark else R.string.reading_list_remove_body)) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onRemove()
            }) { Text(stringResource(R.string.action_remove)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
