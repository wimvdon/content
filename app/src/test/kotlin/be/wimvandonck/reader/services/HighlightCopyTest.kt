package be.wimvandonck.reader.services

import be.wimvandonck.reader.data.Highlight
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightCopyTest {
    /** Highlights at a service in memory, with a limit on how many it makes. */
    private class FakeHighlights(
        var limit: Int = Int.MAX_VALUE,
    ) : HighlightService {
        val have = LinkedHashMap<String, RemoteHighlight>()
        var made = 0
        var listed = 0
        var fail: ServiceProblem? = null

        override suspend fun highlights(remoteId: String): List<RemoteHighlight> {
            listed++
            return have.values.toList()
        }

        override suspend fun addHighlight(
            remoteId: String,
            text: String,
            note: String,
            position: Int,
        ): RemoteHighlight {
            fail?.let { throw ServiceException(it) }
            if (made >= limit) throw ServiceException(ServiceProblem.LimitReached)
            made++
            val h = RemoteHighlight("r$made", text, note)
            have[h.remoteId] = h
            return h
        }

        override suspend fun removeHighlight(highlightId: String) {
            have.remove(highlightId)
        }
    }

    private fun h(
        id: String,
        quote: String,
        note: String = "",
        at: Long = 0,
    ) = Highlight(id = id, quote = quote, note = note, createdAt = at)

    @Test
    fun `copies new highlights and links them`() =
        runBlocking {
            val service = FakeHighlights()
            val pushed = HighlightCopy.push(service, "1", listOf(h("a", "One"), h("b", "Two", "note", 1)), emptyMap(), 0)
            assertNull(pushed.problem)
            assertEquals(setOf("One", "Two"), service.have.values.map { it.text }.toSet())
            assertEquals("note", service.have.values.first { it.text == "Two" }.note)
            assertFalse(HighlightCopy.pending(listOf(h("a", "One"), h("b", "Two", "note", 1)), pushed.links, 0))
        }

    @Test
    fun `nothing to do costs no call`() {
        val links = mapOf("a" to HighlightLink("r1", ""))
        assertFalse(HighlightCopy.pending(listOf(h("a", "One")), links, 0))
        assertTrue(HighlightCopy.pending(listOf(h("a", "One", "changed")), links, 0))
        assertTrue(HighlightCopy.pending(emptyList(), links, 0))
        assertTrue(HighlightCopy.pending(listOf(h("b", "Two")), emptyMap(), 0))
    }

    @Test
    fun `a passage already there is not copied twice`() =
        runBlocking {
            val service = FakeHighlights()
            service.have["x"] = RemoteHighlight("x", "It’s here", "")
            val pushed = HighlightCopy.push(service, "1", listOf(h("a", "It's here")), emptyMap(), 0)
            assertEquals(0, service.made)
            assertEquals("x", pushed.links["a"]?.remoteId)
        }

    @Test
    fun `a changed note makes the highlight again`() =
        runBlocking {
            val service = FakeHighlights()
            val first = HighlightCopy.push(service, "1", listOf(h("a", "One")), emptyMap(), 0)
            val second = HighlightCopy.push(service, "1", listOf(h("a", "One", "new note")), first.links, 0)
            assertEquals(1, service.have.size)
            assertEquals("new note", service.have.values.single().note)
            assertEquals("new note", second.links["a"]?.note)
        }

    @Test
    fun `a highlight deleted here is deleted there`() =
        runBlocking {
            val service = FakeHighlights()
            val first = HighlightCopy.push(service, "1", listOf(h("a", "One")), emptyMap(), 0)
            val second = HighlightCopy.push(service, "1", emptyList(), first.links, 0)
            assertTrue(service.have.isEmpty())
            assertTrue(second.links.isEmpty())
        }

    @Test
    fun `the limit keeps the rest and tries again a day later`() =
        runBlocking {
            val service = FakeHighlights(limit = 1)
            val all = listOf(h("a", "One", at = 1), h("b", "Two", at = 2), h("c", "Three", at = 3))
            val pushed = HighlightCopy.push(service, "1", all, emptyMap(), 1000)
            assertNull(pushed.problem)
            assertEquals(setOf("b", "c"), HighlightCopy.notCopied(pushed.links))
            // Not yet a day: nothing to do.
            assertFalse(HighlightCopy.pending(all, pushed.links, 1000 + HighlightCopy.RETRY_MS - 1))
            assertTrue(HighlightCopy.pending(all, pushed.links, 1000 + HighlightCopy.RETRY_MS))
            service.limit = 3
            val later = HighlightCopy.push(service, "1", all, pushed.links, 1000 + HighlightCopy.RETRY_MS)
            assertTrue(HighlightCopy.notCopied(later.links).isEmpty())
            assertEquals(3, service.have.size)
        }

    @Test
    fun `another problem stops and keeps what got through`() =
        runBlocking {
            val service = FakeHighlights()
            service.fail = ServiceProblem.Offline
            val pushed = HighlightCopy.push(service, "1", listOf(h("a", "One")), emptyMap(), 0)
            assertEquals(ServiceProblem.Offline, pushed.problem)
            assertTrue(HighlightCopy.pending(listOf(h("a", "One")), pushed.links, 0))
        }

    @Test
    fun `links are stored and read back`() {
        val links = mapOf("item" to mapOf("a" to HighlightLink("r1", "note"), "b" to HighlightLink(note = "", refusedAt = 5L)))
        assertEquals(links, HighlightCopy.decode(HighlightCopy.encode(links)))
        assertTrue(HighlightCopy.decode("not json").isEmpty())
        assertTrue(HighlightCopy.decode(null).isEmpty())
    }
}
