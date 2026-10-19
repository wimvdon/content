package be.wimvandonck.reader.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import be.wimvandonck.reader.R
import be.wimvandonck.reader.instapaper.InstapaperReadingList
import be.wimvandonck.reader.instapaper.InstapaperState
import be.wimvandonck.reader.raindrop.RaindropSaved
import be.wimvandonck.reader.raindrop.RaindropState
import be.wimvandonck.reader.services.ServiceProblem
import be.wimvandonck.reader.services.ServiceStatus
import be.wimvandonck.reader.ui.theme.SettingsBlock
import be.wimvandonck.reader.ui.theme.SettingsScaffold
import be.wimvandonck.reader.ui.theme.SettingsSection

/** What Connected services shows of Saved's services (#109, #112). */
data class ServicesState(
    val instapaper: InstapaperState,
    val raindrop: RaindropState,
    /** Each connected service's status, by id. */
    val statuses: Map<String, ServiceStatus>,
    /** The service new saves go to, when one is connected. */
    val saveService: String?,
)

class ServicesActions(
    val onBack: () -> Unit,
    val onSignIn: () -> Unit,
    val onConnectRaindrop: () -> Unit,
    val onDisconnectRaindrop: () -> Unit,
    val onSyncNow: () -> Unit,
    val onSaveServiceChange: (String) -> Unit,
)

/**
 * Connected services (#109, #112): the sections and the services each one is connected to. Reader
 * works on its own; Saved can be connected to Instapaper, Raindrop.io or both. With both, each
 * keeps its own items and new saves go to the one chosen here (#121).
 */
@Composable
fun ServicesScreen(
    state: ServicesState,
    actions: ServicesActions,
) {
    SettingsScaffold(
        title = stringResource(R.string.services_title),
        backLabel = stringResource(R.string.action_back),
        onBack = actions.onBack,
    ) {
        item(key = "feeds") {
            SettingsSection(title = stringResource(R.string.feeds_title)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.services_reader)) },
                    supportingContent = { Text(stringResource(R.string.services_feeds_body)) },
                    leadingContent = { Icon(painterResource(R.drawable.ic_rss_feed), contentDescription = null) },
                )
            }
        }
        item(key = "reading") {
            SettingsSection(title = stringResource(R.string.saved_title)) {
                InstapaperRow(state.instapaper, state.statuses[InstapaperReadingList.ID] ?: ServiceStatus.None, actions)
                RaindropRow(state.raindrop, state.statuses[RaindropSaved.ID] ?: ServiceStatus.None, actions)
                if (state.instapaper is InstapaperState.SignedIn && state.raindrop == RaindropState.SignedIn) {
                    SettingsBlock {
                        Text(stringResource(R.string.save_service_title), style = MaterialTheme.typography.bodyLarge)
                        SaveServiceChoice(state.saveService, actions.onSaveServiceChange)
                    }
                }
            }
        }
    }
}

@Composable
private fun InstapaperRow(
    instapaper: InstapaperState,
    status: ServiceStatus,
    actions: ServicesActions,
) {
    val signedIn = instapaper as? InstapaperState.SignedIn
    ListItem(
        headlineContent = { Text(stringResource(R.string.section_instapaper)) },
        supportingContent = {
            when {
                signedIn == null -> Text(stringResource(R.string.services_reading_body))
                status is ServiceStatus.Syncing -> Text(stringResource(R.string.sync_syncing))
                status is ServiceStatus.Failed -> Text(stringResource(status.problem.messageRes(InstapaperReadingList.ID)), color = MaterialTheme.colorScheme.error)
                else ->
                    Text(
                        stringResource(
                            R.string.services_instapaper_body,
                            signedIn.session.username.ifEmpty { stringResource(R.string.section_instapaper) },
                        ),
                    )
            }
        },
        leadingContent = { Icon(painterResource(R.drawable.ic_reading_list), contentDescription = null) },
    )
    if (instapaper !is InstapaperState.Loading) {
        SettingsBlock {
            if (signedIn == null) {
                Button(onClick = actions.onSignIn) { Text(stringResource(R.string.instapaper_sign_in)) }
            } else {
                FilledTonalButton(onClick = actions.onSyncNow, enabled = status !is ServiceStatus.Syncing) { Text(stringResource(R.string.sync_now)) }
            }
        }
    }
}

@Composable
private fun RaindropRow(
    raindrop: RaindropState,
    status: ServiceStatus,
    actions: ServicesActions,
) {
    val connected = raindrop == RaindropState.SignedIn
    ListItem(
        headlineContent = { Text(stringResource(R.string.section_raindrop)) },
        supportingContent = {
            when {
                !connected -> Text(stringResource(R.string.services_raindrop_off))
                status is ServiceStatus.Syncing -> Text(stringResource(R.string.sync_syncing))
                status is ServiceStatus.Failed -> Text(stringResource(status.problem.messageRes(RaindropSaved.ID)), color = MaterialTheme.colorScheme.error)
                else -> Text(stringResource(R.string.services_raindrop_body))
            }
        },
        leadingContent = { Icon(painterResource(R.drawable.ic_bookmark), contentDescription = null) },
    )
    if (raindrop != RaindropState.Loading) {
        SettingsBlock {
            if (!connected) {
                Button(onClick = actions.onConnectRaindrop) { Text(stringResource(R.string.raindrop_connect)) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = actions.onSyncNow, enabled = status !is ServiceStatus.Syncing) { Text(stringResource(R.string.sync_now)) }
                    OutlinedButton(onClick = actions.onDisconnectRaindrop) { Text(stringResource(R.string.raindrop_disconnect)) }
                }
            }
        }
    }
}

/** Instapaper or Raindrop.io, for where new saves go. */
@Composable
fun SaveServiceChoice(
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val choices = listOf(InstapaperReadingList.ID to R.string.section_instapaper, RaindropSaved.ID to R.string.section_raindrop)
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        choices.forEachIndexed { index, (id, label) ->
            SegmentedButton(
                selected = selected == id,
                onClick = { onSelect(id) },
                shape = SegmentedButtonDefaults.itemShape(index, choices.size),
            ) { Text(stringResource(label)) }
        }
    }
}

/**
 * Asked once when both services are connected (#121): where new saves go. Closing it keeps
 * Instapaper, the first connected; Connected services changes it later.
 */
@Composable
fun SaveServiceDialog(onChoose: (String) -> Unit) {
    var choice by remember { mutableStateOf(InstapaperReadingList.ID) }
    AlertDialog(
        onDismissRequest = { onChoose(InstapaperReadingList.ID) },
        title = { Text(stringResource(R.string.save_service_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.save_service_question))
                SaveServiceChoice(choice, { choice = it })
            }
        },
        confirmButton = { TextButton(onClick = { onChoose(choice) }) { Text(stringResource(R.string.action_done)) } },
    )
}

private fun ServiceProblem.messageRes(service: String): Int =
    when (this) {
        ServiceProblem.SignedOut -> if (service == RaindropSaved.ID) R.string.raindrop_problem_signed_out else R.string.instapaper_problem_signed_out
        ServiceProblem.RateLimited -> if (service == RaindropSaved.ID) R.string.raindrop_problem_later else R.string.services_problem_later
        ServiceProblem.Offline -> R.string.problem_offline
        ServiceProblem.Refused -> R.string.problem_server
    }
