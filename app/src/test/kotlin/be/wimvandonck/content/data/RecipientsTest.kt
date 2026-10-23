package be.wimvandonck.content.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipientsTest {
    @Test
    fun `the last one shared with comes first, once, in lower case`() {
        val list = Recipients.remember(Recipients.remember(emptyList(), "Ann@Example.com "), "bob@example.com")
        assertEquals(listOf("bob@example.com", "ann@example.com"), list)
        assertEquals(listOf("ann@example.com", "bob@example.com"), Recipients.remember(list, "ANN@example.com"))
        assertEquals(list, Recipients.remember(list, "  "))
    }

    @Test
    fun `keeps at most twelve`() {
        val many = (1..20).fold(emptyList<String>()) { l, i -> Recipients.remember(l, "p$i@example.com") }
        assertEquals(Recipients.MAX, many.size)
        assertEquals("p20@example.com", many.first())
    }

    @Test
    fun `reads back what it wrote`() {
        val list = listOf("ann@example.com", "bob@example.com")
        assertEquals(list, Recipients.decode(Recipients.encode(list)))
        assertEquals(listOf("ann@example.com"), Recipients.decode("""["Ann@Example.com", "ann@example.com", 3, "not an address"]"""))
        assertNull(Recipients.decode("nonsense"))
        assertNull(Recipients.decode(null))
    }
}
