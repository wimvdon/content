package be.wimvandonck.content.sync

import be.wimvandonck.content.data.Recipients
import be.wimvandonck.content.data.ServiceImport
import be.wimvandonck.content.data.Settings

/**
 * The settings in the sync file: shared names with text values, the same on Android and in the
 * web app (docs/sync.md). Each app reads the names it knows and keeps the others as they are, so a
 * setting only one of them has survives a sync by the other.
 *
 * Only settings about what the app does are synced (Load full articles, Mark as read on scroll, and
 * what was imported from each connected service, #145: "services.<id>.imported"), and the people
 * shared with (#235: [RECIPIENTS]).
 * How it looks stays on each device (#96): the theme, dynamic colour, the reader's font, size,
 * spacing and page colour, Show images and the list view, since people often want a different
 * view on a phone, a tablet and a computer.
 *
 * Also not synced: the Save and Share targets (each phone has its own apps), the Instapaper sign-in
 * (each device signs in itself) and Unread | All (it is where you are in the list, not a setting).
 */
object SyncSettings {
    const val FULL_ARTICLES = "fullArticles"
    const val MARK_READ_ON_SCROLL = "markReadOnScroll"

    /** The people shared with (#235), a JSON list of addresses, most recent first. */
    const val RECIPIENTS = "sharing.recipients"

    /** The services whose import record travels with the account (#145). */
    val SERVICES = listOf("instapaper")

    fun importName(service: String) = "services.$service.imported"

    private val known = setOf(FULL_ARTICLES, MARK_READ_ON_SCROLL, RECIPIENTS)

    /** Names earlier versions wrote; dropped from the file when this device saves it. */
    private fun dropped(name: String) = name.endsWith(".scope") || name.startsWith("services.raindrop.")

    /** This device's synced settings, plus the names from [base] that this app doesn't know. */
    fun of(
        settings: Settings,
        base: Map<String, String> = emptyMap(),
    ): Map<String, String> {
        val mine =
            mapOf(
                FULL_ARTICLES to settings.fullArticles.toString(),
                MARK_READ_ON_SCROLL to settings.markReadOnScroll.toString(),
                RECIPIENTS to Recipients.encode(settings.recipients),
            ) +
                // An import this device doesn't know of stays as the file has it.
                settings.imports.entries.filter { it.key in SERVICES }.associate { importName(it.key) to ServiceImport.encode(it.value) }
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
        val imports = SERVICES.mapNotNull { id -> ServiceImport.decode(values[importName(id)])?.let { id to it } }.toMap()
        return settings.copy(
            fullArticles = bool(FULL_ARTICLES, settings.fullArticles),
            markReadOnScroll = bool(MARK_READ_ON_SCROLL, settings.markReadOnScroll),
            imports = settings.imports + imports,
            recipients = Recipients.decode(values[RECIPIENTS]) ?: settings.recipients,
        )
    }
}
