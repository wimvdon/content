package be.wimvandonck.reader.feed

import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Reads the dates feeds use: RFC 822 in RSS ("Tue, 3 Jun 2008 11:05:30 GMT") and ISO 8601 in
 * Atom and Dublin Core ("2008-06-03T11:05:30Z"). Forgiving about the variations found in real
 * feeds. Returns epoch milliseconds, or null when the date can't be read.
 */
object FeedDates {
    private val rfc822 =
        Regex(
            """^(?:[A-Za-z]+,?\s*)?(\d{1,2})[\s-]+([A-Za-z]{3})[A-Za-z]*\.?[\s-]+(\d{2,4})\s+(\d{1,2}):(\d{2})(?::(\d{2}))?(?:\.\d+)?\s*(.*)$""",
        )
    private val iso =
        Regex(
            """^(\d{4})-(\d{2})-(\d{2})(?:[T\s](\d{2}):(\d{2})(?::(\d{2})(?:[.,](\d+))?)?)?\s*(Z|z|UTC|GMT|[+-]\d{2}(?::?\d{2})?)?$""",
        )
    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val zones =
        mapOf(
            "" to 0, "GMT" to 0, "UT" to 0, "UTC" to 0, "Z" to 0,
            "EST" to -5, "EDT" to -4, "CST" to -6, "CDT" to -5,
            "MST" to -7, "MDT" to -6, "PST" to -8, "PDT" to -7,
            "CET" to 1, "CEST" to 2, "BST" to 1,
        )

    fun parse(raw: String?): Long? {
        val text = raw?.trim()?.replace(Regex("""\s+"""), " ")
        if (text.isNullOrEmpty()) return null
        return parseIso(text) ?: parseRfc822(text)
    }

    private fun parseRfc822(text: String): Long? {
        val m = rfc822.find(text) ?: return null
        val (day, monthName, yearText, hour, minute, second, zoneText) = m.destructured
        val month = months.indexOf(monthName.lowercase()) + 1
        if (month == 0) return null
        val year =
            yearText.toInt().let {
                when {
                    yearText.length > 2 -> it
                    it < 50 -> 2000 + it
                    else -> 1900 + it
                }
            }
        val offsetSeconds = zoneOffsetSeconds(zoneText.trim()) ?: return null
        return toEpochMillis(year, month, day.toInt(), hour.toInt(), minute.toInt(), second.toIntOrNull() ?: 0, 0, offsetSeconds)
    }

    private fun parseIso(text: String): Long? {
        val m = iso.find(text) ?: return null
        val g = m.groupValues
        val nanos = g[7].takeIf { it.isNotEmpty() }?.padEnd(9, '0')?.take(9)?.toInt() ?: 0
        val offsetSeconds = zoneOffsetSeconds(g[8]) ?: return null
        return toEpochMillis(
            g[1].toInt(),
            g[2].toInt(),
            g[3].toInt(),
            g[4].toIntOrNull() ?: 0,
            g[5].toIntOrNull() ?: 0,
            g[6].toIntOrNull() ?: 0,
            nanos,
            offsetSeconds,
        )
    }

    /** "+0200", "-07:00", "+05" or a zone name; null when unknown. */
    private fun zoneOffsetSeconds(zone: String): Int? {
        zones[zone.uppercase()]?.let { return it * 3600 }
        val m = Regex("""^([+-])(\d{2}):?(\d{2})?$""").find(zone) ?: return null
        val sign = if (m.groupValues[1] == "-") -1 else 1
        val hours = m.groupValues[2].toInt()
        val minutes = m.groupValues[3].toIntOrNull() ?: 0
        if (hours > 18 || minutes > 59) return null
        return sign * (hours * 3600 + minutes * 60)
    }

    @Suppress("LongParameterList")
    private fun toEpochMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
        nanos: Int,
        offsetSeconds: Int,
    ): Long? =
        try {
            LocalDateTime
                .of(year, month, day, hour, minute, second.coerceAtMost(59), nanos)
                .toInstant(ZoneOffset.ofTotalSeconds(offsetSeconds))
                .toEpochMilli()
        } catch (_: DateTimeException) {
            null
        }
}
