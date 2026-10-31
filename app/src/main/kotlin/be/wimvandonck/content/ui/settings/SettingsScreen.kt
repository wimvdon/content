package be.wimvandonck.content.ui.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import be.wimvandonck.content.R
import be.wimvandonck.content.data.ListView
import be.wimvandonck.content.data.ReaderStyle
import be.wimvandonck.content.data.Settings
import be.wimvandonck.content.data.ThemeMode
import be.wimvandonck.content.sync.AccountState
import be.wimvandonck.content.sync.SyncStatus
import be.wimvandonck.content.ui.reader.ReaderStyleControls
import be.wimvandonck.content.ui.reader.ReaderTheme
import be.wimvandonck.content.ui.reader.labelRes
import be.wimvandonck.content.ui.reader.readerBodyStyle
import be.wimvandonck.content.ui.theme.AppShapes
import be.wimvandonck.content.ui.theme.AppearanceSettings
import be.wimvandonck.content.ui.theme.SettingsBlock
import be.wimvandonck.content.ui.theme.SettingsSection
import be.wimvandonck.content.ui.theme.SettingsFooter
import be.wimvandonck.content.ui.theme.SettingsScaffold
import be.wimvandonck.content.ui.theme.SwitchSetting
import be.wimvandonck.content.ui.theme.rememberAppVersion

data class SettingsState(
    val settings: Settings,
    val syncStatus: SyncStatus = SyncStatus.Idle,
    val account: AccountState = AccountState.Loading,
    /** The name on the account address's Gravatar profile (#234), or null while unknown or without one. */
    val accountName: String? = null,
    /** Notifications when something is shared (#235): null when the build has none. */
    val notifyShared: Boolean? = null,
    /** Sites signed in to (#180). */
    val siteSignIns: List<String> = emptyList(),
)

class SettingsActions(
    val onBack: () -> Unit,
    val onReaderStyleChange: (ReaderStyle) -> Unit,
    val onFullArticlesChange: (Boolean) -> Unit,
    val onAlwaysOpenWebsiteChange: (Boolean) -> Unit,
    val onShowImagesChange: (Boolean) -> Unit,
    val onMarkReadOnScrollChange: (Boolean) -> Unit,
    val onListViewChange: (ListView) -> Unit,
    val onThemeModeChange: (ThemeMode) -> Unit,
    val onDynamicColorChange: (Boolean) -> Unit,
    val onSyncNow: () -> Unit,
    val onSignOut: () -> Unit,
    val onDeleteAccountData: () -> Unit,
    /** Emails a sign-in link (or, signed in, one that adds the address); gives back whether it was sent (#166). */
    val onSendLink: (email: String, result: (Boolean) -> Unit) -> Unit = { _, _ -> },
    /** Turns notifications on or off (#235); the result says whether that worked. */
    val onNotifyShared: (Boolean, (Boolean) -> Unit) -> Unit = { _, _ -> },
    /** Forgets a site's sign-in (#180). */
    val onRemoveSiteSignIn: (String) -> Unit = {},
)

