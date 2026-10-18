package be.wimvandonck.reader.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * The live connection to the account while the app is on screen: [onVersion] gets every version
 * another device saved (and the current one right after connecting), so their changes come in at
 * once. It connects again by itself, waiting longer after each failure, until [stop].
 */
class LiveUpdates(
    http: OkHttpClient,
    private val key: String,
    private val scope: CoroutineScope,
    private val onVersion: (Long) -> Unit,
) {
    private val client = http.newBuilder().pingInterval(PING_SECONDS, TimeUnit.SECONDS).build()
    private var socket: WebSocket? = null
    private var retry: Job? = null
    private var failures = 0

    @Volatile
    var connected = false
        private set

    @Volatile
    private var running = false

    fun start() {
        if (running) return
        running = true
        connect()
    }

    fun stop() {
        running = false
        retry?.cancel()
        socket?.close(1000, null)
        socket = null
        connected = false
    }

    private fun connect() {
        val request = Request.Builder().url(AccountClient.liveUrl(key)).build()
        socket =
            client.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response,
                    ) {
                        failures = 0
                        connected = true
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        versionOf(text)?.let(onVersion)
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) = dropped(webSocket, retry = code != ACCOUNT_DELETED)

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?,
                    ) = dropped(webSocket, retry = true)
                },
            )
    }

    private fun dropped(
        webSocket: WebSocket,
        retry: Boolean,
    ) {
        if (webSocket != socket) return
        connected = false
        socket = null
        if (!running || !retry) return
        failures++
        this.retry =
            scope.launch {
                delay(retryDelay(failures))
                if (running) connect()
            }
    }

    companion object {
        /** OkHttp pings this often, which keeps the connection open through networks that drop quiet ones. */
        private const val PING_SECONDS = 45L

        /** The server closes with this code when the account was deleted. */
        private const val ACCOUNT_DELETED = 4401

        /** How long to wait before connecting again (doubling, at most a minute), like the web app. */
        fun retryDelay(failures: Int): Long = minOf(60_000L, 2_000L shl (failures - 1).coerceIn(0, 10))

        /** The version in a live message, or null. */
        fun versionOf(text: String): Long? = ((runCatching { Json.parse(text) }.getOrNull() as? Map<*, *>)?.get("version") as? Number)?.toLong()
    }
}
