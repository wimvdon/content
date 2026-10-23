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
            val h = RemoteHighlight("r$made", text, note, position)
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

    private suspend fun FakeHighlights.sync(
        highlights: List<Highlight>,
        links: Map<String, HighlightLink>,
        now: Long = 0,
        look: Boolean = false,
    ) = HighlightCopy.sync(this, "instapaper", "1", highlights, links, now, look)

    @Test
    fun `copies new highlights and links them`() =
        runBlocking {
            val service = FakeHighlights()
            val all = listOf(h("a", "One"), h("b", "Two", "note", 1))
            val synced = service.sync(all, emptyMap())
            assertNull(synced.problem)
            assertEquals(setOf("One", "Two"), service.have.values.map { it.text }.toSet())
            assertEquals("note", service.have.values.first { it.text == "Two" }.note)
            assertEquals(all, synced.highlights)
            assertFalse(HighlightCopy.pending(all, synced.links, 0))
        }

    @Test
    fun `nothing to send costs no call unless asked to look`() =
        runBlocking {
            val links = mapOf("a" to HighlightLink("r1", ""))
            assertFalse(HighlightCopy.pending(listOf(h("a", "One")), links, 0))
            assertTrue(HighlightCopy.pending(listOf(h("a", "One", "changed")), links, 0))
            assertTrue(HighlightCopy.pending(emptyList(), links, 0))
            assertTrue(HighlightCopy.pending(listOf(h("b", "Two")), emptyMap(), 0))
            val service = FakeHighlights()
            service.have["r1"] = RemoteHighlight("r1", "One")
            service.sync(listOf(h("a", "One")), links)
            assertEquals(0, service.listed)
            service.sync(listOf(h("a", "One")), links, look = true)
            assertEquals(1, service.listed)
        }

    @Test
    fun `a passage already there is not copied twice`() =
        runBlocking {
            val service = FakeHighlights()
            service.have["x"] = RemoteHighlight("x", "It’s here", "")
            val synced = service.sync(listOf(h("a", "It's here")), emptyMap())
            assertEquals(0, service.made)
            assertEquals("x", synced.links["a"]?.remoteId)
        }

    @Test
    fun `a highlight made there comes in`() =
        runBlocking {
            val service = FakeHighlights()
            service.have["r9"] = RemoteHighlight("r9", " A passage ", "Their note", position = 2, createdAt = 5000)
            val synced = service.sync(emptyList(), emptyMap(), look = true)
            assertEquals(listOf(Highlight(id = "instapaper-r9", quote = "A passage", occurrence = 2, note = "Their note", createdAt = 5000)), synced.highlights)
            assertEquals(HighlightLink("r9", "Their note"), synced.links["instapaper-r9"])
            // Nothing sent back.
            assertEquals(0, service.made)
            assertFalse(HighlightCopy.pending(synced.highlights, synced.links, 0))
        }

    @Test
    fun `a highlight deleted there is deleted here`() =
        runBlocking {
            val service = FakeHighlights()
            val first = service.sync(listOf(h("a", "One"), h("b", "Two")), emptyMap())
            service.have.remove(first.links.getValue("a").remoteId)
            val second = service.sync(first.highlights, first.links, look = true)
            assertEquals(listOf("b"), second.highlights.map { it.id })
            assertNull(second.links["a"])
            assertEquals(1, service.have.size)
        }

    @Test
    fun `a note changed there comes in, unless it changed here too`() =
        runBlocking {
            val service = FakeHighlights()
            val first = service.sync(listOf(h("a", "One", "old")), emptyMap())
            val remoteId = first.links.getValue("a").remoteId!!
            service.have[remoteId] = service.have.getValue(remoteId).copy(note = "theirs")
            val taken = service.sync(first.highlights, first.links, look = true)
            assertEquals("theirs", taken.highlights.single().note)
            assertEquals(1, service.made)
            // Changed on both sides: this one goes there.
            service.have[remoteId] = service.have.getValue(remoteId).copy(note = "theirs again")
            val both = service.sync(listOf(h("a", "One", "mine")), taken.links, look = true)
            assertEquals("mine", both.highlights.single().note)
            assertEquals("mine", service.have.values.single().note)
        }

    @Test
    fun `a changed note makes the highlight again`() =
        runBlocking {
            val service = FakeHighlights()
            val first = service.sync(listOf(h("a", "One")), emptyMap())
            val second = service.sync(listOf(h("a", "One", "new note")), first.links)
            assertEquals(1, service.have.size)
            assertEquals("new note", service.have.values.single().note)
            assertEquals("new note", second.links["a"]?.note)
        }

    @Test
    fun `a highlight deleted here is deleted there`() =
        runBlocking {
            val service = FakeHighlights()
            val first = service.sync(listOf(h("a", "One")), emptyMap())
            val second = service.sync(emptyList(), first.links)
            assertTrue(service.have.isEmpty())
            assertTrue(second.links.isEmpty())
        }

    @Test
    fun `the limit keeps the rest and tries again a day later`() =
        runBlocking {
            val service = FakeHighlights(limit = 1)
            val all = listOf(h("a", "One", at = 1), h("b", "Two", at = 2), h("c", "Three", at = 3))
            val synced = service.sync(all, emptyMap(), now = 1000)
            assertNull(synced.problem)
            assertEquals(setOf("b", "c"), HighlightCopy.notCopied(synced.links))
            assertFalse(HighlightCopy.pending(all, synced.links, 1000 + HighlightCopy.RETRY_MS - 1))
            assertTrue(HighlightCopy.pending(all, synced.links, 1000 + HighlightCopy.RETRY_MS))
            service.limit = 3
            val later = service.sync(all, synced.links, now = 1000 + HighlightCopy.RETRY_MS)
            assertTrue(HighlightCopy.notCopied(later.links).isEmpty())
            assertEquals(3, service.have.size)
        }

    @Test
    fun `another problem stops and keeps what got through`() =
        runBlocking {
            val service = FakeHighlights()
            service.fail = ServiceProblem.Offline
            val synced = service.sync(listOf(h("a", "One")), emptyMap())
            assertEquals(ServiceProblem.Offline, synced.problem)
            assertTrue(HighlightCopy.pending(listOf(h("a", "One")), synced.links, 0))
        }

    @Test
    fun `what a sync changed lands on the highlights as they are now`() {
        val before = listOf(h("a", "One"), h("b", "Two", "old"))
        val after = listOf(h("b", "Two", "theirs"), h("instapaper-r9", "New"))
        // Meanwhile: "c" added here.
        val current = before + h("c", "Three")
        assertEquals(listOf(h("b", "Two", "theirs"), h("c", "Three"), h("instapaper-r9", "New")), HighlightCopy.apply(current, before, after))
        // A note changed here meanwhile keeps that change.
        val edited = listOf(h("a", "One"), h("b", "Two", "mine"))
        assertEquals("mine", HighlightCopy.apply(edited, before, after).first { it.id == "b" }.note)
    }

    @Test
    fun `links are stored and read back`() {
        val links = mapOf("item" to mapOf("a" to HighlightLink("r1", "note"), "b" to HighlightLink(note = "", refusedAt = 5L)))
        assertEquals(links, HighlightCopy.decode(HighlightCopy.encode(links)))
        assertTrue(HighlightCopy.decode("not json").isEmpty())
        assertTrue(HighlightCopy.decode(null).isEmpty())
    }
}
