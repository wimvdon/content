package to.wim.content.ui.reader

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import to.wim.content.feed.ArticleBlock
import to.wim.content.feed.Embeds
import to.wim.content.feed.PlayerLoad

/**
 * An embedded player, loaded once its card is tapped: the service's own player (or the site's
 * video) in a small web view, the size the service asks for. Its full screen button shows the
 * player over everything. A link the player opens (the service's logo, "watch on …") goes to
 * [onOpenLink], never into the small view.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EmbedPlayer(
    embed: ArticleBlock.Embed,
    onOpenLink: (String) -> Unit,
) {
    val load = remember(embed) { Embeds.playerLoad(embed) } ?: return
    var fullScreen by remember { mutableStateOf<FullScreen?>(null) }
    val size = embed.size
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        val sized =
            if (size.height != null) {
                Modifier.fillMaxWidth().height(size.height.dp)
            } else {
                // Vertical videos (TikTok) stay narrow enough to fit the screen's height.
                Modifier.widthIn(max = if (size.aspect < 1f) 360.dp else 10_000.dp).fillMaxWidth().aspectRatio(size.aspect)
            }
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    // The players are the services' own pages: they need their scripts.
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    setBackgroundColor(android.graphics.Color.BLACK)
                    // Spotify and others keep their session in a cookie of their own.
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient =
                        object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                // The view only holds the player: a link the user taps in it (the logo,
                                // "watch on …") goes to the browser or the service's app. The player's
                                // own redirects, without a tap, stay.
                                if (!request.isForMainFrame || !request.hasGesture()) return false
                                onOpenLink(request.url.toString())
                                return true
                            }
                        }
                    webChromeClient =
                        object : WebChromeClient() {
                            override fun onShowCustomView(
                                view: View,
                                callback: CustomViewCallback,
                            ) {
                                fullScreen = FullScreen(view, callback)
                            }

                            override fun onHideCustomView() {
                                fullScreen = null
                            }
                        }
                    when (load) {
                        // The service's player as the page itself, with Content's web app as the page
                        // that embeds it: YouTube refuses a player without one (error 153, a black box).
                        is PlayerLoad.Url -> loadUrl(load.url, mapOf("Referer" to Embeds.PLAYER_ORIGIN))
                        is PlayerLoad.Page -> loadDataWithBaseURL(Embeds.PLAYER_ORIGIN, load.html, "text/html", "utf-8", null)
                    }
                }
            },
            onRelease = { it.destroy() },
            // Not clipped: a clipped WebView can draw black on some devices.
            modifier = sized.background(Color.Black),
        )
    }
    fullScreen?.let { shown ->
        Dialog(
            onDismissRequest = {
                shown.callback.onCustomViewHidden()
                fullScreen = null
            },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            AndroidView(
                factory = { (shown.view.parent as? ViewGroup)?.removeView(shown.view); shown.view },
                modifier = Modifier.fillMaxSize().background(Color.Black),
            )
        }
    }
}

private class FullScreen(
    val view: View,
    val callback: WebChromeClient.CustomViewCallback,
)
