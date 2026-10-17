package be.wimvandonck.reader.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import be.wimvandonck.reader.R
import be.wimvandonck.reader.instapaper.InstapaperState
import be.wimvandonck.reader.ui.theme.SettingsBlock
import be.wimvandonck.reader.ui.theme.SettingsSection

/** Instapaper: Sign in, or the saved articles with Sign out. Shown once the stored account is known. */
@Composable
fun InstapaperSection(
    state: InstapaperState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onOpen: () -> Unit,
) {
    if (state is InstapaperState.Loading) return
    SettingsSection(title = stringResource(R.string.section_instapaper)) {
        if (state is InstapaperState.SignedIn) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.instapaper_open)) },
                supportingContent = {
                    Text(stringResource(R.string.instapaper_signed_in_as, state.session.username.ifEmpty { stringResource(R.string.section_instapaper) }))
                },
                leadingContent = { Icon(painterResource(R.drawable.ic_bookmark_add), contentDescription = null) },
                trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
                modifier = Modifier.clickable(role = Role.Button, onClick = onOpen),
            )
            SettingsBlock { TextButton(onClick = onSignOut) { Text(stringResource(R.string.instapaper_sign_out)) } }
        } else {
            ListItem(
                headlineContent = { Text(stringResource(R.string.section_instapaper)) },
                supportingContent = { Text(stringResource(R.string.instapaper_summary)) },
                leadingContent = { Icon(painterResource(R.drawable.ic_bookmark_add), contentDescription = null) },
            )
            SettingsBlock { Button(onClick = onSignIn) { Text(stringResource(R.string.instapaper_sign_in)) } }
        }
    }
}
