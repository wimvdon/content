package be.wimvandonck.reader.data

/** A highlight's place in one paragraph of the reader, as it is drawn there. */
data class Mark(
    val highlightId: String,
    val start: Int,
    val end: Int,
    /** Found roughly: the text differs from the one it was made on. */
    val approximate: Boolean,
)

/**
 * The reader's text as its paragraphs (#102): one text, the paragraphs joined by a blank line,
 * which highlights are anchored in, and back to each paragraph to draw them. Plain Kotlin, unit
 * tested.
 */
class ArticleText(
    /** Each paragraph's text as the reader shows it. */
    val paragraphs: List<String>,
) {
    val text: String = paragraphs.joinToString(SEPARATOR)

    private val starts: IntArray =
        IntArray(paragraphs.size).also { a ->
            var at = 0
            paragraphs.forEachIndexed { i, p ->
                a[i] = at
                at += p.length + SEPARATOR.length
            }
        }

    /**
     * Where [selected] is in paragraph [index], in the whole text: its first occurrence there, without
     * spaces at either end. Null when it isn't there.
     */
    fun locate(
        index: Int,
        selected: String,
    ): IntRange? {
        val paragraph = paragraphs.getOrNull(index) ?: return null
        val quote = selected.trim()
        if (quote.isEmpty()) return null
        val at = paragraph.indexOf(quote).takeIf { it >= 0 } ?: return null
        return (starts[index] + at) until (starts[index] + at + quote.length)
    }

    /** Each paragraph's marks for [highlights], and where each highlight was found. */
    fun place(highlights: List<Highlight>): Placed {
        val anchors = highlights.associate { it.id to Anchors.resolve(it, text) }
        val marks = HashMap<Int, MutableList<Mark>>()
        for ((id, anchor) in anchors) {
            val (start, end, approximate) =
                when (anchor) {
                    is Anchor.Exact -> Triple(anchor.start, anchor.end, false)
                    is Anchor.Approximate -> Triple(anchor.start, anchor.end, true)
                    Anchor.NotFound -> continue
                }
            for (i in paragraphs.indices) {
                val pStart = starts[i]
                val pEnd = pStart + paragraphs[i].length
                val s = maxOf(start, pStart)
                val e = minOf(end, pEnd)
                if (s < e) marks.getOrPut(i) { ArrayList() } += Mark(id, s - pStart, e - pStart, approximate)
            }
        }
        return Placed(marks.mapValues { (_, list) -> list.sortedBy { it.start } }, anchors)
    }

    /** Highlights placed: the marks per paragraph, and each highlight's anchor (by id). */
    data class Placed(
        val marks: Map<Int, List<Mark>>,
        val anchors: Map<String, Anchor>,
    )

    companion object {
        const val SEPARATOR = "\n\n"
    }
}
