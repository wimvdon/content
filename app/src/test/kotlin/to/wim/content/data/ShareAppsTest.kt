package to.wim.content.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareAppsTest {
    private data class App(
        val id: String,
        val label: String,
    )

    private fun rank(
        apps: List<App>,
        counts: Map<String, Int> = emptyMap(),
    ) = ShareApps.rank(apps, counts, { it.id }, { it.label }).map { it.id }

    @Test
    fun `apps picked most in Content's share sheet come first (#251)`() {
        val apps = listOf(App("com.example.b", "Bee"), App("com.example.a", "Ant"), App("com.whatsapp", "WhatsApp"))
        assertEquals(listOf("com.example.b", "com.whatsapp", "com.example.a"), rank(apps, mapOf("com.example.b" to 3)))
    }

    @Test
    fun `never picked, well-known messaging and mail apps, then by name`() {
        val apps = listOf(App("com.example.z", "zebra"), App("com.google.android.gm", "Gmail"), App("com.example.a", "Ant"), App("com.whatsapp", "WhatsApp"))
        assertEquals(listOf("com.whatsapp", "com.google.android.gm", "com.example.a", "com.example.z"), rank(apps))
    }

    @Test
    fun `an app with several share targets counts each one apart`() {
        assertEquals("com.example", ShareApps.idOf("com.example", null))
        assertEquals("com.example/.Send", ShareApps.idOf("com.example", ".Send"))
        val apps = listOf(App("com.whatsapp/.Contact", "WhatsApp contact"), App("com.whatsapp/.Send", "WhatsApp"))
        // Both are WhatsApp, so both are well known; then by name.
        assertEquals(listOf("com.whatsapp/.Send", "com.whatsapp/.Contact"), rank(apps))
    }

    @Test
    fun `counts survive storage`() {
        val counts = ShareApps.count(ShareApps.count(emptyMap(), "a"), "a") + ("b" to 1)
        assertEquals(mapOf("a" to 2, "b" to 1), ShareApps.decode(ShareApps.encode(counts)))
        assertEquals(emptyMap<String, Int>(), ShareApps.decode(null))
        assertEquals(emptyMap<String, Int>(), ShareApps.decode("nonsense"))
    }
}
