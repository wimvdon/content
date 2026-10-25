package be.wimvandonck.content.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import be.wimvandonck.content.R
import be.wimvandonck.content.instapaper.InstapaperState
import be.wimvandonck.content.services.ServiceProblem
import be.wimvandonck.content.services.ServiceStatus
import be.wimvandonck.content.ui.theme.SettingsBlock

/** What the Instapaper part of Manage › Saved shows (#109, #251). */
data class InstapaperSettingsState(
    val instapaper: InstapaperState,
    val status: ServiceStatus,
)

/**
 * Instapaper in Manage › Saved (#251): connect it, or, connected, how it is doing, Sync now and Sign
 * out. There is nothing to choose: everything in Saved syncs both ways, highlights included.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InstapaperSettings(
    state: InstapaperSettingsState,
    onSignIn: () -> Unit,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
    /** The answer to "Import from Instapaper?", while it waits for one (#251). */
    onChooseImport: (everything: Boolean) -> Unit = {},
) {
    val signedIn = state.instapaper as? InstapaperState.SignedIn
    val status = state.status
    ListItem(
        headlineContent = { Text(signedIn?.session?.username?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.section_instapaper)) },
        supportingContent = {
            when {
                signedIn == null -> Text(stringResource(R.string.services_reading_body))
                status is ServiceStatus.ImportChoice -> Text(stringResource(R.string.instapaper_import_body))
                status is ServiceStatus.Syncing -> Text(stringResource(R.string.sync_syncing))
                status is ServiceStatus.Failed -> Text(stringResource(status.problem.messageRes()), color = MaterialTheme.colorScheme.error)
                else -> Text(stringResource(R.string.services_syncs_everything))
            }
        },
        leadingContent = { Icon(painterResource(R.drawable.ic_reading_list), contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
    SettingsBlock {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (signedIn == null) {
                Button(onClick = onSignIn, enabled = state.instapaper !is InstapaperState.Loading) { Text(stringResource(R.string.instapaper_sign_in)) }
            } else if (status is ServiceStatus.ImportChoice) {
                Button(onClick = { onChooseImport(true) }) { Text(stringResource(R.string.service_import_title)) }
                OutlinedButton(onClick = { onChooseImport(false) }) { Text(stringResource(R.string.instapaper_import_skip)) }
                OutlinedButton(onClick = onSignOut) { Text(stringResource(R.string.instapaper_sign_out)) }
            } else {
                FilledTonalButton(onClick = onSyncNow, enabled = status !is ServiceStatus.Syncing) {
                    Text(stringResource(R.string.sync_now))
                }
                OutlinedButton(onClick = onSignOut) { Text(stringResource(R.string.instapaper_sign_out)) }
            }
        }
    }
}

/**
 * Asked once, right after connecting Instapaper (#251): bring in everything it has now, or not.
 * Either way, from then on Saved and Instapaper stay the same. Dismissing asks again next time.
 */
@Composable
fun InstapaperImportDialog(
    onChoose: (everything: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.instapaper_import_title)) },
        text = { Text(stringResource(R.string.instapaper_import_body)) },
        confirmButton = { TextButton(onClick = { onChoose(true) }) { Text(stringResource(R.string.service_import_title)) } },
        dismissButton = { TextButton(onClick = { onChoose(false) }) { Text(stringResource(R.string.instapaper_import_skip)) } },
    )
}

/** A service's name, by id: Instapaper is the only one. */
@Suppress("UNUSED_PARAMETER")
fun serviceName(id: String): Int = R.string.section_instapaper

private fun ServiceProblem.messageRes(): Int =
    when (this) {
        ServiceProblem.SignedOut -> R.string.instapaper_problem_signed_out
        ServiceProblem.RateLimited -> R.string.services_problem_later
        ServiceProblem.Offline -> R.string.problem_offline
        ServiceProblem.Refused -> R.string.problem_server
        ServiceProblem.LimitReached -> R.string.services_problem_limit
    }
