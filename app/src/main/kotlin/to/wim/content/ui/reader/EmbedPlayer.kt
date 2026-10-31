package to.wim.content.ui.reader

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import to.wim.content.feed.ArticleBlock
import to.wim.content.feed.Embeds

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
    val page = remember(embed) { Embeds.playerPage(embed) } ?: return
    var fullScreen by remember { mutableStateOf<FullScreen?>(null) }
    val size = embed.size
    val shape = MaterialTheme.shapes.medium
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
                    webViewClient =
                        object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                // The page itself only holds the player; whatever it opens goes out.
                                if (!request.isForMainFrame) return false
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
                    loadDataWithBaseURL(Embeds.PLAYER_ORIGIN, page, "text/html", "utf-8", null)
                }
            },
            onRelease = { it.destroy() },
            modifier = sized.clip(shape).background(Color.Black),
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
