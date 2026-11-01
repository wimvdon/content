package to.wim.content.data

/**
 * A list in sections, reordered by dragging rows (Manage feeds: the feeds under each tag's heading,
 * then No tag). The sections are flattened into one list of headings and rows, the way it is drawn:
 * a dragged row passes headings as it passes rows, so dropping it under another heading moves it
 * into that section. Nothing can go above the first heading. Plain Kotlin, unit tested.
 */
object SectionOrder {
    sealed interface Entry {
        val key: String
    }

    /** A section's heading ([group] is its id); it is never dragged itself. */
    data class Heading(
        val group: String,
    ) : Entry {
        override val key = "\u0000heading:$group"
    }

    data class Row(
        override val key: String,
    ) : Entry

    /** The sections, in order, as one list: each heading followed by its rows. */
    fun flatten(sections: List<Pair<String, List<String>>>): List<Entry> =
        sections.flatMap { (group, keys) -> listOf(Heading(group)) + keys.map { Row(it) } }

    /** Whether a row may be put at [index]: anywhere under the first heading. */
    fun canMoveTo(
        entries: List<Entry>,
        index: Int,
    ): Boolean = index in entries.indices && index > 0

    /** The section a row is in (the heading above it), or null when it isn't there. */
    fun sectionOf(
        entries: List<Entry>,
        key: String,
    ): String? {
        val at = entries.indexOfFirst { it is Row && it.key == key }
        if (at < 0) return null
        return (at downTo 0).firstNotNullOfOrNull { (entries[it] as? Heading)?.group }
    }

    /** The rows of one section, in order. */
    fun rowsOf(
        entries: List<Entry>,
        group: String,
    ): List<String> {
        val start = entries.indexOfFirst { it is Heading && it.group == group }
        if (start < 0) return emptyList()
        return entries.drop(start + 1).takeWhile { it is Row }.map { it.key }
    }

    /** What a drop means: the row stays in its section in a new order, or moves into another one. */
    sealed interface Drop {
        data class Reordered(
            val group: String,
            val keys: List<String>,
        ) : Drop

        data class Moved(
            val key: String,
            val group: String,
            val keys: List<String>,
        ) : Drop
    }

    /**
     * Row [key] dropped in [after] (the list as it was dropped), coming from section [from] whose
     * rows were [before]: moved into another section, reordered in its own, or null when nothing
     * changed.
     */
    fun dropped(
        after: List<Entry>,
        key: String,
        from: String,
        before: List<String>,
    ): Drop? {
        val group = sectionOf(after, key) ?: return null
        val keys = rowsOf(after, group)
        return when {
            group != from -> Drop.Moved(key, group, keys)
            keys != before -> Drop.Reordered(group, keys)
            else -> null
        }
    }

    /** [entries] with the entry at [from] moved to [to] (the same list when it can't go there). */
    fun moved(
        entries: List<Entry>,
        from: Int,
        to: Int,
    ): List<Entry> {
        if (from !in entries.indices || entries[from] !is Row || !canMoveTo(entries, to) || from == to) return entries
        return entries.toMutableList().apply { add(to, removeAt(from)) }
    }
}
