package io.github.impca201.rssreader.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.impca201.rssreader.R
import io.github.impca201.rssreader.data.Feed
import io.github.impca201.rssreader.data.FeedProblem
import io.github.impca201.rssreader.data.ReaderStyle
import io.github.impca201.rssreader.data.SaveTarget
import io.github.impca201.rssreader.data.Settings
import io.github.impca201.rssreader.data.ThemeMode
import io.github.impca201.rssreader.ui.home.problemText
import io.github.impca201.rssreader.ui.reader.ReaderStyleControls
import io.github.impca201.rssreader.ui.reader.labelRes
import io.github.impca201.rssreader.ui.reader.readerBodyStyle
import io.github.impca201.rssreader.ui.theme.AppShapes
import io.github.impca201.rssreader.ui.theme.AppearanceSettings
import io.github.impca201.rssreader.ui.theme.CollapsibleSection
import io.github.impca201.rssreader.ui.theme.SettingsFooter
import io.github.impca201.rssreader.ui.theme.SettingsScaffold
import io.github.impca201.rssreader.ui.theme.SwitchSetting
import io.github.impca201.rssreader.ui.theme.rememberAppVersion

data class SettingsState(
    val settings: Settings,
    val feeds: List<Feed>,
    val problems: Map<String, FeedProblem>,
    val instapaperInstalled: Boolean,
)

class SettingsActions(
    val onBack: () -> Unit,
    val onAddFeed: () -> Unit,
    val onRemoveFeed: (Feed) -> Unit,
    val onImportOpml: () -> Unit,
    val onImportInoreader: () -> Unit,
    val onExportOpml: () -> Unit,
    val onReaderStyleChange: (ReaderStyle) -> Unit,
    val onFullArticlesChange: (Boolean) -> Unit,
    val onShowImagesChange: (Boolean) -> Unit,
    val onSaveTargetChange: (SaveTarget) -> Unit,
    val onGetInstapaper: () -> Unit,
    val onThemeModeChange: (ThemeMode) -> Unit,
    val onDynamicColorChange: (Boolean) -> Unit,
)

/** Everything that can be set, in collapsible sections in the order DESIGN.md gives. */
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
        item(key = "feeds") { FeedsSection(state, actions) }
        item(key = "reader") { ReaderSection(state.settings, actions) }
        item(key = "save") { SaveSection(state, actions) }
        item(key = "appearance") { AppearanceSection(state.settings, actions) }
        item(key = "footer") {
            SettingsFooter("${stringResource(R.string.app_name)} ${rememberAppVersion()}")
        }
    }
}

@Composable
private fun FeedsSection(
    state: SettingsState,
    actions: SettingsActions,
) {
    var confirmRemove by rememberSaveable { mutableStateOf<String?>(null) }
    CollapsibleSection(
        key = "feeds",
        title = stringResource(R.string.feeds_title),
        summary = pluralStringResource(R.plurals.feeds_count, state.feeds.size, state.feeds.size),
        icon = painterResource(R.drawable.ic_rss_feed),
        // The one thing the user still has to do before the app is useful.
        initiallyExpanded = state.feeds.isEmpty(),
    ) {
        if (state.feeds.isEmpty()) {
            SectionText(stringResource(R.string.home_no_feeds_body))
        }
        state.feeds.forEach { feed ->
            FeedRow(feed, state.problems[feed.url], onRemove = { confirmRemove = feed.url })
        }
        FilledTonalButton(onClick = actions.onAddFeed, modifier = Modifier.fillMaxWidth()) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null)
            Text(stringResource(R.string.feeds_add), modifier = Modifier.padding(start = 8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = actions.onImportOpml, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.feeds_import))
            }
            OutlinedButton(onClick = actions.onExportOpml, enabled = state.feeds.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.feeds_export))
            }
        }
        OutlinedButton(onClick = actions.onImportInoreader, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.inoreader_title))
        }
        SectionText(stringResource(R.string.feeds_opml_hint))
    }

    val feed = confirmRemove?.let { url -> state.feeds.firstOrNull { it.url == url } }
    if (feed != null) {
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text(stringResource(R.string.feeds_remove_title)) },
            text = { Text(stringResource(R.string.feeds_remove_body, feed.title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = null
                    actions.onRemoveFeed(feed)
                }) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun FeedRow(
    feed: Feed,
    problem: FeedProblem?,
    onRemove: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer, AppShapes.Item)
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(feed.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = problem?.let { problemText(it) } ?: feed.url,
                style = MaterialTheme.typography.bodySmall,
                color = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.feeds_remove, feed.title))
        }
    }
}

@Composable
private fun ReaderSection(
    settings: Settings,
    actions: SettingsActions,
) {
    val style = settings.reader
    CollapsibleSection(
        key = "reader",
        title = stringResource(R.string.section_reader),
        summary =
            listOf(
                stringResource(style.font.labelRes()),
                stringResource(R.string.reader_text_size_value, style.textSize),
                stringResource(style.background.labelRes()),
            ).joinToString(" · "),
        icon = painterResource(R.drawable.ic_text_format),
    ) {
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

@Composable
private fun SaveSection(
    state: SettingsState,
    actions: SettingsActions,
) {
    val target = state.settings.saveTarget
    CollapsibleSection(
        key = "save",
        title = stringResource(R.string.section_save),
        summary = stringResource(target.labelRes()),
        icon = painterResource(R.drawable.ic_bookmark_add),
    ) {
        SectionText(stringResource(R.string.save_explanation))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SaveTarget.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == target,
                    onClick = { actions.onSaveTargetChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, SaveTarget.entries.size),
                ) { Text(stringResource(option.labelRes())) }
            }
        }
        if (target == SaveTarget.Instapaper && !state.instapaperInstalled) {
            Text(
                text = stringResource(R.string.save_instapaper_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            FilledTonalButton(onClick = actions.onGetInstapaper, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null)
                Text(stringResource(R.string.save_get_instapaper), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun AppearanceSection(
    settings: Settings,
    actions: SettingsActions,
) {
    CollapsibleSection(
        key = "appearance",
        title = stringResource(R.string.section_appearance),
        summary = stringResource(settings.appearance.themeMode.labelRes()),
        icon = painterResource(R.drawable.ic_palette),
    ) {
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

@Composable
private fun SectionText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@StringRes
private fun ThemeMode.labelRes(): Int =
    when (this) {
        ThemeMode.System -> R.string.theme_system
        ThemeMode.Light -> R.string.theme_light
        ThemeMode.Dark -> R.string.theme_dark
    }

@StringRes
private fun SaveTarget.labelRes(): Int =
    when (this) {
        SaveTarget.Instapaper -> R.string.save_target_instapaper
        SaveTarget.ShareMenu -> R.string.save_target_share
    }
