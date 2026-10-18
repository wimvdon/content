package be.wimvandonck.reader.sync

import be.wimvandonck.reader.data.Settings

/**
 * The settings in the sync file: shared names with text values, the same on Android and in the
 * web app (docs/sync.md). Each app reads the names it knows and keeps the others as they are, so a
 * setting only one of them has (Show images, list view) survives a sync by the other.
 *
 * Not synced: the Save and Share targets (each phone has its own apps), the Instapaper sign-in
 * (each device signs in itself; that sign-in is the account) and Unread | All (it is where you are
 * in the list, not a setting). Older versions synced the sign-in as instapaper.* and the filter as
 * [DROPPED_UNREAD_ONLY]; those names are dropped.
 */
object SyncSettings {
    const val THEME = "theme"
    const val DYNAMIC_COLOR = "dynamicColor"
    const val READER_FONT = "reader.font"
    const val READER_TEXT_SIZE = "reader.textSize"
    const val READER_SPACING = "reader.spacing"
    const val READER_BACKGROUND = "reader.background"
    const val READER_AUTO_DARK = "reader.autoDark"
    const val FULL_ARTICLES = "fullArticles"
    const val SHOW_IMAGES = "showImages"
    const val MARK_READ_ON_SCROLL = "markReadOnScroll"
    const val LIST_VIEW = "listView"

    /** Unread | All, synced by older versions. */
    const val DROPPED_UNREAD_ONLY = "unreadOnly"

    private val known =
        setOf(
            THEME, DYNAMIC_COLOR, READER_FONT, READER_TEXT_SIZE, READER_SPACING, READER_BACKGROUND, READER_AUTO_DARK,
            FULL_ARTICLES, SHOW_IMAGES, MARK_READ_ON_SCROLL, LIST_VIEW,
        )

    /** This device's synced settings, plus the names from [base] that this app doesn't know. */
    fun of(
        settings: Settings,
        base: Map<String, String> = emptyMap(),
    ): Map<String, String> {
        val r = settings.reader
        val mine =
            buildMap {
                put(THEME, settings.appearance.themeMode.text())
                put(DYNAMIC_COLOR, settings.appearance.dynamicColor.toString())
                put(READER_FONT, r.font.text())
                put(READER_TEXT_SIZE, r.textSize.toString())
                put(READER_SPACING, r.spacing.text())
                put(READER_BACKGROUND, r.background.text())
                put(READER_AUTO_DARK, r.autoDark.toString())
                put(FULL_ARTICLES, settings.fullArticles.toString())
                put(SHOW_IMAGES, settings.showImages.toString())
                put(MARK_READ_ON_SCROLL, settings.markReadOnScroll.toString())
                put(LIST_VIEW, settings.listView.text())
            }
        return base.filterKeys { it !in known && it != DROPPED_UNREAD_ONLY && !it.startsWith("instapaper.") } + mine
    }

    /** [settings] with the synced [values] it understands; missing or unknown values change nothing. */
    fun applyTo(
        settings: Settings,
        values: Map<String, String>,
    ): Settings {
        fun bool(
            key: String,
            current: Boolean,
        ) = values[key]?.toBooleanStrictOrNull() ?: current
        val r = settings.reader
        return settings.copy(
            appearance =
                settings.appearance.copy(
                    themeMode = enumFrom(values[THEME], settings.appearance.themeMode),
                    dynamicColor = bool(DYNAMIC_COLOR, settings.appearance.dynamicColor),
                ),
            reader =
                r
                    .copy(
                        font = enumFrom(values[READER_FONT], r.font),
                        spacing = enumFrom(values[READER_SPACING], r.spacing),
                        background = enumFrom(values[READER_BACKGROUND], r.background),
                        autoDark = bool(READER_AUTO_DARK, r.autoDark),
                    ).withTextSize(values[READER_TEXT_SIZE]?.toIntOrNull() ?: r.textSize),
            fullArticles = bool(FULL_ARTICLES, settings.fullArticles),
            showImages = bool(SHOW_IMAGES, settings.showImages),
            markReadOnScroll = bool(MARK_READ_ON_SCROLL, settings.markReadOnScroll),
            listView = enumFrom(values[LIST_VIEW], settings.listView),
        )
    }

    private fun Enum<*>.text() = name.lowercase()

    private inline fun <reified T : Enum<T>> enumFrom(
        text: String?,
        current: T,
    ): T = enumValues<T>().firstOrNull { it.name.lowercase() == text } ?: current
}
