package be.wimvandonck.content.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import be.wimvandonck.content.R
import be.wimvandonck.content.data.SendTo
import be.wimvandonck.content.sync.ShareResult
import kotlinx.coroutines.launch

/**
 * Send with Content in the Save and share sheet (#256): the people shared with before (#235, with
 * their Gravatar picture and name) as chips to pick, several at once, then Add address for someone
 * new, who is checked for a Content account before they join the chips. Sending happens with the
 * sheet's button.
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
    var adding by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            for (email in SendTo.people(recipients.emails, picked)) {
                val name = recipientName(recipients, email)
                FilterChip(
                    selected = SendTo.has(picked, email),
                    onClick = { onChange(SendTo.toggle(picked, email)) },
                    label = { Text(name ?: email, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { RecipientAvatar(email, name, recipients.showImages) },
                )
            }
            if (!adding) {
                AssistChip(
                    onClick = { adding = true },
                    label = { Text(stringResource(R.string.share_add_address)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
        if (adding) {
            AddressField(
                actionLabel = stringResource(R.string.share_add_address),
                actionIcon = R.drawable.ic_add,
                onSubmit = { email ->
                    when (hasAccount(email)) {
                        true -> {
                            onChange(if (SendTo.has(picked, email)) picked else picked + email)
                            null
                        }
                        false -> ShareResult.NoAccount
                        null -> ShareResult.Offline
                    }
                },
            )
        }
    }
}

/**
 * An email address typed in (#256), with one button at its end. [onSubmit] says what went wrong, shown
 * under the field, or null: then the field empties for the next one.
 */
@Composable
fun AddressField(
    actionLabel: String,
    actionIcon: Int,
    onSubmit: suspend (email: String) -> ShareResult?,
    modifier: Modifier = Modifier,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var problem by rememberSaveable { mutableStateOf<ShareResult?>(null) }
    val scope = rememberCoroutineScope()
    val valid = SendTo.isEmail(email)
    val submit = {
        if (valid && !busy) {
            busy = true
            scope.launch {
                val result = onSubmit(email.trim())
                busy = false
                problem = result
                if (result == null) email = ""
            }
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                problem = null
            },
            label = { Text(stringResource(R.string.account_email)) },
            singleLine = true,
            isError = problem != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            trailingIcon = {
                IconButton(onClick = { submit() }, enabled = valid && !busy) {
                    Icon(painterResource(actionIcon), contentDescription = actionLabel)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        problem?.let { Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
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
