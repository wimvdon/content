package be.wimvandonck.reader.data

/** Where a highlight is in a text (#102): found as it was made, found roughly, or not found. */
sealed interface Anchor {
    /** The quote itself, at [start] to [end] (characters of the text). */
    data class Exact(
        val start: Int,
        val end: Int,
    ) : Anchor

    /** The text differs; the passage is most likely at [start] to [end]. Shown as approximate. */
    data class Approximate(
        val start: Int,
        val end: Int,
    ) : Anchor

    /** Not in this text. The highlight is kept and listed, never dropped. */
    data object NotFound : Anchor
}

/**
 * Highlights anchored by quote, not position (#102): the quote, some text before and after it,
 * and which occurrence it is. That finds the passage again in another version of the text (the
 * feed's or the full article). Typographic differences (curly or straight quotes,
 * dashes, spaces, ligatures, letter case as a last resort) don't count. Plain Kotlin, unit tested.
 */
object Anchors {
    /** How much text before and after the quote is kept. */
    const val CONTEXT = 32

    /** A new highlight of [start] to [end] in [text]. */
    fun create(
        text: String,
        start: Int,
        end: Int,
        id: String,
        now: Long,
        note: String = "",
    ): Highlight {
        val from = start.coerceIn(0, text.length)
        val to = end.coerceIn(from, text.length)
        val quote = text.substring(from, to)
        val folded = Folded(text)
        val q = fold(quote)
        // Which occurrence: how many times the quote appears before this one.
        val foldedStart = folded.foldedIndex(from)
        var occurrence = 0
        if (q.isNotEmpty()) {
            var at = folded.text.indexOf(q)
            while (at in 0 until foldedStart) {
                occurrence++
                at = folded.text.indexOf(q, at + 1)
            }
        }
        return Highlight(
            id = id,
            quote = quote.trim(),
            prefix = text.substring((from - CONTEXT).coerceAtLeast(0), from),
            suffix = text.substring(to, (to + CONTEXT).coerceAtMost(text.length)),
            occurrence = occurrence,
            note = note,
            createdAt = now,
        )
    }

    /** Finds [highlight] in [text]. */
    fun resolve(
        highlight: Highlight,
        text: String,
    ): Anchor {
        val folded = Folded(text)
        val quote = fold(highlight.quote)
        if (quote.isEmpty()) return Anchor.NotFound
        exact(folded, folded.text, quote, highlight)?.let { return it }
        // Letter case as a last resort for the quote itself (a heading in capitals, for example).
        exact(folded, folded.text.lowercase(), quote.lowercase(), highlight)?.let { return it }
        return approximate(folded, quote, highlight)
    }

    private fun exact(
        folded: Folded,
        haystack: String,
        quote: String,
        highlight: Highlight,
    ): Anchor.Exact? {
        val starts = generateSequence(haystack.indexOf(quote).takeIf { it >= 0 }) { haystack.indexOf(quote, it + 1).takeIf { i -> i >= 0 } }.toList()
        if (starts.isEmpty()) return null
        val start =
            if (starts.size == 1) {
                starts[0]
            } else {
                // Several: the one whose surroundings fit best; on a tie, the one counted when it was made.
                val prefix = foldKeepingSpaces(highlight.prefix).lowercase()
                val suffix = foldKeepingSpaces(highlight.suffix).lowercase()
                val lower = haystack.lowercase()
                val scores = starts.map { s -> commonSuffix(lower.substring(0, s), prefix) + commonPrefix(lower.substring(s + quote.length), suffix) }
                val best = scores.max()
                val bestOnes = starts.indices.filter { scores[it] == best }
                starts[if (highlight.occurrence in bestOnes) highlight.occurrence else bestOnes.first()]
            }
        return Anchor.Exact(folded.originalIndex(start), folded.originalEnd(start + quote.length))
    }

