package be.wimvandonck.reader.instapaper

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
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Instapaper's answers recorded from API v2's reference; nothing goes to the network. */
class InstapaperReadingListTest {
    private val calls = mutableListOf<String>()
    private var answer: (method: String, path: String, body: String) -> Pair<Int, String> = { _, _, _ -> 200 to "{}" }

    private val http =
        OkHttpClient
            .Builder()
            .addInterceptor(
                Interceptor { chain ->
                    val request = chain.request()
                    val body = request.body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8() }.orEmpty()
                    val path = request.url.encodedPath.removePrefix("/api/2") + (request.url.encodedQuery?.let { "?$it" } ?: "")
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

    private val service = InstapaperReadingList(InstapaperClient(http, "token"), now = { 1_755_000_600_000 })

    private fun bookmark(
        id: Long,
        url: String?,
        folder: Long? = null,
        archived: Boolean = false,
        tags: String = "[]",
    ) = """{"id":$id,"url":${url?.let { "\"$it\"" }},"title":"T$id","description":"D","image":"https://img/$id.jpg","progress":{"percentage":0.42,"timestamp":1},"liked":true,"archived":$archived,"time":1755000000,"folder_id":$folder,"tags":$tags}"""

    @Test
    fun `reads every change since a time, page by page, with deletions and folders`() =
        runBlocking {
            val full = (1..499).joinToString(",") { bookmark(it.toLong(), "https://a.example/$it") }
            answer = { _, path, _ ->
                when {
                    path.startsWith("/folders") -> 200 to """{"folders":[{"id":3,"title":"Work","position":1}]}"""
                    path.startsWith("/tags") -> 200 to """{"tags":[{"id":9,"name":"Recipes"}]}"""
                    path.contains("offset=0") -> 200 to """{"bookmarks":[$full],"deleted_ids":[900],"total":502}"""
                    else -> 200 to """{"bookmarks":[${bookmark(600, "https://b.example/x", folder = 3, tags = """[{"id":9,"name":"Recipes"}]""")},${bookmark(601, null)}],"deleted_ids":[],"total":502}"""
                }
            }
            val changes = service.changes("1755000000")
            assertTrue(calls.any { it == "GET /bookmarks?since=1755000000&limit=500&offset=0" })
            assertTrue(calls.any { it == "GET /bookmarks?since=1755000000&limit=500&offset=500" })
            // 499 + the one with a link from the second page; the private one without an address is left out.
            assertEquals(500, changes.entries.size)
            assertEquals(listOf("900"), changes.deleted)
            val x = changes.entries.last()
            assertEquals(listOf("Recipes"), x.tags)
            // Folders became tags (#187): the folder there is not read.
            assertNull(x.folder)
            assertEquals(0.42f, x.progress, 0.001f)
            assertEquals(1_755_000_000_000L, x.addedAt)
            // The next sync starts a little before this one.
            assertEquals((1_755_000_600L - InstapaperReadingList.CURSOR_MARGIN_S).toString(), changes.cursor)
        }

    @Test
    fun `the first sync asks for everything`() =
        runBlocking {
            answer = { _, path, _ -> if (path.startsWith("/bookmarks")) 200 to """{"bookmarks":[],"deleted_ids":[]}""" else 200 to """{"folders":[],"tags":[]}""" }
            service.changes(null)
            assertTrue(calls.any { it.startsWith("GET /bookmarks?since=1&") })
        }

    @Test
    fun `adds with tags and the archive`() =
        runBlocking {
            answer = { _, path, _ ->
                when {
                    path.endsWith("/tags") -> 200 to """{"tags":[{"id":1,"name":"news"}]}"""
                    else -> 200 to bookmark(5, "https://a.example/5")
                }
            }
            val entry = service.add("https://a.example/5", "Five", listOf("news"), archived = true)
            assertEquals("5", entry.remoteId)
            assertTrue(entry.archived)
            assertEquals(listOf("news"), entry.tags)
            assertEquals(
                listOf(
                    """POST /bookmarks {"url":"https://a.example/5","title":"Five"}""",
                    """POST /bookmarks/5/tags {"add_tags":[{"name":"news"}],"remove_tags":[]}""",
                    """POST /bookmarks/5/move {"section":"archive"}""",
                ),
                calls,
            )
        }

    @Test
    fun `sends only what changed, and leaves an Instapaper folder alone`() =
        runBlocking {
            answer = { _, path, _ ->
                when {
                    path.startsWith("/folders") -> 200 to """{"folders":[{"id":3,"title":"Work","position":1}]}"""
                    path.startsWith("/tags") -> 200 to """{"tags":[{"id":9,"name":"Recipes"}]}"""
                    path.startsWith("/bookmarks?") -> 200 to """{"bookmarks":[],"deleted_ids":[]}"""
                    path.endsWith("/tags") -> 200 to """{"tags":[]}"""
                    else -> 200 to bookmark(5, "https://a.example/5")
                }
            }
            service.changes("5")
            calls.clear()
            val have = RemoteEntry("5", "https://a.example/5", "T", addedAt = 1, archived = false, liked = false, tags = listOf("Recipes"), folder = "Work", progress = 0f)
            val after = service.update(have, have.copy(liked = true, tags = listOf("new"), folder = null, progress = 0.5f, title = "Better"))
            assertEquals(
                listOf(
                    "POST /bookmarks/5/like",
                    """POST /bookmarks/5/tags {"add_tags":[{"name":"new"}],"remove_tags":[{"id":9}]}""",
                    """POST /bookmarks/5 {"title":"Better","progress":{"percentage":0.5,"timestamp":1755000600}}""",
                ),
                calls,
            )
            assertNull(after.folder)
            assertEquals("5", after.remoteId)
        }

    @Test
    fun `folders are neither made nor filed into (#187)`() =
        runBlocking {
            answer = { _, path, _ ->
                when {
                    path.startsWith("/folders") -> 200 to """{"folders":[{"id":3,"title":"Work","position":1}]}"""
                    path.startsWith("/tags") -> 200 to """{"tags":[]}"""
                    path.startsWith("/bookmarks?") -> 200 to """{"bookmarks":[],"deleted_ids":[]}"""
                    else -> 200 to bookmark(5, "https://a.example/5")
                }
            }
            service.changes("5")
            calls.clear()
            val have = RemoteEntry("5", "https://a.example/5", "T", addedAt = 1, archived = false, liked = false, tags = emptyList(), folder = null)
            val after = service.update(have, have.copy(folder = "later"))
            assertEquals(emptyList<String>(), calls)
            assertNull(after.folder)
        }

    @Test
    fun `problems become the service's problems, and a bookmark that is already gone is removed`() =
        runBlocking {
            answer = { _, _, _ -> 429 to "slow down" }
            val e = assertThrows(ServiceException::class.java) { runBlocking { service.changes(null) } }
            assertEquals(ServiceProblem.RateLimited, e.problem)
            answer = { _, _, _ -> 400 to "gone" }
            service.remove("5")
            answer = { _, _, _ -> 401 to "no" }
            assertEquals(ServiceProblem.SignedOut, assertThrows(ServiceException::class.java) { runBlocking { service.remove("5") } }.problem)
            assertEquals(mapOf("percentage" to 0.5, "timestamp" to 1L), (Json.parse("""{"percentage":0.5,"timestamp":1}""")))
        }
}
