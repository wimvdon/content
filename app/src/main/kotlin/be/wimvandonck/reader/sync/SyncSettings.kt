package be.wimvandonck.reader.sync

import be.wimvandonck.reader.data.Settings

/**
 * The settings in the sync file: shared names with text values, the same on Android and in the
 * web app (docs/sync.md). Each app reads the names it knows and keeps the others as they are, so a
 * setting only one of them has survives a sync by the other.
 *
 * Only settings about what the app does are synced (Load full articles, Mark as read on scroll).
 * How it looks stays on each device (#96): the theme, dynamic colour, the reader's font, size,
 * spacing and page colour, Show images and the list view, since people often want a different
 * view on a phone, a tablet and a computer. Older versions synced those as [DROPPED_VIEW]; the
 * names are dropped from the file and ignored when another device still writes them.
 *
 * Also not synced: the Save and Share targets (each phone has its own apps), the Instapaper sign-in
 * (each device signs in itself; that sign-in is the account) and Unread | All (it is where you are
 * in the list, not a setting). Older versions synced the sign-in as instapaper.* and the filter as
 * [DROPPED_UNREAD_ONLY]; those names are dropped too.
 */
object SyncSettings {
    const val FULL_ARTICLES = "fullArticles"
    const val MARK_READ_ON_SCROLL = "markReadOnScroll"

    /** Unread | All, synced by older versions. */
    const val DROPPED_UNREAD_ONLY = "unreadOnly"

    /** View settings, synced by older versions; each device keeps its own now (#96). */
    val DROPPED_VIEW =
        setOf(
            "theme", "dynamicColor", "reader.font", "reader.textSize", "reader.spacing", "reader.background",
            "reader.autoDark", "showImages", "listView",
        )

    private val known = setOf(FULL_ARTICLES, MARK_READ_ON_SCROLL)

    private fun dropped(name: String) = name == DROPPED_UNREAD_ONLY || name in DROPPED_VIEW || name.startsWith("instapaper.")

    /** This device's synced settings, plus the names from [base] that this app doesn't know. */
    fun of(
        settings: Settings,
        base: Map<String, String> = emptyMap(),
    ): Map<String, String> {
        val mine =
            mapOf(
                FULL_ARTICLES to settings.fullArticles.toString(),
                MARK_READ_ON_SCROLL to settings.markReadOnScroll.toString(),
            )
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
        return settings.copy(
            fullArticles = bool(FULL_ARTICLES, settings.fullArticles),
            markReadOnScroll = bool(MARK_READ_ON_SCROLL, settings.markReadOnScroll),
        )
    }
}
