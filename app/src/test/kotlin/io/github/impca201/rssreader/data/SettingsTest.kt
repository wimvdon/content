package io.github.impca201.rssreader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `theme mode decides darkness unless it follows the system`() {
        assertTrue(Appearance(ThemeMode.System).isDark(systemDark = true))
        assertFalse(Appearance(ThemeMode.Light).isDark(systemDark = true))
        assertTrue(Appearance(ThemeMode.Dark).isDark(systemDark = false))
    }

    @Test
    fun `save and share targets survive storage, and the old Save setting carries over`() {
        val app = ShareTarget.App("com.example.notes", "com.example.notes.ShareActivity", "Notes")
        assertEquals(app, ShareTarget.decode(ShareTarget.encode(app), ShareTarget.Sheet))
        assertEquals(ShareTarget.Sheet, ShareTarget.decode(ShareTarget.encode(ShareTarget.Sheet), ShareTarget.Instapaper))
        val noActivity = ShareTarget.App("com.example.notes", null, "Notes")
        assertEquals(noActivity, ShareTarget.decode(ShareTarget.encode(noActivity), ShareTarget.Sheet))
        // Values written by v0.0.1.
        assertEquals(ShareTarget.Instapaper, ShareTarget.decode("Instapaper", ShareTarget.Sheet))
        assertEquals(ShareTarget.Sheet, ShareTarget.decode("ShareMenu", ShareTarget.Instapaper))
        // Nothing stored, or something unreadable: the default.
        assertEquals(ShareTarget.Instapaper, ShareTarget.decode(null, ShareTarget.Instapaper))
        assertEquals(ShareTarget.Sheet, ShareTarget.decode("garbage", ShareTarget.Sheet))
    }

    @Test
    fun `defaults match the design`() {
        val settings = Settings()
        assertFalse(settings.appearance.dynamicColor)
        assertEquals(ShareTarget.Instapaper, settings.saveTarget)
        assertEquals(ShareTarget.Sheet, settings.shareTarget)
        assertEquals(ReaderBackground.Default, settings.reader.background)
        assertFalse(settings.markReadOnScroll)
    }
}
