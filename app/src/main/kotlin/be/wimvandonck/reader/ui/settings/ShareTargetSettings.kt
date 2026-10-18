package be.wimvandonck.reader.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import be.wimvandonck.reader.R
import be.wimvandonck.reader.data.ShareTarget
import be.wimvandonck.reader.ui.ExternalApps
import be.wimvandonck.reader.ui.theme.SettingsSection

/**
 * Where the reader's Save and Share buttons send an article: Saved in this app (Save only), the
 * share menu, or straight to one app. Signed in to Instapaper, Save always goes to the account,
 * so there is no choice to show for it ([showSaveTarget] false).
 */
@Composable
fun ShareTargetsSection(
    saveTarget: ShareTarget,
    shareTarget: ShareTarget,
    apps: List<ExternalApps.ShareApp>,
    onSaveTargetChange: (ShareTarget) -> Unit,
    onShareTargetChange: (ShareTarget) -> Unit,
    showSaveTarget: Boolean = true,
) {
    SettingsSection(title = stringResource(R.string.section_save)) {
        if (showSaveTarget) TargetRow(stringResource(R.string.setting_save_button), saveTarget, apps, onSaveTargetChange, offerSaved = true)
        TargetRow(stringResource(R.string.setting_share_button), shareTarget, apps, onShareTargetChange)
    }
}

@Composable
private fun TargetRow(
    title: String,
    target: ShareTarget,
    apps: List<ExternalApps.ShareApp>,
    onChange: (ShareTarget) -> Unit,
    /** Offer Saved in this app as the first choice (for Save). */
    offerSaved: Boolean = false,
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    val sheet = stringResource(R.string.share_target_sheet)
    val saved = stringResource(R.string.share_target_saved)
    val missing = target is ShareTarget.App && apps.isNotEmpty() && apps.none { it.target.packageName == target.packageName }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Column {
                Text(target.label(sheet, saved))
                if (missing) {
                    Text(
                        stringResource(R.string.share_target_missing, target.label(sheet, saved)),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        modifier = Modifier.clickable(role = Role.Button) { choosing = true },
    )
    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(title) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    if (offerSaved) {
                        item(key = "saved") {
                            Choice(saved, null, selected = target == ShareTarget.Saved, iconRes = R.drawable.ic_bookmark_add) {
                                onChange(ShareTarget.Saved)
                                choosing = false
                            }
                        }
                    }
                    item(key = "sheet") {
                        Choice(sheet, null, selected = target == ShareTarget.Sheet) {
                            onChange(ShareTarget.Sheet)
                            choosing = false
                        }
                    }
                    items(apps, key = { it.target.packageName + "/" + it.target.activityName }) { app ->
                        val selected =
                            target is ShareTarget.App &&
                                target.packageName == app.target.packageName &&
                                (target.activityName == null || target.activityName == app.target.activityName)
                        Choice(app.target.label, app, selected) {
                            onChange(app.target)
                            choosing = false
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun Choice(
    label: String,
    app: ExternalApps.ShareApp?,
    selected: Boolean,
    @DrawableRes iconRes: Int = R.drawable.ic_share,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        if (app != null) {
            val bitmap = remember(app) { app.icon.toBitmap(96, 96).asImageBitmap() }
            Image(bitmap, contentDescription = null, modifier = Modifier.size(32.dp))
        } else {
            Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(32.dp).padding(4.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
    }
}

/** What the setting shows: the app's name, "Share menu" or "Saved, in this app". */
fun ShareTarget.label(
    sheet: String,
    saved: String,
): String =
    when (this) {
        ShareTarget.Sheet -> sheet
        ShareTarget.Saved -> saved
        is ShareTarget.App -> label
    }
