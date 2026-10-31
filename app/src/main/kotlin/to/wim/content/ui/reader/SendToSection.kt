package to.wim.content.ui.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import to.wim.content.R
import to.wim.content.data.SendTo
import to.wim.content.sync.ShareResult
import kotlinx.coroutines.launch

/**
 * Send with Content (#256, #284), laid out like an email's To field, which people know: the people
 * picked as chips in a To box (× takes one out), with a field after them for a name or address.
 * Under it, suggestions: people shared with before (#235, with their Gravatar picture and name) who
 * aren't picked, filtered by what is typed; tap one to add them. A whole new address typed in gives
 * "Add …", checked for a Content account first. Long-press a suggestion to tidy the list: each gets
 * a trash button that forgets them (on every device); Done ends it. Sending is the sheet's button.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun SendToSection(
    picked: List<String>,
    onChange: (List<String>) -> Unit,
    /** Whether the address has a Content account; null when that couldn't be asked. */
    hasAccount: suspend (String) -> Boolean?,
) {
    val recipients = LocalShareRecipients.current
    var query by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf(false) }
    var checking by rememberSaveable { mutableStateOf(false) }
    var problem by rememberSaveable { mutableStateOf<ShareResult?>(null) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    // The Gravatar names, so typing a name finds its person.
    val names by produceState(emptyMap<String, String?>(), recipients.emails) {
        value = recipients.emails.associateWith { recipients.nameOf(it)?.takeIf { name -> name.isNotBlank() } }
    }
    val suggestions = SendTo.suggestions(recipients.emails, picked, query) { names[it] }
    val newAddress = SendTo.newAddress(recipients.emails, picked, query)

    fun add(email: String) {
        onChange(if (SendTo.has(picked, email)) picked else picked + email)
        query = ""
        problem = null
    }

    // Someone new is checked for a Content account before they join the To field.
    fun addNew(email: String) {
        if (checking) return
        checking = true
        scope.launch {
            when (hasAccount(email)) {
                true -> add(email)
                false -> problem = ShareResult.NoAccount
                null -> problem = ShareResult.Offline
            }
            checking = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // The To box: the people picked, then the field.
        FlowRow(
            Modifier
                .fillMaxWidth()
                .border(1.dp, if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.share_to), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (email in picked) {
                val name = recipientName(recipients, email)
                val label = name ?: email
                val removeLabel = stringResource(R.string.share_recipient_remove, label)
                InputChip(
                    selected = true,
                    onClick = { onChange(picked.filterNot { it.equals(email, ignoreCase = true) }) },
                    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    avatar = { RecipientAvatar(email, name, recipients.showImages, size = InputChipDefaults.AvatarSize) },
                    trailingIcon = { Icon(painterResource(R.drawable.ic_close), contentDescription = removeLabel, modifier = Modifier.size(InputChipDefaults.IconSize)) },
                )
            }
            val hint = stringResource(R.string.share_to_hint)
            BasicTextField(
                value = query,
                onValueChange = {
                    query = it
                    problem = null
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                // Done adds what was typed: the one suggestion it matches, or a whole new address.
                keyboardActions =
                    KeyboardActions(onDone = {
                        when {
                            newAddress != null -> addNew(newAddress)
                            query.isNotBlank() && suggestions.size == 1 -> add(suggestions.single())
                        }
                    }),
                decorationBox = { field ->
                    if (query.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    field()
                },
                modifier = Modifier.widthIn(min = 160.dp).padding(vertical = 8.dp).semantics { contentDescription = hint },
            )
        }
        problem?.let { Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        // Someone new: their whole address, typed in.
        newAddress?.let { email ->
            ListItem(
                headlineContent = { Text(stringResource(R.string.share_add_person, email), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingContent = {
                    if (checking) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.combinedClickable(onClick = { addNew(email) }),
            )
        }
        // The suggestions: people shared with before, not picked yet.
        for (email in suggestions) {
            val name = names[email]
            val label = name ?: email
            val forgetLabel = stringResource(R.string.share_recipient_forget, label)
            ListItem(
                headlineContent = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = name?.let { { Text(email, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                leadingContent = { RecipientAvatar(email, name, recipients.showImages, size = 40.dp) },
                trailingContent =
                    if (editing) {
                        {
                            IconButton(onClick = { scope.launch { recipients.forget(email) } }) {
                                Icon(painterResource(R.drawable.ic_delete), contentDescription = forgetLabel)
                            }
                        }
                    } else {
                        null
                    },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier =
                    Modifier
                        .combinedClickable(
                            onClick = { if (!editing) add(email) },
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                editing = true
                            },
                        )
                        // Screen readers: Remove as an action of the row, no long-press needed.
                        .semantics {
                            customActions =
                                listOf(
                                    CustomAccessibilityAction(forgetLabel) {
                                        scope.launch { recipients.forget(email) }
                                        true
                                    },
                                )
                        },
            )
        }
        if (editing) {
            TextButton(onClick = { editing = false }, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.share_recipients_done))
            }
        }
    }
}

/** What a send that didn't get through says. */
fun ShareResult.messageRes(): Int =
    when (this) {
        ShareResult.NoAccount -> R.string.share_user_no_account
        ShareResult.SignedOut -> R.string.share_user_signed_out
        ShareResult.Offline -> R.string.problem_offline
        ShareResult.Sent, ShareResult.Failed -> R.string.problem_server
    }
