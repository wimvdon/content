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
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.delay
import to.wim.content.feed.ArticleBlock
import to.wim.content.feed.Embeds
import to.wim.content.feed.PlayerLoad

/**
 * An embedded player, shown as soon as the article is: the service's own player (or the site's
 * video) in a small web view, the size the service asks for. Its full screen button shows the
 * player over everything. Audio players of a fixed height draw their own card on a see-through
 * view. The player loads once the view has its size, so it picks the right layout. A link the player opens (the service's logo, "watch on …") goes to
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
    // A post (Bluesky, Mastodon, …) takes the height of what it shows: measured a few times while it loads.
    var view by remember(embed) { mutableStateOf<WebView?>(null) }
    var measured by remember(embed) { mutableStateOf<Int?>(null) }
    if (size.fitsContent) {
        LaunchedEffect(view) {
            val webView = view ?: return@LaunchedEffect
            repeat(MEASURES) {
                delay(MEASURE_EVERY_MS)
                webView.evaluateJavascript(Embeds.MEASURE_SCRIPT) { result -> Embeds.measuredHeight(result)?.let { measured = it } }
            }
        }
    }
    // An audio player (Spotify, SoundCloud, …) draws its own card, on a see-through view.
    val seeThrough = remember(embed) { Embeds.seeThrough(embed) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        val sized =
            if (size.fitsContent) {
                Modifier.fillMaxWidth().height((measured ?: Embeds.MIN_POST_HEIGHT).dp)
            } else if (size.height != null) {
                Modifier.fillMaxWidth().height(size.height.dp)
            } else {
                // Vertical videos (TikTok) stay narrow enough to fit the screen's height.
                Modifier.widthIn(max = if (size.aspect < 1f) 360.dp else 10_000.dp).fillMaxWidth().aspectRatio(size.aspect)
            }
        AndroidView(
            factory = { context ->
                WebView(context).also { view = it }.apply {
                    // The players are the services' own pages: they need their scripts.
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Shown straight away, so a player only plays once tapped.
                    settings.mediaPlaybackRequiresUserGesture = true
                    // The player's own text size: the phone's larger font size would cram its fixed height.
                    settings.textZoom = 100
                    setBackgroundColor(if (seeThrough) android.graphics.Color.TRANSPARENT else android.graphics.Color.BLACK)
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
                    // Loaded once the view has its size: a player picks its layout from it (Spotify
                    // came out too short, Bandcamp black, when loaded before).
                    whenSized {
                        when (load) {
                            // The service's player as the page itself, with Content's web app as the page
                            // that embeds it: YouTube refuses a player without one (error 153, a black box).
                            is PlayerLoad.Url -> loadUrl(load.url, mapOf("Referer" to Embeds.PLAYER_ORIGIN))
                            is PlayerLoad.Page -> loadDataWithBaseURL(Embeds.PLAYER_ORIGIN, load.html, "text/html", "utf-8", null)
                        }
                    }
                }
            },
            onRelease = { it.destroy() },
            // Not clipped: a clipped WebView can draw black on some devices.
            modifier = if (seeThrough) sized else sized.background(Color.Black),
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

/** Runs [action] once, as soon as the view has a width and a height. */
private fun View.whenSized(action: () -> Unit) {
    if (width > 0 && height > 0) {
        action()
        return
    }
    addOnLayoutChangeListener(
        object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View,
                left: Int,
                top: Int,
                right: Int,
                bottom: Int,
                oldLeft: Int,
                oldTop: Int,
                oldRight: Int,
                oldBottom: Int,
            ) {
                if (right - left <= 0 || bottom - top <= 0) return
                v.removeOnLayoutChangeListener(this)
                // After this layout pass, so the page sees the view's final size.
                v.post(action)
            }
        },
    )
}

/** How often, and how many times, a post's height is measured while it loads (about 15 seconds). */
private const val MEASURES = 30
private const val MEASURE_EVERY_MS = 500L

private class FullScreen(
    val view: View,
    val callback: WebChromeClient.CustomViewCallback,
)
