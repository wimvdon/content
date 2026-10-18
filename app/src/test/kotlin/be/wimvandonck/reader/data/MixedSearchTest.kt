package be.wimvandonck.reader.data

import be.wimvandonck.reader.instapaper.InstapaperBookmark
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
    ) = InstapaperBookmark(id, "https://site.example/$id", title, "", timeSeconds, liked = false, archived = false, folderId = null, tags = emptyList())

    private fun names(hits: List<SearchHit>) =
        hits.map {
            when (it) {
                is SearchHit.FromFeed -> "feed:${it.article.id}"
                is SearchHit.FromSaved -> "saved:${it.bookmark.id}"
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
    fun `feed and saved matches are ranked together, best first, then newest`() {
        val feeds = listOf(article("a", "Coffee news", 5_000), article("b", "Green tea guide", 1_000))
        val savedOnes = listOf(saved(1, "All about green tea", 9), saved(2, "Gardening", 2))
        val hits = MixedSearch.mix("green tea", feeds, savedOnes)
        // Both titles with the phrase come first, the newest of them on top; then the text-only matches, newest first.
        assertEquals(listOf("saved:1", "feed:b", "feed:a", "saved:2"), names(hits))
    }

    @Test
    fun `the chips show only feeds or only saved articles`() {
        val feeds = listOf(article("a", "Tea", 1))
        val savedOnes = listOf(saved(1, "Tea", 1))
        assertEquals(listOf("feed:a"), names(MixedSearch.mix("tea", feeds, savedOnes, SearchFilter.Feeds)))
        assertEquals(listOf("saved:1"), names(MixedSearch.mix("tea", feeds, savedOnes, SearchFilter.Saved)))
        assertEquals(2, MixedSearch.mix("tea", feeds, savedOnes, SearchFilter.All).size)
        assertEquals(1, MixedSearch.mix("tea", feeds, savedOnes, limit = 1).size)
    }
}
