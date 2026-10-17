package be.wimvandonck.reader.instapaper

import be.wimvandonck.reader.sync.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder

/** Instapaper API v2, called straight from the phone with the user's access token. */
class InstapaperClient(
    private val http: OkHttpClient,
    private val token: String,
    private val apiBase: String = API_BASE,
) : InstapaperService {
    override suspend fun list(
        view: InstapaperView,
        offset: Int,
        limit: Int,
    ): InstapaperPage = parsePage(call("GET", listPath(view, offset, limit)))

    override suspend fun save(
        url: String,
        title: String?,
    ) {
        val body = buildMap<String, Any?> {
            put("url", url)
            if (!title.isNullOrBlank()) put("title", title)
        }
        call("POST", "/bookmarks", Json.write(body))
    }

    override suspend fun move(
        id: Long,
        section: String,
    ) {
        call("POST", "/bookmarks/$id/move", Json.write(mapOf("section" to section)))
    }

    override suspend fun like(
        id: Long,
        liked: Boolean,
    ): Boolean {
        val text = call(if (liked) "POST" else "DELETE", "/bookmarks/$id/like")
        return parseBookmark(runCatching { Json.parse(text) }.getOrNull())?.liked ?: liked
    }

    override suspend fun delete(id: Long) {
        call("DELETE", "/bookmarks/$id")
    }

    override suspend fun setTags(
        id: Long,
        add: List<String>,
        removeIds: List<Long>,
    ): List<InstapaperTag> {
        val body = mapOf("add_tags" to add.map { mapOf("name" to it) }, "remove_tags" to removeIds.map { mapOf("id" to it) })
        return parseTagList(call("POST", "/bookmarks/$id/tags", Json.write(body)), "tags")
    }

    override suspend fun folders(): List<InstapaperFolder> = parseFolderList(call("GET", "/folders"))

    override suspend fun createFolder(title: String): InstapaperFolder =
        parseFolder(parseJson(call("POST", "/folders", Json.write(mapOf("title" to title))))) ?: throw InstapaperException(InstapaperProblem.ServerError)

    override suspend fun deleteFolder(id: Long) {
        call("DELETE", "/folders/$id")
    }

    override suspend fun reorderFolders(order: List<Pair<Long, Int>>): List<InstapaperFolder> {
        val body = mapOf("order" to order.map { (id, position) -> mapOf("folder_id" to id, "position" to position) })
        return parseFolderList(call("POST", "/folders/reorder", Json.write(body)))
    }

    override suspend fun tags(): List<InstapaperTag> = parseTagList(call("GET", "/tags"), "tags")

    override suspend fun renameTag(
        id: Long,
        name: String,
    ): InstapaperTag =
        parseTag(parseJson(call("POST", "/tags/$id", Json.write(mapOf("name" to name))))) ?: throw InstapaperException(InstapaperProblem.ServerError)

    private suspend fun call(
        method: String,
        path: String,
        body: String? = null,
    ): String =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(apiBase + path).header("Authorization", "Bearer $token").header("Accept", "application/json")
            when (method) {
                "GET" -> builder.get()
                "DELETE" -> builder.delete()
                else -> builder.post((body ?: "").toRequestBody(JSON_TYPE))
            }
            val response =
                try {
                    http.newCall(builder.build()).execute()
                } catch (e: IOException) {
                    throw InstapaperException(InstapaperProblem.Offline, e)
                }
            response.use { r ->
                val text =
                    try {
                        r.body.string()
                    } catch (e: IOException) {
                        throw InstapaperException(InstapaperProblem.Offline, e)
                    }
                if (!r.isSuccessful) throw InstapaperException(problemForStatus(r.code))
                text
            }
        }

    companion object {
        const val API_BASE = "https://www.instapaper.com/api/2"
        const val PAGE_SIZE = 25
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

        fun problemForStatus(code: Int): InstapaperProblem =
            when {
                code == 401 -> InstapaperProblem.SignedOut
                code == 403 -> InstapaperProblem.NotApproved
                code == 429 -> InstapaperProblem.RateLimited
                code in 400..499 -> InstapaperProblem.Invalid
                else -> InstapaperProblem.ServerError
            }

        /** The path and query for one page of one list. */
        fun listPath(
            view: InstapaperView,
            offset: Int,
            limit: Int,
        ): String {
            val query =
                buildList {
                    add("limit=$limit")
                    add("offset=$offset")
                    when (view) {
                        InstapaperView.Home -> add("section=home")
                        InstapaperView.Archive -> add("section=archive")
                        InstapaperView.Liked -> add("section=liked")
                        is InstapaperView.InFolder -> {
                            add("section=folder")
                            add("folder_id=${view.folderId}")
                        }
                        is InstapaperView.WithTag -> {
                            add("section=tag")
                            add("tag=" + URLEncoder.encode(view.name, "UTF-8"))
                        }
                    }
                }
            return "/bookmarks?" + query.joinToString("&")
        }

        private fun parseJson(text: String): Any? =
            try {
                Json.parse(text)
            } catch (e: Json.ParseException) {
                throw InstapaperException(InstapaperProblem.ServerError, e)
            }

        private fun Any?.asLong(): Long? = (this as? Number)?.toLong()

        fun parsePage(text: String): InstapaperPage {
            val root = parseJson(text) as? Map<*, *> ?: throw InstapaperException(InstapaperProblem.ServerError)
            val bookmarks = (root["bookmarks"] as? List<*>).orEmpty().mapNotNull(::parseBookmark)
            return InstapaperPage(bookmarks, (root["total"].asLong() ?: bookmarks.size.toLong()).toInt())
        }

        fun parseBookmark(value: Any?): InstapaperBookmark? {
            val map = value as? Map<*, *> ?: return null
            val id = map["id"].asLong() ?: return null
            return InstapaperBookmark(
                id = id,
                url = map["url"] as? String,
                title = (map["title"] as? String).orEmpty(),
                description = (map["description"] as? String).orEmpty(),
                time = map["time"].asLong() ?: 0L,
                liked = map["liked"] == true,
                archived = map["archived"] == true,
                folderId = map["folder_id"].asLong(),
                tags = (map["tags"] as? List<*>).orEmpty().mapNotNull(::parseTag),
            )
        }

        fun parseTag(value: Any?): InstapaperTag? {
            val map = value as? Map<*, *> ?: return null
            val id = map["id"].asLong() ?: return null
            return InstapaperTag(id, (map["name"] as? String).orEmpty())
        }

        /** A list wrapped as {"tags": [...]}. */
        fun parseTagList(
            text: String,
            key: String,
        ): List<InstapaperTag> {
            val root = parseJson(text) as? Map<*, *> ?: throw InstapaperException(InstapaperProblem.ServerError)
            return (root[key] as? List<*>).orEmpty().mapNotNull(::parseTag)
        }

        fun parseFolder(value: Any?): InstapaperFolder? {
            val map = value as? Map<*, *> ?: return null
            val id = map["id"].asLong() ?: return null
            return InstapaperFolder(id, (map["title"] as? String).orEmpty(), (map["position"].asLong() ?: 0L).toInt())
        }

        /** A list wrapped as {"folders": [...]}, in the user's order. */
        fun parseFolderList(text: String): List<InstapaperFolder> {
            val root = parseJson(text) as? Map<*, *> ?: throw InstapaperException(InstapaperProblem.ServerError)
            return (root["folders"] as? List<*>).orEmpty().mapNotNull(::parseFolder).sortedBy { it.position }
        }
    }
}
