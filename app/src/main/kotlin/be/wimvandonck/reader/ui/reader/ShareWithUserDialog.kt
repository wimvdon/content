package be.wimvandonck.reader.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import be.wimvandonck.reader.R
import be.wimvandonck.reader.sync.ShareResult

/**
 * Send to a Content user (#167): their email address; Send says first when it has no Content
 * account. [what] says what goes ("this article", or a tag's articles).
 */
@Composable
fun ShareWithUserDialog(
    what: String,
    onSend: (email: String, result: (ShareResult) -> Unit) -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var sending by rememberSaveable { mutableStateOf(false) }
    var problem by rememberSaveable { mutableStateOf<ShareResult?>(null) }
    val valid = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""").matches(email.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_user_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(what, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        problem = null
                    },
                    label = { Text(stringResource(R.string.account_email)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
                    modifier = Modifier.fillMaxWidth(),
                )
                problem?.let { Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = valid && !sending, onClick = {
                sending = true
                onSend(email.trim()) { result ->
                    sending = false
                    if (result == ShareResult.Sent) onDone() else problem = result
                }
            }) { Text(stringResource(R.string.share_user_send)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun ShareResult.messageRes(): Int =
    when (this) {
        ShareResult.NoAccount -> R.string.share_user_no_account
        ShareResult.NoEmail -> R.string.share_user_no_email
        ShareResult.SignedOut -> R.string.share_user_signed_out
        ShareResult.Offline -> R.string.problem_offline
        ShareResult.Sent, ShareResult.Failed -> R.string.problem_server
    }
