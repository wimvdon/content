package be.wimvandonck.content.feed

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Runs the article extractor ([ArticleExtractor.ASSET]) in a hidden WebView. The view is offline:
 * it loads nothing from the network and gets the page the app downloaded as a string, which the
 * extractor reads without running its scripts or loading its pictures. The view is made on first
 * use and let go after a minute without articles, so it doesn't hold on to memory.
 */
class WebArticleExtractor(
    private val context: Context,
) {
    private val mutex = Mutex()
    private val main = Handler(Looper.getMainLooper())
    private var view: WebView? = null
    private val release =
        Runnable {
            view?.destroy()
            view = null
        }

    /** The article of [html] (downloaded from [pageUrl]) as simple HTML, or null when none is found. */
    suspend fun extract(
        html: String,
        pageUrl: String,
    ): String? =
        mutex.withLock {
            withContext(Dispatchers.Main) {
                main.removeCallbacks(release)
                try {
                    val webView = view ?: start().also { view = it }
                    ArticleExtractor.result(evaluate(webView, ArticleExtractor.script(html, pageUrl)))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // No WebView on this device (or it failed): no article for the reader.
                    null
                } finally {
                    main.postDelayed(release, IDLE_MILLIS)
                }
            }
        }

    private suspend fun start(): WebView {
        val script = withContext(Dispatchers.IO) { context.assets.open(ArticleExtractor.ASSET).bufferedReader().use { it.readText() } }
        val webView = WebView(context.applicationContext)
        try {
            webView.settings.apply {
                // The extractor is JavaScript; the page's own scripts never run.
                javaScriptEnabled = true
                blockNetworkLoads = true
                allowFileAccess = false
                allowContentAccess = false
            }
            suspendCancellableCoroutine { continuation ->
                webView.webViewClient =
                    object : WebViewClient() {
                        override fun onPageFinished(
                            view: WebView,
                            url: String?,
                        ) {
                            if (continuation.isActive) continuation.resume(Unit)
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ) = true
                    }
                webView.loadDataWithBaseURL(null, "<!DOCTYPE html><html><body></body></html>", "text/html", "utf-8", null)
            }
            evaluate(webView, script)
            return webView
        } catch (e: Exception) {
            webView.destroy()
            throw e
        }
    }

    private suspend fun evaluate(
        view: WebView,
        script: String,
    ): String? =
        suspendCancellableCoroutine { continuation ->
            view.evaluateJavascript(script) { result -> if (continuation.isActive) continuation.resume(result) }
        }

    private companion object {
        const val IDLE_MILLIS = 60_000L
    }
}
