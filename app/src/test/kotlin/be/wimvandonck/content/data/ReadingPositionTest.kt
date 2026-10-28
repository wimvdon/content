package be.wimvandonck.content.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingPositionTest {
    @Test
    fun `the position is the share scrolled past, and none on a page that doesn't scroll`() {
        assertEquals(0f, ReadingPosition.of(0, 1000))
        assertEquals(0.25f, ReadingPosition.of(250, 1000))
        assertEquals(1f, ReadingPosition.of(1200, 1000))
        assertNull(ReadingPosition.of(0, 0))
    }

    @Test
    fun `opening scrolls back to the progress, except near the start or the end`() {
        assertEquals(400, ReadingPosition.scrollFor(0.4f, 1000))
        assertNull(ReadingPosition.scrollFor(0.01f, 1000))
        assertNull(ReadingPosition.scrollFor(0.99f, 1000))
        assertNull(ReadingPosition.scrollFor(0.5f, 0))
    }

    @Test
    fun `scrolling back and reading the position again gives the same progress (#270)`() {
        val max = 5321
        val back = ReadingPosition.scrollFor(0.63f, max)!!
        assertEquals(0.63f, ReadingPosition.of(back, max)!!, 0.001f)
    }
}
