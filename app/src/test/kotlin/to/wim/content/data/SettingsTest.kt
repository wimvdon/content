package to.wim.content.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {
    @Test
    fun `text size stays within its range`() {
        assertEquals(ReaderStyle.MAX_TEXT_SIZE, ReaderStyle().withTextSize(99).textSize)
        assertEquals(ReaderStyle.MIN_TEXT_SIZE, ReaderStyle().withTextSize(1).textSize)
        assertEquals(ReaderStyle.MAX_TEXT_SIZE, ReaderStyle(textSize = ReaderStyle.MAX_TEXT_SIZE).larger().textSize)
        assertEquals(ReaderStyle.DEFAULT_TEXT_SIZE - 1, ReaderStyle().smaller().textSize)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an out of range text size is rejected`() {
        ReaderStyle(textSize = 2)
    }

    @Test
    fun `sepia is light and dark is dark whatever the app theme`() {
        assertFalse(ReaderStyle(background = ReaderBackground.Sepia).isDark(appDark = true))
        assertTrue(ReaderStyle(background = ReaderBackground.Dark).isDark(appDark = false))
        assertTrue(ReaderStyle(background = ReaderBackground.Default).isDark(appDark = true))
        assertFalse(ReaderStyle(background = ReaderBackground.Default).isDark(appDark = false))
    }

    @Test
    fun `auto dark turns sepia and theme pages dark when the app is dark`() {
        val sepia = ReaderStyle(background = ReaderBackground.Sepia, autoDark = true)
        assertTrue(sepia.isDark(appDark = true))
        assertEquals(ReaderBackground.Dark, sepia.pageBackground(appDark = true))
        assertFalse(sepia.isDark(appDark = false))
        assertEquals(ReaderBackground.Sepia, sepia.pageBackground(appDark = false))
        // Off: sepia stays sepia at night.
        assertEquals(ReaderBackground.Sepia, sepia.copy(autoDark = false).pageBackground(appDark = true))
    }

    @Test
    fun `theme mode decides darkness unless it follows the system`() {
        assertTrue(Appearance(ThemeMode.System).isDark(systemDark = true))
        assertFalse(Appearance(ThemeMode.Light).isDark(systemDark = true))
        assertTrue(Appearance(ThemeMode.Dark).isDark(systemDark = false))
    }

    @Test
    fun `save and share targets survive storage`() {
        val app = ShareTarget.App("com.example.notes", "com.example.notes.ShareActivity", "Notes")
        assertEquals(app, ShareTarget.decode(ShareTarget.encode(app), ShareTarget.Sheet))
        assertEquals(ShareTarget.Sheet, ShareTarget.decode(ShareTarget.encode(ShareTarget.Sheet), ShareTarget.Saved))
        assertEquals(ShareTarget.Saved, ShareTarget.decode(ShareTarget.encode(ShareTarget.Saved), ShareTarget.Sheet))
        val noActivity = ShareTarget.App("com.example.notes", null, "Notes")
        assertEquals(noActivity, ShareTarget.decode(ShareTarget.encode(noActivity), ShareTarget.Sheet))
        // Nothing stored, or something unreadable: the default.
        assertEquals(ShareTarget.Saved, ShareTarget.decode(null, ShareTarget.Saved))
        assertEquals(ShareTarget.Sheet, ShareTarget.decode("garbage", ShareTarget.Sheet))
    }

    @Test
    fun `defaults match the design`() {
        val settings = Settings()
        assertFalse(settings.appearance.dynamicColor)
        assertEquals(ReaderBackground.Default, settings.reader.background)
        assertFalse(settings.markReadOnScroll)
        assertEquals(ListView.Medium, settings.listView)
        assertFalse(settings.reader.autoDark)
    }

    @Test
    fun `always open the website opens a feed article's web link, else the reader`() {
        val article = Article("https://a/feed", "1", "Title", " https://example.com/post ", null, "", null, 0L)
        // Off by default: the reader.
        assertEquals(false, Settings().alwaysOpenWebsite)
        assertNull(ArticleOpening.websiteFor(false, article))
        assertEquals("https://example.com/post", ArticleOpening.websiteFor(true, article))
        // No link, or not a web link: the reader all the same.
        assertNull(ArticleOpening.websiteFor(true, article.copy(link = null)))
        assertNull(ArticleOpening.websiteFor(true, article.copy(link = "javascript:alert(1)")))
    }
}
