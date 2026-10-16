package io.github.impca201.rssreader.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder

/**
 * The sync file in Google Drive's hidden app folder (appDataFolder): only this app can see it,
 * and the app can see nothing else in the user's Drive. [token] gives an access token for the
 * drive.appdata scope; [onUnauthorized] forgets a token Google refused, so the next one is fresh.
 */
class GoogleDrive(
    private val http: OkHttpClient,
    private val token: suspend () -> String,
    private val onUnauthorized: suspend (String) -> Unit = {},
    private val apiBase: String = "https://www.googleapis.com",
) : SyncProvider {
    override suspend fun read(): String? {
        val id = findFile() ?: return null
        return call { t -> Request.Builder().url("$apiBase/drive/v3/files/$id?alt=media").header("Authorization", "Bearer $t").build() }
    }

    override suspend fun write(text: String) {
        val id = findFile()
        val json = "application/json; charset=utf-8".toMediaType()
        if (id == null) {
            val body = multipartBody(text)
            call { t ->
                Request
                    .Builder()
                    .url("$apiBase/upload/drive/v3/files?uploadType=multipart&fields=id")
                    .header("Authorization", "Bearer $t")
                    .post(body.toRequestBody("multipart/related; boundary=$BOUNDARY".toMediaType()))
                    .build()
            }
        } else {
            val body: RequestBody = text.toRequestBody(json)
            call { t ->
                Request
                    .Builder()
                    .url("$apiBase/upload/drive/v3/files/$id?uploadType=media&fields=id")
                    .header("Authorization", "Bearer $t")
                    .patch(body)
                    .build()
            }
        }
    }

    private suspend fun findFile(): String? {
        val q = URLEncoder.encode("name = '$FILE_NAME' and trashed = false", "UTF-8")
        val text =
            call { t ->
                Request
                    .Builder()
                    .url("$apiBase/drive/v3/files?spaces=appDataFolder&fields=files(id)&orderBy=modifiedTime%20desc&q=$q")
                    .header("Authorization", "Bearer $t")
                    .build()
            }
        return fileIdFromList(text)
    }

    /** Runs a request, once more with a fresh token when Google refused the first one. */
    private suspend fun call(build: (String) -> Request): String =
        withContext(Dispatchers.IO) {
            var t = token()
            repeat(2) { attempt ->
                val response =
                    try {
                        http.newCall(build(t)).execute()
                    } catch (e: IOException) {
                        throw SyncException(SyncProblem.Offline, e)
                    }
                response.use { r ->
                    when {
                        r.isSuccessful -> return@withContext readBody(r)
                        r.code == 401 && attempt == 0 -> {
                            onUnauthorized(t)
                            t = token()
                            Unit
                        }
                        r.code == 401 || r.code == 403 -> throw SyncException(SyncProblem.NeedsApproval)
                        else -> throw SyncException(SyncProblem.ServerError)
                    }
                }
            }
            throw SyncException(SyncProblem.NeedsApproval)
        }

    private fun readBody(r: Response): String =
        try {
            r.body.string()
        } catch (e: IOException) {
            throw SyncException(SyncProblem.Offline, e)
        }

    companion object {
        const val FILE_NAME = "rss-reader-sync.json"
        const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val BOUNDARY = "rss-reader-sync-boundary"

        /** The newest matching file's id in a Drive files.list answer, or null. */
        fun fileIdFromList(text: String): String? {
            val root = runCatching { Json.parse(text) }.getOrNull() as? Map<*, *> ?: throw SyncException(SyncProblem.ServerError)
            val files = root["files"] as? List<*> ?: return null
            return (files.firstOrNull() as? Map<*, *>)?.get("id") as? String
        }

        /** A Drive multipart upload: the file's name and folder, then its text. */
        fun multipartBody(text: String): String =
            buildString {
                append("--").append(BOUNDARY).append("\r\n")
                append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                append(Json.write(mapOf("name" to FILE_NAME, "parents" to listOf("appDataFolder"), "mimeType" to "application/json")))
                append("\r\n--").append(BOUNDARY).append("\r\n")
                append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                append(text)
                append("\r\n--").append(BOUNDARY).append("--")
            }
    }
}
