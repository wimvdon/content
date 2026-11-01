package to.wim.content.instapaper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import to.wim.content.sync.AccountClient
import to.wim.content.sync.Json
import java.io.IOException

/**
 * The text of an Instapaper article without a web address (a forwarded email, a PDF; see
 * `data/InstapaperLinks.kt`), from Instapaper's parser through the feed loader, which adds Content's
 * Instaparser key (`POST /instapaper/text`, docs/instapaper.md). Null when there is none: not
 * connected to Instapaper, offline, the month's free credits used up (402) or no text to give; the
 * reader then offers to open it in Instapaper. Asked once per article: the text is then stored in
 * the account like any other ([to.wim.content.data.SharedText]).
 */
class InstapaperText(
    private val http: OkHttpClient,
    private val url: String = URL,
) {
    suspend fun load(
        token: String,
        bookmarkId: Long,
    ): String? =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .post(Json.write(mapOf("id" to bookmarkId)).toRequestBody(JSON_TYPE))
                    .build()
            try {
                http.newCall(request).execute().use { r ->
                    if (!r.isSuccessful) return@withContext null
                    parse(r.body.string())
                }
            } catch (_: IOException) {
                null
            }
        }

    companion object {
        const val URL = AccountClient.BASE_URL + "/instapaper/text"
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

        /** The HTML of the feed loader's answer, `{"title", "html"}`, or null. */
        fun parse(text: String): String? = ((runCatching { Json.parse(text) }.getOrNull() as? Map<*, *>)?.get("html") as? String)?.takeIf { it.isNotBlank() }
    }
}
