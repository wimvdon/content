package to.wim.content.feed

import to.wim.content.sync.Json
import java.net.URI

/** Signed in to a site you subscribe to (#180): its sign-in cookies, kept on this device only. */
data class SiteSignIn(
    /** The site, without "www." ("standaard.be"). */
    val site: String,
    /** The Cookie header the site set while signing in ("a=1; b=2"). */
    val cookie: String,
    val savedAt: Long,
)

/**
 * Which sign-in goes with an address (#180): a site's cookies go only to that site and its
 * subdomains, over HTTPS, so the full text of articles you pay for loads. Plain Kotlin, unit tested.
 */
object SiteSignIns {
    /** The site of an address, without "www.", or null when it isn't an https address. */
    fun siteOf(url: String): String? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        return uri.host?.lowercase()?.removePrefix("www.")?.takeIf { it.contains('.') }
    }

    /** The sign-in for [url]: its own site's, or a parent site's ("www.standaard.be" uses "standaard.be"). */
    fun forUrl(
        url: String,
        signIns: Collection<SiteSignIn>,
    ): SiteSignIn? {
        val host = siteOf(url) ?: return null
        return signIns.filter { host == it.site || host.endsWith("." + it.site) }.maxByOrNull { it.site.length }
    }

    /** The cookies to send to [url], or null. */
    fun cookieFor(
        url: String,
        signIns: Collection<SiteSignIn>,
    ): String? = forUrl(url, signIns)?.cookie?.takeIf { it.isNotBlank() }

    /** The names of the cookies in a Cookie header, to remove them from the sign-in browser. */
    fun cookieNames(cookie: String): List<String> = cookie.split(';').mapNotNull { part -> part.substringBefore('=').trim().takeIf { it.isNotEmpty() } }

    fun encode(signIns: Collection<SiteSignIn>): String = Json.write(signIns.map { linkedMapOf("site" to it.site, "cookie" to it.cookie, "savedAt" to it.savedAt) })

    fun decode(text: String?): List<SiteSignIn> {
        val list = text?.let { runCatching { Json.parse(it) }.getOrNull() } as? List<*> ?: return emptyList()
        return list.mapNotNull { v ->
            val m = v as? Map<*, *> ?: return@mapNotNull null
            SiteSignIn(m["site"] as? String ?: return@mapNotNull null, m["cookie"] as? String ?: return@mapNotNull null, (m["savedAt"] as? Number)?.toLong() ?: 0L)
        }
    }
}
