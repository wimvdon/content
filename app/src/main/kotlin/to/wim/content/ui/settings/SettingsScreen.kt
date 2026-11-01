package to.wim.content.ui.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.ui.res.pluralStringResource
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import to.wim.content.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedButton
import to.wim.content.ui.feeds.ImportGuides
import to.wim.content.ui.home.rememberFolderIconPainter
import to.wim.content.data.Feed
import to.wim.content.data.Folder
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import to.wim.content.data.ArticleFilter
import to.wim.content.data.ListView
import to.wim.content.data.ReaderStyle
import to.wim.content.data.Settings
import to.wim.content.data.ThemeMode
import to.wim.content.sync.AccountState
import to.wim.content.sync.SyncStatus
import to.wim.content.ui.reader.ReaderStyleControls
import to.wim.content.ui.reader.ReaderTheme
import to.wim.content.ui.reader.labelRes
import to.wim.content.ui.reader.readerBodyStyle
import to.wim.content.ui.theme.AppShapes
import to.wim.content.ui.theme.AppearanceSettings
import to.wim.content.ui.theme.SettingsBlock
import to.wim.content.ui.theme.SettingsSection
import to.wim.content.ui.theme.SettingItem
import to.wim.content.ui.theme.SettingsFooter
import to.wim.content.ui.theme.SettingsScaffold
import to.wim.content.ui.theme.SwitchSetting
import to.wim.content.ui.theme.rememberAppVersion

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
    /** Whether there are feeds to export. */
    val hasFeeds: Boolean = false,
    /** Instapaper (#215): a connected service, next to the Content account. Null while unknown. */
    val instapaper: InstapaperSettingsState? = null,
    /** The feeds and topics, for how many the Manage feeds and Manage topics buttons say. */
    val feeds: List<Feed> = emptyList(),
    val topics: List<Folder> = emptyList(),
)

/** Settings' pages: the main page lists them, each opens its own (Settings › Account and so on). */
enum class SettingsPage { Account, Integrations, Theme, List, Reader }

class SettingsActions(
    val onBack: () -> Unit,
    val onReaderStyleChange: (ReaderStyle) -> Unit,
    val onInstapaperSignIn: () -> Unit = {},
    val onInstapaperSync: () -> Unit = {},
    val onInstapaperSignOut: () -> Unit = {},
    val onInstapaperImport: (everything: Boolean) -> Unit = {},
    val onImportOpml: () -> Unit = {},
    val onExportOpml: () -> Unit = {},
    val onFullArticlesChange: (Boolean) -> Unit,
    val onAlwaysOpenWebsiteChange: (Boolean) -> Unit,
    val onShowImagesChange: (Boolean) -> Unit,
    val onMarkReadOnScrollChange: (Boolean) -> Unit,
    /** Hide articles with words (#277): the words as typed. */
    val onHiddenWordsChange: (String) -> Unit = {},
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
    /** Opens one of Settings' pages. */
    val onOpenPage: (SettingsPage) -> Unit = {},
    /** The two buttons at the top: the Manage feeds and Manage topics sheets. */
    val onManageFeeds: () -> Unit = {},
    val onManageTopics: () -> Unit = {},
)

/**
 * Settings: two large buttons at the top for what is managed most, Manage feeds and Manage topics
 * (sheets, so they open from other places too), then one row per page, each with its icon, what it
 * holds and a chevron (Account, Integrations, Theme, Article list, Article view), then the app's
 * version. Every page is a [SettingsPageScreen] with the same large top app bar.
 */
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
        item(key = "manage") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ManageButton(R.string.settings_feeds_title, pluralStringResource(R.plurals.feeds_count, state.feeds.size, state.feeds.size), "rss-feed", actions.onManageFeeds, Modifier.weight(1f))
                ManageButton(R.string.manage_title, pluralStringResource(R.plurals.topics_count, state.topics.size, state.topics.size), "label", actions.onManageTopics, Modifier.weight(1f))
            }
        }
        item(key = "pages") {
            Column {
                val email = (state.account as? AccountState.SignedIn)?.session?.email
                PageRow(SettingsPage.Account, email ?: stringResource(R.string.settings_account_signed_out), "account-circle", actions)
                PageRow(SettingsPage.Integrations, stringResource(R.string.settings_integrations_summary), "extension", actions)
                PageRow(SettingsPage.Theme, stringResource(state.settings.appearance.themeMode.labelRes()), "palette", actions)
                PageRow(SettingsPage.List, stringResource(R.string.settings_list_summary), "view-agenda", actions)
                PageRow(SettingsPage.Reader, stringResource(R.string.settings_article_view_summary), "chrome-reader-mode", actions)
            }
        }
        item(key = "footer") {
            SettingsFooter("${stringResource(R.string.app_name)} ${rememberAppVersion()}")
        }
    }
}

/** A page's title, for its top app bar and its row on the main page. */
@StringRes
fun SettingsPage.titleRes(): Int =
    when (this) {
        SettingsPage.Account -> R.string.section_account
        SettingsPage.Integrations -> R.string.section_integrations
        SettingsPage.Theme -> R.string.setting_theme_title
        SettingsPage.List -> R.string.section_list
        SettingsPage.Reader -> R.string.section_article_view
    }

