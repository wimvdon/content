package be.wimvandonck.reader.ui.settings

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
import androidx.compose.ui.unit.dp
import be.wimvandonck.reader.R
import be.wimvandonck.reader.data.ServiceImport
import be.wimvandonck.reader.data.ServiceScope
import be.wimvandonck.reader.instapaper.InstapaperReadingList
import be.wimvandonck.reader.instapaper.InstapaperState
import be.wimvandonck.reader.raindrop.RaindropSaved
import be.wimvandonck.reader.raindrop.RaindropState
import be.wimvandonck.reader.services.ServiceProblem
import be.wimvandonck.reader.services.ServiceStatus
import be.wimvandonck.reader.ui.theme.SettingsBlock
import be.wimvandonck.reader.ui.theme.SettingsScaffold
import be.wimvandonck.reader.ui.theme.SettingsSection
import java.text.DateFormat
import java.util.Date

/** What Connected services and a service's own screen show (#109, #112, #145). */
data class ServicesState(
    val instapaper: InstapaperState,
    val raindrop: RaindropState,
    /** Each connected service's status, by id. */
    val statuses: Map<String, ServiceStatus>,
    /** What each service syncs, by id. */
    val scopes: Map<String, ServiceScope> = emptyMap(),
    /** What was imported from each, by id. */
    val imports: Map<String, ServiceImport> = emptyMap(),
    /** The connected services still waiting for their import. */
    val waiting: Set<String> = emptySet(),
    /** Saved's folders, for the scope. */
    val folders: List<String> = emptyList(),
)

class ServicesActions(
    val onBack: () -> Unit,
    val onSignIn: () -> Unit,
    val onSignOut: () -> Unit = {},
    val onConnectRaindrop: () -> Unit,
    val onDisconnectRaindrop: () -> Unit,
    val onSyncNow: () -> Unit,
    /** Opens a connected service's own screen. */
    val onOpenService: (String) -> Unit = {},
    /** Reads the service's folders, for choosing what to import, and gives them back (null: it couldn't). */
    val onLoadFolders: (id: String, result: (List<String>?) -> Unit) -> Unit = { _, _ -> },
    /** Imports from a service; gives back whether it got through. */
    val onImport: (id: String, what: ServiceImport, result: (Boolean) -> Unit) -> Unit = { _, _, _ -> },
    /** Counts what a new scope would take out of the service, and gives the number back. */
    val onCountLeaving: (id: String, scope: ServiceScope, result: (Int) -> Unit) -> Unit = { _, _, _ -> },
    val onScopeChange: (id: String, scope: ServiceScope) -> Unit = { _, _ -> },
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
                ServiceRow(RaindropSaved.ID, state, actions)
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
                !connected -> Text(stringResource(if (id == RaindropSaved.ID) R.string.services_raindrop_off else R.string.services_reading_body))
                id in state.waiting -> Text(stringResource(R.string.services_waiting_import), color = MaterialTheme.colorScheme.primary)
                status is ServiceStatus.Syncing -> Text(stringResource(R.string.sync_syncing))
                status is ServiceStatus.Failed -> Text(stringResource(status.problem.messageRes(id)), color = MaterialTheme.colorScheme.error)
                else -> Text(scopeLine(state.scopes[id] ?: ServiceScope.Everything))
            }
        },
        leadingContent = { Icon(painterResource(if (id == RaindropSaved.ID) R.drawable.ic_bookmark else R.drawable.ic_reading_list), contentDescription = null) },
        modifier = if (connected) Modifier.selectable(selected = false, role = Role.Button, onClick = { actions.onOpenService(id) }) else Modifier,
    )
    val loading = if (id == RaindropSaved.ID) state.raindrop == RaindropState.Loading else state.instapaper is InstapaperState.Loading
    if (!connected && !loading) {
        SettingsBlock {
            if (id == RaindropSaved.ID) {
                Button(onClick = actions.onConnectRaindrop) { Text(stringResource(R.string.raindrop_connect)) }
            } else {
                Button(onClick = actions.onSignIn) { Text(stringResource(R.string.instapaper_sign_in)) }
            }
        }
    }
}

/** What a service syncs, in a line: everything, or its folders. */
@Composable
private fun scopeLine(scope: ServiceScope): String {
    val folders = scope.folders ?: return stringResource(R.string.services_syncs_everything)
    val toRead = stringResource(R.string.reading_list_to_read)
    val names = folders.map { if (it == ServiceScope.TO_READ) toRead else it }.sortedWith(compareBy({ it != toRead }, { it.lowercase() }))
    return stringResource(R.string.services_syncs_folders, names.joinToString(", "))
}

private fun connected(
    id: String,
    state: ServicesState,
): Boolean = if (id == RaindropSaved.ID) state.raindrop == RaindropState.SignedIn else state.instapaper is InstapaperState.SignedIn

/**
 * One service's screen (#145): the account, what was imported (or the import, once, right after
 * connecting), and what it syncs, both ways: everything in Saved, or only some folders. Unticking a
 * folder says first how many articles it archives there.
 */
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
                                ?: stringResource(if (id == RaindropSaved.ID) R.string.services_raindrop_body else R.string.section_instapaper),
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (connected && id !in state.waiting) {
                            FilledTonalButton(onClick = actions.onSyncNow, enabled = status !is ServiceStatus.Syncing) { Text(stringResource(R.string.sync_now)) }
                        }
                        if (connected) {
                            OutlinedButton(onClick = if (id == RaindropSaved.ID) actions.onDisconnectRaindrop else actions.onSignOut) {
                                Text(stringResource(if (id == RaindropSaved.ID) R.string.raindrop_disconnect else R.string.instapaper_sign_out))
                            }
                        }
                    }
                }
            }
        }
        if (connected) {
            item(key = "import") { ImportSection(id, name, state, actions) }
            item(key = "scope") { ScopeSection(id, name, state, actions) }
        }
    }
}

