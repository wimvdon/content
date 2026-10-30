package be.wimvandonck.content.ui.reader

import androidx.compose.foundation.layout.Arrangement
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.material3.InputChip
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
import androidx.compose.material3.FilledTonalIconButton
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
 * new, who is checked for a Content account before they join the chips. Long-press a person to edit
 * the list: each one shared with before gets an ×, which forgets them (on every device); Done ends
 * it. Sending happens with the sheet's button.
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
    var editing by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val forget = { email: String ->
        onChange(picked.filterNot { it.equals(email, ignoreCase = true) })
        scope.launch { recipients.forget(email) }
        Unit
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            for (email in SendTo.people(recipients.emails, picked)) {
                val name = recipientName(recipients, email)
                val label = name ?: email
                // Only people shared with before can be forgotten; one just added is only picked.
                val saved = recipients.emails.any { it.equals(email, ignoreCase = true) }
                val forgetLabel = stringResource(R.string.share_recipient_forget, label)
                if (editing && saved) {
                    InputChip(
                        selected = false,
                        onClick = { forget(email) },
                        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        avatar = { RecipientAvatar(email, name, recipients.showImages) },
                        trailingIcon = { Icon(painterResource(R.drawable.ic_close), contentDescription = forgetLabel, modifier = Modifier.size(18.dp)) },
                    )
                } else {
                    FilterChip(
                        selected = SendTo.has(picked, email),
                        onClick = { onChange(SendTo.toggle(picked, email)) },
                        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { RecipientAvatar(email, name, recipients.showImages) },
                        modifier =
                            if (saved) {
                                Modifier
                                    .onLongPress {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        editing = true
                                    }
                                    // Screen readers: Remove as an action of the chip, no long-press needed.
                                    .semantics { customActions = listOf(CustomAccessibilityAction(forgetLabel) {
                                        forget(email)
                                        true
                                    }) }
                            } else {
                                Modifier
                            },
                    )
                }
            }
            if (editing) {
                AssistChip(
                    onClick = { editing = false },
                    label = { Text(stringResource(R.string.share_recipients_done)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            } else if (!adding) {
                AssistChip(
                    onClick = { adding = true },
                    label = { Text(stringResource(R.string.share_add_address)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
        if (adding) {
            AddressField(
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
 * An email address typed in (#256), with a filled + button at its end that adds the person to the
 * chips. [onSubmit] says what went wrong, shown under the field, or null: then the field empties for
 * the next one.
 */
@Composable
private fun AddressField(onSubmit: suspend (email: String) -> ShareResult?) {
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                FilledTonalIconButton(onClick = { submit() }, enabled = valid && !busy) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.share_add_address))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        problem?.let { Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

/**
 * Calls [onLongPress] when the element is held, and then keeps the press from also being a tap (the
 * chip's own click). Watches before the chip does, so the chip's click handling needs no change.
 */
private fun Modifier.onLongPress(onLongPress: () -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation(PointerEventPass.Initial) }
            if (released == null) {
                onLongPress()
                // The rest of this press belongs to the long-press.
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
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
