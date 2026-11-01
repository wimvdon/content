package to.wim.content.ui.settings

import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.material3.Switch
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.pm.PackageManager
import android.Manifest
import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import coil3.compose.AsyncImage
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import to.wim.content.R
import to.wim.content.ui.home.rememberFolderIconPainter
import to.wim.content.ui.ExternalApps
import androidx.compose.foundation.clickable
import to.wim.content.sync.AccountState
import to.wim.content.sync.Gravatar
import to.wim.content.sync.SyncProblem
import to.wim.content.sync.SyncStatus
import to.wim.content.ui.theme.SettingsBlock
import to.wim.content.ui.theme.SettingsSection

/**
 * The Content account (#166, docs/account.md): sign in with an emailed link; it syncs feeds, read
 * articles, Saved and settings. Instapaper is only a connected service (#215). Signed in: who (the email
 * address with its Gravatar picture and name, #234), how sync is doing, Sync now, Add email address (an account
 * opened with Instapaper), Sign out and Delete synced data. Shown once the stored sign-in is known.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountSection(
    state: AccountState,
    status: SyncStatus,
    /** The name on the address's Gravatar profile (#234), shown above the address. */
    name: String? = null,
    /** Notify me when something is shared with me (#235): on or off, null when the build has no notifications. */
    notifyShared: Boolean? = null,
    /** Turns it on or off; the result says whether that worked. */
    onNotifyShared: (Boolean, (Boolean) -> Unit) -> Unit = { _, _ -> },
    lastSyncedAt: Long?,
    onSendLink: (email: String, result: (Boolean) -> Unit) -> Unit,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteData: () -> Unit,
    showImages: Boolean = true,
) {
    if (state is AccountState.Loading) return
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    SettingsSection(title = stringResource(R.string.account_title)) {
        if (state !is AccountState.SignedIn) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.account_title)) },
                supportingContent = { Text(stringResource(R.string.account_summary)) },
                leadingContent = { Icon(painterResource(R.drawable.ic_cloud_sync), contentDescription = null) },
            )
            EmailLinkForm(R.string.account_email_send, onSendLink)
            WebAppRow()
            return@SettingsSection
        }
        val email = state.session.email
        val signedOutThere = (status as? SyncStatus.Failed)?.problem == SyncProblem.SignedOut
        ListItem(
            headlineContent = { Text(name?.takeIf { it.isNotBlank() } ?: email ?: stringResource(R.string.account_title)) },
            // With a name, the address goes under it.
            overlineContent = if (email != null && !name.isNullOrBlank()) ({ Text(email) }) else null,
            supportingContent = {
                when {
                    status is SyncStatus.Syncing -> Text(stringResource(R.string.sync_syncing))
                    status is SyncStatus.Failed -> Text(stringResource(status.problem.messageRes()), color = MaterialTheme.colorScheme.error)
                    lastSyncedAt != null ->
                        Text(
                            stringResource(
                                R.string.sync_last,
                                DateUtils.getRelativeTimeSpanString(lastSyncedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                            ),
                        )
                    else -> Text(stringResource(R.string.sync_never))
                }
            },
            leadingContent = {
                // The Gravatar picture of the address, if it has one; else the cloud.
                val icon = painterResource(R.drawable.ic_cloud_sync)
                if (email != null && showImages) {
                    AsyncImage(
                        model = Gravatar.url(email),
                        contentDescription = null,
                        placeholder = icon,
                        error = icon,
                        modifier = Modifier.size(40.dp).clip(CircleShape),
                    )
                } else {
                    Icon(icon, contentDescription = null)
                }
            },
        )
        SettingsBlock {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!signedOutThere) {
                    FilledTonalButton(onClick = onSyncNow, enabled = status !is SyncStatus.Syncing) { Text(stringResource(R.string.sync_now)) }
                }
                TextButton(onClick = onSignOut) { Text(stringResource(R.string.account_sign_out)) }
                TextButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.account_delete_data)) }
            }
        }
        if (notifyShared != null) NotifySharedSwitch(notifyShared, onNotifyShared)
        if (email == null) {
            // Opened with Instapaper: an address added once opens the same account.
            ListItem(
                headlineContent = { Text(stringResource(R.string.account_add_email)) },
                supportingContent = { Text(stringResource(R.string.account_add_email_summary)) },
            )
            EmailLinkForm(R.string.account_email_send, onSendLink)
        }
        WebAppRow()
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.account_delete_data)) },
            text = { Text(stringResource(R.string.account_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDeleteData()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@StringRes
private fun SyncProblem.messageRes(): Int =
    when (this) {
        SyncProblem.Offline -> R.string.sync_failed_offline
        SyncProblem.SignedOut -> R.string.sync_failed_signed_out
        SyncProblem.ServerError -> R.string.sync_failed_server
        SyncProblem.UnreadableFile -> R.string.sync_failed_file
        SyncProblem.NotSetUp -> R.string.sync_failed_setup
    }

/** An email address and a button that emails a link to it; then says to open the email. */
@Composable
private fun EmailLinkForm(
    @StringRes button: Int,
    onSendLink: (email: String, result: (Boolean) -> Unit) -> Unit,
) {
    SettingsBlock { EmailLinkFields(button, onSendLink) }
}

/**
 * The address field, the button that emails a sign-in link, and what happened (sent to …, or
 * couldn't be sent). Settings › Account and the welcome screen use it (#166).
 */
@Composable
fun EmailLinkFields(
    @StringRes button: Int,
    onSendLink: (email: String, result: (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var sending by rememberSaveable { mutableStateOf(false) }
    var sentTo by rememberSaveable { mutableStateOf<String?>(null) }
    var failed by rememberSaveable { mutableStateOf(false) }
    val valid = EMAIL.matches(email.trim())
    val send = {
        sending = true
        failed = false
        val to = email.trim()
        onSendLink(to) { ok ->
            sending = false
            if (ok) sentTo = to else failed = true
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                failed = false
            },
            label = { Text(stringResource(R.string.account_email)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (valid && !sending) send() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(enabled = valid && !sending, onClick = send) { Text(stringResource(button)) }
        sentTo?.let { Text(stringResource(R.string.account_email_sent, it), style = MaterialTheme.typography.bodyMedium) }
        if (failed) Text(stringResource(R.string.account_email_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}

private val EMAIL = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")

/**
 * Notify me when something is shared with me (#235). Turning it on asks Android's permission first
 * (Android 13 and later); refused, it says where to allow it.
 */
@Composable
private fun NotifySharedSwitch(
    on: Boolean,
    onChange: (Boolean, (Boolean) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    val turnOn = {
        busy = true
        onChange(true) { busy = false }
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            denied = !granted
            if (granted) turnOn()
        }
    ListItem(
        headlineContent = { Text(stringResource(R.string.notify_shared_setting)) },
        supportingContent = if (denied) ({ Text(stringResource(R.string.notify_shared_denied), color = MaterialTheme.colorScheme.error) }) else null,
        trailingContent = {
            Switch(
                checked = on,
                enabled = !busy,
                onCheckedChange = { value ->
                    denied = false
                    when {
                        !value -> {
                            busy = true
                            onChange(false) { busy = false }
                        }
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED -> turnOn()
                        else -> permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
        },
    )
}

/** The web app (content.wim.to): the same account on a computer. Tapping it opens it in the browser. */
@Composable
private fun WebAppRow() {
    val context = LocalContext.current
    ListItem(
        headlineContent = { Text(stringResource(R.string.account_web_title)) },
        supportingContent = { Text(stringResource(R.string.account_web_summary)) },
        leadingContent = { Icon(rememberFolderIconPainter("computer"), contentDescription = null) },
        trailingContent = { Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null) },
        modifier = Modifier.clickable { ExternalApps.openLink(context, WEB_APP_URL) },
    )
}

private const val WEB_APP_URL = "https://content.wim.to"
