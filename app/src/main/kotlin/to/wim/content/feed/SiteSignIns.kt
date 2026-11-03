package to.wim.content.feed

import to.wim.content.sync.Json
import java.net.URI
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

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

    /**
     * [cookie] with what the site sent back while it was used ([setCookies], its Set-Cookie headers),
     * as a browser keeps them: a renewed cookie replaces the old one, a new one is added, and one the
     * site clears (an empty value, Max-Age 0 or less, an Expires in the past) goes. A site renews its
     * sign-in this way; without it the kept copy runs out while the site still knows you (2026-11-03).
     * A cookie for another domain than [site] is ignored. Null when nothing changes.
     */
    fun updated(
        site: String,
        cookie: String,
        setCookies: List<String>,
        now: Long = System.currentTimeMillis(),
    ): String? {
        val jar = LinkedHashMap<String, String>()
        for (part in cookie.split(';')) {
            val name = part.substringBefore('=').trim()
            if (name.isNotEmpty() && '=' in part) jar[name] = part.substringAfter('=').trim()
        }
        val before = LinkedHashMap(jar)
        for (header in setCookies) {
            val parts = header.split(';')
            val first = parts.first()
            val name = first.substringBefore('=').trim()
            if (name.isEmpty() || '=' !in first) continue
            val value = first.substringAfter('=').trim()
            val attrs = parts.drop(1).associate { it.substringBefore('=').trim().lowercase() to it.substringAfter('=', "").trim() }
            val domain = attrs["domain"]?.removePrefix(".")?.lowercase()?.removePrefix("www.")
            if (domain != null && domain != site && !domain.endsWith(".$site") && !site.endsWith(".$domain")) continue
            val maxAge = attrs["max-age"]?.toLongOrNull()
            val expires = attrs["expires"]?.let { runCatching { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull() }
            val cleared = value.isEmpty() || (maxAge != null && maxAge <= 0) || (maxAge == null && expires != null && expires <= now)
            if (cleared) jar.remove(name) else jar[name] = value
        }
        if (jar == before) return null
        return jar.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

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
