package be.wimvandonck.reader.raindrop

import be.wimvandonck.reader.sync.Json
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
 * Connecting Raindrop.io (#112, OAuth 2 authorization code flow) through the feed loader Worker,
 * which holds the client ID and secret (worker/src/raindrop.ts): the browser goes to the Worker's
 * /raindrop/authorize, which sends it on to Raindrop; Raindrop sends it back to [REDIRECT_URI] (an
 * App Link that opens this app; test builds get a page with a button instead); the code is swapped
 * for tokens by the Worker, and renewed there every two weeks.
 */
object RaindropAuth {
    /** Must equal RAINDROP_REDIRECT_URI in worker/wrangler.toml and be registered with the Raindrop app. */
    const val REDIRECT_URI = "https://rss-reader-web.wim-d57.workers.dev/raindrop/callback"
    private const val WORKER = "https://rss-reader-worker.wim-d57.workers.dev"
    const val AUTHORIZE_URL = "$WORKER/raindrop/authorize"
    const val TOKEN_URL = "$WORKER/raindrop/token"
    const val REFRESH_URL = "$WORKER/raindrop/refresh"

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    sealed interface Callback {
        /** Raindrop may or may not send the state back. */
        data class Code(
            val code: String,
            val state: String?,
        ) : Callback

        data object Denied : Callback

        data object Invalid : Callback
    }

    data class Tokens(
        val access: String,
        val refresh: String,
        val expiresInSeconds: Long?,
    )

    sealed interface Exchange {
        data class Done(
            val tokens: Tokens,
        ) : Exchange

        /** Raindrop refused the code or refresh token: connect again. */
        data object Refused : Exchange

        /** Offline or a server error: try again later. */
        data object Failed : Exchange
    }

    fun authorizeUrl(state: String): String = AUTHORIZE_URL + "?state=" + URLEncoder.encode(state, "UTF-8")

    fun newState(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** True when [link] is the address Raindrop sends the user back to. */
    fun isCallback(link: String): Boolean = link.substringBefore('#').substringBefore('?') == REDIRECT_URI

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
        val code = params["code"]?.takeIf { it.isNotEmpty() } ?: return Callback.Invalid
        return Callback.Code(code, params["state"]?.takeIf { it.isNotEmpty() })
    }

    private fun decode(text: String): String = runCatching { URLDecoder.decode(text, "UTF-8") }.getOrDefault(text)

    /** The tokens in the Worker's answer, or null when it holds none. */
    fun tokensFrom(text: String): Tokens? {
        val map = runCatching { Json.parse(text) }.getOrNull() as? Map<*, *> ?: return null
        val access = (map["access_token"] as? String)?.takeIf { it.isNotEmpty() } ?: return null
        return Tokens(access, (map["refresh_token"] as? String).orEmpty(), (map["expires_in"] as? Number)?.toLong())
    }

    /** Swaps a code (or a refresh token) for tokens through the Worker. */
    suspend fun exchange(
        http: OkHttpClient,
        body: Map<String, String>,
        url: String,
    ): Exchange =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .post(Json.write(body).toRequestBody(JSON_TYPE))
                    .build()
            try {
                http.newCall(request).execute().use { r ->
                    val text = r.body.string()
                    when {
                        r.isSuccessful -> tokensFrom(text)?.let { Exchange.Done(it) } ?: Exchange.Failed
                        r.code == 400 -> Exchange.Refused
                        else -> Exchange.Failed
                    }
                }
            } catch (e: IOException) {
                Exchange.Failed
            }
        }
}
