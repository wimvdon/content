package to.wim.content.instapaper

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
import org.junit.Test

/** The feed loader's answers for an email's text; nothing goes to the network. */
class InstapaperTextTest {
    private val sent = mutableListOf<String>()

    private fun client(
        code: Int,
        body: String,
    ) = InstapaperText(
        OkHttpClient
            .Builder()
            .addInterceptor(
                Interceptor { chain ->
                    val r = chain.request()
                    sent += "${r.method} ${r.url} ${r.header("Authorization")} ${r.body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8() }}"
                    Response
                        .Builder()
                        .request(r)
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message("")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                },
            ).build(),
    )

    @Test
    fun `asks the feed loader with the token and the article, and takes its HTML`() =
        runBlocking {
            val html = client(200, """{"title":"News","html":"<p>Hi</p>"}""").load("tok123", 42)
            assertEquals("<p>Hi</p>", html)
            assertEquals(listOf("""POST ${InstapaperText.URL} Bearer tok123 {"id":42}"""), sent)
        }

    @Test
    fun `no text when the credits are used up or nothing came`() =
        runBlocking {
            assertNull(client(402, "Payment required").load("tok123", 42))
            assertNull(client(200, """{"title":"","html":""}""").load("tok123", 42))
            assertNull(client(200, "not json").load("tok123", 42))
        }
}
