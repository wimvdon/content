package be.wimvandonck.reader.raindrop

import be.wimvandonck.reader.services.RemoteEntry
import be.wimvandonck.reader.services.ServiceException
import be.wimvandonck.reader.services.ServiceProblem
import be.wimvandonck.reader.sync.Json
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Raindrop.io's answers as its API reference gives them; nothing goes to the network. */
class RaindropSavedTest {
    private val calls = mutableListOf<String>()
    private var answer: (method: String, path: String, body: String) -> Pair<Int, String> = { _, _, _ -> 200 to "{}" }
    private val tokensSent = mutableListOf<String>()
    private var token: String? = "old"
    private var refreshed = 0

    private val http =
        OkHttpClient
            .Builder()
            .addInterceptor(
                Interceptor { chain ->
                    val request = chain.request()
                    val body = request.body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8() }.orEmpty()
                    val path = request.url.encodedPath.removePrefix("/rest/v1") + (request.url.encodedQuery?.let { "?$it" } ?: "")
                    tokensSent += request.header("Authorization").orEmpty()
                    calls += "${request.method} $path $body".trim()
                    val (code, text) = answer(request.method, path, body)
                    Response
                        .Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message("")
                        .body(text.toResponseBody("application/json".toMediaType()))
                        .build()
                },
            ).build()

    private var clock = 1_000_000L
    private val client = RaindropClient(http, { token }, { refreshed++; token = "new"; token })
    private val service = RaindropSaved(client, now = { clock })

    private fun raindrop(
        id: Long,
        collection: Long = -1,
        link: String = "https://a.example/$id",
        important: Boolean = false,
        note: String = "",
    ) = """{"_id":$id,"link":"$link","title":"T$id","excerpt":"E$id","note":"$note","cover":"https://img.example/$id.jpg","created":"2026-10-01T10:00:00.000Z","important":$important,"tags":["Work"],"collection":{"${'$'}id":$collection}}"""

    private val collections = """{"result":true,"items":[{"_id":10,"title":"Recipes"},{"_id":11,"title":"Archive"}]}"""
    private val children = """{"result":true,"items":[{"_id":12,"title":"Pasta","parent":{"${'$'}id":10}}]}"""

    private fun account(vararg pages: String) {
        answer = { _, path, _ ->
            when {
                path == "/collections" -> 200 to collections
                path == "/collections/childrens" -> 200 to children
                path.startsWith("/raindrops/0") -> {
                    val page = Regex("[?&]page=(\\d+)").find(path)!!.groupValues[1].toInt()
                    200 to """{"result":true,"items":[${pages.getOrElse(page) { "" }}]}"""
                }
                else -> 404 to "{}"
            }
        }
    }

    @Test
    fun `lists every raindrop page by page, with collections as folders and the archive`() =
        runBlocking {
            val full = (1..50).joinToString(",") { raindrop(it.toLong()) }
            account(full, listOf(raindrop(60, 12, important = true, note = "Try this"), raindrop(61, 11), raindrop(62, 10)).joinToString(","))
            val changes = service.changes(null, full = true)
            assertTrue(calls.contains("GET /raindrops/0?perpage=50&page=0&sort=-created"))
            assertTrue(calls.contains("GET /raindrops/0?perpage=50&page=1&sort=-created"))
            assertTrue(changes.complete)
            assertEquals(53, changes.entries.size)
            val unsorted = changes.entries.first()
            assertNull(unsorted.folder)
            assertFalse(unsorted.archived)
            assertEquals("T1", unsorted.title)
            assertEquals("E1", unsorted.description)
            assertEquals(listOf("Work"), unsorted.tags)
            assertEquals(1_790_848_800_000L, unsorted.addedAt)
            val nested = changes.entries.single { it.remoteId == "60" }
            assertEquals("Recipes / Pasta", nested.folder)
            assertTrue(nested.liked)
            assertEquals("Try this", nested.note)
            val archived = changes.entries.single { it.remoteId == "61" }
            assertTrue(archived.archived)
            assertNull(archived.folder)
            assertEquals("Recipes", changes.entries.single { it.remoteId == "62" }.folder)
            assertTrue(tokensSent.all { it == "Bearer old" })
        }

    @Test
    fun `the quick sync after a change lists everything at most every few minutes`() =
        runBlocking {
            account(raindrop(1))
            service.changes(null, full = true)
            val listed = calls.size
            clock += 60_000
            val quick = service.changes("c", full = false)
            assertEquals(listed, calls.size)
            assertTrue(quick.entries.isEmpty())
            assertFalse(quick.complete)
            clock += RaindropSaved.MIN_LIST_INTERVAL_MS
            assertEquals(1, service.changes("c", full = false).entries.size)
        }

    @Test
    fun `adding puts it in Unsorted, its folder or the archive, making collections when missing`() =
        runBlocking {
            account()
            service.changes(null, full = true)
            calls.clear()
            answer = { method, path, body ->
                when {
                    method == "POST" && path == "/collection" -> 200 to """{"result":true,"item":{"_id":20,"title":"Later"}}"""
                    method == "POST" && path == "/raindrop" -> {
                        val sent = Json.parse(body) as Map<*, *>
                        val collection = ((sent["collection"] as Map<*, *>)["\$id"] as Number).toLong()
                        200 to """{"result":true,"item":${raindrop(30, collection, link = sent["link"] as String, note = (sent["note"] as? String).orEmpty())}}"""
                    }
                    else -> 404 to "{}"
                }
            }
            val toRead = service.add("https://b.example/1", "B", listOf("x"), archived = false, folder = null, note = "Mine")
            assertNull(toRead.folder)
            assertEquals("Mine", toRead.note)
            val sent = Json.parse(calls.single().substringAfter("/raindrop ")) as Map<*, *>
            assertEquals("https://b.example/1", sent["link"])
            assertEquals("B", sent["title"])
            assertEquals(listOf("x"), sent["tags"])
            assertEquals(-1L, ((sent["collection"] as Map<*, *>)["\$id"] as Number).toLong())
            assertEquals("Recipes / Pasta", service.add("https://b.example/2", "B", emptyList(), false, "recipes / pasta", "").folder)
            assertTrue(service.add("https://b.example/3", "B", emptyList(), true, null, "").archived)
            // A folder Raindrop doesn't have yet is made once.
            assertEquals("Later", service.add("https://b.example/4", "B", emptyList(), false, "Later", "").folder)
            service.add("https://b.example/5", "B", emptyList(), false, "Later", "")
            assertEquals(1, calls.count { it.startsWith("POST /collection ") })
        }

    @Test
    fun `the archive collection is made on first use`() =
        runBlocking {
            answer = { method, path, body ->
                when {
                    path == "/collections" -> 200 to """{"items":[]}"""
                    path == "/collections/childrens" -> 200 to """{"items":[]}"""
                    path.startsWith("/raindrops/0") -> 200 to """{"items":[]}"""
                    method == "POST" && path == "/collection" -> 200 to """{"item":{"_id":77,"title":"Archive"}}"""
                    method == "POST" && path == "/raindrop" -> 200 to """{"item":${raindrop(5, 77)}}"""
                    else -> 404 to "{}"
                }
            }
            service.changes(null, full = true)
            assertTrue(service.add("https://a.example/5", "T", emptyList(), true, null, "").archived)
            assertTrue(calls.any { it.startsWith("POST /collection ") && it.contains("\"Archive\"") })
        }

    @Test
    fun `an update sends only what changed`() =
        runBlocking {
            account()
            service.changes(null, full = true)
            calls.clear()
            answer = { _, _, _ -> 200 to """{"item":${raindrop(5, 11, important = true)}}""" }
            val have = RemoteEntry("5", "https://a.example/5", "T5", addedAt = 1, archived = false, liked = false, tags = listOf("Work"), note = "")
            // Nothing changed: no call.
            service.update(have, have)
            assertTrue(calls.isEmpty())
            val result = service.update(have, have.copy(liked = true, archived = true))
            val sent = Json.parse(calls.single().substringAfter("/raindrop/5 ")) as Map<*, *>
            assertEquals(setOf("important", "collection"), sent.keys)
            assertEquals(11L, ((sent["collection"] as Map<*, *>)["\$id"] as Number).toLong())
            assertTrue(result.archived)
            assertTrue(result.liked)
        }

    @Test
    fun `removing moves it to Raindrop's trash, and one already gone counts as removed`() =
        runBlocking {
            answer = { _, _, _ -> 404 to "{}" }
            service.remove("5")
            assertEquals("DELETE /raindrop/5", calls.single())
        }

    @Test
    fun `a refused token is renewed once and the call tried again`() =
        runBlocking {
            answer = { _, _, _ -> if (token == "old") 401 to "{}" else 200 to """{"items":[]}""" }
            assertTrue(client.raindrops(0).isEmpty())
            assertEquals(1, refreshed)
            assertEquals(listOf("Bearer old", "Bearer new"), tokensSent)
            // Still refused with a new one: signed out.
            answer = { _, _, _ -> 401 to "{}" }
            val e = assertThrows(ServiceException::class.java) { runBlocking { client.raindrops(0) } }
            assertEquals(ServiceProblem.SignedOut, e.problem)
        }

    @Test
    fun `without a token, and when Raindrop is busy, the sync stops with the reason`() {
        token = null
        assertEquals(ServiceProblem.SignedOut, assertThrows(ServiceException::class.java) { runBlocking { client.collections() } }.problem)
        token = "old"
        answer = { _, _, _ -> 429 to "{}" }
        assertEquals(ServiceProblem.RateLimited, assertThrows(ServiceException::class.java) { runBlocking { client.collections() } }.problem)
        answer = { _, _, _ -> 200 to "not json" }
        assertEquals(ServiceProblem.Refused, assertThrows(ServiceException::class.java) { runBlocking { client.collections() } }.problem)
    }

    @Test
    fun `raindrops without a web address are left out`() {
        val items = RaindropClient.parseRaindrops("""{"items":[{"_id":1,"link":"file:///x"},{"_id":2},${raindrop(3)}]}""")
        assertEquals(listOf(3L), items.map { it.id })
    }

    @Test
    fun `a folder that arrived flat still lands in its own collection after a restart`() =
        runBlocking {
            // Nothing listed yet in this session: the origins kept with the state say where "Recipes / Pasta" is.
            service.origins = mapOf("Recipes / Pasta" to "12")
            answer = { method, path, _ -> if (method == "PUT" && path == "/raindrop/5") 200 to """{"result":true,"item":${raindrop(5, collection = 12)}}""" else 404 to "{}" }
            val have = RemoteEntry("5", "https://a.example/5", "T5", addedAt = 1, archived = false, liked = false, tags = listOf("Work"))
            val after = service.update(have, have.copy(folder = "Recipes / Pasta"))
            assertEquals(listOf("""PUT /raindrop/5 {"collection":{"${'$'}id":12}}"""), calls)
            assertEquals("5", after.remoteId)
            // And after listing, the origins are Raindrop's own.
            account()
            service.changes(null)
            assertEquals("12", service.origins["Recipes / Pasta"])
            assertEquals(listOf("Recipes", "Recipes / Pasta"), service.folders())
        }

    @Test
    fun `a raindrop says when it last changed`() {
        val r = RaindropClient.parseRaindrop(Json.parse("""{"_id":1,"link":"https://a.example/1","lastUpdate":"2026-10-02T10:00:00.000Z"}"""))!!
        assertEquals(java.time.Instant.parse("2026-10-02T10:00:00Z").toEpochMilli(), service.entryOf(r).changedAt)
    }
}
