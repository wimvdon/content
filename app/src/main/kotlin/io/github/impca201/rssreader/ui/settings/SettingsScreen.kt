package io.github.impca201.rssreader.ui.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.ListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.impca201.rssreader.R
import io.github.impca201.rssreader.data.ListView
import io.github.impca201.rssreader.data.ReaderStyle
import io.github.impca201.rssreader.data.ShareTarget
import io.github.impca201.rssreader.data.Settings
import io.github.impca201.rssreader.data.ThemeMode
import io.github.impca201.rssreader.sync.SyncStatus
import io.github.impca201.rssreader.ui.ExternalApps
import io.github.impca201.rssreader.ui.reader.ReaderStyleControls
import io.github.impca201.rssreader.ui.reader.labelRes
import io.github.impca201.rssreader.ui.reader.readerBodyStyle
import io.github.impca201.rssreader.ui.theme.AppShapes
import io.github.impca201.rssreader.ui.theme.AppearanceSettings
import io.github.impca201.rssreader.ui.theme.SettingsBlock
import io.github.impca201.rssreader.ui.theme.SettingsSection
import io.github.impca201.rssreader.ui.theme.SettingsFooter
import io.github.impca201.rssreader.ui.theme.SettingsScaffold
import io.github.impca201.rssreader.ui.theme.SwitchSetting
import io.github.impca201.rssreader.ui.theme.rememberAppVersion

data class SettingsState(
    val settings: Settings,
    val feedCount: Int,
    val folderCount: Int,
    /** Feeds whose last download failed. */
    val problemCount: Int,
    val shareApps: List<ExternalApps.ShareApp>,
    val syncStatus: SyncStatus = SyncStatus.Idle,
    val instapaper: InstapaperState,
)

class SettingsActions(
    val onBack: () -> Unit,
    val onOpenFeeds: () -> Unit,
    val onReaderStyleChange: (ReaderStyle) -> Unit,
    val onFullArticlesChange: (Boolean) -> Unit,
    val onShowImagesChange: (Boolean) -> Unit,
    val onMarkReadOnScrollChange: (Boolean) -> Unit,
    val onListViewChange: (ListView) -> Unit,
    val onSaveTargetChange: (ShareTarget) -> Unit,
    val onShareTargetChange: (ShareTarget) -> Unit,
    val onThemeModeChange: (ThemeMode) -> Unit,
    val onDynamicColorChange: (Boolean) -> Unit,
    val onConnectSync: () -> Unit,
    val onSyncNow: () -> Unit,
    val onDisconnectSync: () -> Unit,
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
        item(key = "feeds") { FeedsSection(state, actions.onOpenFeeds) }
        item(key = "sync") {
            SyncSection(
                service = state.settings.syncService,
                status = state.syncStatus,
                lastSyncedAt = state.settings.lastSyncedAt,
                onConnect = actions.onConnectSync,
                onSyncNow = actions.onSyncNow,
                onDisconnect = actions.onDisconnectSync,
            )
        }
        item(key = "list") { ListSection(state.settings, actions) }
        item(key = "reader") { ReaderSection(state.settings, actions) }
        item(key = "save") {
            ShareTargetsSection(
                saveTarget = state.settings.saveTarget,
                shareTarget = state.settings.shareTarget,
                apps = state.shareApps,
                onSaveTargetChange = actions.onSaveTargetChange,
                onShareTargetChange = actions.onShareTargetChange,
                instapaper = state.instapaper,
            )
        }
        item(key = "appearance") { AppearanceSection(state.settings, actions) }
        item(key = "footer") {
            SettingsFooter("${stringResource(R.string.app_name)} ${rememberAppVersion()}")
        }
    }
}

/** Opens the Feeds and folders page; shows how many there are, and failing feeds in red. */
@Composable
private fun FeedsSection(
    state: SettingsState,
    onOpen: () -> Unit,
) {
    SettingsSection(title = stringResource(R.string.section_feeds)) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.feeds_page_title)) },
            supportingContent = {
                Column {
                    Text(
                        listOf(
                            pluralStringResource(R.plurals.feeds_count, state.feedCount, state.feedCount),
                            pluralStringResource(R.plurals.folders_count, state.folderCount, state.folderCount),
                        ).joinToString(" · "),
                    )
                    if (state.problemCount > 0) {
                        Text(
                            pluralStringResource(R.plurals.feeds_problem_count, state.problemCount, state.problemCount),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            leadingContent = { Icon(painterResource(R.drawable.ic_rss_feed), contentDescription = null) },
            trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
            modifier = Modifier.clickable(role = Role.Button, onClick = onOpen),
        )
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
            summary = stringResource(R.string.setting_mark_read_on_scroll_summary),
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
        SettingsBlock {
        Text(
            text = stringResource(R.string.reader_preview),
            style = readerBodyStyle(style),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, AppShapes.Item)
                    .padding(16.dp),
        )
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
