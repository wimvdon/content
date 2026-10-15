package io.github.impca201.rssreader.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.impca201.rssreader.R

/** Inoreader's web app, where Preferences › Import/Export lives. */
const val INOREADER_URL = "https://www.inoreader.com/"

/**
 * Moves the user's feeds from Inoreader: they export an OPML file on Inoreader's website, then
 * pick it here. No Inoreader account is linked to this app.
 */
@Composable
fun InoreaderImportDialog(
    onOpenInoreader: () -> Unit,
    onChooseFile: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.inoreader_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    stringResource(R.string.inoreader_step1),
                    stringResource(R.string.inoreader_step2),
                    stringResource(R.string.inoreader_step3),
                ).forEachIndexed { index, step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.widthIn(min = 16.dp),
                        )
                        Text(step, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                FilledTonalButton(onClick = onOpenInoreader, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null)
                    Text(stringResource(R.string.inoreader_open), modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onChooseFile) { Text(stringResource(R.string.inoreader_choose_file)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
