package be.wimvandonck.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingListsTest {
    private val feedArticle = Article("https://news.example/feed", "7", "From the feed", "https://news.example/story", "Ann", "<p>Feed text</p>", 50, 60)
    private val library =
        Library(feeds = listOf(Feed("https://news.example/feed", "News", siteUrl = "https://news.example")), articles = listOf(feedArticle))
            .addToReadingList("https://news.example/story", null, 1_000).first
            .addToReadingList("https://other.example/a", "Other", 2_000).first
            .addToReadingList("https://third.example/b", "Third", 3_000).first
            .setLiked("other.example/a", true)
            .setTags("other.example/a", listOf("work"))
            .setTags("third.example/b", listOf("Home"))
            .setReadingState("third.example/b", ReadingState.Archived)

    @Test
    fun `reading times`() {
        assertEquals(0, ReadingLists.minutes(0))
        assertEquals(1, ReadingLists.minutes(10))
        assertEquals(5, ReadingLists.minutes(1_000))
        assertEquals(3, ReadingLists.minutesLeft(10, 0.75f))
        assertEquals(1, ReadingLists.minutesLeft(10, 0.98f))
        assertEquals(0, ReadingLists.minutesLeft(10, 1f))
        assertEquals(4, ReadingLists.words("<p>One two <b>three</b></p><p>four</p>"))
    }

    @Test
    fun `an item shows like the feed article it came from, or with its own description`() {
        val story = library.item("news.example/story")!!
        val article = ReadingLists.article(story, library)
        assertEquals("<p>Feed text</p>", article.html)
        assertEquals("Ann", article.author)
        assertEquals(ReadingLists.readerKey(story), article.key)
        assertEquals("news.example/story", ReadingLists.itemKeyOf(article.key))
        assertNull(ReadingLists.itemKeyOf(feedArticle.key))
        assertEquals("News", ReadingLists.source(story, library)?.title)
        // A text from a service or the extraction wins.
        assertEquals("<p>Full</p>", ReadingLists.article(story, library, "<p>Full</p>").html)

        val other = library.item("other.example/a")!!.copy(description = "About <a>")
        assertEquals("<p>About &lt;a&gt;</p>", ReadingLists.article(other, library).html)
        val source = ReadingLists.source(other, library)!!
        assertEquals("other.example", source.title)
        assertEquals("https://other.example/favicon.ico", source.iconUrl)
        assertTrue(ReadingLists.article(library.item("third.example/b")!!, library).read)
    }
}
