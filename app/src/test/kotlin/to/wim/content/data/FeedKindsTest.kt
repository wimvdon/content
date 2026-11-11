package to.wim.content.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedKindsTest {
    private val rss = Feed("https://example.com/feed", "Example")
    private val other = Feed("https://blog.example.org/rss.xml", "Blog")
    private val letter = Feed("https://content-worker.wim.to/newsletters/k7q2m9abcdef/0123456789abcdef0123456789abcdef", "Letter")

    @Test
    fun `a newsletter address's feed is a newsletter feed, any other an RSS feed`() {
        assertEquals(FeedKind.Rss, FeedKinds.of(rss))
        assertEquals(FeedKind.Newsletter, FeedKinds.of(letter))
        assertEquals(listOf(rss, other), FeedKinds.only(listOf(rss, letter, other), FeedKind.Rss))
        assertEquals(listOf(letter), FeedKinds.only(listOf(rss, letter, other), FeedKind.Newsletter))
    }

    @Test
    fun `reordering one kind keeps the other kind's feeds in their places`() {
        // Topic: a, N, b, c (N a newsletter feed); RSS feeds dragged into c, a, b.
        assertEquals(listOf("c", "N", "a", "b"), FeedKinds.withOrder(listOf("a", "N", "b", "c"), listOf("c", "a", "b")))
        // Newsletter feeds reordered among RSS ones.
        assertEquals(listOf("N2", "a", "N1"), FeedKinds.withOrder(listOf("N1", "a", "N2"), listOf("N2", "N1")))
    }

    @Test
    fun `a feed dragged in from another topic keeps its place among its kind`() {
        assertEquals(listOf("b", "N", "a", "x"), FeedKinds.withOrder(listOf("a", "N", "b"), listOf("b", "a", "x")))
        assertEquals(listOf("b", "N", "x", "a"), FeedKinds.withOrder(listOf("a", "N", "b"), listOf("b", "x", "a")))
        assertEquals(listOf("x"), FeedKinds.withOrder(emptyList(), listOf("x")))
    }
}
