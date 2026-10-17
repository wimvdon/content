package be.wimvandonck.reader.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlTest {
    @Test
    fun `clean removes scripts, embeds, images and inline styling`() {
        val cleaned =
            Html.clean(
                """
                <p style="color:#000" class="x">Hello <font color="red">world</font></p>
                <script>alert(1)</script><iframe src="https://ads"></iframe>
                <img src="https://tracker/pixel.gif" width="1"><!-- note -->
                <a href="https://example.com">link</a>
                """.trimIndent(),
            )
        assertEquals("<p>Hello world</p>\n\n\n<a href=\"https://example.com\">link</a>", cleaned)
    }

    @Test
    fun `plain text decodes entities and collapses whitespace`() {
        assertEquals("Fish & chips — “tasty”", Html.toPlainText("<b>Fish</b> &amp;  chips\n&mdash; &#8220;tasty&#x201D;"))
        assertEquals("One Two", Html.toPlainText("<p>One</p><p>Two</p>"))
        assertEquals("Kept", Html.toPlainText("<style>p{}</style>Kept"))
    }

    @Test
    fun `unknown entities are left as they are`() {
        assertEquals("&bogus; &#0;", Html.decodeEntities("&bogus; &#0;"))
    }

    @Test
    fun `snippet cuts long text at a word`() {
        val snippet = Html.snippet("word ".repeat(100), maxLength = 22)
        assertTrue(snippet.endsWith("…"))
        assertTrue(snippet.length <= 23)
        assertFalse(snippet.contains("wor…"))
        assertEquals("Short", Html.snippet("<p>Short</p>"))
    }

    @Test
    fun `escape makes text safe in HTML and XML attributes`() {
        assertEquals("a &amp; &lt;b&gt; &quot;c&quot; &#39;d&#39;", Html.escape("a & <b> \"c\" 'd'"))
    }
}
