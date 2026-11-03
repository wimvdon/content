package to.wim.content.feed

import android.webkit.CookieManager

/**
 * The sign-in browser's cookies (Android's WebView cookie store), the one place a site's sign-in
 * is kept: the sign-in page writes it, the app's own requests to a signed-in site read it and keep
 * what the site sends back, as a browser does (2026-11-03). Which site gets cookies is decided in
 * [SiteSignIns]. A device without a WebView has none: the copy taken at Done is used then.
 */
object SiteCookieJar {
    /** What the store sends to [url] now, or null. */
    fun cookie(url: String): String? = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()

    /** Keeps the cookies [url] set ([setCookies], its Set-Cookie headers), with their own expiry. */
    fun keep(
        url: String,
        setCookies: List<String>,
    ) {
        runCatching {
            val cookies = CookieManager.getInstance()
            setCookies.forEach { cookies.setCookie(url, it) }
            cookies.flush()
        }
    }

    /** Forgets a site's cookies (all it has now, and those of the copy), so removing it signs out. */
    fun forget(
        site: String,
        copy: String,
    ) {
        runCatching {
            val cookies = CookieManager.getInstance()
            for (host in listOf("https://$site", "https://www.$site")) {
                val names = (SiteSignIns.cookieNames(cookies.getCookie(host).orEmpty()) + SiteSignIns.cookieNames(copy)).toSet()
                for (name in names) {
                    cookies.setCookie(host, "$name=; Max-Age=0; Path=/")
                    cookies.setCookie(host, "$name=; Max-Age=0; Path=/; Domain=.$site")
                }
            }
            cookies.flush()
        }
    }
}
