package be.wimvandonck.reader.raindrop

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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RaindropAuthTest {
    private val callback = RaindropAuth.REDIRECT_URI

    @Test
    fun `the callback gives the code and state, or says why not`() {
        assertEquals(RaindropAuth.Callback.Code("a b", "s1"), RaindropAuth.parseCallback("$callback?code=a+b&state=s1"))
        assertEquals(RaindropAuth.Callback.Code("c", null), RaindropAuth.parseCallback("$callback?code=c"))
        assertEquals(RaindropAuth.Callback.Denied, RaindropAuth.parseCallback("$callback?error=access_denied"))
        assertEquals(RaindropAuth.Callback.Invalid, RaindropAuth.parseCallback("$callback?state=s1"))
        assertEquals(RaindropAuth.Callback.Invalid, RaindropAuth.parseCallback("https://evil.example/raindrop/callback?code=c"))
        assertTrue(RaindropAuth.isCallback("$callback?code=c#x"))
        assertFalse(RaindropAuth.isCallback("$callback/more?code=c"))
    }

    @Test
    fun `sign-in starts at the Worker with a fresh state`() {
        val state = RaindropAuth.newState()
        assertEquals(32, state.length)
        assertTrue(state.matches(Regex("[0-9a-f]+")))
        assertTrue(state != RaindropAuth.newState())
        assertEquals(RaindropAuth.AUTHORIZE_URL + "?state=$state", RaindropAuth.authorizeUrl(state))
        assertTrue(RaindropAuth.AUTHORIZE_URL.startsWith("https://"))
    }

    @Test
    fun `the Worker's answer gives the tokens`() {
        assertEquals(RaindropAuth.Tokens("a", "r", 1_209_599), RaindropAuth.tokensFrom("""{"access_token":"a","refresh_token":"r","expires_in":1209599}"""))
        assertEquals(RaindropAuth.Tokens("a", "", null), RaindropAuth.tokensFrom("""{"access_token":"a","expires_in":null}"""))
        assertNull(RaindropAuth.tokensFrom("""{"access_token":""}"""))
        assertNull(RaindropAuth.tokensFrom("nope"))
    }

    private fun http(answer: (String) -> Pair<Int, String>) =
        OkHttpClient
            .Builder()
            .addInterceptor(
                Interceptor { chain ->
                    val request = chain.request()
                    val body = request.body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8() }.orEmpty()
                    val (code, text) = answer(body)
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

    @Test
    fun `swapping a code tells done, refused and failed apart`() =
        runBlocking {
            var sent = ""
            val ok = http { body -> sent = body; 200 to """{"access_token":"a","refresh_token":"r","expires_in":10}""" }
            assertEquals(RaindropAuth.Exchange.Done(RaindropAuth.Tokens("a", "r", 10)), RaindropAuth.exchange(ok, mapOf("code" to "c"), RaindropAuth.TOKEN_URL))
            assertEquals("""{"code":"c"}""", sent)
            assertEquals(RaindropAuth.Exchange.Refused, RaindropAuth.exchange(http { 400 to "no" }, mapOf("code" to "c"), RaindropAuth.TOKEN_URL))
            assertEquals(RaindropAuth.Exchange.Failed, RaindropAuth.exchange(http { 502 to "no" }, mapOf("code" to "c"), RaindropAuth.TOKEN_URL))
            assertEquals(RaindropAuth.Exchange.Failed, RaindropAuth.exchange(http { throw IOException("offline") }, mapOf("code" to "c"), RaindropAuth.TOKEN_URL))
        }
}
