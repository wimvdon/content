package be.wimvandonck.reader.ui.settings

import android.content.ClipData
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontFamily
import be.wimvandonck.reader.ui.theme.SwitchSetting
import kotlinx.coroutines.launch
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import be.wimvandonck.reader.R
import be.wimvandonck.reader.instapaper.InstapaperState
import be.wimvandonck.reader.services.ServiceProblem
import be.wimvandonck.reader.services.ServiceStatus
import be.wimvandonck.reader.ui.theme.SettingsBlock
import be.wimvandonck.reader.ui.theme.SettingsScaffold
import be.wimvandonck.reader.ui.theme.SettingsSection

/**
 * Connected services (#109): the three sections and the service each one is connected to. Reader
 * works on its own; the reading list can be connected to Instapaper. Feeds and Bookmarks have no
 * service yet.
 */
@Composable
fun ServicesScreen(
    instapaper: InstapaperState,
    status: ServiceStatus,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onSyncNow: () -> Unit,
    copyHighlights: Boolean = false,
    onCopyHighlightsChange: (Boolean) -> Unit = {},
    /** The matching test of #102, in test builds only (null hides it). */
    matchTest: MatchTestState? = null,
    onRunMatchTest: () -> Unit = {},
) {
    SettingsScaffold(
        title = stringResource(R.string.services_title),
        backLabel = stringResource(R.string.action_back),
        onBack = onBack,
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
            SettingsSection(title = stringResource(R.string.reading_list_title)) {
                val signedIn = instapaper as? InstapaperState.SignedIn
                ListItem(
                    headlineContent = { Text(if (signedIn != null) stringResource(R.string.section_instapaper) else stringResource(R.string.services_reader)) },
                    supportingContent = {
                        when {
                            signedIn == null -> Text(stringResource(R.string.services_reading_body))
                            status is ServiceStatus.Syncing -> Text(stringResource(R.string.sync_syncing))
                            status is ServiceStatus.Failed -> Text(stringResource(status.problem.messageRes()), color = MaterialTheme.colorScheme.error)
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
                            Button(onClick = onSignIn) { Text(stringResource(R.string.instapaper_sign_in)) }
                        } else {
                            FilledTonalButton(onClick = onSyncNow, enabled = status !is ServiceStatus.Syncing) { Text(stringResource(R.string.sync_now)) }
                        }
                    }
                }
                if (signedIn != null) {
                    SwitchSetting(
                        title = stringResource(R.string.services_copy_highlights_title),
                        summary = stringResource(R.string.services_copy_highlights_summary),
                        checked = copyHighlights,
                        onCheckedChange = onCopyHighlightsChange,
                    )
                    if (matchTest != null) MatchTestBlock(matchTest, onRunMatchTest)
                }
            }
        }
    }
}

/** Where the matching test is: not run, running (articles checked), or done with its report. */
data class MatchTestState(
    val running: Boolean = false,
    val checked: Int = 0,
    val result: String? = null,
)

@Composable
private fun MatchTestBlock(
    state: MatchTestState,
    onRun: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    SettingsBlock {
        Text(stringResource(R.string.match_test_title), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.match_test_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.running || state.checked > 0) {
            Text(pluralStringResource(R.plurals.match_test_progress, state.checked, state.checked), style = MaterialTheme.typography.labelLarge)
        }
        if (state.running) LinearProgressIndicator(Modifier.fillMaxWidth())
        val result = state.result
        if (result != null) {
            SelectionContainer { Text(result, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)) }
            OutlinedButton(onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("match", result))) } }) {
                Text(stringResource(R.string.match_test_copy))
            }
        }
        FilledTonalButton(onClick = onRun, enabled = !state.running) { Text(stringResource(R.string.match_test_run)) }
    }
}

private fun ServiceProblem.messageRes(): Int =
    when (this) {
        ServiceProblem.SignedOut -> R.string.instapaper_problem_signed_out
        ServiceProblem.RateLimited, ServiceProblem.LimitReached -> R.string.services_problem_later
        ServiceProblem.Offline -> R.string.problem_offline
        ServiceProblem.Refused -> R.string.problem_server
    }
