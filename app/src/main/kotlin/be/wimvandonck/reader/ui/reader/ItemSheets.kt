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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import be.wimvandonck.reader.R
import be.wimvandonck.reader.data.Item
import be.wimvandonck.reader.data.ItemKeys
import be.wimvandonck.reader.data.Place
import be.wimvandonck.reader.data.SaveChoice
import be.wimvandonck.reader.data.SaveChoices
import be.wimvandonck.reader.feed.FeedDiscovery
import be.wimvandonck.reader.ui.settings.serviceName

/**
 * An item's tags in one row, the same shape as Saved's filter row (#143): an optional [leading] chip
 * (Like, set apart by a divider), the tags as removable chips, then Add a tag, which opens a field
 * with the other tags in use to add with a tap. [onChange] gets the whole new list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsEditor(
    tags: List<String>,
    allTags: List<String>,
    onChange: (List<String>) -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var adding by rememberSaveable { mutableStateOf(false) }
    val others = allTags.filter { tag -> tags.none { it.equals(tag, ignoreCase = true) } }
    fun add(tag: String) {
        val clean = tag.trim()
        if (clean.isNotEmpty() && tags.none { it.equals(clean, ignoreCase = true) }) onChange(tags + clean)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                VerticalDivider(Modifier.height(24.dp))
            }
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
            if (!adding) {
                AssistChip(
                    onClick = { adding = true },
                    label = { Text(stringResource(R.string.tags_add)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
        if (adding) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.tags_add)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions =
                    KeyboardActions(onDone = {
                        add(name)
                        name = ""
                    }),
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

/** What the sheet at the end of an article does. */
enum class FinishChoice { Archive, File }

/**
 * "Finished reading" (#107, #119): archive (done with it; it stays searchable) or file it in a
 * folder with a note and tags. File is chosen first when the article has highlights or a note.
 * Opens at the end of an article and from the reader's File and Archive buttons ([initial]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinishSheet(
    item: Item,
    initial: FinishChoice?,
    folders: List<String>,
    allTags: List<String>,
    onArchive: () -> Unit,
    onFile: (folder: String, note: String, tags: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val worthKeeping = item.highlights.isNotEmpty() || item.note.isNotBlank()
    var choice by rememberSaveable(item.key) { mutableStateOf(initial ?: if (worthKeeping) FinishChoice.File else FinishChoice.Archive) }
    var folder by rememberSaveable(item.key) { mutableStateOf(item.folder.orEmpty()) }
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
                    selected = choice == FinishChoice.File,
                    icon = R.drawable.ic_folder,
                    title = stringResource(R.string.file_title),
                    body = stringResource(R.string.finish_file_body),
                    onClick = { choice = FinishChoice.File },
                )
            }
            if (choice == FinishChoice.File) {
                FolderChips(
                    place = folder.trim().ifEmpty { null }?.let(Place::Folder) ?: Place.ToRead,
                    folders = folders,
                    offerArchive = false,
                    onChange = { place -> folder = (place as? Place.Folder)?.name.orEmpty() },
                )
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
                    if (choice == FinishChoice.Archive) onArchive() else onFile(folder, note, tags)
                    onDismiss()
                }) {
                    Text(
                        when {
                            choice == FinishChoice.Archive -> stringResource(R.string.action_archive)
                            folder.isBlank() -> stringResource(R.string.file_in, stringResource(R.string.reading_list_to_read))
                            else -> stringResource(R.string.file_in, folder.trim())
                        },
                    )
                }
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

/** Which way into the save sheet (#143): the heading and Cancel depend on whether the save has happened. */
enum class SheetKind {
    /** A share or Add link: nothing is saved until Save, and Cancel saves nothing. */
    New,

    /** Saved a moment ago (the reader, a swipe): "Saved to …"; closing keeps what the sheet changed. */
    JustSaved,

    /** An article saved before, organised from Saved or the reader (#139). */
    Organise,
}

