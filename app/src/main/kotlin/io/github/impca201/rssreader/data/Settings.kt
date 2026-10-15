package io.github.impca201.rssreader.data

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
) {
    init {
        require(textSize in MIN_TEXT_SIZE..MAX_TEXT_SIZE) { "textSize out of range: $textSize" }
    }

    fun withTextSize(size: Int): ReaderStyle = copy(textSize = size.coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE))

    fun larger(): ReaderStyle = withTextSize(textSize + 1)

    fun smaller(): ReaderStyle = withTextSize(textSize - 1)

    /** True when the page is dark, given whether the app's theme is dark. */
    fun isDark(appDark: Boolean): Boolean =
        when (background) {
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

/** Where the reader's Save button sends an article. */
enum class SaveTarget { Instapaper, ShareMenu }

/** Everything the user can set, as stored. */
data class Settings(
    val appearance: Appearance = Appearance(),
    val reader: ReaderStyle = ReaderStyle(),
    val saveTarget: SaveTarget = SaveTarget.Instapaper,
    val unreadOnly: Boolean = true,
    /** Load the whole article from its web page when the feed only has a summary. */
    val fullArticles: Boolean = true,
    /** Show article pictures, thumbnails and embed previews (they are downloaded from the sites). */
    val showImages: Boolean = true,
)
