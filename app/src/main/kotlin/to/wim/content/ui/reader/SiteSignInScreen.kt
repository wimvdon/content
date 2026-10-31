package to.wim.content.ui.reader

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import to.wim.content.R
import to.wim.content.feed.SiteSignIns

/**
 * Signing in to a site you pay for (#180): the site's own page in a small browser, where you sign
 * in as usual. Done keeps the cookies the site set (for that site only, on this device), so its
 * articles load in full. Back leaves without keeping anything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteSignInScreen(
    url: String,
    onDone: (site: String, cookie: String?) -> Unit,
    onBack: () -> Unit,
) {
    val site = remember(url) { SiteSignIns.siteOf(url) ?: url }
    var current by remember { mutableStateOf(url) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.site_sign_in_title, site)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back)) }
                },
                actions = {
                    TextButton(onClick = {
                        val cookies = CookieManager.getInstance()
                        cookies.flush()
                        onDone(site, cookies.getCookie(current)?.takeIf { it.isNotBlank() } ?: cookies.getCookie(url))
                    }) { Text(stringResource(R.string.site_sign_in_done)) }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        // Signing in needs the site's own scripts; this view shows only that site's pages.
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        webViewClient =
                            object : WebViewClient() {
                                override fun onPageFinished(
                                    view: WebView,
                                    page: String,
                                ) {
                                    current = page
                                }
                            }
                        loadUrl(url)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    DisposableEffect(Unit) { onDispose { CookieManager.getInstance().flush() } }
}

/** Forgets a site's sign-in in the sign-in browser too, so removing it signs out there. */
fun forgetSiteCookies(
    site: String,
    cookie: String,
) {
    val cookies = CookieManager.getInstance()
    for (host in listOf("https://$site", "https://www.$site")) {
        for (name in SiteSignIns.cookieNames(cookie)) cookies.setCookie(host, "$name=; Max-Age=0; Path=/")
    }
    cookies.flush()
}
