package be.wimvandonck.reader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchResultsTest {
    private fun article(
        id: String,
        feed: String,
        time: Long,
        title: String = "Article $id",
    ) = Article(feedUrl = feed, id = id, title = title, link = null, author = null, html = "", publishedAt = time, fetchedAt = time)

    private fun saved(
        key: String,
        added: Long,
        folder: String? = null,
        tags: List<String> = emptyList(),
        url: String = "https://$key",
        state: ReadingState = ReadingState.ToRead,
    ) = Item(key, url, "Saved $key", createdAt = 0, readingList = ReadingListEntry(state, added), folder = folder, tags = tags)

    private val news = "https://news.example/rss"
    private val tech = "https://tech.example/rss"
    private val library =
        Library(
            feeds = listOf(Feed(news, "News", folderId = "f1"), Feed(tech, "Tech")),
            articles = listOf(article("n1", news, 10), article("t1", tech, 30), article("n2", news, 50, title = "Green tea")),
            items =
                listOf(
                    saved("a.example/1", 20, folder = "Work", tags = listOf("ai")),
                    saved("youtu.be/x", 40, url = "https://youtu.be/x"),
                    saved("a.example/2", 60, state = ReadingState.Archived, tags = listOf("AI")),
                    Item("b.example/3", "https://b.example/3", "Not saved", createdAt = 70, tags = listOf("ai")),
                ),
        )

    private fun names(hits: List<SearchHit>) =
        hits.map {
            when (it) {
                is SearchHit.FromFeed -> it.article.id
                is SearchHit.FromItem -> it.item.key
            }
        }

    private fun browse(filters: SearchFilters) = names(SearchResults.of("", library, emptyList(), emptyList(), filters))

    @Test
    fun `without words or filters it shows everything, newest first, as the old All views did`() {
        assertEquals(listOf("a.example/2", "n2", "youtu.be/x", "t1", "a.example/1", "n1"), browse(SearchFilters()))
        assertEquals(listOf("n2", "t1", "n1"), browse(SearchFilters(SearchFilter.Feeds)))
        // Saved's All, archived ones included; an item that isn't saved isn't there.
        assertEquals(listOf("a.example/2", "youtu.be/x", "a.example/1"), browse(SearchFilters(SearchFilter.Saved)))
    }

    @Test
    fun `feeds by folder or by feed`() {
        assertEquals(listOf("n2", "n1"), browse(SearchFilters(SearchFilter.Feeds, feedFolderId = "f1")))
        assertEquals(listOf("t1"), browse(SearchFilters(SearchFilter.Feeds, feedUrl = tech)))
    }

    @Test
    fun `Saved by folder, tag (case ignored) and type`() {
        assertEquals(listOf("a.example/1"), browse(SearchFilters(SearchFilter.Saved, savedFolder = "work")))
        assertEquals(listOf("a.example/2", "a.example/1"), browse(SearchFilters(SearchFilter.Saved, tag = "ai")))
        assertEquals(listOf("youtu.be/x"), browse(SearchFilters(SearchFilter.Saved, type = SavedType.Video)))
    }

    @Test
    fun `typed words search, and the filters narrow the matches`() {
        val hits = SearchResults.of("green", library, listOf(library.articles[2]), listOf(library.items[0]), SearchFilters())
        assertEquals(listOf("n2", "a.example/1"), names(hits))
        val onlyNews = SearchResults.of("green", library, listOf(library.articles[2]), listOf(library.items[0]), SearchFilters(SearchFilter.Feeds, feedUrl = tech))
        assertEquals(emptyList<String>(), names(onlyNews))
    }

    @Test
    fun `choosing another kind clears the other kind's filters`() {
        val filters = SearchFilters(SearchFilter.Saved, savedFolder = "Work", tag = "ai")
        assertEquals(SearchFilters(SearchFilter.Feeds), filters.withScope(SearchFilter.Feeds))
        assertEquals(true, filters.narrowed)
        assertEquals(false, SearchFilters(SearchFilter.Saved).narrowed)
    }

    @Test
    fun `Feeds shows rows, All and Saved show notes`() {
        assertEquals(false, SearchResults.showsNotes(SearchFilter.Feeds))
        assertEquals(true, SearchResults.showsNotes(SearchFilter.All))
        assertEquals(true, SearchResults.showsNotes(SearchFilter.Saved))
    }
}
