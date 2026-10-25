package be.wimvandonck.content.sync

import be.wimvandonck.content.data.ListView
import be.wimvandonck.content.data.ReaderBackground
import be.wimvandonck.content.data.ReaderFont
import be.wimvandonck.content.data.ReaderSpacing
import be.wimvandonck.content.data.ReaderStyle
import be.wimvandonck.content.data.ServiceImport
import be.wimvandonck.content.data.Settings
import be.wimvandonck.content.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            listView = ListView.Large,
        )

    @Test
    fun `settings go to shared names and come back the same`() {
        val values = SyncSettings.of(custom)
        assertEquals(
            mapOf(
                SyncSettings.FULL_ARTICLES to "false",
                SyncSettings.MARK_READ_ON_SCROLL to "true",
                SyncSettings.RECIPIENTS to "[]",
            ),
            values,
        )
        val applied = SyncSettings.applyTo(Settings(), values)
        assertEquals(false, applied.fullArticles)
        assertEquals(true, applied.markReadOnScroll)
    }

    @Test
    fun `view settings stay on each device`() {
        val values = SyncSettings.of(custom)
        val view = setOf("theme", "dynamicColor", "reader.font", "reader.textSize", "reader.spacing", "reader.background", "reader.autoDark", "showImages", "listView")
        assertFalse(values.keys.any { it in view })
        // Another device still writing them changes nothing here.
        val fromOlder =
            mapOf(
                "theme" to "light", "dynamicColor" to "false", "reader.font" to "serif", "reader.textSize" to "16",
                "reader.spacing" to "compact", "reader.background" to "default", "reader.autoDark" to "false",
                "showImages" to "true", "listView" to "small",
            )
        assertEquals(custom, SyncSettings.applyTo(custom, fromOlder))
    }

    @Test
    fun `save and share targets and the sync choice are not synced`() {
        val values = SyncSettings.of(custom)
        assertFalse(values.keys.any { "save" in it.lowercase() || "share" in it.lowercase() || "sync" in it.lowercase() })
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
        val values = mapOf(SyncSettings.FULL_ARTICLES to "yes", SyncSettings.MARK_READ_ON_SCROLL to "1")
        val applied = SyncSettings.applyTo(custom, values)
        assertEquals(false, applied.fullArticles)
        assertEquals(true, applied.markReadOnScroll)
        assertEquals(custom, SyncSettings.applyTo(custom, emptyMap()))
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
}
