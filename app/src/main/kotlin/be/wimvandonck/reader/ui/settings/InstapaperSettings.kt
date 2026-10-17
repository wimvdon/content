package be.wimvandonck.reader.ui.settings

import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import be.wimvandonck.reader.R
import be.wimvandonck.reader.instapaper.InstapaperState
import be.wimvandonck.reader.ui.theme.SettingsBlock
import be.wimvandonck.reader.ui.theme.SettingsSection

/**
 * Instapaper: Sign in, or who is signed in with Sign out. The saved articles, folders and tags are
 * in the drawer. Shown once the stored account is known.
 */
@Composable
fun InstapaperSection(
    state: InstapaperState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    if (state is InstapaperState.Loading) return
    SettingsSection(title = stringResource(R.string.section_instapaper)) {
        if (state is InstapaperState.SignedIn) {
            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.instapaper_signed_in_as, state.session.username.ifEmpty { stringResource(R.string.section_instapaper) }))
                },
                supportingContent = { Text(stringResource(R.string.instapaper_summary)) },
                leadingContent = { Icon(painterResource(R.drawable.ic_bookmark_add), contentDescription = null) },
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
