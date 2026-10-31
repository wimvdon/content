package to.wim.content.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import to.wim.content.sync.SyncSettings
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** The user's settings, stored with DataStore. */
class SettingsStore(
    context: Context,
    scope: CoroutineScope,
) {
    private val dataStore = context.applicationContext.settingsDataStore

    /** `null` until read for the first time, so the first frame never shows the wrong theme. */
    val settings: StateFlow<Settings?> =
        dataStore.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .map(::read)
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[THEME_MODE] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) = edit { it[DYNAMIC_COLOR] = enabled }

    suspend fun setReaderStyle(style: ReaderStyle) =
        edit {
            it[READER_FONT] = style.font.name
            it[READER_TEXT_SIZE] = style.textSize
            it[READER_SPACING] = style.spacing.name
            it[READER_BACKGROUND] = style.background.name
            it[READER_AUTO_DARK] = style.autoDark
        }

    suspend fun setUnreadOnly(unreadOnly: Boolean) = edit { it[UNREAD_ONLY] = unreadOnly }

    suspend fun setFullArticles(enabled: Boolean) = edit { it[FULL_ARTICLES] = enabled }

    suspend fun setShowImages(enabled: Boolean) = edit { it[SHOW_IMAGES] = enabled }

    suspend fun setMarkReadOnScroll(enabled: Boolean) = edit { it[MARK_READ_ON_SCROLL] = enabled }

    suspend fun setAlwaysOpenWebsite(enabled: Boolean) = edit { it[ALWAYS_OPEN_WEBSITE] = enabled }

    suspend fun setListView(view: ListView) = edit { it[LIST_VIEW] = view.name }

    suspend fun setNotifyShared(enabled: Boolean) = edit { it[NOTIFY_SHARED] = enabled }

    /** What was imported from [service], or null to forget it (disconnected). */
    suspend fun setImport(
        service: String,
        record: ServiceImport?,
    ) = edit {
        val all = ServiceSettings.readImports(it[IMPORTS])
        it[IMPORTS] = ServiceSettings.writeImports(if (record == null) all - service else all + (service to record))
    }

    /** Counts one more pick of a share app in Content's share sheet ([ShareApps.idOf]). */
    suspend fun countShare(app: String) = edit { it[SHARE_COUNTS] = ShareApps.encode(ShareApps.count(ShareApps.decode(it[SHARE_COUNTS]), app)) }

    /** Remembers that [email] was just shared with (#235), first among the recipients. */
    suspend fun rememberRecipient(email: String) = edit { it[RECIPIENTS] = Recipients.encode(Recipients.remember(Recipients.decode(it[RECIPIENTS]).orEmpty(), email)) }

    /** Forgets [email] as a recipient: no longer offered, on every device (synced). */
    suspend fun forgetRecipient(email: String) = edit { it[RECIPIENTS] = Recipients.encode(Recipients.forget(Recipients.decode(it[RECIPIENTS]).orEmpty(), email)) }

    /** Takes the synced settings from another device (see SyncSettings); the rest stays as it is. */
    suspend fun applySynced(values: Map<String, String>) =
        edit { prefs ->
            val current = read(prefs)
            val s = SyncSettings.applyTo(current, values)
            if (s != current) {
                prefs[THEME_MODE] = s.appearance.themeMode.name
                prefs[DYNAMIC_COLOR] = s.appearance.dynamicColor
                prefs[READER_FONT] = s.reader.font.name
                prefs[READER_TEXT_SIZE] = s.reader.textSize
                prefs[READER_SPACING] = s.reader.spacing.name
                prefs[READER_BACKGROUND] = s.reader.background.name
                prefs[READER_AUTO_DARK] = s.reader.autoDark
                prefs[UNREAD_ONLY] = s.unreadOnly
                prefs[FULL_ARTICLES] = s.fullArticles
                prefs[SHOW_IMAGES] = s.showImages
                prefs[MARK_READ_ON_SCROLL] = s.markReadOnScroll
                prefs[LIST_VIEW] = s.listView.name
                prefs[IMPORTS] = ServiceSettings.writeImports(s.imports)
                prefs[RECIPIENTS] = Recipients.encode(s.recipients)
            }
        }

    /** Forgets when the last sync was, for a new sign-in. */
    suspend fun clearLastSyncedAt() = edit { it.remove(LAST_SYNCED_AT) }

    suspend fun setLastSyncedAt(time: Long) = edit { it[LAST_SYNCED_AT] = time }

    private suspend fun edit(change: (MutablePreferences) -> Unit) {
        dataStore.edit(change)
    }

    private fun read(p: Preferences): Settings {
        val defaults = Settings()
        return Settings(
            appearance =
                Appearance(
                    themeMode = enumOr(p[THEME_MODE], defaults.appearance.themeMode),
                    dynamicColor = p[DYNAMIC_COLOR] ?: defaults.appearance.dynamicColor,
                ),
            reader =
                ReaderStyle(
                    font = enumOr(p[READER_FONT], defaults.reader.font),
                    spacing = enumOr(p[READER_SPACING], defaults.reader.spacing),
                    background = enumOr(p[READER_BACKGROUND], defaults.reader.background),
                    autoDark = p[READER_AUTO_DARK] ?: defaults.reader.autoDark,
                ).withTextSize(p[READER_TEXT_SIZE] ?: defaults.reader.textSize),
            unreadOnly = p[UNREAD_ONLY] ?: defaults.unreadOnly,
            fullArticles = p[FULL_ARTICLES] ?: defaults.fullArticles,
            showImages = p[SHOW_IMAGES] ?: defaults.showImages,
            markReadOnScroll = p[MARK_READ_ON_SCROLL] ?: defaults.markReadOnScroll,
            alwaysOpenWebsite = p[ALWAYS_OPEN_WEBSITE] ?: defaults.alwaysOpenWebsite,
            listView = enumOr(p[LIST_VIEW], defaults.listView),
            imports = ServiceSettings.readImports(p[IMPORTS]),
            recipients = Recipients.decode(p[RECIPIENTS]).orEmpty(),
            shareCounts = ShareApps.decode(p[SHARE_COUNTS]),
            notifyShared = p[NOTIFY_SHARED] ?: defaults.notifyShared,
            lastSyncedAt = p[LAST_SYNCED_AT],
        )
    }

    private inline fun <reified T : Enum<T>> enumOr(
        name: String?,
        default: T,
    ): T = enumValues<T>().firstOrNull { it.name == name } ?: default

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val READER_FONT = stringPreferencesKey("reader_font")
        val READER_TEXT_SIZE = intPreferencesKey("reader_text_size")
        val READER_SPACING = stringPreferencesKey("reader_spacing")
        val READER_BACKGROUND = stringPreferencesKey("reader_background")
        val READER_AUTO_DARK = booleanPreferencesKey("reader_auto_dark")
        val UNREAD_ONLY = booleanPreferencesKey("unread_only")
        val FULL_ARTICLES = booleanPreferencesKey("full_articles")
        val SHOW_IMAGES = booleanPreferencesKey("show_images")
        val MARK_READ_ON_SCROLL = booleanPreferencesKey("mark_read_on_scroll")
        val ALWAYS_OPEN_WEBSITE = booleanPreferencesKey("always_open_website")
        val LIST_VIEW = stringPreferencesKey("list_view")
        val IMPORTS = stringPreferencesKey("service_imports")
        val RECIPIENTS = stringPreferencesKey("share_recipients")
        val SHARE_COUNTS = stringPreferencesKey("share_counts")
        val NOTIFY_SHARED = booleanPreferencesKey("notify_shared")
        val LAST_SYNCED_AT = longPreferencesKey("last_synced_at")
    }
}
