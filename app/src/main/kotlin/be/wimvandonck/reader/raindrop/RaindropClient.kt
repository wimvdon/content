package be.wimvandonck.reader.raindrop

import be.wimvandonck.reader.services.ServiceException
import be.wimvandonck.reader.services.ServiceProblem
import be.wimvandonck.reader.sync.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant

/** A Raindrop.io bookmark ("raindrop"), as far as Reader uses it. */
data class Raindrop(
    val id: Long,
    val link: String,
    val title: String,
    val excerpt: String,
    val note: String,
    val cover: String?,
    /** When it was saved, in milliseconds. */
    val created: Long,
    /** Raindrop's "favorite" flag: Reader's Liked. */
    val important: Boolean,
    val tags: List<String>,
    /** -1 is Unsorted, -99 the trash, otherwise a collection. */
    val collectionId: Long,
    /** When it last changed, in milliseconds (0 when Raindrop doesn't say). */
    val lastUpdate: Long = 0,
)

/** A Raindrop.io collection; [parentId] is null for a top-level one. */
data class RaindropCollection(
    val id: Long,
    val title: String,
    val parentId: Long?,
)

/**
 * Raindrop.io's REST API (#112), called straight from the phone with the user's token. [token] gives
 * the current token; [refresh] gets a new one when Raindrop refuses it (they last two weeks). Only
 * this package knows Raindrop's ways.
 */
class RaindropClient(
    private val http: OkHttpClient,
    private val token: suspend () -> String?,
    private val refresh: suspend () -> String?,
    private val apiBase: String = API_BASE,
) {
    suspend fun collections(): List<RaindropCollection> = parseCollections(call("GET", "/collections"))

    suspend fun childCollections(): List<RaindropCollection> = parseCollections(call("GET", "/collections/childrens"))

    suspend fun createCollection(
        title: String,
        parentId: Long? = null,
    ): RaindropCollection {
        val body = buildMap<String, Any?> {
            put("title", title)
            if (parentId != null) put("parent", mapOf("\$id" to parentId))
        }
        return parseCollection((parse(call("POST", "/collection", Json.write(body))) as? Map<*, *>)?.get("item")) ?: throw ServiceException(ServiceProblem.Refused)
    }

    /** One page (from 0) of every raindrop outside the trash, newest first. */
    suspend fun raindrops(page: Int): List<Raindrop> = parseRaindrops(call("GET", "/raindrops/0?perpage=$PAGE&page=$page&sort=-created"))

    suspend fun create(fields: Map<String, Any?>): Raindrop = item(call("POST", "/raindrop", Json.write(fields)))

    suspend fun update(
        id: Long,
        fields: Map<String, Any?>,
    ): Raindrop = item(call("PUT", "/raindrop/$id", Json.write(fields)))

    /** Moves it to Raindrop's trash; one already gone counts as removed. */
    suspend fun delete(id: Long) {
        try {
            call("DELETE", "/raindrop/$id")
        } catch (e: NotFound) {
            // Already gone: that is what was wanted.
        }
    }

    private fun item(text: String): Raindrop = parseRaindrop((parse(text) as? Map<*, *>)?.get("item")) ?: throw ServiceException(ServiceProblem.Refused)

    private class NotFound : Exception()

    private suspend fun call(
        method: String,
        path: String,
        body: String? = null,
    ): String {
        val first = token() ?: throw ServiceException(ServiceProblem.SignedOut)
        return try {
            send(method, path, body, first)
        } catch (e: Unauthorized) {
            // An expired token: once more with a fresh one.
            val fresh = refresh() ?: throw ServiceException(ServiceProblem.SignedOut)
            try {
                send(method, path, body, fresh)
            } catch (again: Unauthorized) {
                throw ServiceException(ServiceProblem.SignedOut)
            }
        }
    }

    private class Unauthorized : Exception()

    private suspend fun send(
        method: String,
        path: String,
        body: String?,
        token: String,
    ): String =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(apiBase + path).header("Authorization", "Bearer $token").header("Accept", "application/json")
            when (method) {
                "GET" -> builder.get()
                "DELETE" -> builder.delete()
                "PUT" -> builder.put((body ?: "{}").toRequestBody(JSON_TYPE))
                else -> builder.post((body ?: "{}").toRequestBody(JSON_TYPE))
            }
            val response =
                try {
                    http.newCall(builder.build()).execute()
                } catch (e: IOException) {
                    throw ServiceException(ServiceProblem.Offline, e)
                }
            response.use { r ->
                val text =
                    try {
                        r.body.string()
                    } catch (e: IOException) {
                        throw ServiceException(ServiceProblem.Offline, e)
                    }
                when {
                    r.isSuccessful -> text
                    r.code == 401 -> throw Unauthorized()
                    r.code == 404 -> throw NotFound()
                    r.code == 429 -> throw ServiceException(ServiceProblem.RateLimited)
                    else -> throw ServiceException(ServiceProblem.Refused)
                }
            }
        }

    companion object {
        const val API_BASE = "https://api.raindrop.io/rest/v1"

        /** Raindrop's largest page. */
        const val PAGE = 50

        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun parse(text: String): Any? =
            try {
                Json.parse(text)
            } catch (e: Json.ParseException) {
                throw ServiceException(ServiceProblem.Refused, e)
            }

        private fun Any?.asLong(): Long? = (this as? Number)?.toLong()

        private fun idOf(value: Any?): Long? = (value as? Map<*, *>)?.get("\$id").asLong()

        fun parseRaindrops(text: String): List<Raindrop> = ((parse(text) as? Map<*, *>)?.get("items") as? List<*>).orEmpty().mapNotNull(::parseRaindrop)

        fun parseRaindrop(value: Any?): Raindrop? {
            val m = value as? Map<*, *> ?: return null
            val id = m["_id"].asLong() ?: return null
            val link = (m["link"] as? String)?.takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) } ?: return null
            return Raindrop(
                id = id,
                link = link,
                title = (m["title"] as? String).orEmpty(),
                excerpt = (m["excerpt"] as? String).orEmpty(),
                note = (m["note"] as? String).orEmpty(),
                cover = (m["cover"] as? String)?.takeIf { it.startsWith("https://") },
                created = (m["created"] as? String)?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L,
                important = m["important"] == true,
                tags = (m["tags"] as? List<*>).orEmpty().filterIsInstance<String>(),
                collectionId = idOf(m["collection"]) ?: m["collectionId"].asLong() ?: -1L,
                lastUpdate = (m["lastUpdate"] as? String)?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L,
            )
        }

        fun parseCollections(text: String): List<RaindropCollection> = ((parse(text) as? Map<*, *>)?.get("items") as? List<*>).orEmpty().mapNotNull(::parseCollection)

        fun parseCollection(value: Any?): RaindropCollection? {
            val m = value as? Map<*, *> ?: return null
            val id = m["_id"].asLong() ?: return null
            return RaindropCollection(id, (m["title"] as? String).orEmpty(), idOf(m["parent"]))
        }
    }
}
