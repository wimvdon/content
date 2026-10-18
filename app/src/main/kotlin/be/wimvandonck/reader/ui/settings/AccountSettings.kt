package be.wimvandonck.reader.ui.settings

import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
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
import be.wimvandonck.reader.R
import be.wimvandonck.reader.instapaper.InstapaperState
import be.wimvandonck.reader.sync.SyncProblem
import be.wimvandonck.reader.sync.SyncStatus
import be.wimvandonck.reader.ui.theme.SettingsBlock
import be.wimvandonck.reader.ui.theme.SettingsSection

/**
 * The account: signing in with Instapaper also syncs feeds, read articles and settings
 * (docs/account.md). Signed in: who, how sync is doing, Sync now, Sign out and Delete synced data.
 * Shown once the stored sign-in is known.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountSection(
    state: InstapaperState,
    status: SyncStatus,
    lastSyncedAt: Long?,
    onSignIn: () -> Unit,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteData: () -> Unit,
) {
    if (state is InstapaperState.Loading) return
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    SettingsSection(title = stringResource(R.string.section_account)) {
        if (state !is InstapaperState.SignedIn) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.section_instapaper)) },
                supportingContent = { Text(stringResource(R.string.account_summary)) },
                leadingContent = { Icon(painterResource(R.drawable.ic_cloud_sync), contentDescription = null) },
            )
            SettingsBlock { Button(onClick = onSignIn) { Text(stringResource(R.string.instapaper_sign_in)) } }
            return@SettingsSection
        }
        val hasAccount = state.session.accountKey != null
        val signedOutThere = (status as? SyncStatus.Failed)?.problem == SyncProblem.SignedOut
        ListItem(
            headlineContent = {
                Text(stringResource(R.string.instapaper_signed_in_as, state.session.username.ifEmpty { stringResource(R.string.section_instapaper) }))
            },
            supportingContent = {
                when {
                    !hasAccount -> Text(stringResource(R.string.sync_no_account), color = MaterialTheme.colorScheme.error)
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
            leadingContent = { Icon(painterResource(R.drawable.ic_cloud_sync), contentDescription = null) },
        )
        SettingsBlock {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasAccount && !signedOutThere) {
                    FilledTonalButton(onClick = onSyncNow, enabled = status !is SyncStatus.Syncing) { Text(stringResource(R.string.sync_now)) }
                } else {
                    Button(onClick = onSignIn) { Text(stringResource(R.string.instapaper_sign_in)) }
                }
                TextButton(onClick = onSignOut) { Text(stringResource(R.string.instapaper_sign_out)) }
                if (hasAccount) {
                    TextButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.account_delete_data)) }
                }
            }
        }
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
