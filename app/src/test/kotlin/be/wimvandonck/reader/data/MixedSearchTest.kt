package be.wimvandonck.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MixedSearchTest {
    private fun article(
        id: String,
        title: String,
        time: Long,
    ) = Article(feedUrl = "https://feed.example/rss", id = id, title = title, link = null, author = null, html = "", publishedAt = time, fetchedAt = time)

    private fun saved(
        id: Long,
        title: String,
        timeSeconds: Long,
    ) = Item("site.example/$id", "https://site.example/$id", title, createdAt = 0, readingList = ReadingListEntry(ReadingState.ToRead, timeSeconds * 1000))

    private fun names(hits: List<SearchHit>) =
        hits.map {
            when (it) {
                is SearchHit.FromFeed -> "feed:${it.article.id}"
                is SearchHit.FromItem -> "saved:${it.item.key.substringAfter('/')}"
            }
        }

    @Test
    fun `the whole query in the title beats single words, which beat matches in the text only`() {
        assertTrue(MixedSearch.relevance("green tea", "Why green tea helps") > MixedSearch.relevance("green tea", "Tea that is green"))
        assertTrue(MixedSearch.relevance("green tea", "Tea that is green") > MixedSearch.relevance("green tea", "Green beans"))
        assertTrue(MixedSearch.relevance("green tea", "Green beans") > MixedSearch.relevance("green tea", "Coffee"))
        assertEquals(0, MixedSearch.relevance("green tea", "Coffee"))
        // Capitals and accents don't matter.
        assertEquals(MixedSearch.relevance("cafe", "Café"), MixedSearch.relevance("cafe", "cafe"))
        assertEquals(0, MixedSearch.relevance("  ", "Anything"))
    }

    @Test
    fun `feed and reading list matches are ranked together, best first, then newest`() {
        val feeds = listOf(article("a", "Coffee news", 5_000), article("b", "Green tea guide", 1_000))
        val savedOnes = listOf(saved(1, "All about green tea", 9), saved(2, "Gardening", 2))
        val hits = MixedSearch.mix("green tea", feeds, savedOnes)
        // Both titles with the phrase come first, the newest of them on top; then the text-only matches, newest first.
        assertEquals(listOf("saved:1", "feed:b", "feed:a", "saved:2"), names(hits))
    }

    @Test
    fun `the chips show only feeds or only the reading list`() {
        val feeds = listOf(article("a", "Tea", 1))
        val savedOnes = listOf(saved(1, "Tea", 1))
        assertEquals(listOf("feed:a"), names(MixedSearch.mix("tea", feeds, savedOnes, SearchFilter.Feeds)))
        assertEquals(listOf("saved:1"), names(MixedSearch.mix("tea", feeds, savedOnes, SearchFilter.Saved)))
        assertEquals(2, MixedSearch.mix("tea", feeds, savedOnes, SearchFilter.All).size)
        assertEquals(1, MixedSearch.mix("tea", feeds, savedOnes, limit = 1).size)
    }
}

class ItemSearchTest {
    private fun item(
        key: String,
        title: String,
        added: Long,
        description: String = "",
        tags: List<String> = emptyList(),
        note: String = "",
    ) = Item(key, "https://$key", title, description = description, createdAt = 0, readingList = ReadingListEntry(ReadingState.ToRead, added), tags = tags, note = note)

    @Test
    fun `finds every word in the title, site, description, tags, note and highlights, title matches first`() {
        val items =
            listOf(
                item("a.example/1", "Coffee", 1, description = "All about green tea"),
                item("b.example/2", "Green tea at home", 2),
                item("c.example/3", "Biscuits", 3, tags = listOf("Green"), note = "with tea"),
                item("d.example/4", "Café life", 4).copy(highlights = listOf(Highlight("h", "a green tea field", createdAt = 1))),
                item("e.example/5", "Nothing", 5),
            )
        assertEquals(listOf("b.example/2", "d.example/4", "c.example/3", "a.example/1"), ItemSearch.search(items, "green TEA").map { it.key })
        assertEquals(listOf("d.example/4"), ItemSearch.search(items, "cafe").map { it.key })
        assertEquals(listOf("e.example/5"), ItemSearch.search(items, "e.example").map { it.key })
        assertTrue(ItemSearch.search(items, " ").isEmpty())
    }
}

class SavedSearchTest {
    @Test
    fun `the Saved chip shows only saved articles, and folders are searched`() {
        val reading = Item("a.example/1", "https://a.example/1", "Tea one", createdAt = 1, readingList = ReadingListEntry(ReadingState.ToRead, 1), folder = "Brewing")
        val tagged = Item("b.example/2", "https://b.example/2", "Tea two", createdAt = 2, tags = listOf("x"))
        assertEquals(listOf(reading), MixedSearch.mix("tea", emptyList(), listOf(reading, tagged), SearchFilter.Saved).map { (it as SearchHit.FromItem).item })
        assertEquals(listOf(reading), ItemSearch.search(listOf(reading, tagged), "brewing"))
    }
}
