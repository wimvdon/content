package be.wimvandonck.reader.data


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

/** How Bookmarks shows its cards (#108): two columns of cards, or one list. */

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
 * Where the reader's Save or Share button sends an article: this app's own Saved list (Save only),
 * the share menu, or straight to one app.
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
        const val INSTAPAPER_PACKAGE = "com.instapaper.android"

        /** The Instapaper app, Save's default before the Saved screen worked without an account. */
        val Instapaper: ShareTarget = App(INSTAPAPER_PACKAGE, null, "Instapaper")

        private const val SEPARATOR = '\u001F'

        fun encode(target: ShareTarget): String =
            when (target) {
                Sheet -> "sheet"
                Saved -> "saved"
                is App -> listOf("app", target.packageName, target.activityName.orEmpty(), target.label).joinToString(SEPARATOR.toString())
            }

        /** Reads a stored value; also the values of the old Save setting ("Instapaper", "ShareMenu"). */
        fun decode(
            text: String?,
            default: ShareTarget,
        ): ShareTarget =
            when (text) {
                null -> default
                "sheet", "ShareMenu" -> Sheet
                "saved" -> Saved
                "Instapaper" -> Instapaper
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
    val saveTarget: ShareTarget = ShareTarget.Saved,
    val shareTarget: ShareTarget = ShareTarget.Sheet,
    val unreadOnly: Boolean = true,
    /** Load the whole article from its web page when the feed only has a summary. */
    val fullArticles: Boolean = true,
    /** Show article pictures, thumbnails and embed previews (they are downloaded from the sites). */
    val showImages: Boolean = true,
    /** Mark articles as read once they scroll off the top of the list. */
    val markReadOnScroll: Boolean = false,
    val listView: ListView = ListView.Medium,
    /** Tag an article saved from a feed with the feed's folder (#120). */
    val tagWithFolder: Boolean = true,
    /** What each service syncs (#145), by service id; a service without one syncs everything in Saved. */
    val scopes: Map<String, ServiceScope> = emptyMap(),
    /** What was imported from each service when it was connected (#145), by service id. */
    val imports: Map<String, ServiceImport> = emptyMap(),
    /** When the last sync succeeded, or null. */
    val lastSyncedAt: Long? = null,
)

/** What [service] syncs: its scope, everything in Saved until one is set (#145). */
fun Settings.scopeOf(service: String): ServiceScope = scopes[service] ?: ServiceScope.Everything
