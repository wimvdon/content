package io.github.impca201.rssreader.ui.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import io.github.impca201.rssreader.R
import io.github.impca201.rssreader.data.ShareTarget
import io.github.impca201.rssreader.ui.ExternalApps
import io.github.impca201.rssreader.ui.theme.SettingsBlock
import io.github.impca201.rssreader.ui.theme.SettingsSection

/**
 * Where the reader's Save and Share buttons send an article: the share menu, or straight to one
 * app (for example Instapaper for Save).
 */
@Composable
fun ShareTargetsSection(
    saveTarget: ShareTarget,
    shareTarget: ShareTarget,
    apps: List<ExternalApps.ShareApp>,
    onSaveTargetChange: (ShareTarget) -> Unit,
    onShareTargetChange: (ShareTarget) -> Unit,
    instapaper: InstapaperState,
) {
    val sheet = stringResource(R.string.share_target_sheet)
    SettingsSection(title = stringResource(R.string.section_save)) {
        SettingsBlock {
            Text(
                stringResource(R.string.save_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val account = instapaper.username != null
        TargetRow(stringResource(R.string.setting_save_button), saveTarget, apps, onSaveTargetChange, account)
        TargetRow(stringResource(R.string.setting_share_button), shareTarget, apps, onShareTargetChange, account)
        InstapaperAccountRow(instapaper)
    }
}

/** The Instapaper account and what it can do, for Settings. */
class InstapaperState(
    /** Signed in as, or null. */
    val username: String?,
    val busy: Boolean,
    @param:StringRes val error: Int?,
    val onSignIn: (username: String, password: String) -> Unit,
    val onSignOut: () -> Unit,
)

/** Sign in to save straight to Instapaper (Simple API), or who is signed in with Sign out. */
@Composable
private fun InstapaperAccountRow(state: InstapaperState) {
    var dialog by rememberSaveable { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(stringResource(R.string.instapaper_account)) },
        supportingContent = {
            Text(
                if (state.username != null) {
                    stringResource(R.string.instapaper_signed_in, state.username)
                } else {
                    stringResource(R.string.instapaper_account_summary)
                },
            )
        },
        trailingContent = {
            if (state.username != null) {
                TextButton(onClick = state.onSignOut) { Text(stringResource(R.string.instapaper_sign_out)) }
            } else {
                TextButton(onClick = { dialog = true }) { Text(stringResource(R.string.instapaper_sign_in)) }
            }
        },
    )
    // Closes by itself once signed in.
    if (dialog && state.username == null) {
        var username by rememberSaveable { mutableStateOf("") }
        var password by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { dialog = false },
            title = { Text(stringResource(R.string.instapaper_account)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.instapaper_username)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.instapaper_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (username.isNotBlank()) state.onSignIn(username.trim(), password) }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.error != null) Text(stringResource(state.error), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(onClick = { state.onSignIn(username.trim(), password) }, enabled = username.isNotBlank() && !state.busy) {
                    Text(stringResource(R.string.instapaper_sign_in))
                }
            },
            dismissButton = { TextButton(onClick = { dialog = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun TargetRow(
    title: String,
    target: ShareTarget,
    apps: List<ExternalApps.ShareApp>,
    onChange: (ShareTarget) -> Unit,
    instapaperAccount: Boolean,
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    val sheet = stringResource(R.string.share_target_sheet)
    val instapaperLabel = stringResource(R.string.instapaper_target)
    val missing = target is ShareTarget.App && apps.isNotEmpty() && apps.none { it.target.packageName == target.packageName }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Column {
                Text(target.label(sheet, instapaperLabel))
                if (missing) {
                    Text(
                        stringResource(R.string.share_target_missing, target.label(sheet, instapaperLabel)),
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
                    if (instapaperAccount) {
                        item(key = "instapaper-account") {
                            Choice(stringResource(R.string.instapaper_target), null, selected = target == ShareTarget.InstapaperAccount, iconRes = R.drawable.ic_bookmark_add) {
                                onChange(ShareTarget.InstapaperAccount)
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

/** What the setting shows: the app's name, "Share menu", or the Instapaper account. */
fun ShareTarget.label(
    sheet: String,
    instapaperAccount: String,
): String =
    when (this) {
        ShareTarget.Sheet -> sheet
        ShareTarget.InstapaperAccount -> instapaperAccount
        is ShareTarget.App -> label
    }
