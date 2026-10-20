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
 * shared link's title and site), the folder as chips (To read and Archive, a divider, the folders and
 * New folder: #156), the tags with Like in front, then the note. Then Cancel and Save; closing the
 * sheet is Cancel, so only Save changes anything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveSheet(
    kind: SheetKind,
    initial: SaveChoice,
    folders: List<String>,
    allTags: List<String>,
    /** With the link for a new save; null for one already saved, which gets this on every close. */
    onSave: (url: String?, choice: SaveChoice) -> Unit,
    onDismiss: () -> Unit,
    /** The shared link (with [title]); null with [SheetKind.New] asks for an address. */
    url: String? = null,
    title: String? = null,
    /** The sheet's state belongs to this article. */
    key: String? = url,
    /** Cancel, or the sheet closed without Save. */
    onCancel: () -> Unit = {},
) {
    var address by rememberSaveable(key) { mutableStateOf(url.orEmpty()) }
    // A typed address may leave out https://.
    val link = remember(address) { url ?: FeedDiscovery.normalizeUrl(address)?.takeIf { ItemKeys.isWebLink(it) } }
    var choice by remember(key) { mutableStateOf(initial) }
    val saved = kind != SheetKind.New
    val save = {
        if (saved) {
            onSave(null, choice)
        } else {
            link?.let { onSave(it, choice) }
        }
        onDismiss()
    }
    // Cancel, or closing the sheet, changes nothing (#156); an article saved a moment ago stays where it went.
    val cancel = {
        onCancel()
        onDismiss()
    }
    ModalBottomSheet(onDismissRequest = cancel, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
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
            FolderChips(choice.place, folders, onChange = { choice = choice.copy(place = it) })
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
                TextButton(onClick = cancel) { Text(stringResource(R.string.action_cancel)) }
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
 * An item's one place (#119, #143, #156), as chips to pick one: To read and Archive, each with its
 * icon, then a divider, each folder, and New folder, the only thing left to type.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolderChips(
    place: Place,
    folders: List<String>,
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
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            SaveChoices.places(folders, place).forEach { option ->
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
                // The folders after To read and Archive, set apart as Like is from the tags.
                if (option == Place.Archive) VerticalDivider(Modifier.height(24.dp))
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
