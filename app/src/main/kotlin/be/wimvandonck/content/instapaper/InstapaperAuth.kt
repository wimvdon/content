package be.wimvandonck.content.instapaper

import be.wimvandonck.content.sync.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom

/**
 * Signing in with Instapaper (OAuth 2, authorization code flow). The user approves Reader on
 * Instapaper's own page, which sends them back to [REDIRECT_URI] (an Android App Link that opens
 * this app). The one-time code is swapped for a token by the feed loader Worker, because that swap
 * needs the client secret, which can't live in an app (worker/src/instapaper.ts). Its answer also
 * holds this device's key to the user's Reader account (sync, worker/src/account.ts).
 */
object InstapaperAuth {
    /** The Instapaper application's client ID is public; the secret lives only in the Worker. */
    const val CLIENT_ID = "94f820c94dc04d0386c71d7b0c26bd80"

    /** Must be registered with the Instapaper application and equal INSTAPAPER_REDIRECT_URI in worker/wrangler.toml. */
    const val REDIRECT_URI = "https://rss-reader-web.wim-d57.workers.dev/instapaper/callback"
    const val AUTHORIZE_URL = "https://www.instapaper.com/oauth2/authorize"
    const val TOKEN_URL = "https://rss-reader-worker.wim-d57.workers.dev/instapaper/token"

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    sealed interface Callback {
        data class Code(
            val code: String,
            val state: String,
        ) : Callback

        data object Denied : Callback

        data object Invalid : Callback
    }

    /** Instapaper's sign-in page. [state] comes back unchanged and proves the answer belongs to this sign-in. */
    fun authorizeUrl(state: String): String =
        AUTHORIZE_URL + "?" +
            listOf("client_id" to CLIENT_ID, "redirect_uri" to REDIRECT_URI, "response_type" to "code", "state" to state)
                .joinToString("&") { (key, value) -> key + "=" + URLEncoder.encode(value, "UTF-8") }

    fun newState(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** True when [link] is the address Instapaper sends the user back to. */
    fun isCallback(link: String): Boolean = link.substringBefore('#').substringBefore('?') == REDIRECT_URI

    /** What Instapaper put in the address when it sent the user back. */
    fun parseCallback(link: String): Callback {
        if (!isCallback(link)) return Callback.Invalid
        val params =
            link
                .substringBefore('#')
                .substringAfter('?', "")
                .split('&')
                .filter { it.isNotEmpty() }
                .associate { part -> decode(part.substringBefore('=')) to decode(part.substringAfter('=', "")) }
        if (!params["error"].isNullOrEmpty()) return Callback.Denied
        val code = params["code"]
        val state = params["state"]
        return if (!code.isNullOrEmpty() && !state.isNullOrEmpty()) Callback.Code(code, state) else Callback.Invalid
    }

    private fun decode(text: String): String = runCatching { URLDecoder.decode(text, "UTF-8") }.getOrDefault(text)

    /** The session in the Worker's answer to the token swap, or null when it holds no token. */
    fun sessionFrom(text: String): InstapaperSession? {
        val map = runCatching { Json.parse(text) }.getOrNull() as? Map<*, *> ?: return null
        val token = map["access_token"] as? String ?: return null
        if (token.isEmpty()) return null
        val user = map["user"] as? Map<*, *>
        val accountKey = ((map["account"] as? Map<*, *>)?.get("key") as? String)?.takeIf { it.isNotEmpty() }
        return InstapaperSession(token, (user?.get("id") as? Number)?.toLong(), (user?.get("username") as? String).orEmpty(), accountKey)
    }

    /** Swaps the one-time code for a session, through the Worker. */
    suspend fun exchange(
        http: OkHttpClient,
        code: String,
        tokenUrl: String = TOKEN_URL,
    ): InstapaperSession =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url(tokenUrl)
                    .header("Accept", "application/json")
                    .post(Json.write(mapOf("code" to code)).toRequestBody(JSON_TYPE))
                    .build()
            val response =
                try {
                    http.newCall(request).execute()
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
                if (!r.isSuccessful) throw InstapaperException(if (r.code == 400) InstapaperProblem.Invalid else InstapaperProblem.ServerError)
                sessionFrom(text) ?: throw InstapaperException(InstapaperProblem.ServerError)
            }
        }
}