/** The import: once, right after connecting, then Import again when the service has drifted. */
@Composable
private fun ImportSection(
    id: String,
    name: String,
    state: ServicesState,
    actions: ServicesActions,
) {
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    // The service's folders while the dialog to choose them is open.
    var choosing by remember { mutableStateOf<List<String>?>(null) }
    val run = { what: ServiceImport ->
        busy = true
        failed = false
        actions.onImport(id, what) { ok ->
            busy = false
            failed = !ok
        }
    }
    val record = state.imports[id]
    val waiting = id in state.waiting
    SettingsSection(title = stringResource(R.string.service_import_title)) {
        SettingsBlock {
            if (record != null) {
                val date = DateFormat.getDateInstance(DateFormat.LONG).format(Date(record.at))
                Text(pluralStringResource(R.plurals.service_imported, record.count, record.count, date), style = MaterialTheme.typography.bodyLarge)
            } else if (waiting) {
                Text(stringResource(R.string.service_import_body, name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (failed) Text(stringResource(R.string.service_import_failed), color = MaterialTheme.colorScheme.error)
            if (busy) {
                CircularProgressIndicator()
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (waiting) {
                        Button(onClick = { run(ServiceImport(0, 0)) }) { Text(stringResource(R.string.service_import_all)) }
                    }
                    OutlinedButton(onClick = {
                        busy = true
                        actions.onLoadFolders(id) { folders ->
                            busy = false
                            if (folders == null) failed = true else choosing = folders
                        }
                    }) { Text(stringResource(if (waiting) R.string.service_import_choose else R.string.service_import_again)) }
                }
            }
        }
    }
    choosing?.let { folders ->
        ImportDialog(
            folders = folders,
            onImport = {
                choosing = null
                run(it)
            },
            onDismiss = { choosing = null },
        )
    }
}

/** What to bring in: everything, or To read, some folders and the archive. */
@Composable
private fun ImportDialog(
    folders: List<String>,
    onImport: (ServiceImport) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(setOf(ServiceScope.TO_READ) + folders) }
    var archive by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.service_import_what)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                CheckRow(stringResource(R.string.reading_list_to_read), ServiceScope.TO_READ in picked) { on -> picked = if (on) picked + ServiceScope.TO_READ else picked - ServiceScope.TO_READ }
                for (folder in folders) CheckRow(folder, folder in picked) { on -> picked = if (on) picked + folder else picked - folder }
                CheckRow(stringResource(R.string.service_import_archive), archive) { archive = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = picked.isNotEmpty() || archive,
                onClick = {
                    val everything = archive && picked.size == folders.size + 1
                    onImport(ServiceImport(0, 0, folders = if (everything) null else picked, archive = archive))
                },
            ) { Text(stringResource(R.string.service_import_start)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** What the service syncs, both ways: everything in Saved, or only the folders ticked (To read counts as one). */
@Composable
private fun ScopeSection(
    id: String,
    name: String,
    state: ServicesState,
    actions: ServicesActions,
) {
    val scope = state.scopes[id] ?: ServiceScope.Everything
    // A narrower scope waiting for its confirmation, with how many articles it archives there.
    var asking by remember { mutableStateOf<Pair<ServiceScope, Int>?>(null) }
    val change = { next: ServiceScope ->
        actions.onCountLeaving(id, next) { count -> if (count == 0) actions.onScopeChange(id, next) else asking = next to count }
    }
    SettingsSection(title = stringResource(R.string.service_scope_title)) {
        Column(Modifier.selectableGroup()) {
            RadioRow(stringResource(R.string.service_scope_all), scope.everything) { if (!scope.everything) actions.onScopeChange(id, ServiceScope.Everything) }
            RadioRow(stringResource(R.string.service_scope_some), !scope.everything) {
                // Starts from every folder there is, so nothing leaves until a folder is unticked.
                if (scope.everything) change(ServiceScope(setOf(ServiceScope.TO_READ) + state.folders))
            }
        }
        if (!scope.everything) {
            Column(Modifier.fillMaxWidth()) {
                val toRead = stringResource(R.string.reading_list_to_read)
                CheckRow(toRead, !scope.everything && scope.coversPlace(null, archived = false), indent = true) { on -> change(scope.with(null, on)) }
                for (folder in state.folders) {
                    CheckRow(folder, scope.coversPlace(folder, archived = false), indent = true) { on -> change(scope.with(folder, on)) }
                }
            }
        }
        SettingsBlock {
            Text(stringResource(R.string.service_scope_note, name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    asking?.let { (next, count) ->
        AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text(stringResource(R.string.service_scope_leave_title, name)) },
            text = { Text(pluralStringResource(R.plurals.service_scope_leave_body, count, count, name)) },
            confirmButton = {
                TextButton(onClick = {
                    asking = null
                    actions.onScopeChange(id, next)
                }) { Text(stringResource(R.string.service_scope_leave_confirm)) }
            },
            dismissButton = { TextButton(onClick = { asking = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun RadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    )
}

@Composable
private fun CheckRow(
    label: String,
    checked: Boolean,
    indent: Boolean = false,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
            .then(if (indent) Modifier.padding(start = 56.dp) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
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
