package to.wim.content.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import to.wim.content.R
import to.wim.content.data.ArticleReport

/**
 * Report a problem with this article (#181): what is wrong and an optional comment. Send opens a
 * pre-filled GitHub issue in the browser, where the user submits it; the article's text isn't sent.
 * A bottom sheet, like everything done with an article (#256).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSheet(
    onSend: (ArticleReport.Problem, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var problem by rememberSaveable { mutableStateOf(ArticleReport.Problem.TextMissing) }
    var comment by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.report_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Column(Modifier.selectableGroup()) {
                for (p in ArticleReport.Problem.entries) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = p == problem, role = Role.RadioButton, onClick = { problem = p })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = p == problem, onClick = null)
                        Text(stringResource(p.labelRes()), modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it.take(ArticleReport.MAX_COMMENT) },
                label = { Text(stringResource(R.string.report_comment)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.report_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = { onSend(problem, comment) }) { Text(stringResource(R.string.report_send)) }
            }
        }
    }
}

private fun ArticleReport.Problem.labelRes(): Int =
    when (this) {
        ArticleReport.Problem.TextMissing -> R.string.report_text_missing
        ArticleReport.Problem.JunkLeft -> R.string.report_junk
        ArticleReport.Problem.PicturesWrong -> R.string.report_pictures
        ArticleReport.Problem.Other -> R.string.report_other
    }
