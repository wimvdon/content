package io.github.impca201.rssreader.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedDatesTest {
    private val june3 = 1_212_491_130_000L // 2008-06-03T11:05:30Z

    @Test
    fun `reads RFC 822 dates in their common shapes`() {
        assertEquals(june3, FeedDates.parse("Tue, 03 Jun 2008 11:05:30 GMT"))
        assertEquals(june3, FeedDates.parse("3 Jun 2008 11:05:30 +0000"))
        assertEquals(june3, FeedDates.parse("Tue, 3 June 2008 13:05:30 +02:00"))
        assertEquals(june3, FeedDates.parse("Tue, 03 Jun 2008 07:05:30 EDT"))
        assertEquals(june3, FeedDates.parse("  Tue,  03 Jun 08 11:05:30 Z "))
        assertEquals(june3 - 30_000, FeedDates.parse("Tue, 03 Jun 2008 11:05 GMT"))
    }

    @Test
    fun `reads ISO 8601 dates`() {
        assertEquals(june3, FeedDates.parse("2008-06-03T11:05:30Z"))
        assertEquals(june3, FeedDates.parse("2008-06-03T13:05:30+02:00"))
        assertEquals(june3 + 250, FeedDates.parse("2008-06-03T11:05:30.25Z"))
        assertEquals(june3, FeedDates.parse("2008-06-03 11:05:30"))
        assertEquals(1_212_451_200_000L, FeedDates.parse("2008-06-03"))
    }

    @Test
    fun `returns null for what is not a date`() {
        assertNull(FeedDates.parse(null))
        assertNull(FeedDates.parse(""))
        assertNull(FeedDates.parse("yesterday"))
        assertNull(FeedDates.parse("Tue, 31 Feb 2008 11:05:30 GMT"))
        assertNull(FeedDates.parse("2008-13-03T11:05:30Z"))
        assertNull(FeedDates.parse("Tue, 03 Jun 2008 11:05:30 XYZ"))
    }
}
