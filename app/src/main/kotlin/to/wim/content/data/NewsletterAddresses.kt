package to.wim.content.data

/**
 * Newsletter addresses (docs/newsletters.md): an email address per tag, made by the account's feed
 * loader, whose newsletters arrive as a feed. Made in + › Subscribe to newsletter › Your own address;
 * its feed is then added under the tag like any feed, and removing that feed removes the address.
 * Plain Kotlin, unit tested; the web app's `newsletterAddresses.ts` is the same.
 */
data class NewsletterAddress(
    val code: String,
    val tag: String,
    /** The email address to subscribe with, e.g. content+k7q2m9abcdef@in.wim.to. */
    val address: String,
    val feedUrl: String,
)

object NewsletterAddresses {
    private val FEED = Regex("""^https://[^/]+/newsletters/([a-z0-9]{12})/[0-9a-f]{32}/?$""")

    /** The code of a newsletter address's feed, or null for any other feed. */
    fun codeOfFeed(url: String?): String? = url?.trim()?.let { FEED.matchEntire(it) }?.groupValues?.get(1)

    /** One address from the feed loader's answer, or null when it isn't one. */
    fun of(value: Any?): NewsletterAddress? {
        val map = value as? Map<*, *> ?: return null
        val code = map["code"] as? String ?: return null
        val address = map["address"] as? String ?: return null
        val feedUrl = (map["feedUrl"] as? String)?.takeIf { codeOfFeed(it) == code } ?: return null
        return NewsletterAddress(code, (map["tag"] as? String).orEmpty(), address, feedUrl)
    }

    /** The list `GET /account/newsletters` answers. */
    fun listOf(value: Any?): List<NewsletterAddress> = ((value as? Map<*, *>)?.get("addresses") as? List<*>).orEmpty().mapNotNull(::of)

    /** The topic (folder) a new address's feed goes under: the one with that name, if any (case ignored). */
    fun topicFor(
        folders: List<Folder>,
        tag: String,
    ): Folder? = folders.firstOrNull { it.title.trim().equals(tag.trim(), ignoreCase = true) }

    /**
     * The addresses as + › Subscribe to newsletter lists them, grouped by their feed's topic in the
     * topics' order, No topic last; each with its feed's name. An address whose feed isn't here
     * (removed on this device, not yet synced) keeps the name it was made with, under No topic.
     */
    fun grouped(
        addresses: List<NewsletterAddress>,
        feeds: List<Feed>,
        topics: List<Folder>,
    ): List<NewsletterGroup> {
        val byUrl = feeds.associateBy { it.url }
        val rows = addresses.map { a -> val feed = byUrl[a.feedUrl]; Triple(a, feed?.title?.ifBlank { null } ?: a.tag, feed?.folderId?.takeIf { id -> topics.any { it.id == id } }) }
        val groups = topics.mapNotNull { topic -> rows.filter { it.third == topic.id }.takeIf { it.isNotEmpty() }?.let { NewsletterGroup(topic, it.map { r -> NamedAddress(r.second, r.first) }) } }
        val loose = rows.filter { it.third == null }
        return groups + listOfNotNull(loose.takeIf { it.isNotEmpty() }?.let { NewsletterGroup(null, it.map { r -> NamedAddress(r.second, r.first) }) })
    }
}

/** A newsletter address with its feed's name. */
data class NamedAddress(
    val name: String,
    val address: NewsletterAddress,
)

/** The addresses of one topic (null: No topic). */
data class NewsletterGroup(
    val topic: Folder?,
    val addresses: List<NamedAddress>,
)
