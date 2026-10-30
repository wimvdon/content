package be.wimvandonck.content.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import be.wimvandonck.content.R
import be.wimvandonck.content.data.Item
import be.wimvandonck.content.data.ItemKeys
import be.wimvandonck.content.data.Place
import be.wimvandonck.content.data.SaveChoice
import be.wimvandonck.content.data.SaveChoices
import be.wimvandonck.content.data.SendTo
import be.wimvandonck.content.feed.FeedDiscovery

/**
 * An item's tags (#143): an optional [leading] chip (Like), then every tag in use as a chip to tap
 * on or off, in a steady order so nothing moves when one is chosen, then New tag, which opens a field
 * to make a tag that doesn't exist yet. [onChange] gets the whole new list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsEditor(
    tags: List<String>,
    allTags: List<String>,
    onChange: (List<String>) -> Unit,
    leading: (@Composable () -> Unit)? = null,
    /** The tag being typed, kept by the sheet so Save adds it too. */
    name: String,
    onNameChange: (String) -> Unit,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    val shown = SaveChoices.tagChoices(allTags, tags)
    fun has(tag: String) = tags.any { it.equals(tag, ignoreCase = true) }
    fun add(tag: String) {
        val clean = tag.trim()
        if (clean.isNotEmpty() && !has(clean)) onChange(tags + clean)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            if (leading != null) leading()
            shown.forEach { tag ->
                val on = has(tag)
                // No check mark: the fill shows it, and the chips keep their width and place.
                FilterChip(
                    selected = on,
                    onClick = { onChange(if (on) tags.filterNot { it.equals(tag, ignoreCase = true) } else tags + tag) },
                    label = { Text(tag) },
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
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.tags_add)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions =
                    KeyboardActions(onDone = {
                        add(name)
                        onNameChange("")
                    }),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            add(name)
                            onNameChange("")
                        },
                        enabled = name.isNotBlank(),
                    ) { Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.tags_add)) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Which way into the save sheet (#143): the heading and Cancel depend on whether the save has happened. */
enum class SheetKind {
    /** A share or Add link: nothing is saved until Save, and Cancel saves nothing. */
    New,

    /** Saved a moment ago (the reader, a swipe): "Saved to …"; Remove takes the save back, closing keeps it. */
    JustSaved,

    /** An article saved before, organised from Saved or the reader (#139). */
    Organise,
}

/**
 * Save and share (#143, #256), the one sheet for an article: saving one, filing one later, tagging
 * one saved last week, and sending it with Content on the way. In order: what is being saved (only
 * for [SheetKind.New]: the address field for Add link, or the shared link's title and site), the
 * folder as chips (To read and Archive, a divider, the folders and New folder: #156; before the save
 * also Don't save, to only send it), the tags with Like in front, the note, then Send with Content
 * when signed in (the people shared with as chips, several at once, and Add address). Then Cancel
 * and the button, which says Save, Save and send or Send; closing the sheet is Cancel, so only the
 * button changes or sends anything. Just saved, Remove takes the place of Cancel: it takes the save
 * back, where closing keeps it as it was saved.
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
    /**
     * Remove, at the bottom left of a saved article's sheet: for [SheetKind.JustSaved] it takes the
     * save back; for [SheetKind.Organise] it removes the article from Saved. The caller offers Undo.
     */
    onRemove: () -> Unit = {},
    /** Signed in to a Content account: Send with Content is offered (#256). */
    signedIn: Boolean = false,
    /** Whether an address added under Send with Content has a Content account; null when that couldn't be asked. */
    hasAccount: suspend (String) -> Boolean? = { null },
    /** Only Send with Content, nothing saved: the share menu's Send target. */
    sendOnly: Boolean = false,
) {
    var address by rememberSaveable(key) { mutableStateOf(url.orEmpty()) }
    // A typed address may leave out https://.
    val link = remember(address) { url ?: FeedDiscovery.normalizeUrl(address)?.takeIf { ItemKeys.isWebLink(it) } }
    var choice by remember(key) { mutableStateOf(if (sendOnly) initial.copy(keep = false) else initial) }
    // A folder or tag typed but not added yet: Save takes it too, rather than dropping it.
    var folderName by rememberSaveable(key) { mutableStateOf("") }
    var tagName by rememberSaveable(key) { mutableStateOf("") }
    val saved = kind != SheetKind.New
    val save = {
        val chosen = SaveChoices.withTyped(choice, folderName, tagName, folders)
        if (saved) {
            onSave(null, chosen)
        } else {
            link?.let { onSave(it, chosen) }
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
            // The sheet itself picks To read or the Archive, so the title doesn't name one (#229).
            Text(
                stringResource(
                    when {
                        sendOnly -> R.string.share_send_with_content
                        // Nothing to share here (Share is next to Organise): it only organises.
                        kind == SheetKind.Organise -> R.string.organise_title
                        else -> R.string.save_share_title
                    },
                ),
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
            if (!sendOnly) {
                SheetSection(stringResource(R.string.folder_field))
                FolderChips(
                    choice.place,
                    folders,
                    onChange = { choice = choice.copy(place = it) },
                    name = folderName,
                    onNameChange = { folderName = it },
                )
            }
            // Only sent: nothing of it is kept, so there is nothing to tag or note.
            if (choice.keep) SavedParts(choice, allTags, tagName, onTagNameChange = { tagName = it }, onChange = { choice = it })
            // Organising a saved article doesn't send: Share is right next to Organise.
            if (signedIn && kind != SheetKind.Organise) {
                // The sheet's title says it already when it only sends.
                if (!sendOnly) SheetSection(stringResource(R.string.share_send_with_content))
                SendToSection(choice.sendTo, onChange = { choice = choice.copy(sendTo = it) }, hasAccount = hasAccount)
            } else if (sendOnly) {
                Text(stringResource(R.string.share_user_signed_out), style = MaterialTheme.typography.bodyMedium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // A saved article (just saved, or organised later): Remove at the bottom left, away from Save.
                if (saved) {
                    TextButton(
                        onClick = {
                            onDismiss()
                            onRemove()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Text(stringResource(R.string.action_remove), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = cancel) { Text(stringResource(R.string.action_cancel)) }
                Button(enabled = SendTo.canConfirm(choice, hasLink = saved || link != null), onClick = save) {
                    Text(
                        stringResource(
                            when (SendTo.action(choice)) {
                                SendTo.Action.Save -> R.string.action_save
                                SendTo.Action.SaveAndSend -> R.string.action_save_and_send
                                SendTo.Action.Send -> R.string.share_user_send
                            },
                        ),
                    )
                }
            }
        }
    }
}

/** The parts of the sheet for what is kept: the tags with Like in front, then the note. */
@Composable
private fun SavedParts(
    choice: SaveChoice,
    allTags: List<String>,
    tagName: String,
    onTagNameChange: (String) -> Unit,
    onChange: (SaveChoice) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SheetSection(stringResource(R.string.tags_title))
        TagsEditor(
            tags = choice.tags,
            allTags = allTags,
            onChange = { onChange(choice.copy(tags = it)) },
            name = tagName,
            onNameChange = onTagNameChange,
            leading = {
                FilterChip(
                    selected = choice.liked,
                    onClick = { onChange(choice.copy(liked = !choice.liked)) },
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
            onValueChange = { onChange(choice.copy(note = it)) },
            placeholder = { Text(stringResource(R.string.note_title)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
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
    /** The folder being typed, kept by the sheet so Save files it there too. */
    name: String,
    onNameChange: (String) -> Unit,
) {
    var making by rememberSaveable { mutableStateOf(false) }
    val make = {
        SaveChoices.newFolder(name, folders)?.let(onChange)
        onNameChange("")
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
            }
        }
        if (making) {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
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
