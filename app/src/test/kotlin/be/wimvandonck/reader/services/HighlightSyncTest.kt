package be.wimvandonck.reader.services

import be.wimvandonck.reader.data.Highlight
import be.wimvandonck.reader.data.Item
import be.wimvandonck.reader.data.ReadingListEntry
import be.wimvandonck.reader.data.ReadingState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightSyncTest {
    /** Highlights at a fake service: made, deleted, and refused after [limit]. */
    private class FakeHighlights(
        var limit: Int = Int.MAX_VALUE,
        var failWith: ServiceProblem? = null,
    ) : ReadingListService {
        override val id = "fake"
        override val features = setOf(ServiceFeature.Highlights)
        val made = LinkedHashMap<String, RemoteHighlight>()
        val calls = ArrayList<String>()
        private var next = 1

        override suspend fun changes(cursor: String?) = RemoteChanges(emptyList(), emptyList(), "c")

        override suspend fun add(
            url: String,
            title: String,
            tags: List<String>,
            archived: Boolean,
        ) = throw UnsupportedOperationException()

        override suspend fun update(
            have: RemoteEntry,
            want: RemoteEntry,
        ) = want

        override suspend fun remove(remoteId: String) = Unit

        override suspend fun text(remoteId: String): String? = null

        override suspend fun highlights(remoteId: String) = made.values.toList()

        override suspend fun addHighlight(
            remoteId: String,
            text: String,
            note: String,
            position: Int,
        ): RemoteHighlight {
            failWith?.let { throw ServiceException(it) }
            if (made.size >= limit) throw ServiceException(ServiceProblem.LimitReached)
            val h = RemoteHighlight("r${next++}", text, note, position, 1)
            made[h.remoteId] = h
            calls += "add $text $position"
            return h
        }

        override suspend fun removeHighlight(remoteId: String) {
            made.remove(remoteId)
            calls += "remove $remoteId"
        }
    }

    private fun item(vararg highlights: Highlight) =
        Item("a.example/1", "https://a.example/1", "A", createdAt = 0, readingList = ReadingListEntry(ReadingState.ToRead, 1), highlights = highlights.toList())

    private fun h(
        id: String,
        quote: String,
        note: String = "",
        at: Long = 1,
    ) = Highlight(id, quote, note = note, createdAt = at)

    @Test
    fun `new highlights are made at the service once, with their position`() =
        runBlocking {
            val service = FakeHighlights()
            val one = item(h("a", "first quote"), h("b", "second quote", at = 2))
            val pushed = HighlightSync.push(service, "9", one, emptyMap(), now = 10) { if (it.id == "b") 1 else 0 }
            assertNull(pushed.problem)
            assertEquals(listOf("add first quote 0", "add second quote 1"), service.calls)
            // Nothing changed: nothing sent.
            service.calls.clear()
            val again = HighlightSync.push(service, "9", one, pushed.links, now = 11)
            assertEquals(emptyList<String>(), service.calls)
            assertEquals(pushed.links, again.links)
        }

    @Test
    fun `a changed note is made again, a deleted highlight is deleted there`() =
        runBlocking {
            val service = FakeHighlights()
            val before = item(h("a", "first quote"), h("b", "second quote"))
            val links = HighlightSync.push(service, "9", before, emptyMap(), now = 1).links
            service.calls.clear()
            val after = item(h("a", "first quote", note = "why"))
            val pushed = HighlightSync.push(service, "9", after, links, now = 2)
            assertEquals(listOf("remove r2", "remove r1", "add first quote 0"), service.calls)
            assertEquals(HighlightLink("r3", "why"), pushed.links["a"])
            assertEquals(setOf("a"), pushed.links.keys)
        }

    @Test
    fun `past the plan's limit the highlight stays, marked not copied, and is tried again a day later`() =
        runBlocking {
            val service = FakeHighlights(limit = 1)
            val one = item(h("a", "first quote"), h("b", "second quote", at = 2), h("c", "third quote", at = 3))
            val pushed = HighlightSync.push(service, "9", one, emptyMap(), now = 1_000)
            assertNull(pushed.problem)
            assertEquals(setOf("b", "c"), HighlightSync.notCopied(pushed.links))
            // Within the day: not tried.
            service.calls.clear()
            HighlightSync.push(service, "9", one, pushed.links, now = 2_000)
            assertEquals(emptyList<String>(), service.calls)
            // A day later, with room again.
            service.limit = 10
            val later = HighlightSync.push(service, "9", one, pushed.links, now = 1_000 + HighlightSync.RETRY_MS)
            assertEquals(emptySet<String>(), HighlightSync.notCopied(later.links))
            assertEquals(3, service.made.size)
        }

    @Test
    fun `another failure stops, keeping what was done`() =
        runBlocking {
            val service = FakeHighlights()
            val first = HighlightSync.push(service, "9", item(h("a", "first quote")), emptyMap(), now = 1).links
            service.failWith = ServiceProblem.Offline
            val pushed = HighlightSync.push(service, "9", item(h("a", "first quote"), h("b", "second quote")), first, now = 2)
            assertEquals(ServiceProblem.Offline, pushed.problem)
            assertEquals(first, pushed.links)
        }

    @Test
    fun `highlights made at the service come in, a same passage is linked, and one deleted there goes`() {
        val here = item(h("a", "It’s here already"), h("b", "linked one"))
        val links = mapOf("b" to HighlightLink("r7", ""))
        val remote =
            listOf(
                RemoteHighlight("r1", "A new passage", "a note", 2, 5_000),
                RemoteHighlight("r2", "It's here already", "", 0, 6_000),
            )
        val (highlights, newLinks) = HighlightSync.takeIn("instapaper", here, remote, links)
        // b's copy (r7) was deleted there: b goes. r2 matches a. r1 is new.
        assertEquals(listOf("a", "instapaper-r1"), highlights.map { it.id })
        val made = highlights.last()
        assertEquals("A new passage", made.quote)
        assertEquals(2, made.occurrence)
        assertEquals("a note", made.note)
        assertEquals(mapOf("a" to HighlightLink("r2", ""), "instapaper-r1" to HighlightLink("r1", "a note")), newLinks)
        // Taking in the same again changes nothing.
        val again = HighlightSync.takeIn("instapaper", here.copy(highlights = highlights), remote, newLinks)
        assertEquals(highlights to newLinks, again)
    }

    @Test
    fun `links are stored with the service's rows`() {
        val entry = RemoteEntry("1", "https://a.example/1", "A", addedAt = 1, archived = false, liked = false, tags = emptyList(), highlights = mapOf("a" to HighlightLink("r1", "n"), "b" to HighlightLink(refusedAt = 5)))
        assertEquals(entry, ServiceRows.decode(ServiceRows.encode(entry)))
        assertTrue(ServiceRows.decode(ServiceRows.encode(entry.copy(highlights = emptyMap())))!!.highlights.isEmpty())
    }
}
