package to.wim.content.data

/**
 * Unsubscribe from a newsletter (2026-11-15, docs/newsletters.md): the feed loader does it, not the
 * phone. The app sends the newest email's id of a sender to `<feed URL>/unsubscribe`, and the feed
 * loader answers what it did or what the app should open ([Answer]). Plain Kotlin, unit tested; the
 * web app's `unsubscribe.ts` is the same.
 */
object NewsletterUnsubscribe {
    /** What the feed loader answered. */
    sealed interface Answer {
        /** It unsubscribed (the sender's one-click unsubscribe took it). */
        data object Done : Answer

        /** Open this https page in the browser to unsubscribe there. */
        data class Open(
            val url: String,
        ) : Answer

        /** Write this email (a mailto: link) to unsubscribe. */
        data class Mailto(
            val uri: String,
        ) : Answer

        /** The email offers no way to unsubscribe that the feed loader found. */
        data object None : Answer
    }

    private val ENTRY = Regex("""^urn:content:newsletter:[a-z0-9]{12}:([0-9a-f]{16})$""")

    /** The email's id in a newsletter article's id (`urn:content:newsletter:<code>:<id>`), or null when it isn't one. */
    fun emailIdOf(articleId: String?): String? = articleId?.trim()?.let { ENTRY.matchEntire(it) }?.groupValues?.get(1)

    /** Where to ask for newsletter feed [feedUrl]: its address with `/unsubscribe`; null when it isn't a newsletter feed. */
    fun routeOf(feedUrl: String?): String? = if (NewsletterAddresses.isNewsletterFeed(feedUrl)) feedUrl!!.trim().trimEnd('/') + "/unsubscribe" else null

    /** Whether [article] can be unsubscribed from: a newsletter feed's email. */
    fun canUnsubscribe(article: Article?): Boolean = article != null && routeOf(article.feedUrl) != null && emailIdOf(article.id) != null

    /**
     * The email to unsubscribe with: the newest of [sender] (with the senders combined into it)
     * among [articles], whose unsubscribe link is the most likely to still work; null when none is
     * a newsletter email.
     */
    fun newestOf(
        articles: List<Article>,
        sender: NewsletterSender,
        senders: NewsletterSenders,
    ): Article? =
        articles
            .filter { it.feedUrl == sender.feedUrl && canUnsubscribe(it) && senders.groupOf(it) == sender.key }
            .maxByOrNull { it.sortTime }

    /** The feed whose route unsubscribes with [email]: the one it is stored under, also for a sender shown under another tag. */
    fun feedOf(email: Article): String = email.storedFeedUrl ?: email.feedUrl

    /** The feed loader's answer (parsed JSON), or null when it isn't one. Only https pages and mailto: links are opened. */
    fun answerOf(value: Any?): Answer? {
        val map = value as? Map<*, *> ?: return null
        if (map["done"] == true) return Answer.Done
        (map["open"] as? String)?.trim()?.let { url ->
            return if (url.startsWith("https://", ignoreCase = true)) Answer.Open(url) else null
        }
        (map["mailto"] as? String)?.trim()?.let { uri ->
            return if (uri.startsWith("mailto:", ignoreCase = true)) Answer.Mailto(uri) else null
        }
        if (map["none"] == true) return Answer.None
        return null
    }
}
