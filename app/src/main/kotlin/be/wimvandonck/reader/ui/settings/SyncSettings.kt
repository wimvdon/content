package be.wimvandonck.reader.ui.settings

import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import be.wimvandonck.reader.R
import be.wimvandonck.reader.sync.SyncProblem
import be.wimvandonck.reader.sync.SyncService
import be.wimvandonck.reader.sync.SyncStatus
import be.wimvandonck.reader.ui.theme.SettingsBlock
import be.wimvandonck.reader.ui.theme.SettingsSection

/** Sync with Google Drive: Connect, or the last sync with Sync now and Disconnect. */
@Composable
fun SyncSection(
    service: SyncService,
    status: SyncStatus,
    lastSyncedAt: Long?,
    onConnect: () -> Unit,
    onSyncNow: () -> Unit,
    onDisconnect: () -> Unit,
) {
    SettingsSection(title = stringResource(R.string.section_sync)) {
        val connected = service == SyncService.GoogleDrive
        ListItem(
            headlineContent = { Text(stringResource(R.string.sync_google_drive)) },
            supportingContent = {
                when {
                    !connected -> Text(stringResource(R.string.sync_off_summary))
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!connected) {
                    Button(onClick = onConnect) { Text(stringResource(R.string.sync_connect)) }
                } else {
                    val needsApproval = (status as? SyncStatus.Failed)?.problem == SyncProblem.NeedsApproval
                    if (needsApproval) {
                        Button(onClick = onConnect) { Text(stringResource(R.string.sync_connect)) }
                    } else {
                        FilledTonalButton(onClick = onSyncNow, enabled = status !is SyncStatus.Syncing) { Text(stringResource(R.string.sync_now)) }
                    }
                    TextButton(onClick = onDisconnect) { Text(stringResource(R.string.sync_disconnect)) }
                }
            }
        }
    }
}

@StringRes
private fun SyncProblem.messageRes(): Int =
    when (this) {
        SyncProblem.Offline -> R.string.sync_failed_offline
        SyncProblem.NeedsApproval -> R.string.sync_failed_approval
        SyncProblem.ServerError -> R.string.sync_failed_server
        SyncProblem.UnreadableFile -> R.string.sync_failed_file
        SyncProblem.NotSetUp -> R.string.sync_failed_setup
    }
