package be.wimvandonck.reader.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleBlocksTest {
    private val base = "https://example.com/posts/1"

    @Test
    fun `splits text, pictures with captions and embeds in order`() {
        val blocks =
            ArticleBlocks.split(
                """
                <p>Intro</p>
                <figure><img src="/a.jpg" alt="A"><figcaption>The <b>caption</b></figcaption></figure>
                <p>Middle <img src="b.png"> text</p>
                <iframe src="https://www.youtube-nocookie.com/embed/abc123XYZ_-" allowfullscreen></iframe>
                <p>End</p>
                """.trimIndent(),
                base,
            )
        assertEquals(
            listOf(
                ArticleBlock.Text::class,
                ArticleBlock.Image::class,
                ArticleBlock.Text::class,
                ArticleBlock.Image::class,
                ArticleBlock.Text::class,
                ArticleBlock.Embed::class,
                ArticleBlock.Text::class,
            ),
            blocks.map { it::class },
        )
        assertEquals(ArticleBlock.Image("https://example.com/a.jpg", "The caption"), blocks[1])
        assertEquals(ArticleBlock.Image("https://example.com/posts/b.png", null), blocks[3])
        assertEquals(
            ArticleBlock.Embed(EmbedKind.YouTube, "https://www.youtube.com/watch?v=abc123XYZ_-", "https://i.ytimg.com/vi/abc123XYZ_-/hqdefault.jpg"),
            blocks[5],
        )
    }

    @Test
    fun `a caption written twice in a row shows once`() {
        val blocks = ArticleBlocks.split("<p>The bell. Credit: A. Photographer</p><p>The bell.  Credit: A. Photographer</p><p>Text</p>", base)
        assertEquals(listOf(ArticleBlock.Text("The bell. Credit: A. Photographer"), ArticleBlock.Text("Text")), blocks)
    }

    @Test
    fun `recognises Vimeo, plain videos and other embeds`() {
        assertEquals(EmbedKind.Vimeo, ArticleBlocks.embedFor("https://player.vimeo.com/video/76979871", base)?.kind)
        assertEquals("https://vimeo.com/76979871", ArticleBlocks.embedFor("https://player.vimeo.com/video/76979871", base)?.url)
        assertEquals(EmbedKind.Other, ArticleBlocks.embedFor("//open.spotify.com/embed/track/1", base)?.kind)
        assertEquals("https://open.spotify.com/embed/track/1", ArticleBlocks.embedFor("//open.spotify.com/embed/track/1", base)?.url)
        assertNull(ArticleBlocks.embedFor("javascript:alert(1)", base))
        val video = ArticleBlocks.split("<video poster=\"p.jpg\"><source src=\"v.mp4\"></video>", base).single()
        assertEquals(ArticleBlock.Embed(EmbedKind.Video, "https://example.com/posts/v.mp4", "https://example.com/posts/p.jpg"), video)
    }

    @Test
    fun `skips tracking pixels and picks the largest srcset picture`() {
        val blocks =
            ArticleBlocks.split(
                """<img src="https://feeds.feedburner.com/~r/x/~4/abc" width="1" height="1">""" +
                    """<img src="https://stats.wordpress.com/b.gif?x=1">""" +
                    """<img src="s.jpg" srcset="s.jpg 400w, l.jpg 1600w, m.jpg 800w">""" +
                    """<img data-src="lazy.jpg" src="data:image/gif;base64,R0lGOD">""",
                base,
            )
        assertEquals(
            listOf("https://example.com/posts/l.jpg", "https://example.com/posts/lazy.jpg"),
            blocks.map { (it as ArticleBlock.Image).url },
        )
    }

    @Test
    fun `picture addresses become absolute https`() {
        assertEquals("https://cdn.example.com/a.jpg", ArticleBlocks.imageUrl("http://cdn.example.com/a.jpg", base))
        assertEquals("https://cdn.example.com/a.jpg", ArticleBlocks.imageUrl("//cdn.example.com/a.jpg", base))
        assertEquals("https://example.com/img/a.jpg", ArticleBlocks.imageUrl("/img/a.jpg", base))
        assertNull(ArticleBlocks.imageUrl("data:image/png;base64,xx", base))
        assertNull(ArticleBlocks.imageUrl("ftp://example.com/a.jpg", base))
        assertNull(ArticleBlocks.imageUrl("  ", base))
    }

    @Test
    fun `finds the first real picture`() {
        assertEquals(
            "https://example.com/posts/real.jpg",
            ArticleBlocks.firstImage("<img src=\"px.gif\" width=\"1\"><p>x</p><img src=\"real.jpg\">", base),
        )
        assertNull(ArticleBlocks.firstImage("<p>No pictures</p>", base))
    }

    @Test
    fun `the same picture in another size counts as the same`() {
        assertTrue(ArticleBlocks.isSameImage("https://a.com/wp/photo-1024x768.jpg", "https://a.com/wp/photo.jpg?w=2000"))
        assertTrue(ArticleBlocks.isSameImage("https://a.com/x/photo@2x.png", "https://cdn.a.com/photo.png"))
        assertFalse(ArticleBlocks.isSameImage("https://a.com/one.jpg", "https://a.com/two.jpg"))
        assertFalse(ArticleBlocks.isSameImage(null, "https://a.com/one.jpg"))
    }

    @Test
    fun `text without content is dropped, and a tracking pixel doesn't split a paragraph`() {
        val blocks = ArticleBlocks.split("<p>One</p><img src=\"https://x.com/1x1.gif\" width=\"1\"><p>Two and <img src=\"https://x.com/p.gif\" height=\"1\">three</p><p>   </p>", base)
        assertEquals(listOf(ArticleBlock.Text("One"), ArticleBlock.Text("Two and three")), blocks)
    }
}