    /**
     * The text changed inside the quote: its surroundings, or its first and last words, mark where
     * it most likely is.
     */
    private fun approximate(
        folded: Folded,
        quote: String,
        highlight: Highlight,
    ): Anchor {
        val text = folded.text.lowercase()
        val q = quote.lowercase()
        val prefix = fold(highlight.prefix).lowercase().takeLast(CONTEXT).trim()
        val suffix = fold(highlight.suffix).lowercase().take(CONTEXT).trim()
        val maxLength = q.length * 2 + CONTEXT
        // Between its surroundings.
        if (prefix.length >= MIN_PIECE && suffix.length >= MIN_PIECE) {
            var p = text.indexOf(prefix)
            while (p >= 0) {
                val from = p + prefix.length
                val s = text.indexOf(suffix, from)
                if (s >= 0 && s - from <= maxLength) return approximateRange(folded, from, s)
                p = text.indexOf(prefix, p + 1)
            }
        }
        // From its first words to its last words (whole words only).
        val head = q.take(PIECE).let { if (q.length > PIECE && ' ' in it) it.substringBeforeLast(' ') else it }.trim()
        val tail = q.takeLast(PIECE).let { if (q.length > PIECE && ' ' in it) it.substringAfter(' ') else it }.trim()
        if (head.length >= MIN_PIECE && tail.length >= MIN_PIECE) {
            var h = text.indexOf(head)
            while (h >= 0) {
                val t = text.indexOf(tail, h + head.length.coerceAtMost(q.length - tail.length).coerceAtLeast(0))
                if (t >= 0 && t + tail.length - h <= maxLength) return approximateRange(folded, h, t + tail.length)
                h = text.indexOf(head, h + 1)
            }
        }
        // Only its start, or only what came right before it.
        if (head.length >= MIN_PIECE) {
            text.indexOf(head).takeIf { it >= 0 }?.let { return approximateRange(folded, it, (it + q.length).coerceAtMost(text.length)) }
        }
        if (prefix.length >= MIN_PIECE) {
            text.indexOf(prefix).takeIf { it >= 0 }?.let { p ->
                val from = p + prefix.length
                return approximateRange(folded, from, (from + q.length).coerceAtMost(text.length))
            }
        }
        return Anchor.NotFound
    }

    private fun approximateRange(
        folded: Folded,
        from: Int,
        to: Int,
    ): Anchor.Approximate {
        // Without the spaces at either end.
        var a = from
        var b = to
        while (a < b && folded.text[a] == ' ') a++
        while (b > a && folded.text[b - 1] == ' ') b--
        return Anchor.Approximate(folded.originalIndex(a), folded.originalEnd(b))
    }

    private fun commonPrefix(
        a: String,
        b: String,
    ): Int {
        var i = 0
        while (i < a.length && i < b.length && a[i] == b[i]) i++
        return i
    }

    private fun commonSuffix(
        a: String,
        b: String,
    ): Int {
        var i = 0
        while (i < a.length && i < b.length && a[a.length - 1 - i] == b[b.length - 1 - i]) i++
        return i
    }

    /** Typographic variants made the same, spaces of any kind and length made one space. */
    fun fold(text: String): String = Folded(text).text.trim()

    /** [fold] keeping a space at either end: surroundings start or end with one. */
    private fun foldKeepingSpaces(text: String): String = Folded(text).text

    /** A text with its typographic variants folded, and where each folded character came from. */
    private class Folded(original: String) {
        val text: String
        private val origin: IntArray
        private val originalLength: Int = original.length

        init {
            val out = StringBuilder(original.length)
            val from = ArrayList<Int>(original.length)
            var lastSpace = false
            for ((i, c) in original.withIndex()) {
                val replacement = FOLD[c] ?: if (c.isWhitespace()) " " else c.toString()
                if (replacement.isEmpty()) continue
                if (replacement == " ") {
                    if (lastSpace) continue
                    lastSpace = true
                } else {
                    lastSpace = false
                }
                for (r in replacement) {
                    out.append(r)
                    from.add(i)
                }
            }
            text = out.toString()
            origin = from.toIntArray()
        }

        /** The original index of folded index [i]. */
        fun originalIndex(i: Int): Int = if (i >= origin.size) originalLength else origin[i]

        /** The original end (exclusive) of a folded range ending at [end]. */
        fun originalEnd(end: Int): Int = if (end <= 0) 0 else if (end > origin.size) originalLength else origin[end - 1] + 1

        /** The folded index at or after original index [i]. */
        fun foldedIndex(i: Int): Int {
            val at = origin.indexOfFirst { it >= i }
            return if (at < 0) text.length else at
        }
    }

    private val FOLD: Map<Char, String> =
        mapOf(
            '\u2018' to "'",
            '\u2019' to "'",
            '\u201A' to "'",
            '\u201B' to "'",
            '\u2032' to "'",
            '\u00B4' to "'",
            '`' to "'",
            '\u201C' to "\"",
            '\u201D' to "\"",
            '\u201E' to "\"",
            '\u201F' to "\"",
            '\u2033' to "\"",
            '\u00AB' to "\"",
            '\u00BB' to "\"",
            '\u2010' to "-",
            '\u2011' to "-",
            '\u2012' to "-",
            '\u2013' to "-",
            '\u2014' to "-",
            '\u2015' to "-",
            '\u2212' to "-",
            '\u2026' to "...",
            '\uFB00' to "ff",
            '\uFB01' to "fi",
            '\uFB02' to "fl",
            '\uFB03' to "ffi",
            '\uFB04' to "ffl",
            // Invisible: soft hyphen, zero-width spaces and joiners, byte order mark.
            '\u00AD' to "",
            '\u200B' to "",
            '\u200C' to "",
            '\u200D' to "",
            '\u2060' to "",
            '\uFEFF' to "",
        )

    /** At least this many characters of surroundings or of the quote to look for. */
    private const val MIN_PIECE = 8

    /** How much of the quote's start and end is looked for when the quote itself isn't found. */
    private const val PIECE = 16
}