/**
 * The one sheet for an article (#143): saving one, filing one later, or tagging one saved last week.
 * In order: what is being saved (only for [SheetKind.New]: the address field for Add link, or the
 * shared link's title and site), the folder as chips (To read, the folders, New folder, and Archive
 * for a new save), the tags with Like in front, then the note. Then Save, and Cancel only when
 * nothing has been saved yet. Swiping away a sheet whose save already happened keeps what it says.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveSheet(
    kind: SheetKind,
    initial: SaveChoice,
    folders: List<String>,
    allTags: List<String>,
    onSave: (url: String?, choice: SaveChoice) -> Unit,
    onDismiss: () -> Unit,
    /** The shared link (with [title]); null with [SheetKind.New] asks for an address. */
    url: String? = null,
    title: String? = null,
    /** The sheet's state belongs to this article. */
    key: String? = url,
) {
    var address by rememberSaveable(key) { mutableStateOf(url.orEmpty()) }
    // A typed address may leave out https://.
    val link = remember(address) { url ?: FeedDiscovery.normalizeUrl(address)?.takeIf { ItemKeys.isWebLink(it) } }
    var choice by remember(key) { mutableStateOf(initial) }
    val saved = kind != SheetKind.New
    val save = {
        if (saved) {
            if (choice != initial) onSave(null, choice)
        } else {
            link?.let { onSave(it, choice) }
        }
        onDismiss()
    }
    ModalBottomSheet(onDismissRequest = if (saved) save else onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val toRead = stringResource(R.string.reading_list_to_read)
            Text(
                when (kind) {
                    SheetKind.New -> stringResource(R.string.save_title)
                    SheetKind.Organise -> stringResource(R.string.organise_title)
                    SheetKind.JustSaved ->
                        stringResource(
                            R.string.saved_to,
                            when (val place = choice.place) {
                                Place.ToRead -> toRead
                                Place.Archive -> stringResource(R.string.reading_list_archive)
                                is Place.Folder -> place.name
                            },
                        )
                },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            if (kind == SheetKind.New) {
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
            }
            SheetSection(stringResource(R.string.folder_field))
            FolderChips(choice.place, folders, offerArchive = kind == SheetKind.New, onChange = { choice = choice.copy(place = it) })
            SheetSection(stringResource(R.string.tags_title))
            TagsEditor(
                tags = choice.tags,
                allTags = allTags,
                onChange = { choice = choice.copy(tags = it) },
                leading = {
                    FilterChip(
                        selected = choice.liked,
                        onClick = { choice = choice.copy(liked = !choice.liked) },
                        label = { Text(stringResource(R.string.action_like)) },
                        leadingIcon = {
                            Icon(
                                painterResource(if (choice.liked) R.drawable.ic_favorite_filled else R.drawable.ic_favorite),
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        },
                    )
                },
            )
            SheetSection(stringResource(R.string.note_title))
            OutlinedTextField(
                value = choice.note,
                onValueChange = { choice = choice.copy(note = it) },
                placeholder = { Text(stringResource(R.string.note_title)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!saved) TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(enabled = saved || link != null, onClick = save) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
}

/** A section's heading in the sheet, in the primary colour. */
@Composable
private fun SheetSection(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp).semantics { heading() })
}

/**
 * An item's one place (#119, #143), as chips to pick one: To read, each folder, Archive (when
 * offered), and New folder, the only thing left to type.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolderChips(
    place: Place,
    folders: List<String>,
    offerArchive: Boolean,
    onChange: (Place) -> Unit,
) {
    var making by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    val make = {
        SaveChoices.newFolder(name, folders)?.let(onChange)
        name = ""
        making = false
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SaveChoices.places(folders, place, offerArchive).forEach { option ->
                val selected = SaveChoices.same(option, place)
                FilterChip(
                    selected = selected,
                    onClick = { onChange(option) },
                    label = {
                        Text(
                            when (option) {
                                Place.ToRead -> stringResource(R.string.reading_list_to_read)
                                Place.Archive -> stringResource(R.string.action_archive)
                                is Place.Folder -> option.name
                            },
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painterResource(
                                when {
                                    selected -> R.drawable.ic_check
                                    option == Place.ToRead -> R.drawable.ic_reading_list
                                    option == Place.Archive -> R.drawable.ic_inventory
                                    else -> R.drawable.ic_folder
                                },
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    },
                    modifier = Modifier.semantics { role = Role.RadioButton },
                )
            }
            if (!making) {
                AssistChip(
                    onClick = { making = true },
                    label = { Text(stringResource(R.string.folder_new)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
        if (making) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.folder_new)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { make() }),
                trailingIcon = {
                    IconButton(onClick = make, enabled = name.isNotBlank()) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.folder_new))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Remove from Saved (the reader's ⋮): asks first, says what stays. An article both services keep
 * (#121) can also go from one of them only; it then stays in Saved, kept in the other.
 */
@Composable
fun RemoveDialog(
    item: Item,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
    services: Set<String> = emptySet(),
    onRemoveFrom: (String) -> Unit = {},
) {
    val both = services.size > 1
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.saved_remove)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(if (both) R.string.saved_remove_both_body else R.string.saved_remove_body))
                if (both) {
                    for (id in services) {
                        TextButton(onClick = {
                            onDismiss()
                            onRemoveFrom(id)
                        }) { Text(stringResource(R.string.saved_remove_only_from, stringResource(serviceName(id)))) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onRemove()
            }) { Text(stringResource(if (both) R.string.saved_remove_from_both else R.string.action_remove)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