/** Everything that can be set, as one list of sections in the order DESIGN.md gives. */
@Composable
fun SettingsScreen(
    state: SettingsState,
    snackbarHostState: SnackbarHostState,
    actions: SettingsActions,
) {
    SettingsScaffold(
        title = stringResource(R.string.menu_settings),
        backLabel = stringResource(R.string.action_back),
        onBack = actions.onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) {
        item(key = "account") {
            AccountSection(
                state = state.account,
                name = state.accountName,
                notifyShared = state.notifyShared,
                onNotifyShared = actions.onNotifyShared,
                status = state.syncStatus,
                lastSyncedAt = state.settings.lastSyncedAt,
                onSendLink = actions.onSendLink,
                showImages = state.settings.showImages,
                onSyncNow = actions.onSyncNow,
                onSignOut = actions.onSignOut,
                onDeleteData = actions.onDeleteAccountData,
            )
        }
        item(key = "sites") {
            SettingsSection(title = stringResource(R.string.site_sign_ins_title)) {
                for (site in state.siteSignIns) {
                    ListItem(
                        headlineContent = { Text(site) },
                        trailingContent = { TextButton(onClick = { actions.onRemoveSiteSignIn(site) }) { Text(stringResource(R.string.site_sign_in_remove)) } },
                    )
                }
                SettingsBlock {
                    Text(
                        stringResource(if (state.siteSignIns.isEmpty()) R.string.site_sign_ins_none else R.string.site_sign_ins_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item(key = "list") { ListSection(state.settings, actions) }
        item(key = "reader") { ReaderSection(state.settings, actions) }
        item(key = "appearance") { AppearanceSection(state.settings, actions) }
        item(key = "footer") {
            SettingsFooter("${stringResource(R.string.app_name)} ${rememberAppVersion()}")
        }
    }
}

/** How the article list looks and behaves. */
@Composable
private fun ListSection(
    settings: Settings,
    actions: SettingsActions,
) {
    SettingsSection(title = stringResource(R.string.section_list)) {
        SettingsBlock {
        Text(stringResource(R.string.setting_list_view), style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ListView.entries.forEachIndexed { index, view ->
                SegmentedButton(
                    selected = settings.listView == view,
                    onClick = { actions.onListViewChange(view) },
                    shape = SegmentedButtonDefaults.itemShape(index, ListView.entries.size),
                    icon = { Icon(painterResource(view.iconRes()), contentDescription = null, modifier = Modifier.size(SegmentedButtonDefaults.IconSize)) },
                ) { Text(stringResource(view.labelRes())) }
            }
        }
        }
        SwitchSetting(
            title = stringResource(R.string.setting_mark_read_on_scroll_title),
            summary = null,
            checked = settings.markReadOnScroll,
            onCheckedChange = actions.onMarkReadOnScrollChange,
        )
    }
}

@StringRes
private fun ListView.labelRes(): Int =
    when (this) {
        ListView.Small -> R.string.list_view_small
        ListView.Medium -> R.string.list_view_medium
        ListView.Large -> R.string.list_view_large
    }

@DrawableRes
private fun ListView.iconRes(): Int =
    when (this) {
        ListView.Small -> R.drawable.ic_view_small
        ListView.Medium -> R.drawable.ic_view_medium
        ListView.Large -> R.drawable.ic_view_large
    }

@Composable
private fun ReaderSection(
    settings: Settings,
    actions: SettingsActions,
) {
    val style = settings.reader
    SettingsSection(title = stringResource(R.string.section_reader)) {
        SwitchSetting(
            title = stringResource(R.string.reader_always_website_title),
            summary = stringResource(R.string.reader_always_website_summary),
            checked = settings.alwaysOpenWebsite,
            onCheckedChange = actions.onAlwaysOpenWebsiteChange,
        )
        SwitchSetting(
            title = stringResource(R.string.reader_full_articles_title),
            summary = stringResource(R.string.reader_full_articles_summary),
            checked = settings.fullArticles,
            onCheckedChange = actions.onFullArticlesChange,
        )
        SwitchSetting(
            title = stringResource(R.string.reader_show_images_title),
            summary = stringResource(R.string.reader_show_images_summary),
            checked = settings.showImages,
            onCheckedChange = actions.onShowImagesChange,
        )
        ReaderStyleControls(style = style, onChange = actions.onReaderStyleChange)
        val appDark = settings.appearance.isDark(isSystemInDarkTheme())
        SettingsBlock {
        // The preview is a small piece of the reader page: same page colour (and Auto dark) as the
        // reader itself, with a thin outline so the default colour still stands out from Settings.
        val outline = MaterialTheme.colorScheme.outlineVariant
        ReaderTheme(style = style, appearance = settings.appearance, appDark = appDark) {
            Text(
                text = stringResource(R.string.reader_preview),
                style = readerBodyStyle(style),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, AppShapes.Item)
                        .border(1.dp, outline, AppShapes.Item)
                        .padding(16.dp),
            )
        }
        }
    }
}

@Composable
private fun AppearanceSection(
    settings: Settings,
    actions: SettingsActions,
) {
    SettingsSection(title = stringResource(R.string.section_appearance)) {
        AppearanceSettings(
            themeLabel = stringResource(R.string.setting_theme_title),
            themeOptions = ThemeMode.entries.map { stringResource(it.labelRes()) },
            selectedTheme = settings.appearance.themeMode.ordinal,
            onThemeSelect = { actions.onThemeModeChange(ThemeMode.entries[it]) },
            dynamicColorTitle = stringResource(R.string.setting_dynamic_color_title),
            dynamicColorSummary = stringResource(R.string.setting_dynamic_color_summary),
            dynamicColor = settings.appearance.dynamicColor,
            onDynamicColorChange = actions.onDynamicColorChange,
            // minSdk 31: dynamic color is always available.
            dynamicColorSupported = true,
        )
    }
}

@StringRes
private fun ThemeMode.labelRes(): Int =
    when (this) {
        ThemeMode.System -> R.string.theme_system
        ThemeMode.Light -> R.string.theme_light
        ThemeMode.Dark -> R.string.theme_dark
    }
