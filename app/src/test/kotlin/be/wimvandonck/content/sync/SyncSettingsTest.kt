package be.wimvandonck.content.sync

import be.wimvandonck.content.data.ListView
import be.wimvandonck.content.data.ReaderBackground
import be.wimvandonck.content.data.ReaderFont
import be.wimvandonck.content.data.ReaderSpacing
import be.wimvandonck.content.data.ReaderStyle
import be.wimvandonck.content.data.ServiceImport
import be.wimvandonck.content.data.ServiceScope
import be.wimvandonck.content.data.Settings
import be.wimvandonck.content.data.ShareTarget
import be.wimvandonck.content.data.ThemeMode
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
        assertEquals(
            mapOf(
                SyncSettings.FULL_ARTICLES to "false",
                SyncSettings.MARK_READ_ON_SCROLL to "true",
                "services.instapaper.scope" to "*",
                "services.raindrop.scope" to "*",
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
        assertFalse(values.keys.any { it in SyncSettings.DROPPED_VIEW })
        // Another device, or an older version, still writing them changes nothing here.
        val fromOlder =
            mapOf(
                "theme" to "light", "dynamicColor" to "false", "reader.font" to "serif", "reader.textSize" to "16",
                "reader.spacing" to "compact", "reader.background" to "default", "reader.autoDark" to "false",
                "showImages" to "true", "listView" to "small",
            )
        assertEquals(custom, SyncSettings.applyTo(custom, fromOlder))
    }

    @Test
    fun `view settings that older versions synced are dropped from the file`() {
        val base = mapOf("theme" to "light", "listView" to "small", "reader.font" to "serif", "web.only" to "x")
        val values = SyncSettings.of(custom, base)
        assertFalse(values.keys.any { it in SyncSettings.DROPPED_VIEW })
        assertEquals("x", values["web.only"])
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

    @Test
    fun `what each service syncs and what was imported travel with the account`() {
        val record = ServiceImport(at = 5, count = 12, folders = setOf("Research", ServiceScope.TO_READ), archive = false)
        val mine = Settings(scopes = mapOf("raindrop" to ServiceScope(setOf("Research"))), imports = mapOf("instapaper" to record))
        val values = SyncSettings.of(mine)
        assertEquals("[\"Research\"]", values["services.raindrop.scope"])
        assertEquals("*", values["services.instapaper.scope"])
        val applied = SyncSettings.applyTo(Settings(), values)
        assertEquals(ServiceScope(setOf("Research")), applied.scopes["raindrop"])
        assertEquals(record, applied.imports["instapaper"])
        // An import this device doesn't know of stays as the file has it.
        val fromFile = mapOf("services.raindrop.imported" to ServiceImport.encode(record))
        assertEquals(fromFile["services.raindrop.imported"], SyncSettings.of(Settings(), fromFile)["services.raindrop.imported"])
        // Unreadable values change nothing.
        assertEquals(Settings(), SyncSettings.applyTo(Settings(), mapOf("services.raindrop.scope" to "{", "services.raindrop.imported" to "x")))
    }
}
