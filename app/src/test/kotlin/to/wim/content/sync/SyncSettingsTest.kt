package to.wim.content.sync

import to.wim.content.data.ListView
import to.wim.content.data.ReaderBackground
import to.wim.content.data.ReaderFont
import to.wim.content.data.ReaderSpacing
import to.wim.content.data.ReaderStyle
import to.wim.content.data.ServiceImport
import to.wim.content.data.Settings
import to.wim.content.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SyncSettingsTest {
    private val custom =
        Settings(
            appearance = Settings().appearance.copy(themeMode = ThemeMode.Dark, dynamicColor = true),
            reader = ReaderStyle(ReaderFont.Sans, 22, ReaderSpacing.Relaxed, ReaderBackground.Sepia, autoDark = true),
            unreadOnly = false,
            fullArticles = false,
            showImages = false,
            markReadOnScroll = true,
            alwaysOpenWebsite = true,
            hiddenWords = "crypto, apple watch",
            listView = ListView.Large,
        )

    @Test
    fun `the Android app's settings go under android and come back the same`() {
        val values = SyncSettings.of(custom)
        assertEquals(
            mapOf(
                "android.theme" to "Dark",
                "android.dynamicColor" to "true",
                "android.reader.font" to "Sans",
                "android.reader.textSize" to "22",
                "android.reader.spacing" to "Relaxed",
                "android.reader.background" to "Sepia",
                "android.reader.autoDark" to "true",
                "android.fullArticles" to "false",
                "android.showImages" to "false",
                "android.markReadOnScroll" to "true",
                "android.alwaysOpenWebsite" to "true",
                "android.listView" to "Large",
                "android.hiddenWords" to "crypto, apple watch",
                SyncSettings.RECIPIENTS to "[]",
            ),
            values,
        )
        // Another Android device gets all of it; Unread | All is where that device is, not a setting.
        assertEquals(custom.copy(unreadOnly = true), SyncSettings.applyTo(Settings(), values))
    }

    @Test
    fun `the web app's settings, and the names older versions wrote, change nothing here`() {
        val fromElsewhere =
            mapOf(
                "web.theme" to "light", "web.fullArticles" to "true", "web.listView" to "small",
                "theme" to "light", "dynamicColor" to "false", "reader.font" to "serif", "reader.textSize" to "16",
                "reader.spacing" to "compact", "reader.background" to "default", "reader.autoDark" to "false",
                "showImages" to "true", "listView" to "small",
            )
        assertEquals(custom, SyncSettings.applyTo(custom, fromElsewhere))
        // And they stay in the file for the web app.
        assertEquals("light", SyncSettings.of(custom, fromElsewhere)["web.theme"])
    }

    @Test
    fun `Load full articles and Mark as read on scroll shared before are read once, then dropped`() {
        val before = mapOf(SyncSettings.LEGACY_FULL_ARTICLES to "false", SyncSettings.LEGACY_MARK_READ_ON_SCROLL to "true")
        val applied = SyncSettings.applyTo(Settings(), before)
        assertEquals(false, applied.fullArticles)
        assertEquals(true, applied.markReadOnScroll)
        // The Android names win once they are there.
        assertEquals(true, SyncSettings.applyTo(Settings(), before + (SyncSettings.FULL_ARTICLES to "true")).fullArticles)
        val saved = SyncSettings.of(Settings(), before)
        assertFalse(SyncSettings.LEGACY_FULL_ARTICLES in saved)
        assertFalse(SyncSettings.LEGACY_MARK_READ_ON_SCROLL in saved)
    }

    @Test
    fun `save and share targets and the sync choice are not synced`() {
        val values = SyncSettings.of(custom)
        assertFalse(values.keys.any { "save" in it.lowercase() || "share" in it.lowercase() || "sync" in it.lowercase() || "notify" in it.lowercase() })
    }

    @Test
    fun `names this app doesn't know are kept, its own come from the device`() {
        val base = mapOf("web.only" to "x", SyncSettings.FULL_ARTICLES to "true")
        val values = SyncSettings.of(custom, base)
        assertEquals("x", values["web.only"])
        assertEquals("false", values[SyncSettings.FULL_ARTICLES])
    }

    @Test
    fun `bad or missing values change nothing`() {
        val values =
            mapOf(
                SyncSettings.FULL_ARTICLES to "yes", SyncSettings.MARK_READ_ON_SCROLL to "1", SyncSettings.THEME to "Purple",
                SyncSettings.READER_TEXT_SIZE to "99", SyncSettings.LIST_VIEW to "large",
            )
        assertEquals(custom, SyncSettings.applyTo(custom, values))
        assertEquals(custom, SyncSettings.applyTo(custom, emptyMap()))
    }

    @Test
    fun `the Instapaper sign-in goes in the file only when given, else the file's stays`() {
        assertNull(SyncSettings.of(custom)[SyncSettings.INSTAPAPER_TOKEN])
        assertEquals("t", SyncSettings.of(custom, instapaperToken = "t")[SyncSettings.INSTAPAPER_TOKEN])
        assertEquals("", SyncSettings.of(custom, mapOf(SyncSettings.INSTAPAPER_TOKEN to "t"), instapaperToken = "")[SyncSettings.INSTAPAPER_TOKEN])
        assertEquals("t", SyncSettings.of(custom, mapOf(SyncSettings.INSTAPAPER_TOKEN to "t"))[SyncSettings.INSTAPAPER_TOKEN])
        assertEquals("t", SyncSettings.instapaperToken(mapOf(SyncSettings.INSTAPAPER_TOKEN to "t")))
    }

    @Test
    fun `Unread or All is not synced`() {
        assertFalse("unreadOnly" in SyncSettings.of(custom))
        assertEquals(true, SyncSettings.applyTo(Settings(unreadOnly = true), mapOf("unreadOnly" to "false")).unreadOnly)
    }

    @Test
    fun `what was imported travels with the account, and scopes from before are dropped`() {
        val record = ServiceImport(at = 5, count = 12, everything = false)
        val values = SyncSettings.of(Settings(imports = mapOf("instapaper" to record)), base = mapOf("services.instapaper.scope" to "*", "services.raindrop.imported" to "x"))
        assertEquals(setOf("services.instapaper.imported"), values.keys.filter { it.startsWith("services.") }.toSet())
        assertEquals(record, SyncSettings.applyTo(Settings(), values).imports["instapaper"])
        // Unreadable values change nothing.
        assertEquals(Settings(), SyncSettings.applyTo(Settings(), mapOf("services.instapaper.imported" to "x")))
        // An import record from before #251 had no choice: everything was imported.
        assertEquals(ServiceImport(5, 2), ServiceImport.decode("""{"at":5,"count":2,"folders":null,"archive":true}"""))
    }

    @Test
    fun `the people shared with travel with the account (#235)`() {
        val mine = Settings(recipients = listOf("ann@example.com", "bob@example.com"))
        val values = SyncSettings.of(mine)
        assertEquals("""["ann@example.com","bob@example.com"]""", values[SyncSettings.RECIPIENTS])
        assertEquals(mine.recipients, SyncSettings.applyTo(Settings(), values).recipients)
        // Missing or not a list: this device's stay.
        assertEquals(mine.recipients, SyncSettings.applyTo(mine, emptyMap()).recipients)
        assertEquals(mine.recipients, SyncSettings.applyTo(mine, mapOf(SyncSettings.RECIPIENTS to "nonsense")).recipients)
    }

    @Test
    fun `the hidden words (#277) travel to other Android devices, emptied too`() {
        val values = SyncSettings.of(Settings(hiddenWords = "crypto"))
        assertEquals("crypto", values[SyncSettings.HIDDEN_WORDS])
        assertEquals("crypto", SyncSettings.applyTo(Settings(), values).hiddenWords)
        // Cleared on another phone: cleared here; missing (an older version): kept.
        assertEquals("", SyncSettings.applyTo(custom, mapOf(SyncSettings.HIDDEN_WORDS to "")).hiddenWords)
        assertEquals(custom.hiddenWords, SyncSettings.applyTo(custom, emptyMap()).hiddenWords)
        // The web app's settings never set it.
        assertEquals(custom.hiddenWords, SyncSettings.applyTo(custom, mapOf("web.hiddenWords" to "x")).hiddenWords)
    }
}
