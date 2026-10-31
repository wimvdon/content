package to.wim.content.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleTextTest {
    private val article = ArticleText(listOf("The first paragraph says yes.", "The second one says yes too.", "Third."))

    @Test
    fun `a selection is found in its paragraph, in the whole text`() {
        val range = article.locate(1, " says yes ")!!
        assertEquals("says yes", article.text.substring(range.first, range.last + 1))
        // In the second paragraph, not the first.
        assertTrue(range.first > article.paragraphs[0].length)
        assertNull(article.locate(2, "says"))
        assertNull(article.locate(5, "x"))
        assertNull(article.locate(0, "  "))
    }

    @Test
    fun `highlights are drawn in each paragraph they touch, approximate ones marked`() {
        val second = article.locate(1, "says yes")!!
        val h1 = Anchors.create(article.text, second.first, second.last + 1, "a", 1)
        // Across the end of the first paragraph and the start of the second.
        val across = Anchors.create(article.text, article.text.indexOf("paragraph says"), article.text.indexOf("second") + 6, "b", 2)
        val changed = ArticleText(listOf("The first paragraph says yes.", "The second one now says yes too.", "Third."))
        val placed = changed.place(listOf(h1, across, Highlight("c", "not here at all", createdAt = 3)))
        assertEquals(listOf(Mark("b", 10, 29, false)), placed.marks[0])
        assertEquals(listOf("b", "a"), placed.marks[1]!!.map { it.highlightId })
        assertEquals(Mark("b", 0, 10, false), placed.marks[1]!![0])
        assertEquals("says yes", changed.paragraphs[1].substring(placed.marks[1]!![1].start, placed.marks[1]!![1].end))
        assertEquals(Anchor.NotFound, placed.anchors["c"])
        assertNull(placed.marks[2])
    }
}
