package to.wim.content.sync

import to.wim.content.data.Appearance
import to.wim.content.data.ListView
import to.wim.content.data.ReaderBackground
import to.wim.content.data.ReaderFont
import to.wim.content.data.ReaderSpacing
import to.wim.content.data.ReaderStyle
import to.wim.content.data.Recipients
import to.wim.content.data.ServiceImport
import to.wim.content.data.Settings
import to.wim.content.data.ThemeMode

/**
 * The settings in the sync file: names with text values (docs/sync.md). Each app reads the names it
 * knows and keeps the others as they are, so the web app's settings survive a sync by this app.
 *
 * The Android app's own settings go under "android." (the web app's under "web."), so they are the
 * same on every Android device and the web app's are the same in every browser, but the two never
 * mix (the owner's choice): a phone and a computer often want something different. That is how it
 * looks (theme, dynamic colour, the reader's font, size, spacing and page colour, Show images, the
 * list view) and what it does (Load full articles, Mark as read on scroll, Always open the website).
 *
 * Shared by both apps: what was imported from each connected service (#145:
 * "services.<id>.imported"), the people shared with (#235: [RECIPIENTS]) and the Instapaper
 * sign-in ([INSTAPAPER_TOKEN]: connect once, on every device).
 *
 * Not synced: the Save and Share targets and the share sheet's counts (each phone has its own
 * apps), notifications (each phone registers itself) and Unread | All (it is where you are in the
 * list, not a setting).
 */
object SyncSettings {
    /** The prefix of the Android app's own settings. */
    const val PREFIX = "android."

    const val THEME = PREFIX + "theme"
    const val DYNAMIC_COLOR = PREFIX + "dynamicColor"
    const val READER_FONT = PREFIX + "reader.font"
    const val READER_TEXT_SIZE = PREFIX + "reader.textSize"
    const val READER_SPACING = PREFIX + "reader.spacing"
    const val READER_BACKGROUND = PREFIX + "reader.background"
    const val READER_AUTO_DARK = PREFIX + "reader.autoDark"
    const val FULL_ARTICLES = PREFIX + "fullArticles"
    const val SHOW_IMAGES = PREFIX + "showImages"
    const val MARK_READ_ON_SCROLL = PREFIX + "markReadOnScroll"
    const val ALWAYS_OPEN_WEBSITE = PREFIX + "alwaysOpenWebsite"
    const val LIST_VIEW = PREFIX + "listView"

    /** Load full articles and Mark as read on scroll were shared by both apps before; read once, never written. */
    const val LEGACY_FULL_ARTICLES = "fullArticles"
    const val LEGACY_MARK_READ_ON_SCROLL = "markReadOnScroll"

    /** The people shared with (#235), a JSON list of addresses, most recent first. */
    const val RECIPIENTS = "sharing.recipients"

    /** The Instapaper sign-in (its token), shared by every device of the account; empty: signed out. */
    const val INSTAPAPER_TOKEN = "services.instapaper.token"

    /** The services whose import record travels with the account (#145). */
    val SERVICES = listOf("instapaper")

    fun importName(service: String) = "services.$service.imported"

    private val known =
        setOf(
            THEME, DYNAMIC_COLOR, READER_FONT, READER_TEXT_SIZE, READER_SPACING, READER_BACKGROUND, READER_AUTO_DARK,
            FULL_ARTICLES, SHOW_IMAGES, MARK_READ_ON_SCROLL, ALWAYS_OPEN_WEBSITE, LIST_VIEW, RECIPIENTS,
        )

    /** Names earlier versions wrote; dropped from the file when this device saves it. */
    private fun dropped(name: String) =
        name.endsWith(".scope") || name.startsWith("services.raindrop.") || name == LEGACY_FULL_ARTICLES || name == LEGACY_MARK_READ_ON_SCROLL

    /**
     * This device's synced settings, plus the names from [base] that this app doesn't know.
     * [instapaperToken] is this device's Instapaper sign-in: null leaves the file's as it is.
     */
    fun of(
        settings: Settings,
        base: Map<String, String> = emptyMap(),
        instapaperToken: String? = null,
    ): Map<String, String> {
        val reader = settings.reader
        val mine =
            mapOf(
                THEME to settings.appearance.themeMode.name,
                DYNAMIC_COLOR to settings.appearance.dynamicColor.toString(),
                READER_FONT to reader.font.name,
                READER_TEXT_SIZE to reader.textSize.toString(),
                READER_SPACING to reader.spacing.name,
                READER_BACKGROUND to reader.background.name,
                READER_AUTO_DARK to reader.autoDark.toString(),
                FULL_ARTICLES to settings.fullArticles.toString(),
                SHOW_IMAGES to settings.showImages.toString(),
                MARK_READ_ON_SCROLL to settings.markReadOnScroll.toString(),
                ALWAYS_OPEN_WEBSITE to settings.alwaysOpenWebsite.toString(),
                LIST_VIEW to settings.listView.name,
                RECIPIENTS to Recipients.encode(settings.recipients),
            ) +
                // An import this device doesn't know of stays as the file has it.
                settings.imports.entries.filter { it.key in SERVICES }.associate { importName(it.key) to ServiceImport.encode(it.value) } +
                listOfNotNull(instapaperToken?.let { INSTAPAPER_TOKEN to it })
        return base.filterKeys { it !in known && !dropped(it) } + mine
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

        fun <E : Enum<E>> enum(
            key: String,
            entries: List<E>,
            current: E,
        ) = entries.firstOrNull { it.name == values[key] } ?: current
        val reader = settings.reader
        val size = values[READER_TEXT_SIZE]?.toIntOrNull()?.takeIf { it in ReaderStyle.MIN_TEXT_SIZE..ReaderStyle.MAX_TEXT_SIZE } ?: reader.textSize
        val imports = SERVICES.mapNotNull { id -> ServiceImport.decode(values[importName(id)])?.let { id to it } }.toMap()
        return settings.copy(
            appearance =
                Appearance(
                    themeMode = enum(THEME, ThemeMode.entries, settings.appearance.themeMode),
                    dynamicColor = bool(DYNAMIC_COLOR, settings.appearance.dynamicColor),
                ),
            reader =
                ReaderStyle(
                    font = enum(READER_FONT, ReaderFont.entries, reader.font),
                    textSize = size,
                    spacing = enum(READER_SPACING, ReaderSpacing.entries, reader.spacing),
                    background = enum(READER_BACKGROUND, ReaderBackground.entries, reader.background),
                    autoDark = bool(READER_AUTO_DARK, reader.autoDark),
                ),
            fullArticles = bool(FULL_ARTICLES, bool(LEGACY_FULL_ARTICLES, settings.fullArticles)),
            showImages = bool(SHOW_IMAGES, settings.showImages),
            markReadOnScroll = bool(MARK_READ_ON_SCROLL, bool(LEGACY_MARK_READ_ON_SCROLL, settings.markReadOnScroll)),
            alwaysOpenWebsite = bool(ALWAYS_OPEN_WEBSITE, settings.alwaysOpenWebsite),
            listView = enum(LIST_VIEW, ListView.entries, settings.listView),
            imports = settings.imports + imports,
            recipients = Recipients.decode(values[RECIPIENTS]) ?: settings.recipients,
        )
    }

    /** The Instapaper sign-in in [values]: a token, "" when signed out on another device, or null when the file has none. */
    fun instapaperToken(values: Map<String, String>): String? = values[INSTAPAPER_TOKEN]
}
