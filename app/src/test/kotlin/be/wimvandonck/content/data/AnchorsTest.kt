package be.wimvandonck.content.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnchorsTest {
    private fun highlight(
        text: String,
        quote: String,
        occurrence: Int = 0,
    ): Highlight {
        var start = -1
        repeat(occurrence + 1) { start = text.indexOf(quote, start + 1) }
        return Anchors.create(text, start, start + quote.length, "h", 1)
    }

    private fun found(
        anchor: Anchor,
        text: String,
    ): String =
        when (anchor) {
            is Anchor.Exact -> text.substring(anchor.start, anchor.end)
            is Anchor.Approximate -> "~" + text.substring(anchor.start, anchor.end)
            Anchor.NotFound -> "not found"
        }

    @Test
    fun `a highlight keeps its quote, its surroundings and which occurrence it is`() {
        val text = "The cat sat. The cat ran. The dog sat."
        val h = highlight(text, "The cat", occurrence = 1)
        assertEquals("The cat", h.quote)
        assertEquals("The cat sat. ", h.prefix)
        assertEquals(" ran. The dog sat.", h.suffix)
        assertEquals(1, h.occurrence)
        assertEquals(0, highlight(text, "The cat").occurrence)
    }

    @Test
    fun `the same text finds it where it was made`() {
        val text = "Coffee is good. Tea is better, they say."
        val anchor = Anchors.resolve(highlight(text, "Tea is better"), text)
        assertEquals(Anchor.Exact(16, 29), anchor)
    }

    @Test
    fun `typographic variants don't matter`() {
        val made = "He said \u201Cit\u2019s fine\u201D \u2014 and left\u2026 The \uFB01nal word."
        val other = "He said \"it's   fine\" - and left... The final word."
        assertEquals("\"it's   fine\" - and left...", found(Anchors.resolve(highlight(made, "\u201Cit\u2019s fine\u201D \u2014 and left\u2026"), other), other))
        assertEquals("final word", found(Anchors.resolve(highlight(made, "\uFB01nal word"), other), other))
        // And the other way round, with a soft hyphen and a no-break space.
        val back = "He said \u201Cit\u2019s\u00A0fine\u201D. The fi\u00ADnal word."
        assertEquals("it\u2019s\u00A0fine", found(Anchors.resolve(highlight(other, "it's   fine"), back), back))
        assertEquals("fi\u00ADnal word", found(Anchors.resolve(highlight(other, "final word"), back), back))
    }

    @Test
    fun `letter case is a last resort`() {
        val made = "Chapter One. The beginning of it all."
        val other = "CHAPTER ONE. The beginning of it all."
        assertEquals("CHAPTER ONE", found(Anchors.resolve(highlight(made, "Chapter One"), other), other))
    }

    @Test
    fun `a repeated quote is told apart by its surroundings, even when more text came before`() {
        val made = "Yes. I said yes to the plan. Then no. Later I said yes to the cake."
        val second = highlight(made, "said yes", occurrence = 1)
        // Another version adds a "said yes" in front: the occurrence count is off, the surroundings aren't.
        val other = "Intro: he said yes too. Yes. I said yes to the plan. Then no. Later I said yes to the cake."
        val anchor = Anchors.resolve(second, other) as Anchor.Exact
        assertEquals(other.lastIndexOf("said yes"), anchor.start)
        // In the same text, each occurrence is found again.
        assertEquals(made.indexOf("said yes"), (Anchors.resolve(highlight(made, "said yes"), made) as Anchor.Exact).start)
        assertEquals(made.lastIndexOf("said yes"), (Anchors.resolve(second, made) as Anchor.Exact).start)
    }

    @Test
    fun `a repeated quote without telling surroundings falls back to its occurrence`() {
        val made = "na na na na"
        val h = Highlight("h", "na", occurrence = 2, createdAt = 1)
        assertEquals(Anchor.Exact(6, 8), Anchors.resolve(h, made))
        // An occurrence that no longer exists takes the first.
        assertEquals(Anchor.Exact(0, 2), Anchors.resolve(h.copy(occurrence = 9), made))
    }

    @Test
    fun `a changed quote is found roughly between its surroundings`() {
        val made = "Some opening words here. The quick brown fox jumps over the lazy dog. And then the story goes on."
        val h = highlight(made, "The quick brown fox jumps over the lazy dog.")
        val other = "Some opening words here. The quick red fox leapt over the lazy dog. And then the story goes on."
        assertEquals("~The quick red fox leapt over the lazy dog.", found(Anchors.resolve(h, other), other))
    }

    @Test
    fun `a changed quote with other surroundings is found by its first and last words`() {
        val made = "Intro text that will change. The quick brown fox jumps over the lazy dog today. Outro that changes."
        val h = highlight(made, "The quick brown fox jumps over the lazy dog today.")
        val other = "Completely new intro. The quick brown fox leaps across the lazy dog today. Different end."
        assertEquals("~The quick brown fox leaps across the lazy dog today.", found(Anchors.resolve(h, other), other))
    }

    @Test
    fun `a quote that isn't in the text is not found, and kept`() {
        val h = highlight("Alpha beta gamma delta epsilon.", "gamma delta")
        assertEquals(Anchor.NotFound, Anchors.resolve(h, "Something else entirely, nothing in common at all."))
        assertEquals(Anchor.NotFound, Anchors.resolve(h.copy(quote = " "), "gamma delta"))
    }

    @Test
    fun `typographic variants fold to one form`() {
        assertTrue(Anchors.fold(" a — b ") == "a - b")
    }
}
