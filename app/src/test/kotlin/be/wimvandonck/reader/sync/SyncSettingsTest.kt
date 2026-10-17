package be.wimvandonck.reader.sync

import be.wimvandonck.reader.data.ListView
import be.wimvandonck.reader.data.ReaderBackground
import be.wimvandonck.reader.data.ReaderFont
import be.wimvandonck.reader.data.ReaderSpacing
import be.wimvandonck.reader.data.ReaderStyle
import be.wimvandonck.reader.data.Settings
import be.wimvandonck.reader.data.ShareTarget
import be.wimvandonck.reader.data.ThemeMode
import be.wimvandonck.reader.instapaper.InstapaperSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SyncSettingsTest {
    private val custom =
        Settings(
            appearance = Settings().appearance.copy(themeMode = ThemeMode.Dark, dynamicColor = true),
            reader = ReaderStyle(ReaderFont.Sans, 22, ReaderSpacing.Relaxed, ReaderBackground.Sepia, autoDark = true),
            saveTarget = ShareTarget.Sheet,
            unreadOnly = false,
            fullArticles = false,
            showImages = false,
            markReadOnScroll = true,
            listView = ListView.Large,
        )

    @Test
    fun `settings go to shared names and come back the same`() {
        val values = SyncSettings.of(custom, null)
        assertEquals("dark", values[SyncSettings.THEME])
        assertEquals("sepia", values[SyncSettings.READER_BACKGROUND])
        assertEquals("22", values[SyncSettings.READER_TEXT_SIZE])
        assertEquals("large", values[SyncSettings.LIST_VIEW])
        assertEquals(custom.copy(saveTarget = Settings().saveTarget), SyncSettings.applyTo(Settings(), values))
    }

    @Test
    fun `save and share targets and the sync choice are not synced`() {
        val values = SyncSettings.of(custom, null)
        assertFalse(values.keys.any { "save" in it.lowercase() || "share" in it.lowercase() || "sync" in it.lowercase() })
    }

    @Test
    fun `names this app doesn't know are kept, its own come from the device`() {
        val base = mapOf("web.only" to "x", SyncSettings.THEME to "light")
        val values = SyncSettings.of(custom, null, base)
        assertEquals("x", values["web.only"])
        assertEquals("dark", values[SyncSettings.THEME])
    }

    @Test
    fun `bad or missing values change nothing`() {
        val values = mapOf(SyncSettings.THEME to "purple", SyncSettings.READER_TEXT_SIZE to "99", SyncSettings.FULL_ARTICLES to "yes")
        val applied = SyncSettings.applyTo(custom, values)
        assertEquals(ThemeMode.Dark, applied.appearance.themeMode)
        assertEquals(ReaderStyle.MAX_TEXT_SIZE, applied.reader.textSize)
        assertEquals(false, applied.fullArticles)
        assertEquals(custom, SyncSettings.applyTo(custom, emptyMap()))
    }

    @Test
    fun `the Instapaper sign-in travels with the settings`() {
        val session = InstapaperSession("token", 42, "me@example.com")
        val values = SyncSettings.of(Settings(), session)
        assertEquals(session, SyncSettings.instapaperOf(values))
        assertNull(SyncSettings.instapaperOf(SyncSettings.of(Settings(), null)))
        assertEquals(InstapaperSession("t", null, ""), SyncSettings.instapaperOf(mapOf(SyncSettings.INSTAPAPER_TOKEN to "t")))
    }
}
