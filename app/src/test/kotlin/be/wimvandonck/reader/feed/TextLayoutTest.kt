package be.wimvandonck.reader.feed

import org.junit.Assert.assertEquals
import org.junit.Test

class TextLayoutTest {
    private fun text(vararg html: String) = html.map { ArticleBlock.Text(it) }

    @Test
    fun `every paragraph is its own block`() {
        assertEquals(text("One <b>bold</b>", "Two <a href=\"https://a.example/\">link</a>"), TextLayout.blocks("<p>One <b>bold</b></p>\n<p>Two <a href=\"https://a.example/\">link</a></p>"))
    }

    @Test
    fun `divs and two line breaks separate paragraphs, one line break stays`() {
        assertEquals(
            text("First", "Second<br>same paragraph", "Third", "Fourth"),
            TextLayout.blocks("<div>First</div><div>Second<br>same paragraph<br><br>Third</div>Fourth"),
        )
        assertEquals(text("A", "B"), TextLayout.blocks("A<br/>\n<br />B<br>"))
    }

    @Test
    fun `headings keep their level`() {
        assertEquals(
            listOf(ArticleBlock.Heading(2, "Part <i>two</i>"), ArticleBlock.Text("Body")),
            TextLayout.blocks("<h2>Part <i>two</i></h2><p>Body</p>"),
        )
    }

    @Test
    fun `lists keep bullets, numbers and nesting`() {
        val blocks = TextLayout.blocks("<p>Intro</p><ul><li>Apples</li><li>Pears<ol><li>Conference</li><li><p>Doyenné</p><p>More</p></li></ol></li></ul><p>After</p>")
        assertEquals(
            listOf(
                ArticleBlock.Text("Intro"),
                ArticleBlock.ListBlock(
                    listOf(
                        ArticleBlock.ListItem("Apples", "•", 1),
                        ArticleBlock.ListItem("Pears", "•", 1),
                        ArticleBlock.ListItem("Conference", "1.", 2),
                        ArticleBlock.ListItem("Doyenné", "2.", 2),
                        ArticleBlock.ListItem("More", "", 2),
                    ),
                ),
                ArticleBlock.Text("After"),
            ),
            blocks,
        )
    }

    @Test
    fun `quotes keep their paragraphs together`() {
        assertEquals(
            listOf(ArticleBlock.Text("He said:"), ArticleBlock.Quote(listOf("One", "Two")), ArticleBlock.Text("Then")),
            TextLayout.blocks("<p>He said:</p><blockquote><p>One</p><p>Two</p></blockquote><p>Then</p>"),
        )
    }

    @Test
    fun `code keeps its line breaks and characters`() {
        assertEquals(
            listOf(ArticleBlock.Code("val a = 1 < 2\n  println(a)")),
            TextLayout.blocks("<pre><code>val a = 1 &lt; 2\n  println(a)\n</code></pre>"),
        )
    }

    @Test
    fun `empty markup gives nothing`() {
        assertEquals(emptyList<ArticleBlock>(), TextLayout.blocks("<p> </p><div><br></div>"))
    }
}
