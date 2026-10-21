package be.wimvandonck.reader.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedTextTest {
    private val account = mutableMapOf<String, String>()
    private var extracted = 0
    private var offline = false

    private fun open(
        key: String,
        saved: Boolean = true,
        page: String? = "<p>This device's text</p>",
    ): String? =
        runBlocking {
            SharedText.resolve(
                saved,
                stored = {
                    if (offline) error("offline")
                    account[key]
                },
                extract = {
                    extracted++
                    page
                },
                store = { html ->
                    if (offline) error("offline")
                    account.getOrPut(key) { html }
                },
            )
        }

    @Test
    fun `the first device's text is stored, and every device shows that one`() {
        assertEquals("<p>Phone</p>", open("a", page = "<p>Phone</p>"))
        assertEquals("<p>Phone</p>", account["a"])
        // The web opens it: no extraction, the stored text.
        extracted = 0
        assertEquals("<p>Phone</p>", open("a", page = "<p>Web</p>"))
        assertEquals(0, extracted)
    }

    @Test
    fun `two devices at once, the one stored first wins for both`() {
        // The other device stored its text between this one's look and its own store.
        val result =
            runBlocking {
                SharedText.resolve(true, stored = { null }, extract = { "<p>Mine</p>" }, store = { account.getOrPut("b") { "<p>Theirs</p>" } })
            }
        assertEquals("<p>Theirs</p>", result)
    }

    @Test
    fun `offline or signed out, the device's own text shows`() {
        offline = true
        assertEquals("<p>This device's text</p>", open("c"))
        assertNull(account["c"])
    }

    @Test
    fun `an article that isn't saved isn't stored, and a page without an article gives nothing`() {
        assertEquals("<p>This device's text</p>", open("d", saved = false))
        assertNull(account["d"])
        assertNull(open("e", page = null))
        assertNull(account["e"])
    }
}
