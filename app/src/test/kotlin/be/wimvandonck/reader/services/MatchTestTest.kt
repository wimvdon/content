package be.wimvandonck.reader.services

import be.wimvandonck.reader.data.MatchRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchTestTest {
    @Test
    fun `highlights are counted per text source, and summed over articles`() {
        val highlights = listOf(RemoteHighlight("1", "green tea", "", 0, 1), RemoteHighlight("2", "nowhere at all here", "", 0, 1)).map(MatchReport::highlightOf)
        val report =
            MatchReport()
                .add(highlights, mapOf("instapaper" to "I like green tea.", "feed" to "I like green tea."))
                .add(highlights.take(1), mapOf("instapaper" to "Green tea is nice", "reader" to "Coffee only"))
                .add(emptyList(), mapOf("feed" to "ignored"))
        assertEquals(2, report.articles)
        assertEquals(3, report.highlights)
        assertEquals(MatchRate(2, 0, 1), report.bySource["instapaper"])
        assertEquals(MatchRate(1, 0, 1), report.bySource["feed"])
        assertEquals(MatchRate(0, 0, 1), report.bySource["reader"])
        val summary = report.summary()
        assertTrue(summary, summary.startsWith("2 articles, 3 highlights"))
        assertTrue(summary, "| instapaper | 3 | 2 (67%) | 0 (0%) | 1 (33%) |" in summary)
    }
}
