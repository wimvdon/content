package io.github.impca201.rssreader.feed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Downloads feeds and web pages over HTTPS. The only network code in the app: it fetches
 * exactly the addresses the user subscribed to (or typed), sends no cookies or identifiers,
 * and follows at most [MAX_REDIRECTS] redirects.
 */
class FeedFetcher(
    private val userAgent: String,
) {
    suspend fun fetch(url: String): Fetched =
        withContext(Dispatchers.IO) {
            var current = FeedDiscovery.normalizeUrl(url) ?: throw FetchException(FetchError.NotFound)
            repeat(MAX_REDIRECTS + 1) {
                val connection =
                    try {
                        URL(current).openConnection() as HttpURLConnection
                    } catch (e: IOException) {
                        throw FetchException(FetchError.Offline, e)
                    }
                try {
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = TIMEOUT_MS
                    connection.readTimeout = TIMEOUT_MS
                    connection.useCaches = false
                    connection.setRequestProperty("User-Agent", userAgent)
                    connection.setRequestProperty("Accept", ACCEPT)
                    val code = connection.responseCode
                    when {
                        code in 300..399 -> {
                            val location = connection.getHeaderField("Location") ?: throw FetchException(FetchError.ServerError)
                            current = redirectTarget(current, location) ?: throw FetchException(FetchError.NotFound)
                        }

                        code == 404 || code == 410 -> throw FetchException(FetchError.NotFound)

                        code !in 200..299 -> throw FetchException(FetchError.ServerError)

                        else -> return@withContext Fetched(current, readLimited(connection))
                    }
                } catch (e: IOException) {
                    throw FetchException(FetchError.Offline, e)
                } finally {
                    connection.disconnect()
                }
            }
            throw FetchException(FetchError.ServerError)
        }

    private fun redirectTarget(
        from: String,
        location: String,
    ): String? = runCatching { URI(from).resolve(URI(location.trim())).toString() }.getOrNull()?.let(FeedDiscovery::normalizeUrl)

    private fun readLimited(connection: HttpURLConnection): ByteArray {
        if (connection.contentLengthLong > MAX_BYTES) throw FetchException(FetchError.TooLarge)
        connection.inputStream.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                if (out.size() > MAX_BYTES) throw FetchException(FetchError.TooLarge)
            }
            return out.toByteArray()
        }
    }

    private companion object {
        const val MAX_REDIRECTS = 5
        const val TIMEOUT_MS = 20_000
        const val MAX_BYTES = 10 * 1024 * 1024
        const val ACCEPT =
            "application/rss+xml, application/atom+xml, application/xml;q=0.9, text/xml;q=0.9, text/html;q=0.8, */*;q=0.5"
    }
}
