package io.github.impca201.rssreader.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

    suspend fun setSaveTarget(target: ShareTarget) = edit { it[SAVE_TARGET] = ShareTarget.encode(target) }

    suspend fun setShareTarget(target: ShareTarget) = edit { it[SHARE_TARGET] = ShareTarget.encode(target) }

    suspend fun setUnreadOnly(unreadOnly: Boolean) = edit { it[UNREAD_ONLY] = unreadOnly }

    suspend fun setFullArticles(enabled: Boolean) = edit { it[FULL_ARTICLES] = enabled }

    suspend fun setShowImages(enabled: Boolean) = edit { it[SHOW_IMAGES] = enabled }

    suspend fun setMarkReadOnScroll(enabled: Boolean) = edit { it[MARK_READ_ON_SCROLL] = enabled }

    suspend fun setListView(view: ListView) = edit { it[LIST_VIEW] = view.name }

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
            saveTarget = ShareTarget.decode(p[SAVE_TARGET], defaults.saveTarget),
            shareTarget = ShareTarget.decode(p[SHARE_TARGET], defaults.shareTarget),
            unreadOnly = p[UNREAD_ONLY] ?: defaults.unreadOnly,
            fullArticles = p[FULL_ARTICLES] ?: defaults.fullArticles,
            showImages = p[SHOW_IMAGES] ?: defaults.showImages,
            markReadOnScroll = p[MARK_READ_ON_SCROLL] ?: defaults.markReadOnScroll,
            listView = enumOr(p[LIST_VIEW], defaults.listView),
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
        val SAVE_TARGET = stringPreferencesKey("save_target")
        val SHARE_TARGET = stringPreferencesKey("share_target")
        val UNREAD_ONLY = booleanPreferencesKey("unread_only")
        val FULL_ARTICLES = booleanPreferencesKey("full_articles")
        val SHOW_IMAGES = booleanPreferencesKey("show_images")
        val MARK_READ_ON_SCROLL = booleanPreferencesKey("mark_read_on_scroll")
        val LIST_VIEW = stringPreferencesKey("list_view")
    }
}