/**
 * One of the two large buttons at the top of Settings: its icon on a circle, its name and how many
 * it holds, on `surfaceContainerHigh`, like the Add sheet's choices.
 */
@Composable
private fun ManageButton(
    @StringRes title: Int,
    detail: String,
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(onClick = onClick, shape = AppShapes.Item, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(rememberFolderIconPainter(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** One row of the main page: the page's icon (a Material Symbol), title, what it holds, and a chevron. */
@Composable
private fun PageRow(
    page: SettingsPage,
    summary: String,
    icon: String,
    actions: SettingsActions,
) {
    SettingItem(
        title = stringResource(page.titleRes()),
        summary = summary,
        onClick = { actions.onOpenPage(page) },
        icon = rememberFolderIconPainter(icon),
        trailing = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
    )
}

/** One of Settings' pages, with the same large top app bar and back button as Settings itself. */
@Composable
fun SettingsPageScreen(
    page: SettingsPage,
    state: SettingsState,
    snackbarHostState: SnackbarHostState,
    actions: SettingsActions,
) {
    SettingsScaffold(
        title = stringResource(page.titleRes()),
        backLabel = stringResource(R.string.action_back),
        onBack = actions.onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) {
        when (page) {
            SettingsPage.Account ->
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
            SettingsPage.Integrations -> {
                // Moving to or from another reader: the feeds in and out.
                item(key = "opml") {
                    SettingsSection(title = stringResource(R.string.feeds_opml_title)) {
                        SettingsBlock {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = actions.onImportOpml, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.feeds_import)) }
                                    OutlinedButton(onClick = actions.onExportOpml, enabled = state.hasFeeds, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.feeds_export)) }
                                }
                                Text(stringResource(R.string.feeds_opml_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ImportGuides()
                            }
                        }
                    }
                }
                // The service Saved is also kept in.
                state.instapaper?.let { instapaper ->
                    item(key = "instapaper") {
                        SettingsSection(title = stringResource(R.string.section_instapaper)) {
                            InstapaperSettings(instapaper, actions.onInstapaperSignIn, actions.onInstapaperSync, actions.onInstapaperSignOut, actions.onInstapaperImport)
                        }
                    }
                }
            }
            SettingsPage.Theme -> item(key = "appearance") { AppearanceRows(state.settings, actions) }
            SettingsPage.List -> item(key = "list") { ListRows(state.settings, actions) }
            SettingsPage.Reader -> {
                item(key = "reader") { ReaderRows(state.settings, actions) }
                // Sites you pay for belong to reading: they decide which articles load in full (#180).
                item(key = "sites") { SiteSignInsSection(state.siteSignIns, actions) }
            }
        }
    }
}

/** The sites signed in to (#180), each with Remove. */
@Composable
private fun SiteSignInsSection(
    sites: List<String>,
    actions: SettingsActions,
) {
    SettingsSection(title = stringResource(R.string.site_sign_ins_title)) {
        for (site in sites) {
            ListItem(
                headlineContent = { Text(site) },
                trailingContent = { TextButton(onClick = { actions.onRemoveSiteSignIn(site) }) { Text(stringResource(R.string.site_sign_in_remove)) } },
            )
        }
        SettingsBlock {
            Text(
                stringResource(if (sites.isEmpty()) R.string.site_sign_ins_none else R.string.site_sign_ins_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Settings › Article list: how the list looks and behaves. */
@Composable
private fun ListRows(
    settings: Settings,
    actions: SettingsActions,
) {
    Column {
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
        HiddenWordsSetting(settings.hiddenWords, actions.onHiddenWordsChange)
    }
}

/** Hide articles with words (#277): the words in the summary; a dialog to change them. */
@Composable
private fun HiddenWordsSetting(
    text: String,
    onChange: (String) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val words = remember(text) { ArticleFilter.words(text) }
    SettingItem(
        title = stringResource(R.string.setting_hidden_words_title),
        summary = if (words.isEmpty()) stringResource(R.string.setting_hidden_words_none) else words.joinToString(", "),
        onClick = { editing = true },
    )
    if (editing) {
        var draft by rememberSaveable { mutableStateOf(text) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.setting_hidden_words_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text(stringResource(R.string.setting_hidden_words_label)) },
                        minLines = 3,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.setting_hidden_words_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onChange(draft.trim())
                    editing = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.action_cancel)) } },
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

/** Settings › Article view: how articles open, then their text (the reader's Text sheet) with a preview. */
@Composable
private fun ReaderRows(
    settings: Settings,
    actions: SettingsActions,
) {
    val style = settings.reader
    Column {
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
    }
    SettingsSection(title = stringResource(R.string.reader_text_settings)) {
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

/** Settings › Theme: System, Light or Dark, and Dynamic color. */
@Composable
private fun AppearanceRows(
    settings: Settings,
    actions: SettingsActions,
) {
    Column {
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
