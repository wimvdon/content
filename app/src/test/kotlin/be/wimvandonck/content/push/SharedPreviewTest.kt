package be.wimvandonck.content.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedPreviewTest {
    @Test
    fun `reads the preview the feed loader sends`() {
        val data = mapOf("kind" to "shared", "title" to " A story ", "intro" to "Its intro.", "image" to "https://x.be/p.jpg", "sender" to "Ann", "count" to "3")
        assertEquals(SharedPreview("A story", "Its intro.", "https://x.be/p.jpg", "Ann", 3), SharedPreview.of(data))
    }

    @Test
    fun `an older feed loader sends no preview, and odd values are made safe`() {
        assertNull(SharedPreview.of(mapOf("kind" to "shared")))
        val odd = SharedPreview.of(mapOf("title" to "T", "image" to "http://x.be/p.jpg", "count" to "zero"))!!
        assertNull(odd.imageUrl)
        assertEquals(1, odd.count)
        assertEquals("", odd.from)
    }
}
