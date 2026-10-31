package to.wim.content.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JsonTest {
    @Test
    fun `round trip keeps every kind of value`() {
        val value =
            linkedMapOf(
                "s" to "quote \" back \\ tab \t line \n é 😀 \u0001",
                "n" to 1_700_000_000_000L,
                "neg" to -3L,
                "d" to 1.5,
                "t" to true,
                "f" to false,
                "z" to null,
                "list" to listOf(1L, "two", listOf<Any?>()),
                "empty" to mapOf<String, Any?>(),
            )
        assertEquals(value, Json.parse(Json.write(value)))
    }

    @Test
    fun `reads whitespace and unicode escapes`() {
        assertEquals(mapOf("a" to listOf("é", 2L)), Json.parse(" { \"a\" : [ \"\\u00e9\" , 2 ] } "))
    }

    @Test
    fun `rejects broken text`() {
        listOf("", "{", "[1,]", "{\"a\" 1}", "\"open", "tru", "{} x").forEach { text ->
            assertThrows(Json.ParseException::class.java) { Json.parse(text) }
        }
    }
}
