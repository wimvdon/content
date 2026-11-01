package to.wim.content.ui.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderToolbarVisibilityTest {
    @Test
    fun showsWhileTheTopBarShows() {
        assertTrue(ReaderToolbarVisibility.shown(collapsedFraction = 0f, atEnd = false))
        assertTrue(ReaderToolbarVisibility.shown(collapsedFraction = 0.3f, atEnd = false))
    }

    @Test
    fun stepsAsideWhileReadingDown() {
        assertFalse(ReaderToolbarVisibility.shown(collapsedFraction = 1f, atEnd = false))
        assertFalse(ReaderToolbarVisibility.shown(collapsedFraction = 0.5f, atEnd = false))
    }

    @Test
    fun comesBackAtTheEndOfTheArticle() {
        assertTrue(ReaderToolbarVisibility.shown(collapsedFraction = 1f, atEnd = true))
    }
}
