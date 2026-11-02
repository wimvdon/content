package to.wim.content.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import to.wim.content.R
import to.wim.content.data.SendTo
import to.wim.content.sync.ShareResult

/**
 * Send with Content (#256), the same way as tags (2026-11-02, instead of an email-style To field):
 * the people shared with before as filter chips, each with their Gravatar picture (#235) and name;
 * tap one or more to pick them. With nobody yet, a tonal **Add someone** is all it shows. **Edit people** after the chips (as Edit tags) opens [ManagePeopleSheet]
 * as a page of the same sheet: add someone by their address (checked for a Content account first; they
 * are picked at once) or forget someone. Sending is the sheet's button.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SendToSection(
    picked: List<String>,
    onChange: (List<String>) -> Unit,
    /** Whether the address has a Content account; null when that couldn't be asked. */
    hasAccount: suspend (String) -> Boolean?,
) {
    val recipients = LocalShareRecipients.current
    var managing by rememberSaveable { mutableStateOf(false) }
    // Someone picked who isn't in the list (Direct Share) still shows, after the others.
    val people = SendTo.people(recipients.emails, picked)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        for (email in people) {
            val name = recipientName(recipients, email)
            // No check mark: the fill shows it, and the chips keep their width and place, as tags.
            FilterChip(
                selected = SendTo.has(picked, email),
                onClick = { onChange(SendTo.toggle(picked, email)) },
                label = { Text(name ?: email, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp)) },
                leadingIcon = { RecipientAvatar(email, name, recipients.showImages, size = FilterChipDefaults.IconSize) },
            )
        }
        if (people.isEmpty()) {
            // Nobody yet: adding someone is the section's first step, so it stands out (tonal, as New tag).
            FilledTonalButton(onClick = { managing = true }, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Text(stringResource(R.string.share_people_add), modifier = Modifier.padding(start = 8.dp))
            }
        } else {
            // An action, not a person: a text button, as Edit tags.
            TextButton(onClick = { managing = true }, contentPadding = ButtonDefaults.TextButtonWithIconContentPadding) {
                Icon(painterResource(R.drawable.ic_edit), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Text(stringResource(R.string.share_people_edit), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
    if (managing) {
        ManagePeopleSheet(
            hasAccount = hasAccount,
            // Someone added here is picked at once, as a tag made in the sheet.
            onAdded = { email -> if (!SendTo.has(picked, email)) onChange(picked + email) },
            onForgot = { email -> onChange(picked.filterNot { it.equals(email, ignoreCase = true) }) },
            onDismiss = { managing = false },
        )
    }
}

/**
 * Edit people, as Manage tags: the people you can send to, each with their picture, name and address
 * and a trash can that forgets them (on every device); under them an address field and **Add**,
 * which checks for a Content account first and says so when there is none. Opened from the share
 * sheet it is a page of it ([ContentSheet]), with a back arrow.
 */
@Composable
fun ManagePeopleSheet(
    hasAccount: suspend (String) -> Boolean?,
    onAdded: (String) -> Unit,
    onForgot: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val recipients = LocalShareRecipients.current
    val scope = rememberCoroutineScope()
    var address by rememberSaveable { mutableStateOf("") }
    var checking by rememberSaveable { mutableStateOf(false) }
    var problem by rememberSaveable { mutableStateOf<ShareResult?>(null) }
    val typed = address.trim()
    val canAdd = SendTo.isEmail(typed) && !checking

    fun add() {
        if (!canAdd) return
        // Someone already in the list: just picked.
        if (SendTo.has(recipients.emails, typed)) {
            onAdded(recipients.emails.first { it.equals(typed, ignoreCase = true) })
            address = ""
            return
        }
        checking = true
        problem = null
        scope.launch {
            when (hasAccount(typed)) {
                true -> {
                    recipients.add(typed)
                    onAdded(typed.lowercase())
                    address = ""
                }
                false -> problem = ShareResult.NoAccount
                null -> problem = ShareResult.Offline
            }
            checking = false
        }
    }

    ContentSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SheetHeader(stringResource(R.string.share_people_title), onCancel = onDismiss)
            if (recipients.emails.isEmpty()) {
                Text(stringResource(R.string.share_people_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column {
                for (email in recipients.emails) {
                    val name = recipientName(recipients, email)
                    val forgetLabel = stringResource(R.string.share_recipient_forget, name ?: email)
                    ListItem(
                        headlineContent = { Text(name ?: email, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = name?.let { { Text(email, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                        leadingContent = { RecipientAvatar(email, name, recipients.showImages, size = 40.dp) },
                        trailingContent = {
                            IconButton(onClick = {
                                scope.launch { recipients.forget(email) }
                                onForgot(email)
                            }) {
                                Icon(painterResource(R.drawable.ic_delete), contentDescription = forgetLabel)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    )
                }
            }
            // The field across the page, Add under it at the end (2026-11-02): side by side, the field
            // stood taller than the button.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = {
                        address = it
                        problem = null
                    },
                    label = { Text(stringResource(R.string.share_people_address)) },
                    singleLine = true,
                    isError = problem != null,
                    supportingText = problem?.let { { Text(stringResource(it.messageRes())) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                // The page's action, tonal as New tag in Manage tags.
                FilledTonalButton(onClick = { add() }, enabled = canAdd, modifier = Modifier.align(Alignment.End)) {
                    if (checking) {
                        CircularProgressIndicator(Modifier.size(ButtonDefaults.IconSize), strokeWidth = 2.dp)
                    } else {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    }
                    Text(stringResource(R.string.share_people_add_action), modifier = Modifier.padding(start = 8.dp))
                }
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
