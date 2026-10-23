package be.wimvandonck.content.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ArticleDaysTest {
    private val zone = ZoneId.of("Europe/Brussels")

    private fun at(
        day: Int,
        hour: Int,
    ) = ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun article(
        id: String,
        time: Long,
    ) = Article("https://a.example.com/feed", id, "T", null, null, "", time, time)

    @Test
    fun `articles are grouped by their local day, keeping their order`() {
        val articles = listOf(article("1", at(15, 9)), article("2", at(15, 0)), article("3", at(14, 23)), article("4", at(12, 8)))
        val days = ArticleDays.group(articles, zone)
        assertEquals(listOf(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 12)), days.map { it.first })
        assertEquals(listOf(listOf("1", "2"), listOf("3"), listOf("4")), days.map { day -> day.second.map { it.id } })
    }

    @Test
    fun `the day depends on the time zone`() {
        // 23:30 in Brussels is already the next day in Tokyo.
        val time = at(14, 23) + 30 * 60_000
        assertEquals(LocalDate.of(2026, 10, 14), ArticleDays.dayOf(time, zone))
        assertEquals(LocalDate.of(2026, 10, 15), ArticleDays.dayOf(time, ZoneId.of("Asia/Tokyo")))
        assertEquals(emptyList<Pair<LocalDate, List<Article>>>(), ArticleDays.group(emptyList(), zone))
    }
}
