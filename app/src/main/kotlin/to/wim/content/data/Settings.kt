package to.wim.content.data


enum class ThemeMode { System, Light, Dark }

data class Appearance(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,
) {
    fun isDark(systemDark: Boolean): Boolean =
        when (themeMode) {
            ThemeMode.System -> systemDark
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
        }
}

/** How big each article is in the list: title only, with a thumbnail, or a card with a large picture. */
enum class ListView { Small, Medium, Large }

enum class ReaderFont { Sans, Serif }

/** Line height as a multiple of the text size. */
enum class ReaderSpacing(val lineHeight: Float) {
    Compact(1.3f),
    Normal(1.5f),
    Relaxed(1.75f),
}

/** The reader's page colour: the app's theme, sepia paper, or dark whatever the theme. */
enum class ReaderBackground { Default, Sepia, Dark }

/** How articles look on the reader screen. */
data class ReaderStyle(
    val font: ReaderFont = ReaderFont.Serif,
    val textSize: Int = DEFAULT_TEXT_SIZE,
    val spacing: ReaderSpacing = ReaderSpacing.Normal,
    val background: ReaderBackground = ReaderBackground.Default,
    /** Use the dark page whenever the app is dark, whatever [background] says (e.g. sepia by day). */
    val autoDark: Boolean = false,
) {
    init {
        require(textSize in MIN_TEXT_SIZE..MAX_TEXT_SIZE) { "textSize out of range: $textSize" }
    }

    fun withTextSize(size: Int): ReaderStyle = copy(textSize = size.coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE))

    fun larger(): ReaderStyle = withTextSize(textSize + 1)

    fun smaller(): ReaderStyle = withTextSize(textSize - 1)

    /** The page colour actually shown, given whether the app's theme is dark. */
    fun pageBackground(appDark: Boolean): ReaderBackground = if (autoDark && appDark) ReaderBackground.Dark else background

    /** True when the page is dark, given whether the app's theme is dark. */
    fun isDark(appDark: Boolean): Boolean =
        when (pageBackground(appDark)) {
            ReaderBackground.Default -> appDark
            ReaderBackground.Sepia -> false
            ReaderBackground.Dark -> true
        }

    companion object {
        /** In sp. */
        const val MIN_TEXT_SIZE = 14
        const val MAX_TEXT_SIZE = 28
        const val DEFAULT_TEXT_SIZE = 18
    }
}

/**
 * Where an article is shared to: the share menu, or straight to one app (Content's share sheet,
 * #251). Save and Share went to one of these by a setting until Save and share (#256); stored
 * values still read.
 */
sealed interface ShareTarget {
    data object Sheet : ShareTarget

    /** Saved in this app (the Saved screen), also without an Instapaper account. Save's default. */
    data object Saved : ShareTarget

    /** A share target of another app; [activityName] picks one when the app has several. */
    data class App(
        val packageName: String,
        val activityName: String?,
        val label: String,
    ) : ShareTarget

    companion object {
        private const val SEPARATOR = '\u001F'

        fun encode(target: ShareTarget): String =
            when (target) {
                Sheet -> "sheet"
                Saved -> "saved"
                is App -> listOf("app", target.packageName, target.activityName.orEmpty(), target.label).joinToString(SEPARATOR.toString())
            }

        /** Reads a stored value. */
        fun decode(
            text: String?,
            default: ShareTarget,
        ): ShareTarget =
            when (text) {
                null -> default
                "sheet" -> Sheet
                "saved" -> Saved
                else -> {
                    val parts = text.split(SEPARATOR)
                    if (parts.size == 4 && parts[0] == "app" && parts[1].isNotEmpty()) {
                        App(parts[1], parts[2].ifEmpty { null }, parts[3].ifEmpty { parts[1] })
                    } else {
                        default
                    }
                }
            }
    }
}

/** Everything the user can set, as stored. */
data class Settings(
    val appearance: Appearance = Appearance(),
    val reader: ReaderStyle = ReaderStyle(),
    val unreadOnly: Boolean = true,
    /** Load the whole article from its web page when the feed only has a summary. */
    val fullArticles: Boolean = true,
    /** Show article pictures, thumbnails and embed previews (they are downloaded from the sites). */
    val showImages: Boolean = true,
    /** Mark articles as read once they scroll off the top of the list. */
    val markReadOnScroll: Boolean = false,
    /** Open feed articles on their website instead of in the reader; on this phone only, off by default. */
    val alwaysOpenWebsite: Boolean = false,
    val listView: ListView = ListView.Medium,
    /** What was imported from each service when it was connected (#145), by service id. */
    val imports: Map<String, ServiceImport> = emptyMap(),
    /** Notifications when something is shared (#235); on this phone only, off until turned on. */
    val notifyShared: Boolean = false,
    /** The people shared with (#235), most recent first; synced (see Recipients). */
    val recipients: List<String> = emptyList(),
    /** How often each app was picked in Content's share sheet, by [ShareApps.idOf]; on this device only. */
    val shareCounts: Map<String, Int> = emptyMap(),
    /** When the last sync succeeded, or null. */
    val lastSyncedAt: Long? = null,
)

/**
 * Where a feed article opens: on its website with [Settings.alwaysOpenWebsite], else in the reader.
 * Saved articles always open in the reader, which keeps their place, highlights and notes.
 */
object ArticleOpening {
    /** The address to open instead of the reader, or null for the reader (setting off, or no web link). */
    fun websiteFor(
        alwaysOpenWebsite: Boolean,
        article: Article,
    ): String? = article.link?.trim()?.takeIf { alwaysOpenWebsite && ItemKeys.isWebLink(it) }
}
