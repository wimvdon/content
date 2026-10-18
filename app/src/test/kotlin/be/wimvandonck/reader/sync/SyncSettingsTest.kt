package be.wimvandonck.reader.sync

import be.wimvandonck.reader.data.ListView
import be.wimvandonck.reader.data.ReaderBackground
import be.wimvandonck.reader.data.ReaderFont
import be.wimvandonck.reader.data.ReaderSpacing
import be.wimvandonck.reader.data.ReaderStyle
import be.wimvandonck.reader.data.Settings
import be.wimvandonck.reader.data.ShareTarget
import be.wimvandonck.reader.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val values = SyncSettings.of(custom)
        assertEquals("dark", values[SyncSettings.THEME])
        assertEquals("sepia", values[SyncSettings.READER_BACKGROUND])
        assertEquals("22", values[SyncSettings.READER_TEXT_SIZE])
        assertEquals("large", values[SyncSettings.LIST_VIEW])
        assertEquals(custom.copy(saveTarget = Settings().saveTarget, unreadOnly = Settings().unreadOnly), SyncSettings.applyTo(Settings(), values))
    }

    @Test
    fun `save and share targets and the sync choice are not synced`() {
        val values = SyncSettings.of(custom)
        assertFalse(values.keys.any { "save" in it.lowercase() || "share" in it.lowercase() || "sync" in it.lowercase() })
    }

    @Test
    fun `names this app doesn't know are kept, its own come from the device`() {
        val base = mapOf("web.only" to "x", SyncSettings.THEME to "light")
        val values = SyncSettings.of(custom, base)
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
    fun `the Instapaper sign-in that older versions synced is dropped`() {
        val base = mapOf("instapaper.token" to "t", "instapaper.username" to "me", "web.only" to "x")
        val values = SyncSettings.of(Settings(), base)
        assertFalse(values.keys.any { it.startsWith("instapaper.") })
        assertEquals("x", values["web.only"])
    }

    @Test
    fun `Unread or All is not synced, and the name older versions synced is dropped`() {
        val values = SyncSettings.of(custom, base = mapOf("unreadOnly" to "true"))
        assertFalse("unreadOnly" in values)
        assertEquals(true, SyncSettings.applyTo(Settings(unreadOnly = true), mapOf("unreadOnly" to "false")).unreadOnly)
    }
}
