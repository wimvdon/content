package to.wim.content.ui.feeds

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import to.wim.content.R
import to.wim.content.ui.theme.ExpandableSetting

/**
 * How to bring feeds over from Feedly, Inoreader and Substack, under Import OPML: each a row that
 * opens its numbered steps, as the guides in the owner's other apps (Snoozer's `NumberedSteps`).
 */
@Composable
fun ImportGuides() {
    Column {
        ExpandableSetting(key = "import-feedly", title = stringResource(R.string.import_feedly_title)) {
            NumberedSteps(
                listOf(
                    stringResource(R.string.import_feedly_step1),
                    stringResource(R.string.import_step_import),
                ),
            )
            Text(stringResource(R.string.import_feedly_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ExpandableSetting(key = "import-inoreader", title = stringResource(R.string.import_inoreader_title)) {
            NumberedSteps(
                listOf(
                    stringResource(R.string.import_inoreader_step1),
                    stringResource(R.string.import_inoreader_step2),
                    stringResource(R.string.import_step_import),
                ),
            )
        }
        ExpandableSetting(key = "import-substack", title = stringResource(R.string.import_substack_title)) {
            NumberedSteps(
                listOf(
                    stringResource(R.string.import_substack_step1),
                    stringResource(R.string.import_substack_step2),
                    stringResource(R.string.import_substack_step3),
                ),
            )
        }
    }
}

/** Steps, each with its number in a filled circle, as in the owner's other apps. */
@Composable
private fun NumberedSteps(steps: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        steps.forEachIndexed { index, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text((index + 1).toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
                }
                Text(step, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).align(Alignment.CenterVertically))
            }
        }
    }
}
