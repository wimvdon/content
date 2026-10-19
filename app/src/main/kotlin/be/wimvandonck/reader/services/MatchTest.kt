package be.wimvandonck.reader.services

import be.wimvandonck.reader.data.Anchors
import be.wimvandonck.reader.data.Highlight
import be.wimvandonck.reader.data.MatchRate

/**
 * The matching test of #102: highlights made in a service, looked for in each version of an
 * article's text (the service's, the feed's, Reader's own extraction), counted per source. Plain
 * Kotlin, unit tested; the test build runs it on the owner's account.
 */
data class MatchReport(
    val articles: Int = 0,
    val highlights: Int = 0,
    /** Per text source ("instapaper", "feed", "reader"): how its texts found the highlights. */
    val bySource: Map<String, MatchRate> = emptyMap(),
) {
    /** One more article: its highlights, and its text in each source that has one. */
    fun add(
        highlights: List<Highlight>,
        texts: Map<String, String>,
    ): MatchReport {
        if (highlights.isEmpty()) return this
        val sources = bySource.toMutableMap()
        for ((source, text) in texts) {
            val rate = Anchors.matchRate(highlights, text)
            val before = sources[source] ?: MatchRate(0, 0, 0)
            sources[source] = MatchRate(before.exact + rate.exact, before.approximate + rate.approximate, before.notFound + rate.notFound)
        }
        return MatchReport(articles + 1, this.highlights + highlights.size, sources)
    }

    /** The result as a Markdown table, to paste into the pull request. */
    fun summary(): String =
        buildString {
            appendLine("$articles articles, $highlights highlights")
            appendLine()
            appendLine("| Text source | Highlights checked | Exact | Approximate | Not found |")
            appendLine("|---|---|---|---|---|")
            for ((source, rate) in bySource.toSortedMap()) {
                fun pct(n: Int) = if (rate.total == 0) "0%" else "${Math.round(n * 100.0 / rate.total)}%"
                appendLine("| $source | ${rate.total} | ${rate.exact} (${pct(rate.exact)}) | ${rate.approximate} (${pct(rate.approximate)}) | ${rate.notFound} (${pct(rate.notFound)}) |")
            }
        }.trimEnd()

    companion object {
        /** A service's highlight as Reader's, placed by its text and occurrence only. */
        fun highlightOf(r: RemoteHighlight): Highlight = Highlight(r.remoteId, r.text.trim(), occurrence = r.position, note = r.note, createdAt = r.createdAt)
    }
}
