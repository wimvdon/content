package to.wim.content.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Groups the article list by day, for its date headers. */
object ArticleDays {
    /** The articles (already newest first) under the day each appeared, in order. */
    fun group(
        articles: List<Article>,
        zone: ZoneId,
    ): List<Pair<LocalDate, List<Article>>> {
        val days = mutableListOf<Pair<LocalDate, MutableList<Article>>>()
        for (article in articles) {
            val day = dayOf(article.sortTime, zone)
            if (days.lastOrNull()?.first == day) days.last().second.add(article) else days.add(day to mutableListOf(article))
        }
        return days
    }

    fun dayOf(
        time: Long,
        zone: ZoneId,
    ): LocalDate = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
}
