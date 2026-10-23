package be.wimvandonck.content.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.unit.dp
import be.wimvandonck.content.R
import be.wimvandonck.content.data.ServiceImport
import be.wimvandonck.content.instapaper.InstapaperReadingList
import be.wimvandonck.content.instapaper.InstapaperState
import be.wimvandonck.content.services.ServiceProblem
import be.wimvandonck.content.services.ServiceStatus
import be.wimvandonck.content.ui.theme.SettingsBlock
import be.wimvandonck.content.ui.theme.SettingsScaffold
import be.wimvandonck.content.ui.theme.SettingsSection
import java.text.DateFormat
import java.util.Date

/** What Connected services and a service's own screen show (#109, #112, #145). */
data class ServicesState(
    val instapaper: InstapaperState,
    /** Each connected service's status, by id. */
    val statuses: Map<String, ServiceStatus>,
    /** What was brought in from each when it was connected, by id. */
    val imports: Map<String, ServiceImport> = emptyMap(),
    /** Copy highlights to Instapaper (#164). */
    val copyHighlights: Boolean = false,
)

class ServicesActions(
    val onBack: () -> Unit,
    val onSignIn: () -> Unit,
    val onSignOut: () -> Unit = {},
    val onSyncNow: () -> Unit,
    /** Opens a connected service's own screen. */
    val onOpenService: (String) -> Unit = {},
    val onCopyHighlightsChange: (Boolean) -> Unit = {},
)

/**
 * Connected services (#109, #145): Reader keeps the feeds and Saved; each connected service is
 * somewhere Saved is also kept. This page shows the services and nothing else; each one has its own
 * screen with its account, what was imported and what it syncs.
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
        item(key = "saved") {
            SettingsSection(title = stringResource(R.string.saved_title)) {
                ServiceRow(InstapaperReadingList.ID, state, actions)
            }
        }
    }
}

/** A service in the list: its name, what it syncs (or how it is doing), and a way in or to connect. */
@Composable
private fun ServiceRow(
    id: String,
    state: ServicesState,
    actions: ServicesActions,
) {
    val connected = connected(id, state)
    val status = state.statuses[id] ?: ServiceStatus.None
    ListItem(
        headlineContent = { Text(stringResource(serviceName(id))) },
        supportingContent = {
            when {
                !connected -> Text(stringResource(R.string.services_reading_body))
                status is ServiceStatus.Syncing -> Text(stringResource(R.string.sync_syncing))
                status is ServiceStatus.Failed -> Text(stringResource(status.problem.messageRes(id)), color = MaterialTheme.colorScheme.error)
                else -> Text(stringResource(R.string.services_syncs_everything))
            }
        },
        leadingContent = { Icon(painterResource(R.drawable.ic_reading_list), contentDescription = null) },
        modifier = if (connected) Modifier.selectable(selected = false, role = Role.Button, onClick = { actions.onOpenService(id) }) else Modifier,
    )
    val loading = state.instapaper is InstapaperState.Loading
    if (!connected && !loading) {
        SettingsBlock {
            Button(onClick = actions.onSignIn) { Text(stringResource(R.string.instapaper_sign_in)) }
        }
    }
}

private fun connected(
    id: String,
    state: ServicesState,
): Boolean = id == InstapaperReadingList.ID && state.instapaper is InstapaperState.SignedIn

/**
 * One service's screen (#145): the account, what was brought in when it was connected, and copying
 * highlights. Everything in Saved syncs, both ways: there is nothing to choose.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServiceScreen(
    id: String,
    state: ServicesState,
    actions: ServicesActions,
) {
    val name = stringResource(serviceName(id))
    val status = state.statuses[id] ?: ServiceStatus.None
    val connected = connected(id, state)
    SettingsScaffold(
        title = name,
        backLabel = stringResource(R.string.action_back),
        onBack = actions.onBack,
    ) {
        item(key = "account") {
            SettingsSection(title = stringResource(R.string.section_account)) {
                ListItem(
                    headlineContent = {
                        Text(
                            (state.instapaper as? InstapaperState.SignedIn)?.session?.username?.takeIf { id == InstapaperReadingList.ID && it.isNotEmpty() }
                                ?: stringResource(R.string.section_instapaper),
                        )
                    },
                    supportingContent = {
                        when (status) {
                            ServiceStatus.Syncing -> Text(stringResource(R.string.sync_syncing))
                            is ServiceStatus.Failed -> Text(stringResource(status.problem.messageRes(id)), color = MaterialTheme.colorScheme.error)
                            else -> Unit
                        }
                    },
                )
                SettingsBlock {
                    // Wraps rather than squeezes the two buttons in longer languages.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (connected) {
                            FilledTonalButton(onClick = actions.onSyncNow, enabled = status !is ServiceStatus.Syncing) { Text(stringResource(R.string.sync_now)) }
                        }
                        if (connected) {
                            OutlinedButton(onClick = actions.onSignOut) {
                                Text(stringResource(R.string.instapaper_sign_out))
                            }
                        }
                    }
                }
            }
        }
        if (connected) {
            item(key = "import") { ImportSection(id, state) }
            if (id == InstapaperReadingList.ID) {
                item(key = "highlights") {
                    SettingsSection(title = stringResource(R.string.highlights_title)) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.instapaper_copy_highlights)) },
                            supportingContent = { Text(stringResource(R.string.instapaper_copy_highlights_summary)) },
                            trailingContent = { Switch(checked = state.copyHighlights, onCheckedChange = null) },
                            modifier = Modifier.toggleable(value = state.copyHighlights, role = Role.Switch, onValueChange = actions.onCopyHighlightsChange),
                        )
                    }
                }
            }
        }
    }
}

/** What was brought in when it was connected; everything in Saved syncs, both ways. */
@Composable
private fun ImportSection(id: String, state: ServicesState) {
    val record = state.imports[id] ?: return
    SettingsSection(title = stringResource(R.string.service_import_title)) {
        SettingsBlock {
            val date = DateFormat.getDateInstance(DateFormat.LONG).format(Date(record.at))
            Text(pluralStringResource(R.plurals.service_imported, record.count, record.count, date), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** A service's name, by id. */
@Suppress("UNUSED_PARAMETER")
fun serviceName(id: String): Int = R.string.section_instapaper

@Suppress("UNUSED_PARAMETER")
private fun ServiceProblem.messageRes(service: String): Int =
    when (this) {
        ServiceProblem.SignedOut -> R.string.instapaper_problem_signed_out
        ServiceProblem.RateLimited -> R.string.services_problem_later
        ServiceProblem.Offline -> R.string.problem_offline
        ServiceProblem.Refused -> R.string.problem_server
        ServiceProblem.LimitReached -> R.string.services_problem_limit
    }
