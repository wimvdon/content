package be.wimvandonck.content.data

/**
 * One text per saved article, shared by every device (docs/account.md, "Stored texts"): highlights
 * then find their passage in the same text on the phone, on the web and later elsewhere. The first
 * device to extract the article stores it in the Reader account; every other device takes that one,
 * and when two extract it at the same moment, the one stored first wins for both. Plain Kotlin.
 */
object SharedText {
    /**
     * The text to show for an article: for a saved one ([saved]), the account's ([stored]) when it
     * has one, else this device's extraction ([extract]), stored for the others ([store] gives back
     * the one that won); otherwise just this device's. Null when there is none at all. A failed
     * account call (offline, signed out) counts as no stored text: the device's own is shown.
     */
    suspend fun resolve(
        saved: Boolean,
        stored: suspend () -> String?,
        extract: suspend () -> String?,
        store: suspend (String) -> String?,
    ): String? {
        if (saved) runCatching { stored() }.getOrNull()?.let { return it }
        val own = extract() ?: return null
        if (!saved) return own
        return runCatching { store(own) }.getOrNull() ?: own
    }
}
