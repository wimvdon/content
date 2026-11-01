package to.wim.content.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import to.wim.content.R
import to.wim.content.data.Folder
import to.wim.content.data.Item
import to.wim.content.data.ItemKeys
import to.wim.content.data.SaveChoice
import to.wim.content.data.SaveChoices
import to.wim.content.data.SendTo
import to.wim.content.feed.FeedDiscovery
import to.wim.content.ui.feeds.TopicEditActions
import to.wim.content.ui.feeds.TopicChipRow

/** Which way into the save sheet (#143): the heading and Cancel depend on whether the save has happened. */
enum class SheetKind {
    /** A share or Add link: nothing is saved until Save, and Cancel saves nothing. */
    New,

    /** Saved a moment ago (the reader, a long press): "Saved to …"; Remove takes the save back, closing keeps it. */
    JustSaved,

    /** An article saved before, organised from Saved or the reader (#139). */
    Organise,
}

/**
 * The one sheet for an article (#143, #304, #305): saving one, tagging one saved last week. Headed
 * Save while saving and Organise for one saved before; it doesn't send (#304; Share and the share
 * menu's Send with Content do that). In order: what is being saved (only for [SheetKind.New]: the
 * address field for Add link, or the shared link's title and site), **To read** or **Archive**
 * (To read to start with when saving; Save's icon follows it), the topics with Like in front, the
 * note. At the bottom, Cancel at the left (with Remove next to it when organising one saved
 * before) and Save at the right. Closing the sheet is Cancel, so only Save changes anything. [sendOnly]
 * makes it the share menu's Send with Content instead: only the people to send to (#256). Topics
 * are edited right in their chips (#314: New topic, and Edit for a pen and a drag handle on each);
 * a topic renamed or removed there changes the sheet's choice too.
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
    /** Remove, at the bottom left of [SheetKind.Organise]: out of Saved. The caller asks first. */
    onRemove: () -> Unit = {},
    /** Signed in to a Content account: Send with Content is offered (#256). */
    signedIn: Boolean = false,
    /** Whether an address added under Send with Content has a Content account; null when that couldn't be asked. */
    hasAccount: suspend (String) -> Boolean? = { null },
    /** Only Send with Content, nothing saved: the share menu's Send target. */
    sendOnly: Boolean = false,
    /** Every topic, in the user's order: the sheet's topic chips. */
    topics: List<Folder> = emptyList(),
    /** Editing topics from the sheet: Edit topics after the chips opens Manage topics over it; null: no Edit topics. */
    topicActions: TopicEditActions? = null,
) {
    var address by rememberSaveable(key) { mutableStateOf(url.orEmpty()) }
    // A typed address may leave out https://.
    val link = remember(address) { url ?: FeedDiscovery.normalizeUrl(address)?.takeIf { ItemKeys.isWebLink(it) } }
    var choice by remember(key) { mutableStateOf(if (sendOnly) initial.copy(keep = false) else initial) }
    // Topics edited in the sheet (#307, #314): a topic renamed or removed changes the choice too, so
    // Save doesn't bring an old name back.
    val sheetTopicActions =
        topicActions?.let { actions ->
            TopicEditActions(
                onNew = actions.onNew,
                onEdit = { topic, name, icon ->
                    choice = SaveChoices.withTopicRenamed(choice, topic.title, name)
                    actions.onEdit(topic, name, icon)
                },
                onRemove = { topic ->
                    choice = SaveChoices.withoutTopic(choice, topic.title)
                    actions.onRemove(topic)
                },
                onReorder = actions.onReorder,
            )
        }
    val saved = kind != SheetKind.New
    val save = { picked: SaveChoice ->
        val chosen = SaveChoices.withTyped(picked, "", "", folders)
        if (saved) {
            onSave(null, chosen)
        } else {
            link?.let { onSave(it, chosen) }
        }
        onDismiss()
    }
    // Cancel, or closing the sheet, saves and sends nothing: before the save nothing happens, and an
    // article saved a moment ago is taken back ([onCancel]). Organising, it changes nothing (#156).
    val cancel = {
        onCancel()
        onDismiss()
    }
    ContentSheet(onDismiss = cancel) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // One sheet, headed for what it does: Save before the save (and right after it), Organise for
            // one saved before, Send with Content for the share menu's Send.
            SheetHeader(
                stringResource(
                    when {
                        sendOnly -> R.string.share_send_with_content
                        kind == SheetKind.Organise -> R.string.organise_title
                        else -> R.string.action_save
                    },
                ),
                onCancel = cancel,
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
            // Only sent: nothing of it is kept, so there is nothing to tag or note.
            if (choice.keep) {
                // To read or Archive (To read to start with, saving): where it goes, above the topics.
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(false, true).forEachIndexed { i, archive ->
                        SegmentedButton(
                            selected = SaveChoices.archived(choice) == archive,
                            onClick = { choice = SaveChoices.withArchived(choice, archive) },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                            icon = {
                                Icon(
                                    painterResource(if (archive) R.drawable.ic_inventory else R.drawable.ic_reading_list),
                                    contentDescription = null,
                                    modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                                )
                            },
                        ) { Text(stringResource(if (archive) R.string.reading_list_archive else R.string.reading_list_to_read)) }
                    }
                }
                SavedParts(
                    choice,
                    allTags,
                    topics,
                    onChange = { choice = it },
                    topicActions = sheetTopicActions,
                )
            }
            // Only the share menu's Send with Content sends (#304): saving and organising don't.
            if (sendOnly) {
                if (signedIn) {
                    SendToSection(choice.sendTo, onChange = { choice = choice.copy(sendTo = it) }, hasAccount = hasAccount)
                } else {
                    Text(stringResource(R.string.share_user_signed_out), style = MaterialTheme.typography.bodyMedium)
                }
            }
            val canSave = SendTo.canConfirm(choice, hasLink = saved || link != null)
            // Cancel at the bottom left; organising a saved article, Remove next to it, away from Save.
            // Just saved, Cancel already takes the save back, so there is nothing to remove.
            SheetActions(
                onCancel = cancel,
                leading = {
                    if (kind == SheetKind.Organise) {
                        TextButton(
                            onClick = {
                                onDismiss()
                                onRemove()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text(stringResource(R.string.action_remove)) }
                    }
                },
            ) {
                Button(enabled = canSave, onClick = { save(choice) }) {
                    // Kept, the icon says where it goes: To read's open book, the Archive's box.
                    if (choice.keep && !sendOnly) {
                        Icon(
                            painterResource(if (SaveChoices.archived(choice)) R.drawable.ic_inventory else R.drawable.ic_reading_list),
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.size(8.dp))
                    }
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

/**
 * The parts of the sheet for what is kept: the topics as chips with Like in front, Edit topics at
 * the end, then the note.
 */
@Composable
private fun SavedParts(
    choice: SaveChoice,
    allTags: List<String>,
    topics: List<Folder>,
    onChange: (SaveChoice) -> Unit,
    topicActions: TopicEditActions?,
) {
    fun has(name: String) = choice.tags.any { it.trim().equals(name.trim(), ignoreCase = true) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SheetSection(stringResource(R.string.tags_title))
        TopicChipRow(
            topics = topics,
            selected = { has(it.title) },
            onToggle = { onChange(SaveChoices.withTag(choice, it.title)) },
            actions = topicActions,
            // A topic made here is on the article at once.
            onCreated = { name -> if (name.isNotEmpty() && !has(name)) onChange(choice.copy(tags = choice.tags + name)) },
            // Its names that aren't topics (yet), as plain chips after the topics.
            extra = SaveChoices.tagChoices(allTags, choice.tags).filter { name -> topics.none { it.title.equals(name.trim(), ignoreCase = true) } },
            isExtraSelected = ::has,
            onToggleExtra = { onChange(SaveChoices.withTag(choice, it)) },
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

/**
 * A sheet's heading, the same in every sheet: its title. Cancel is at the bottom left
 * ([SheetActions]). On a page of a sheet ([ContentSheet]) a back arrow before the title goes back
 * ([onCancel]; [enabled] false shows it as off, while busy).
 */
@Composable
internal fun SheetHeader(
    title: String,
    onCancel: () -> Unit,
    enabled: Boolean = true,
) {
    // A page of a sheet (ContentSheet): a back arrow takes you back to the sheet under it.
    if (LocalInSheetPage.current) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel, enabled = enabled, modifier = Modifier.offset(x = (-12).dp)) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
            }
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).offset(x = (-8).dp).semantics { heading() })
        }
        return
    }
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).semantics { heading() })
}

/**
 * A sheet's buttons, at its bottom, the same in every sheet: **Cancel** at the left (not on a page
 * of a sheet, whose back arrow does it; [cancel] false for sheets without Cancel, such as Manage
 * feeds with Done), then [leading] (Remove, in red), and [actions] at the right (the main button).
 * [enabled] false shows Cancel as off (while busy).
 */
@Composable
internal fun SheetActions(
    onCancel: () -> Unit,
    enabled: Boolean = true,
    cancel: Boolean = true,
    leading: @Composable RowScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (cancel && !LocalInSheetPage.current) {
            TextButton(onClick = onCancel, enabled = enabled) { Text(stringResource(R.string.action_cancel)) }
        }
        leading()
        Spacer(Modifier.weight(1f))
        actions()
    }
}

/** A section's heading in a sheet, in the primary colour. */
@Composable
internal fun SheetSection(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp).semantics { heading() })
}

/** Remove from Saved, wherever it is offered (the reader's ⋮, Organise, a row's menu): asks first, says what goes. */
@Composable
fun RemoveDialog(
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.saved_remove)) },
        text = { Text(stringResource(R.string.saved_remove_body)) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onRemove()
            }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.action_remove)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
