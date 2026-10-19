package be.wimvandonck.reader.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import be.wimvandonck.reader.R
import be.wimvandonck.reader.data.DuplicateSync
import be.wimvandonck.reader.instapaper.InstapaperReadingList
import be.wimvandonck.reader.instapaper.InstapaperState
import be.wimvandonck.reader.raindrop.RaindropSaved
import be.wimvandonck.reader.raindrop.RaindropState
import be.wimvandonck.reader.services.CopyProgress
import be.wimvandonck.reader.services.ServiceProblem
import be.wimvandonck.reader.services.ServiceStatus
import be.wimvandonck.reader.ui.theme.SettingsBlock
import be.wimvandonck.reader.ui.theme.SettingsScaffold
import be.wimvandonck.reader.ui.theme.SettingsSection
import be.wimvandonck.reader.ui.theme.SwitchSetting

/** What Connected services shows of Saved's services (#109, #112). */
data class ServicesState(
    val instapaper: InstapaperState,
    val raindrop: RaindropState,
    /** Each connected service's status, by id. */
    val statuses: Map<String, ServiceStatus>,
    /** The service new saves go to, when one is connected. */
    val saveService: String?,
    /** What the two copies of an article both keep share (#121). */
    val duplicates: DuplicateSync = DuplicateSync(),
    /** A copy from one service to the other, running or just ended. */
    val copying: CopyProgress? = null,
)

class ServicesActions(
    val onBack: () -> Unit,
    val onSignIn: () -> Unit,
    val onConnectRaindrop: () -> Unit,
    val onDisconnectRaindrop: () -> Unit,
    val onSyncNow: () -> Unit,
    val onSaveServiceChange: (String) -> Unit,
    val onDuplicatesChange: (DuplicateSync) -> Unit = {},
    /** Counts what copying [from]'s articles to [to] would add, and gives the number back. */
    val onCountCopy: (from: String, to: String, result: (Int) -> Unit) -> Unit = { _, _, _ -> },
    val onCopy: (from: String, to: String) -> Unit = { _, _ -> },
    val onCancelCopy: () -> Unit = {},
    val onCopyDone: () -> Unit = {},
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
        if (state.instapaper is InstapaperState.SignedIn && state.raindrop == RaindropState.SignedIn) {
            item(key = "duplicates") { DuplicatesSection(state.duplicates, actions.onDuplicatesChange) }
            item(key = "copy") { CopySection(state.copying, actions) }
        }
    }
}

/** What an article both services keep shares between its two copies (#121). */
@Composable
private fun DuplicatesSection(
    value: DuplicateSync,
    onChange: (DuplicateSync) -> Unit,
) {
    SettingsSection(title = stringResource(R.string.duplicates_title)) {
        SettingsBlock { Text(stringResource(R.string.duplicates_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        SwitchSetting(stringResource(R.string.duplicates_archive), null, value.archive, { onChange(value.copy(archive = it)) })
        SwitchSetting(stringResource(R.string.duplicates_liked), null, value.liked, { onChange(value.copy(liked = it)) })
        SwitchSetting(stringResource(R.string.duplicates_tags), null, value.tags, { onChange(value.copy(tags = it)) })
        SwitchSetting(stringResource(R.string.duplicates_folder), stringResource(R.string.duplicates_folder_body), value.folder, { onChange(value.copy(folder = it)) })
        ListItem(
            headlineContent = { Text(stringResource(R.string.duplicates_remove)) },
            supportingContent = { Text(stringResource(R.string.duplicates_remove_body)) },
        )
    }
}

/** Copying all of one service's articles to the other, once, when asked (#121). */
@Composable
private fun CopySection(
    copying: CopyProgress?,
    actions: ServicesActions,
) {
    // The copy asked for, with how many it would add, while its dialog is open.
    var asked by remember { mutableStateOf<Triple<String, String, Int>?>(null) }
    SettingsSection(title = stringResource(R.string.copy_title)) {
        SettingsBlock {
            Text(stringResource(R.string.copy_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (copying == null) {
                val directions = listOf(RaindropSaved.ID to InstapaperReadingList.ID, InstapaperReadingList.ID to RaindropSaved.ID)
                for ((from, to) in directions) {
                    OutlinedButton(onClick = { actions.onCountCopy(from, to) { count -> asked = Triple(from, to, count) } }) {
                        Text(stringResource(R.string.copy_action, stringResource(serviceName(from)), stringResource(serviceName(to))))
                    }
                }
            } else {
                val to = stringResource(serviceName(copying.to))
                Text(
                    when {
                        !copying.finished -> stringResource(R.string.copy_running, copying.done, copying.total, to)
                        copying.problem != null -> stringResource(R.string.copy_stopped, copying.done, copying.total, to)
                        else -> pluralStringResource(R.plurals.copy_done, copying.done, copying.done, to)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (copying.problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                LinearProgressIndicator(progress = { if (copying.total == 0) 1f else copying.done / copying.total.toFloat() }, modifier = Modifier.fillMaxWidth())
                if (copying.finished) {
                    FilledTonalButton(onClick = actions.onCopyDone) { Text(stringResource(R.string.action_done)) }
                } else {
                    OutlinedButton(onClick = actions.onCancelCopy) { Text(stringResource(R.string.action_cancel)) }
                }
            }
        }
    }
    asked?.let { (from, to, count) ->
        val toName = stringResource(serviceName(to))
        AlertDialog(
            onDismissRequest = { asked = null },
            title = { Text(stringResource(R.string.copy_action, stringResource(serviceName(from)), toName)) },
            text = {
                Text(
                    if (count == 0) {
                        stringResource(R.string.copy_nothing, toName)
                    } else {
                        pluralStringResource(R.plurals.copy_confirm, count, count, toName) + "\n\n" +
                            stringResource(if (to == InstapaperReadingList.ID) R.string.copy_note_to_instapaper else R.string.copy_note_to_raindrop)
                    },
                )
            },
            confirmButton = {
                if (count > 0) {
                    TextButton(onClick = {
                        asked = null
                        actions.onCopy(from, to)
                    }) { Text(stringResource(R.string.copy_start)) }
                } else {
                    TextButton(onClick = { asked = null }) { Text(stringResource(R.string.action_done)) }
                }
            },
            dismissButton = { if (count > 0) TextButton(onClick = { asked = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
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

/** A service's name, by id. */
fun serviceName(id: String): Int = if (id == RaindropSaved.ID) R.string.section_raindrop else R.string.section_instapaper

private fun ServiceProblem.messageRes(service: String): Int =
    when (this) {
        ServiceProblem.SignedOut -> if (service == RaindropSaved.ID) R.string.raindrop_problem_signed_out else R.string.instapaper_problem_signed_out
        ServiceProblem.RateLimited -> if (service == RaindropSaved.ID) R.string.raindrop_problem_later else R.string.services_problem_later
        ServiceProblem.Offline -> R.string.problem_offline
        ServiceProblem.Refused -> R.string.problem_server
    }
